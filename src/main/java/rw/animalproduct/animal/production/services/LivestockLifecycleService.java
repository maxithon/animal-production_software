package rw.animalproduct.animal.production.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LivestockLifecycleService — FIXED VERSION
 *
 * Root causes of all-zeros dashboard (3 bugs fixed):
 *
 * BUG 1 — getAgeInDays() only used dateReceived, but most animals have
 *          dateReceived = NULL and use birthDate instead.
 *          FIX: fall back to birthDate when dateReceived is null.
 *
 * BUG 2 — countPregnant() only checked livestock.status = 'PREGNANT',
 *          but confirmed pregnancies live in livestock_breeding.status = 'CONFIRMED_PREGNANT'.
 *          The livestock row itself is never updated to PREGNANT in the current workflow.
 *          FIX: also count animals that have a CONFIRMED_PREGNANT breeding record.
 *
 * BUG 3 — getRecentlyBorn() required mother != null OR acquisitionMethod == "BORN",
 *          which excluded recently purchased newborns (GOA-007, GOA-008 have
 *          acquisition_method = 'PURCHASE' but dateReceived within last 30 days).
 *          FIX: use age-based detection (same as v_newborn_animals view does).
 */
@Service
public class LivestockLifecycleService {

    // ── Age thresholds (days) ─────────────────────────────────────────
    private static final int NEWBORN_DAYS       = 30;
    private static final int YOUNG_DAYS         = 180;
    private static final int PRE_BREEDING_DAYS  = 365;
    private static final int APPROACHING_DAYS   = 60;
    private static final int NURSING_DAYS       = 90;

    private final LivestockRepository        livestockRepository;
    private final LivestockBreedingRepository breedingRepository;

    @Autowired
    public LivestockLifecycleService(LivestockRepository livestockRepository,
                                     LivestockBreedingRepository breedingRepository) {
        this.livestockRepository = livestockRepository;
        this.breedingRepository  = breedingRepository;
    }

    // =========================================================================
    // AGE HELPERS  ← BUG 1 FIX: use birthDate as fallback for dateReceived
    // =========================================================================

    /**
     * Returns the animal's age in days.
     *
     * Priority:
     *   1. dateReceived  (set when the animal was received / registered)
     *   2. birthDate     (set for animals born on-farm or with known DOB)
     *
     * Previously only dateReceived was used, causing age = 0 for most animals
     * whose dateReceived is NULL in the database.
     */
    public long getAgeInDays(Livestock animal) {
        if (animal == null) return 0;

        LocalDate referenceDate = animal.getDateReceived() != null
                ? animal.getDateReceived()
                : animal.getBirthDate();   // ← FIXED: fall back to birthDate

        if (referenceDate == null) return 0;

        long days = ChronoUnit.DAYS.between(referenceDate, LocalDate.now());
        return Math.max(0, days);
    }

    public long getAgeInMonths(Livestock animal) {
        if (animal == null) return 0;

        LocalDate referenceDate = animal.getDateReceived() != null
                ? animal.getDateReceived()
                : animal.getBirthDate();   // ← FIXED: fall back to birthDate

        if (referenceDate == null) return 0;

        return Math.max(0, ChronoUnit.MONTHS.between(referenceDate, LocalDate.now()));
    }

    // =========================================================================
    // STAGE COMPUTATION
    // =========================================================================

    public String getCurrentStage(Livestock animal) {
        if (animal == null) return "UNKNOWN";

        if (Livestock.STATUS_DEAD.equals(animal.getStatus()))  return "DECEASED";
        if (Livestock.STATUS_SOLD.equals(animal.getStatus()))  return "SOLD";

        // Pregnant check — status field OR confirmed breeding record
        if (Livestock.STATUS_PREGNANT.equals(animal.getStatus())) return "PREGNANT";
        if (Boolean.TRUE.equals(animal.getIsPregnant()))           return "PREGNANT";
        if (hasConfirmedPregnancy(animal))                         return "PREGNANT"; // ← FIXED

        long ageDays = getAgeInDays(animal);

        // In active breeding?
        boolean inBreeding = breedingRepository
                .findByLivestockId(animal.getId())
                .stream()
                .anyMatch(b -> LivestockBreeding.STATUS_PENDING.equals(b.getStatus()));
        if (inBreeding) return "IN_BREEDING";

        // Nursing?
        if (animal.getLastBirthDate() != null) {
            long daysSinceBirth = ChronoUnit.DAYS.between(animal.getLastBirthDate(), LocalDate.now());
            if (daysSinceBirth >= 0 && daysSinceBirth <= NURSING_DAYS) return "NURSING";
        }

        // Age-based stages
        if (ageDays <= NEWBORN_DAYS)       return "NEWBORN";
        if (ageDays <= YOUNG_DAYS)         return "YOUNG";
        if (ageDays <= PRE_BREEDING_DAYS)  return "PRE_BREEDING";
        return "READY_TO_BREED";
    }

