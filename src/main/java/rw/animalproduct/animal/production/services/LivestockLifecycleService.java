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
 * LivestockLifecycleService
 *
 * Central service for all lifecycle queries.
 *
 * Stage definitions (age-based, configurable per category):
 *   NEWBORN        0 – 30 days
 *   YOUNG          31 – 180 days (6 months)
 *   PRE_BREEDING   181 – 365 days (up to 12 months)   ← auto-transition
 *   READY_TO_BREED 366+ days (over 12 months)          ← link appears
 *   IN_BREEDING    has active LivestockBreeding record (PENDING)
 *   PREGNANT       status == PREGNANT or confirmed breeding
 *   NURSING        gave birth in last 90 days
 *
 * Note: Males follow the same age bands but are never PREGNANT / NURSING.
 * Males become READY_TO_BREED at 12 months and are tracked via male-management.
 *
 * All transitions from NEWBORN → YOUNG → PRE_BREEDING → READY_TO_BREED are
 * automatic (computed from dateReceived / birthDate). No manual action needed.
 *
 * Transitions into IN_BREEDING and PREGNANT require a LivestockBreeding record.
 */
@Service
public class LivestockLifecycleService {

    // ── Age thresholds (days) — adjust per your species ──────────────
    private static final int NEWBORN_DAYS       = 30;
    private static final int YOUNG_DAYS         = 180;
    private static final int PRE_BREEDING_DAYS  = 365;   // below this = pre-breeding
    // above PRE_BREEDING_DAYS = ready to breed

    private static final int APPROACHING_DAYS   = 60;    // "approaching" = within 60 days of breeding age
    private static final int NURSING_DAYS       = 90;    // nursing period after birth

    private final LivestockRepository livestockRepository;
    private final LivestockBreedingRepository breedingRepository;

    @Autowired
    public LivestockLifecycleService(LivestockRepository livestockRepository,
                                     LivestockBreedingRepository breedingRepository) {
        this.livestockRepository = livestockRepository;
        this.breedingRepository  = breedingRepository;
    }

    // =========================================================================
    // STAGE COMPUTATION
    // =========================================================================

    /**
     * Returns the lifecycle stage label for a single animal.
     * Stage is computed automatically from age + breeding records.
     */
    public String getCurrentStage(Livestock animal) {
        if (animal == null) return "UNKNOWN";

        // Override by status first
        if (Livestock.STATUS_DEAD.equals(animal.getStatus()))  return "DECEASED";
        if (Livestock.STATUS_SOLD.equals(animal.getStatus()))  return "SOLD";

        // Pregnant?
        if (Livestock.STATUS_PREGNANT.equals(animal.getStatus())) return "PREGNANT";
        if (Boolean.TRUE.equals(animal.getIsPregnant()))           return "PREGNANT";

        long ageDays = getAgeInDays(animal);

        // In active breeding (PENDING record)?
        List<LivestockBreeding> activeBreedings = breedingRepository
                .findByLivestockId(animal.getId())
                .stream()
                .filter(b -> LivestockBreeding.STATUS_PENDING.equals(b.getStatus()))
                .collect(Collectors.toList());
        if (!activeBreedings.isEmpty()) return "IN_BREEDING";

        // Nursing? (gave birth in last NURSING_DAYS days)
        if (animal.getLastBirthDate() != null) {
            long daysSinceBirth = ChronoUnit.DAYS.between(animal.getLastBirthDate(), LocalDate.now());
            if (daysSinceBirth >= 0 && daysSinceBirth <= NURSING_DAYS) return "NURSING";
        }

        // Age-based
        if (ageDays <= NEWBORN_DAYS)       return "NEWBORN";
        if (ageDays <= YOUNG_DAYS)         return "YOUNG";
        if (ageDays <= PRE_BREEDING_DAYS)  return "PRE_BREEDING";
        return "READY_TO_BREED";
    }