    public String getNextMilestone(Livestock animal) {
        String stage = getCurrentStage(animal);
        long ageDays = getAgeInDays(animal);
        switch (stage) {
            case "NEWBORN":
                return "Enters young stage in " + (NEWBORN_DAYS - ageDays) + " days";
            case "YOUNG":
                return "Enters pre-breeding in " + (YOUNG_DAYS - ageDays) + " days";
            case "PRE_BREEDING":
                return "Ready to breed in " + (PRE_BREEDING_DAYS - ageDays) + " days";
            case "READY_TO_BREED":
                return "Record a breeding event to track pregnancy";
            case "IN_BREEDING":
                return "Awaiting pregnancy confirmation";
            case "PREGNANT":
                if (animal.getExpectedDueDate() != null) {
                    long days = ChronoUnit.DAYS.between(LocalDate.now(), animal.getExpectedDueDate());
                    return days >= 0
                            ? "Expected to give birth in " + days + " days"
                            : "Overdue by " + Math.abs(days) + " days";
                }
                // Check breeding record for due date
                return getPregnantAnimals().stream()
                        .filter(b -> b.getId().equals(animal.getId()))
                        .findFirst()
                        .map(b -> b.getExpectedDueDate() != null
                                ? "Due " + b.getExpectedDueDate()
                                : "Due date not set")
                        .orElse("Due date not set");
            case "NURSING":
                if (animal.getLastBirthDate() != null) {
                    long nursingLeft = NURSING_DAYS - ChronoUnit.DAYS.between(animal.getLastBirthDate(), LocalDate.now());
                    return "Nursing ends in approximately " + nursingLeft + " days";
                }
                return "Nursing period";
            default:
                return "—";
        }
    }

    // =========================================================================
    // COUNTS
    // =========================================================================

    public long countAll() {
        return getActiveAnimals().size();
    }

    public long countNewborns() {
        return getActiveAnimals().stream()
                .filter(l -> {
                    long d = getAgeInDays(l);
                    return d > 0 && d <= NEWBORN_DAYS;
                })
                .count();
    }

    /**
     * YOUNG: 31–180 days (returned separately from pre-breeding now)
     */
    public long countYoung() {
        return getActiveAnimals().stream()
                .filter(l -> {
                    long d = getAgeInDays(l);
                    return d > NEWBORN_DAYS && d <= YOUNG_DAYS;
                })
                .count();
    }

    public long countPreBreeding() {
        return getActiveAnimals().stream()
                .filter(l -> {
                    long d = getAgeInDays(l);
                    return d > YOUNG_DAYS && d <= PRE_BREEDING_DAYS;
                })
                .count();
    }

    public long countReadyToBreed() {
        return getReadyToBreed().size();
    }

    public long countInBreeding() {
        return breedingRepository.findByStatus(LivestockBreeding.STATUS_PENDING).size();
    }

    /**
     * BUG 2 FIX: count pregnant animals from BOTH sources:
     *   - livestock.status = 'PREGNANT'  (manually set)
     *   - livestock_breeding.status = 'CONFIRMED_PREGNANT'  (set by breeding workflow)
     *
     * Previously only the first source was checked, missing all confirmed pregnancies
     * because the livestock row status was never updated to 'PREGNANT'.
     */
    public long countPregnant() {
        // IDs from livestock.status = 'PREGNANT'
        Set<UUID> fromStatus = livestockRepository.findByStatus(Livestock.STATUS_PREGNANT)
                .stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .map(Livestock::getId)
                .collect(Collectors.toSet());

        // IDs from confirmed breeding records
        Set<UUID> fromBreeding = breedingRepository.findByStatus(LivestockBreeding.STATUS_CONFIRMED_PREGNANT)
                .stream()
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .filter(b -> b.getLivestock() != null)
                .map(b -> b.getLivestock().getId())
                .collect(Collectors.toSet());

        // Union of both sets (no double counting)
        Set<UUID> all = new HashSet<>(fromStatus);
        all.addAll(fromBreeding);
        return all.size();
    }

    public long countDueSoon(int withinDays) {
        return getDueSoon(withinDays).size();
    }

    public int countAllNotifications() {
        return getDueSoon(30).size()
                + getOverdue().size()
                + getOverduePregnancyCheck().size()
                + getApproachingBreedingAge().size()
                + getRecentlyBorn(14).size()
                + getFailedBreedings().size();
    }

    // =========================================================================
    // LISTS — age-based  (all now use the fixed getAgeInDays)
    // =========================================================================

    public List<Livestock> getYoungFemales() {
        return getActiveAnimals().stream()
                .filter(l -> "FEMALE".equalsIgnoreCase(l.getGender()))
                .filter(l -> {
                    long d = getAgeInDays(l);
                    return d > NEWBORN_DAYS && d <= PRE_BREEDING_DAYS;
                })
                .sorted(Comparator.comparingLong(this::getAgeInDays))
                .collect(Collectors.toList());
    }

    public List<Livestock> getYoungMales() {
        return getActiveAnimals().stream()
                .filter(l -> "MALE".equalsIgnoreCase(l.getGender()))
                .filter(l -> {
                    long d = getAgeInDays(l);
                    return d > NEWBORN_DAYS && d <= PRE_BREEDING_DAYS;
                })
                .sorted(Comparator.comparingLong(this::getAgeInDays))
                .collect(Collectors.toList());
    }

    public List<Livestock> getApproachingBreedingAge() {
        return getActiveAnimals().stream()
                .filter(l -> {
                    long d = getAgeInDays(l);
                    return d > (PRE_BREEDING_DAYS - APPROACHING_DAYS) && d <= PRE_BREEDING_DAYS;
                })
                .sorted(Comparator.comparingLong(this::getAgeInDays))
                .collect(Collectors.toList());
    }

    public List<Livestock> getReadyToBreed() {
        Set<UUID> inBreeding = breedingRepository.findByStatus(LivestockBreeding.STATUS_PENDING)
                .stream()
                .filter(b -> b.getLivestock() != null)
                .map(b -> b.getLivestock().getId())
                .collect(Collectors.toSet());

        Set<UUID> confirmedPregnant = breedingRepository.findByStatus(LivestockBreeding.STATUS_CONFIRMED_PREGNANT)
                .stream()
                .filter(b -> b.getLivestock() != null)
                .map(b -> b.getLivestock().getId())
                .collect(Collectors.toSet());

        return getActiveAnimals().stream()
                .filter(l -> "FEMALE".equalsIgnoreCase(l.getGender()))
                .filter(l -> getAgeInDays(l) > PRE_BREEDING_DAYS)
                .filter(l -> !Livestock.STATUS_PREGNANT.equals(l.getStatus()))
                .filter(l -> !confirmedPregnant.contains(l.getId()))  // ← FIXED: exclude confirmed pregnancies
                .filter(l -> !inBreeding.contains(l.getId()))
                .filter(l -> !isCurrentlyNursing(l))
                .sorted(Comparator.comparingLong(this::getAgeInDays).reversed())
                .collect(Collectors.toList());
    }

    public List<Livestock> getMalesReadyToBreed() {
        return getActiveAnimals().stream()
                .filter(l -> "MALE".equalsIgnoreCase(l.getGender()))
                .filter(l -> getAgeInDays(l) > PRE_BREEDING_DAYS)
                .sorted(Comparator.comparingLong(this::getAgeInDays).reversed())
                .collect(Collectors.toList());
    }

    public List<Livestock> getNursingAnimals() {
        return getActiveAnimals().stream()
                .filter(this::isCurrentlyNursing)
                .collect(Collectors.toList());
    }