    /**
     * Describe the next expected milestone for this animal.
     */
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
                    if (days >= 0) return "Expected to give birth in " + days + " days";
                    return "Overdue by " + Math.abs(days) + " days";
                }
                return "Due date not set";
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
        return livestockRepository.findAll().stream()
                .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus())
                          && !Livestock.STATUS_SOLD.equals(l.getStatus()))
                .count();
    }

    public long countNewborns() {
        return getActiveAnimals().stream()
                .filter(l -> getAgeInDays(l) <= NEWBORN_DAYS)
                .count();
    }

    public long countPreBreeding() {
        return getActiveAnimals().stream()
                .filter(l -> {
                    long d = getAgeInDays(l);
                    return d > NEWBORN_DAYS && d <= PRE_BREEDING_DAYS;
                })
                .count();
    }

    public long countReadyToBreed() {
        return getReadyToBreed().size();
    }

    public long countInBreeding() {
        return breedingRepository.findByStatus(LivestockBreeding.STATUS_PENDING).size();
    }

    public long countPregnant() {
        return livestockRepository.findByStatus(Livestock.STATUS_PREGNANT).size();
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
    // LISTS — age-based
    // =========================================================================

    public List<Livestock> getYoungFemales() {
        return getActiveAnimals().stream()
                .filter(l -> "FEMALE".equalsIgnoreCase(l.getGender()))
                .filter(l -> {
                    long d = getAgeInDays(l);
                    return d > NEWBORN_DAYS && d <= PRE_BREEDING_DAYS;
                })
                .sorted(Comparator.comparing(l -> getAgeInDays(l)))
                .collect(Collectors.toList());
    }

    public List<Livestock> getYoungMales() {
        return getActiveAnimals().stream()
                .filter(l -> "MALE".equalsIgnoreCase(l.getGender()))
                .filter(l -> {
                    long d = getAgeInDays(l);
                    return d > NEWBORN_DAYS && d <= PRE_BREEDING_DAYS;
                })
                .sorted(Comparator.comparing(l -> getAgeInDays(l)))
                .collect(Collectors.toList());
    }

    public List<Livestock> getApproachingBreedingAge() {
        LocalDate threshold = LocalDate.now().minusDays(PRE_BREEDING_DAYS - APPROACHING_DAYS);
        LocalDate breedingAge = LocalDate.now().minusDays(PRE_BREEDING_DAYS);
        return getActiveAnimals().stream()
                .filter(l -> l.getDateReceived() != null)
                .filter(l -> {
                    long d = getAgeInDays(l);
                    return d > (PRE_BREEDING_DAYS - APPROACHING_DAYS) && d <= PRE_BREEDING_DAYS;
                })
                .sorted(Comparator.comparing(this::getAgeInDays))
                .collect(Collectors.toList());
    }

    /** Females that have passed breeding age and are not currently pregnant / in breeding */
    public List<Livestock> getReadyToBreed() {
        Set<UUID> inBreeding = breedingRepository.findByStatus(LivestockBreeding.STATUS_PENDING)
                .stream()
                .filter(b -> b.getLivestock() != null)
                .map(b -> b.getLivestock().getId())
                .collect(Collectors.toSet());

        return getActiveAnimals().stream()
                .filter(l -> "FEMALE".equalsIgnoreCase(l.getGender()))
                .filter(l -> getAgeInDays(l) > PRE_BREEDING_DAYS)
                .filter(l -> !Livestock.STATUS_PREGNANT.equals(l.getStatus()))
                .filter(l -> !inBreeding.contains(l.getId()))
                .filter(l -> !isCurrentlyNursing(l))
                .sorted(Comparator.comparing(this::getAgeInDays).reversed())
                .collect(Collectors.toList());
    }

    public List<Livestock> getMalesReadyToBreed() {
        return getActiveAnimals().stream()
                .filter(l -> "MALE".equalsIgnoreCase(l.getGender()))
                .filter(l -> getAgeInDays(l) > PRE_BREEDING_DAYS)
                .sorted(Comparator.comparing(this::getAgeInDays).reversed())
                .collect(Collectors.toList());
    }

    public List<Livestock> getNursingAnimals() {
        return getActiveAnimals().stream()
                .filter(this::isCurrentlyNursing)
                .collect(Collectors.toList());
    }

    public List<Livestock> getRecentlyBorn(int withinDays) {
        LocalDate cutoff = LocalDate.now().minusDays(withinDays);
        return getActiveAnimals().stream()
                .filter(l -> l.getDateReceived() != null && l.getDateReceived().isAfter(cutoff))
                .filter(l -> l.getMother() != null || "BORN".equalsIgnoreCase(l.getAcquisitionMethod()))
                .sorted(Comparator.comparing(Livestock::getDateReceived).reversed())
                .collect(Collectors.toList());
    }

    // =========================================================================
    // LISTS — pregnancy
    // =========================================================================

    public List<Livestock> getPregnantAnimals() {
        return livestockRepository.findByStatus(Livestock.STATUS_PREGNANT).stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .sorted(Comparator.comparing(l -> l.getExpectedDueDate() != null ? l.getExpectedDueDate() : LocalDate.MAX))
                .collect(Collectors.toList());
    }

    public List<Livestock> getDueSoon(int withinDays) {
        LocalDate today  = LocalDate.now();
        LocalDate cutoff = today.plusDays(withinDays);
        return getPregnantAnimals().stream()
                .filter(l -> l.getExpectedDueDate() != null)
                .filter(l -> !l.getExpectedDueDate().isBefore(today)
                          && !l.getExpectedDueDate().isAfter(cutoff))
                .collect(Collectors.toList());
    }

    public List<Livestock> getOverdue() {
        LocalDate today = LocalDate.now();
        return getPregnantAnimals().stream()
                .filter(l -> l.getExpectedDueDate() != null)
                .filter(l -> l.getExpectedDueDate().isBefore(today))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getDueDateCalendar() {
        return getPregnantAnimals().stream()
                .filter(l -> l.getExpectedDueDate() != null)
                .map(l -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",         l.getId());
                    m.put("tagNumber",  l.getTagNumber());
                    m.put("dueDate",    l.getExpectedDueDate().toString());
                    m.put("category",   l.getLivestockCategory() != null ? l.getLivestockCategory().getName() : null);
                    long days = ChronoUnit.DAYS.between(LocalDate.now(), l.getExpectedDueDate());
                    m.put("daysLeft",   days);
                    m.put("status",     days < 0 ? "OVERDUE" : days <= 7 ? "CRITICAL" : days <= 30 ? "SOON" : "OK");
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
        return getPendingPregnancyCheck(); // same query — overdue = past check date
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
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED.equals(b.getStatus()))
                .count();
        return (confirmed * 100.0) / all.size();
    }

    public double getAvgDaysToConception() {
        return breedingRepository.findAll().stream()
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED.equals(b.getStatus()))
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
            // Match by category first, then any
            Optional<Livestock> matchingMale = males.stream()
                    .filter(m -> female.getLivestockCategory() != null
                              && female.getLivestockCategory().equals(m.getLivestockCategory()))
                    .findFirst()
                    .or(() -> males.stream().findFirst());

            Map<String, Object> s = new LinkedHashMap<>();
            s.put("female",     female);
            s.put("male",       matchingMale.orElse(null));
            s.put("maleAvailable", matchingMale.isPresent());
            suggestions.add(s);
            if (suggestions.size() >= 10) break; // limit
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
        dist.put("PRE_BREEDING",   countPreBreeding());
        dist.put("READY_TO_BREED", countReadyToBreed());
        dist.put("IN_BREEDING",    countInBreeding());
        dist.put("PREGNANT",       countPregnant());
        dist.put("NURSING",        (long) getNursingAnimals().size());
        return dist;
    }

    public Map<String, Long> getAgeBandBreakdown() {
        Map<String, Long> bands = new LinkedHashMap<>();
        List<Livestock> active = getActiveAnimals();
        bands.put("0-30 days",    active.stream().filter(l -> getAgeInDays(l) <= 30).count());
        bands.put("31-90 days",   active.stream().filter(l -> { long d = getAgeInDays(l); return d > 30 && d <= 90; }).count());
        bands.put("91-180 days",  active.stream().filter(l -> { long d = getAgeInDays(l); return d > 90 && d <= 180; }).count());
        bands.put("181-365 days", active.stream().filter(l -> { long d = getAgeInDays(l); return d > 180 && d <= 365; }).count());
        bands.put("1-2 years",    active.stream().filter(l -> { long d = getAgeInDays(l); return d > 365 && d <= 730; }).count());
        bands.put("2+ years",     active.stream().filter(l -> getAgeInDays(l) > 730).count());
        return bands;
    }

    public List<Map<String, Object>> getMaleBreedingStats() {
        List<Livestock> males = getAllMales();
        return males.stream().map(male -> {
            List<LivestockBreeding> breedings = breedingRepository.findAll().stream()
                    .filter(b -> b.getMaleLivestock() != null && b.getMaleLivestock().getId().equals(male.getId()))
                    .collect(Collectors.toList());
            long successful = breedings.stream().filter(b -> LivestockBreeding.STATUS_CONFIRMED.equals(b.getStatus())).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("male",          male);
            m.put("totalBreedings", breedings.size());
            m.put("successful",    successful);
            m.put("successRate",   breedings.isEmpty() ? 0.0 : (successful * 100.0 / breedings.size()));
            m.put("lastBreeding",  breedings.stream().map(LivestockBreeding::getBreedingDate)
                                            .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null));
            return m;
        }).collect(Collectors.toList());
    }

    // =========================================================================
    // HISTORY (stage audit trail — computed from events)
    // =========================================================================

    public List<Map<String, Object>> getStageHistory(Livestock animal) {
        List<Map<String, Object>> history = new ArrayList<>();

        // Birth / acquisition
        if (animal.getDateReceived() != null) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("date",  animal.getDateReceived());
            e.put("stage", animal.getMother() != null ? "BORN ON FARM" : "ACQUIRED");
            e.put("notes", animal.getMother() != null
                    ? "Born, mother: " + animal.getMother().getTagNumber()
                    : "Acquisition method: " + animal.getAcquisitionMethod());
            history.add(e);
        }

        // Breeding events
        breedingRepository.findByLivestockId(animal.getId()).forEach(b -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("date",  b.getBreedingDate());
            e.put("stage", "BREEDING — " + (b.getStatus() != null ? b.getStatus() : "PENDING"));
            e.put("notes", b.getNotes());
            history.add(e);
        });

        // Pregnancy
        if (animal.getConceptionDate() != null) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("date",  animal.getConceptionDate());
            e.put("stage", "PREGNANT");
            e.put("notes", "Expected due: " + (animal.getExpectedDueDate() != null ? animal.getExpectedDueDate() : "not set"));
            history.add(e);
        }

        // Last birth
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
                // Other stages are read-only (computed from age)
                break;
        }
        livestockRepository.save(animal);
    }

    // =========================================================================
    // AGE HELPERS
    // =========================================================================

    public long getAgeInDays(Livestock animal) {
        if (animal == null || animal.getDateReceived() == null) return 0;
        long days = ChronoUnit.DAYS.between(animal.getDateReceived(), LocalDate.now());
        return Math.max(0, days);
    }

    public long getAgeInMonths(Livestock animal) {
        if (animal == null || animal.getDateReceived() == null) return 0;
        return Math.max(0, ChronoUnit.MONTHS.between(animal.getDateReceived(), LocalDate.now()));
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
}