    /**
     * BUG 3 FIX: use age-based detection (≤ NEWBORN_DAYS) instead of
     * requiring mother != null OR acquisitionMethod == "BORN".
     *
     * Your recently added animals (GOA-007, GOA-008) are PURCHASE method
     * but were received/born within the last 30 days — they should appear
     * as newborns. The v_newborn_animals view already does this correctly.
     */
    public List<Livestock> getRecentlyBorn(int withinDays) {
        return getActiveAnimals().stream()
                .filter(l -> {
                    long ageDays = getAgeInDays(l);
                    return ageDays > 0 && ageDays <= withinDays;
                })
                .sorted(Comparator.comparingLong(this::getAgeInDays))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // LISTS — pregnancy  (BUG 2 FIX applied here too)
    // =========================================================================

    /**
     * Returns all pregnant animals from both sources:
     *   1. livestock.status = 'PREGNANT'
     *   2. livestock_breeding.status = 'CONFIRMED_PREGNANT'
     */
    public List<Livestock> getPregnantAnimals() {
        // Animals explicitly marked PREGNANT in livestock table
        Set<UUID> alreadyIncluded = new HashSet<>();
        List<Livestock> result = new ArrayList<>();

        livestockRepository.findByStatus(Livestock.STATUS_PREGNANT).stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .forEach(l -> {
                    result.add(l);
                    alreadyIncluded.add(l.getId());
                });

        // Animals with confirmed pregnancy in breeding table
        breedingRepository.findByStatus(LivestockBreeding.STATUS_CONFIRMED_PREGNANT).stream()
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .filter(b -> b.getLivestock() != null)
                .forEach(b -> {
                    Livestock l = b.getLivestock();
                    if (!alreadyIncluded.contains(l.getId())) {
                        // Enrich with due date from breeding record if not set on livestock
                        if (l.getExpectedDueDate() == null && b.getExpectedDueDate() != null) {
                            l.setExpectedDueDate(b.getExpectedDueDate());
                        }
                        result.add(l);
                        alreadyIncluded.add(l.getId());
                    }
                });

        result.sort(Comparator.comparing(
                l -> l.getExpectedDueDate() != null ? l.getExpectedDueDate() : LocalDate.MAX));
        return result;
    }

    public List<Livestock> getDueSoon(int withinDays) {
        LocalDate today  = LocalDate.now();
        LocalDate cutoff = today.plusDays(withinDays);

        // Also check breeding records for due dates
        Set<UUID> dueSoonFromBreeding = breedingRepository
                .findByStatus(LivestockBreeding.STATUS_CONFIRMED_PREGNANT).stream()
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .filter(b -> b.getExpectedDueDate() != null)
                .filter(b -> !b.getExpectedDueDate().isBefore(today) && !b.getExpectedDueDate().isAfter(cutoff))
                .filter(b -> b.getLivestock() != null)
                .map(b -> b.getLivestock().getId())
                .collect(Collectors.toSet());

        return getPregnantAnimals().stream()
                .filter(l -> {
                    if (l.getExpectedDueDate() != null) {
                        return !l.getExpectedDueDate().isBefore(today) && !l.getExpectedDueDate().isAfter(cutoff);
                    }
                    return dueSoonFromBreeding.contains(l.getId());
                })
                .collect(Collectors.toList());
    }

    public List<Livestock> getOverdue() {
        LocalDate today = LocalDate.now();
        return getPregnantAnimals().stream()
                .filter(l -> l.getExpectedDueDate() != null && l.getExpectedDueDate().isBefore(today))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getDueDateCalendar() {
        return getPregnantAnimals().stream()
                .filter(l -> l.getExpectedDueDate() != null)
                .map(l -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",        l.getId());
                    m.put("tagNumber", l.getTagNumber());
                    m.put("dueDate",   l.getExpectedDueDate().toString());
                    m.put("category",  l.getLivestockCategory() != null ? l.getLivestockCategory().getName() : null);
                    long days = ChronoUnit.DAYS.between(LocalDate.now(), l.getExpectedDueDate());
                    m.put("daysLeft",  days);
                    m.put("status",    days < 0 ? "OVERDUE" : days <= 7 ? "CRITICAL" : days <= 30 ? "SOON" : "OK");
                    return m;
                })
                .sorted(Comparator.comparing(m -> (String) m.get("dueDate")))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // LISTS — breeding
    // =========================================================================

    public List<LivestockBreeding> getActiveBreedings() {
        return breedingRepository.findByStatus(LivestockBreeding.STATUS_PENDING);
    }

    public List<LivestockBreeding> getPendingPregnancyCheck() {
        return breedingRepository
                .findByExpectedPregnancyCheckDateBeforeAndStatus(LocalDate.now(), LivestockBreeding.STATUS_PENDING);
    }

    public List<LivestockBreeding> getOverduePregnancyCheck() {
        return getPendingPregnancyCheck();
    }

    public List<LivestockBreeding> getRecentlyBred(int withinDays) {
        LocalDate cutoff = LocalDate.now().minusDays(withinDays);
        return breedingRepository.findAll().stream()
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .filter(b -> b.getBreedingDate() != null && b.getBreedingDate().isAfter(cutoff))
                .sorted(Comparator.comparing(LivestockBreeding::getBreedingDate).reversed())
                .collect(Collectors.toList());
    }

    public List<LivestockBreeding> getFailedBreedings() {
        return breedingRepository.findByStatus(LivestockBreeding.STATUS_FAILED);
    }

    public double getBreedingSuccessRate() {
        List<LivestockBreeding> all = breedingRepository.findAll().stream()
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .collect(Collectors.toList());
        if (all.isEmpty()) return 0.0;
        long confirmed = all.stream()
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                        || LivestockBreeding.STATUS_CONFIRMED.equals(b.getStatus()))
                .count();
        return (confirmed * 100.0) / all.size();
    }

    public double getAvgDaysToConception() {
        return breedingRepository.findAll().stream()
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                        || LivestockBreeding.STATUS_CONFIRMED.equals(b.getStatus()))
                .filter(b -> b.getBreedingDate() != null && b.getExpectedPregnancyCheckDate() != null)
                .mapToLong(b -> ChronoUnit.DAYS.between(b.getBreedingDate(), b.getExpectedPregnancyCheckDate()))
                .average()
                .orElse(0.0);
    }

    // =========================================================================
    // SUGGESTIONS
    // =========================================================================

    public List<Map<String, Object>> getBreedingSuggestions() {
        List<Livestock> females = getReadyToBreed();
        List<Livestock> males   = getMalesReadyToBreed();
        List<Map<String, Object>> suggestions = new ArrayList<>();
        for (Livestock female : females) {
            Optional<Livestock> matchingMale = males.stream()
                    .filter(m -> female.getLivestockCategory() != null
                            && female.getLivestockCategory().equals(m.getLivestockCategory()))
                    .findFirst()
                    .or(() -> males.stream().findFirst());

            Map<String, Object> s = new LinkedHashMap<>();
            s.put("female",        female);
            s.put("male",          matchingMale.orElse(null));
            s.put("maleAvailable", matchingMale.isPresent());
            suggestions.add(s);
            if (suggestions.size() >= 10) break;
        }
        return suggestions;
    }

    // =========================================================================
    // GENDER-BASED LISTS
    // =========================================================================

    public List<Livestock> getAllMales() {
        return getActiveAnimals().stream()
                .filter(l -> "MALE".equalsIgnoreCase(l.getGender()))
                .sorted(Comparator.comparing(Livestock::getTagNumber))
                .collect(Collectors.toList());
    }

    public List<Livestock> getAllFemales() {
        return getActiveAnimals().stream()
                .filter(l -> "FEMALE".equalsIgnoreCase(l.getGender()))
                .sorted(Comparator.comparing(Livestock::getTagNumber))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // STATS / CHARTS
    // =========================================================================

    public Map<String, Long> getStageDistribution() {
        Map<String, Long> dist = new LinkedHashMap<>();
        dist.put("NEWBORN",        countNewborns());
        dist.put("YOUNG",          countYoung());
        dist.put("PRE_BREEDING",   countPreBreeding());
        dist.put("READY_TO_BREED", countReadyToBreed());
        dist.put("IN_BREEDING",    countInBreeding());
        dist.put("PREGNANT",       countPregnant());
        dist.put("NURSING",        (long) getNursingAnimals().size());
        return dist;
    }

    public Map<String, Long> getAgeBandBreakdown() {
        List<Livestock> active = getActiveAnimals();
        Map<String, Long> bands = new LinkedHashMap<>();
        bands.put("0-30 days",    active.stream().filter(l -> { long d = getAgeInDays(l); return d > 0 && d <= 30; }).count());
        bands.put("31-90 days",   active.stream().filter(l -> { long d = getAgeInDays(l); return d > 30 && d <= 90; }).count());
        bands.put("91-180 days",  active.stream().filter(l -> { long d = getAgeInDays(l); return d > 90 && d <= 180; }).count());
        bands.put("181-365 days", active.stream().filter(l -> { long d = getAgeInDays(l); return d > 180 && d <= 365; }).count());
        bands.put("1-2 years",    active.stream().filter(l -> { long d = getAgeInDays(l); return d > 365 && d <= 730; }).count());
        bands.put("2+ years",     active.stream().filter(l -> getAgeInDays(l) > 730).count());
        return bands;
    }

    public List<Map<String, Object>> getMaleBreedingStats() {
        return getAllMales().stream().map(male -> {
            List<LivestockBreeding> breedings = breedingRepository.findAll().stream()
                    .filter(b -> b.getMaleLivestock() != null
                            && b.getMaleLivestock().getId().equals(male.getId()))
                    .collect(Collectors.toList());
            long successful = breedings.stream()
                    .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                            || LivestockBreeding.STATUS_CONFIRMED.equals(b.getStatus()))
                    .count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("male",           male);
            m.put("totalBreedings", breedings.size());
            m.put("successful",     successful);
            m.put("successRate",    breedings.isEmpty() ? 0.0 : (successful * 100.0 / breedings.size()));
            m.put("lastBreeding",   breedings.stream().map(LivestockBreeding::getBreedingDate)
                    .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
            return m;
        }).collect(Collectors.toList());
    }

    // =========================================================================
    // HISTORY
    // =========================================================================

    public List<Map<String, Object>> getStageHistory(Livestock animal) {
        List<Map<String, Object>> history = new ArrayList<>();

        LocalDate refDate = animal.getDateReceived() != null
                ? animal.getDateReceived()
                : animal.getBirthDate();

        if (refDate != null) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("date",  refDate);
            e.put("stage", animal.getMother() != null ? "BORN ON FARM" : "ACQUIRED");
            e.put("notes", animal.getMother() != null
                    ? "Born, mother: " + animal.getMother().getTagNumber()
                    : "Acquisition method: " + animal.getAcquisitionMethod());
            history.add(e);
        }

        breedingRepository.findByLivestockId(animal.getId()).forEach(b -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("date",  b.getBreedingDate());
            e.put("stage", "BREEDING — " + (b.getStatus() != null ? b.getStatus() : "PENDING"));
            e.put("notes", b.getNotes());
            history.add(e);
        });

        if (animal.getConceptionDate() != null) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("date",  animal.getConceptionDate());
            e.put("stage", "PREGNANT");
            e.put("notes", "Expected due: " + (animal.getExpectedDueDate() != null ? animal.getExpectedDueDate() : "not set"));
            history.add(e);
        }

        if (animal.getLastBirthDate() != null) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("date",  animal.getLastBirthDate());
            e.put("stage", "GAVE BIRTH");
            e.put("notes", "Offspring count: " + (animal.getOffspringCount() != null ? animal.getOffspringCount() : "?"));
            history.add(e);
        }

        history.sort(Comparator.comparing(m -> (LocalDate) m.get("date")));
        return history;
    }

    // =========================================================================
    // OFFSPRING
    // =========================================================================

    public List<Livestock> getOffspring(UUID motherId) {
        return livestockRepository.findByMotherId(motherId);
    }

    // =========================================================================
    // MANUAL STAGE UPDATE
    // =========================================================================

    @Transactional
    public void updateStage(UUID id, String stage, String notes) {
        Livestock animal = livestockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Animal not found: " + id));

        switch (stage.toUpperCase()) {
            case "PREGNANT":
                animal.setStatus(Livestock.STATUS_PREGNANT);
                animal.setIsPregnant(true);
                break;
            case "ACTIVE":
                animal.setStatus(Livestock.STATUS_ACTIVE);
                animal.setIsPregnant(false);
                break;
            case "NURSING":
                animal.setStatus(Livestock.STATUS_ACTIVE);
                animal.setIsPregnant(false);
                animal.setLastBirthDate(LocalDate.now());
                break;
            default:
                break;
        }
        livestockRepository.save(animal);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private List<Livestock> getActiveAnimals() {
        return livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus()))
                .filter(l -> !Livestock.STATUS_SOLD.equals(l.getStatus()))
                .collect(Collectors.toList());
    }

    private boolean isCurrentlyNursing(Livestock animal) {
        if (animal.getLastBirthDate() == null) return false;
        long daysSinceBirth = ChronoUnit.DAYS.between(animal.getLastBirthDate(), LocalDate.now());
        return daysSinceBirth >= 0 && daysSinceBirth <= NURSING_DAYS;
    }

    /**
     * Returns true if this animal has a CONFIRMED_PREGNANT breeding record.
     * Used to detect pregnancies that were recorded via the breeding workflow
     * but where livestock.status was never updated to 'PREGNANT'.
     */
    private boolean hasConfirmedPregnancy(Livestock animal) {
        return breedingRepository.findByLivestockId(animal.getId()).stream()
                .anyMatch(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                        && !Boolean.TRUE.equals(b.getIsDeleted()));
    }
}
