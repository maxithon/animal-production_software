package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class LivestockBirthService {

    private static final Logger log = LoggerFactory.getLogger(LivestockBirthService.class);

    private static final ZoneId APP_ZONE = ZoneId.of("Africa/Kigali");

    private static final double GESTATION_TOLERANCE_FRACTION = 0.15;
    private static final int EXPECTED_DUE_DATE_FALLBACK_TOLERANCE_DAYS = 21;

    private static final String LOCKED_MESSAGE =
            "This birth record is locked (verified/completed). Amend (reopen) it before making changes.";

    private static final int CHILD_BIRTH_DATE_TOLERANCE_DAYS = 30;

    private static final int MAX_PAGE_SIZE = 100;

    private final LivestockBirthRepository birthRepository;
    private final LivestockRepository livestockRepository;
    private final LivestockOffspringRepository offspringRepository;
    private final LivestockBreedingRepository breedingRepository;
    private final LivestockCategoryRepository categoryRepository;
    private final VeterinarianRepository veterinarianRepository;

    public LivestockBirthService(
            LivestockBirthRepository birthRepository,
            LivestockRepository livestockRepository,
            LivestockOffspringRepository offspringRepository,
            LivestockBreedingRepository breedingRepository,
            LivestockCategoryRepository categoryRepository,
            VeterinarianRepository veterinarianRepository) {
        this.birthRepository = birthRepository;
        this.livestockRepository = livestockRepository;
        this.offspringRepository = offspringRepository;
        this.breedingRepository = breedingRepository;
        this.categoryRepository = categoryRepository;
        this.veterinarianRepository = veterinarianRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // REAL DATE HELPERS — derived from existing category columns only
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Real weaning date = birth date + the category's weaning period.
     */
    public LocalDate calculateRealWeaningDate(LocalDate birthDate, LivestockCategory category) {
        if (birthDate == null || category == null) {
            return null;
        }
        Integer weaningDays = category.getWeaningPeriodDays();
        if (weaningDays == null || weaningDays < 0) {
            return null;
        }
        return birthDate.plusDays(weaningDays);
    }

    /**
     * Real next breeding date = birth date + the category's postpartum recovery period.
     */
    public LocalDate calculateRealNextBreedingDate(LocalDate birthDate, LivestockCategory category) {
        if (birthDate == null || category == null) {
            return null;
        }
        Integer recoveryDays = category.getPostpartumRecoveryDays();
        if (recoveryDays == null || recoveryDays < 0) {
            return null;
        }
        return birthDate.plusDays(recoveryDays);
    }

    /**
     * Payload for /api/date-suggestions — used when the user manually edits the
     * birth date and we want to recompute weaning/next breeding on the fly.
     */
    public Map<String, Object> getDateSuggestionExplanation(LocalDate birthDate,
                                                            LivestockCategory category) {
        Map<String, Object> info = new LinkedHashMap<>();
        if (birthDate == null || category == null) {
            return info;
        }

        LocalDate weaningDate      = calculateRealWeaningDate(birthDate, category);
        LocalDate nextBreedingDate = calculateRealNextBreedingDate(birthDate, category);

        info.put("weaningDate",      weaningDate);
        info.put("nextBreedingDate", nextBreedingDate);

        if (weaningDate != null) {
            info.put("weaningExplanation",
                    "Birth date + " + category.getWeaningPeriodDays()
                            + " days weaning period = " + weaningDate);
        }
        if (nextBreedingDate != null) {
            info.put("breedingExplanation",
                    "Birth date + " + category.getPostpartumRecoveryDays()
                            + " days postpartum recovery = " + nextBreedingDate);
        }
        return info;
    }

    /**
     * Looks up the "real" dates for a mother using data ALREADY IN THE TABLES:
     *
     *   - expected birth date: pulled from the mother's active breeding record
     *     (livestock_breeding.expected_due_date). Falls back to breeding_date +
     *     category.gestation_period_days if the due date is missing.
     *
     *   - suggested weaning date: birthDate + category.weaning_period_days
     *
     *   - suggested next breeding date: birthDate + category.postpartum_recovery_days
     *
     * All three are returned as ISO yyyy-MM-dd strings (or null). Nothing is
     * persisted — this is a read-only suggestion the form can pre-fill with.
     */
    public Map<String, Object> getAutoDatesForMother(UUID motherId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("birthDate",        null);
        result.put("weaningDate",      null);
        result.put("nextBreedingDate", null);

        if (motherId == null) {
            return result;
        }

        Livestock mother = livestockRepository.findById(motherId).orElse(null);
        if (mother == null) {
            return result;
        }

        LivestockCategory category = mother.getLivestockCategory();

        // ── 1. Expected birth date: prefer the active breeding record's
        //       expected_due_date. Fall back to breeding_date + gestation days.
        LocalDate expectedBirth = null;

        List<LivestockBreeding> activePregnancies = breedingRepository.findActiveByLivestockId(
                motherId,
                List.of(LivestockBreeding.STATUS_CONFIRMED_PREGNANT, LivestockBreeding.STATUS_PREGNANT));

        LivestockBreeding latest = activePregnancies.stream()
                .filter(b -> b.getBreedingDate() != null)
                .max(Comparator.comparing(LivestockBreeding::getBreedingDate))
                .orElse(null);

        if (latest != null) {
            if (latest.getExpectedDueDate() != null) {
                expectedBirth = latest.getExpectedDueDate();
            } else if (category != null && category.getGestationPeriodDays() != null) {
                expectedBirth = latest.getBreedingDate().plusDays(category.getGestationPeriodDays());
            }
        }

        if (expectedBirth != null) {
            result.put("birthDate", expectedBirth.toString());
        }

        // ── 2 & 3. Weaning + Next Breeding derive from birthDate + category.
        LocalDate anchor = expectedBirth != null ? expectedBirth : LocalDate.now(APP_ZONE);

        if (category != null) {
            LocalDate weaning = calculateRealWeaningDate(anchor, category);
            if (weaning != null) {
                result.put("weaningDate", weaning.toString());
                result.put("weaningExplanation",
                        "Birth date + " + category.getWeaningPeriodDays()
                                + " days weaning period = " + weaning);
            }

            LocalDate nextBreeding = calculateRealNextBreedingDate(anchor, category);
            if (nextBreeding != null) {
                result.put("nextBreedingDate", nextBreeding.toString());
                result.put("breedingExplanation",
                        "Birth date + " + category.getPostpartumRecoveryDays()
                                + " days postpartum recovery = " + nextBreeding);
            }
        }

        if (expectedBirth != null && latest != null && latest.getExpectedDueDate() != null) {
            result.put("birthDateExplanation",
                    "From breeding record — expected due date was " + latest.getExpectedDueDate());
        } else if (expectedBirth != null && latest != null) {
            result.put("birthDateExplanation",
                    "From breeding record — breeding_date " + latest.getBreedingDate()
                            + " + gestation " + (category != null ? category.getGestationPeriodDays() : "?")
                            + " days");
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────────────

    public LivestockBirth createBirth(LivestockBirth birth) {
        return createBirth(birth, false);
    }

    private LivestockBirth createBirth(LivestockBirth birth, boolean willHaveLinkedChildren) {
        log.debug("Creating new birth record");

        if (birth.getLivestockId() == null) {
            throw new IllegalArgumentException("Mother livestock ID is required");
        }
        if (birth.getBirthDate() == null) {
            throw new IllegalArgumentException("Birth date is required");
        }
        if (birth.getBirthDate().isAfter(LocalDate.now(APP_ZONE))) {
            throw new IllegalStateException("Birth date cannot be in the future");
        }

        validateBirthEligibility(birth.getLivestockId(), birth.getBirthDate());

        if (birth.getNumberBornAlive() != null && birth.getNumberBornAlive() < 0) {
            throw new IllegalArgumentException("Number born alive cannot be negative");
        }
        if (birth.getNumberStillborn() != null && birth.getNumberStillborn() < 0) {
            throw new IllegalArgumentException("Number stillborn cannot be negative");
        }

        if (birth.getIsDeleted() == null) {
            birth.setIsDeleted(false);
        }
        if (birth.getOffspringCount() == null) {
            birth.setOffspringCount(0);
        }
        if (birth.getAssistanceRequired() == null) {
            birth.setAssistanceRequired(false);
        }
        if (birth.getNumberStillborn() == null) {
            birth.setNumberStillborn(0);
        }

        validateOffspringCountConsistency(birth, willHaveLinkedChildren);

        Livestock motherForCalc = livestockRepository.findById(birth.getLivestockId()).orElse(null);
        LivestockCategory category = motherForCalc != null ? motherForCalc.getLivestockCategory() : null;

        // Server-side fallback: fill real dates when the form left them blank.
        if (birth.getWeaningDate() == null && category != null) {
            birth.setWeaningDate(calculateRealWeaningDate(birth.getBirthDate(), category));
        }
        if (birth.getNextBreedingDate() == null && category != null) {
            birth.setNextBreedingDate(calculateRealNextBreedingDate(birth.getBirthDate(), category));
        }

        LivestockBirth savedBirth = birthRepository.save(birth);

        livestockRepository.findById(birth.getLivestockId()).ifPresent(mother -> {
            mother.setLastBirthDate(birth.getBirthDate());
            if (mother.getOffspringCount() == null) {
                mother.setOffspringCount(0);
            }
            mother.setOffspringCount(mother.getOffspringCount() + 1);

            mother.setReproductiveStatus(Livestock.REPRO_OPEN);
            mother.setExpectedDueDate(null);

            livestockRepository.save(mother);
        });

        if (birth.getBreedingId() != null) {
            breedingRepository.findById(birth.getBreedingId()).ifPresent(breeding -> {
                breeding.setStatus(LivestockBreeding.STATUS_COMPLETED);
                breeding.setNotes((breeding.getNotes() != null ? breeding.getNotes() + " " : "") +
                        "Birth recorded on " + birth.getBirthDate());
                breedingRepository.save(breeding);
            });
        } else if (birth.getLivestockId() != null) {
            breedingRepository.findActiveByLivestockId(
                    birth.getLivestockId(),
                    List.of(LivestockBreeding.STATUS_CONFIRMED_PREGNANT, LivestockBreeding.STATUS_PREGNANT)
            ).forEach(breeding -> {
                breeding.setStatus(LivestockBreeding.STATUS_COMPLETED);
                breeding.setNotes((breeding.getNotes() != null ? breeding.getNotes() + " " : "") +
                        "Birth recorded on " + birth.getBirthDate());
                breedingRepository.save(breeding);
            });
        }

        return savedBirth;
    }

    public LivestockBirth addNew(LivestockBirth birth, List<UUID> linkedChildIds) {
        return addNew(birth, linkedChildIds, null);
    }

    public LivestockBirth addNew(LivestockBirth birth, List<UUID> linkedChildIds, UUID fatherId) {
        log.debug("Registering new birth (addNew)");

        resolveAndSetLivestock(birth);
        resolveAndSetVeterinarian(birth);

        boolean willHaveLinkedChildren = linkedChildIds != null && !linkedChildIds.isEmpty();

        if (willHaveLinkedChildren) {
            applyDerivedOffspringInfo(birth, linkedChildIds);
        }

        LivestockBirth saved = createBirth(birth, willHaveLinkedChildren);

        if (linkedChildIds != null) {
            for (UUID childId : linkedChildIds) {
                try {
                    addOffspring(saved.getId(), childId, 1, LivestockOffspring.OUTCOME_ALIVE, null, fatherId);
                } catch (Exception e) {
                    log.warn("Could not link child {} to birth {}: {}", childId, saved.getId(), e.getMessage());
                }
            }
        }

        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // OFFSPRING COUNT / GENDER DERIVATION
    // ─────────────────────────────────────────────────────────────────────────────

    private void applyDerivedOffspringInfo(LivestockBirth birth, List<UUID> linkedChildIds) {
        List<Livestock> children = linkedChildIds.stream()
                .map(id -> livestockRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        birth.setOffspringCount(children.size());
        birth.setOffspringGender(buildGenderSummary(children));
    }

    private void recalcOffspringSummary(UUID birthId) {
        LivestockBirth birth = birthRepository.findById(birthId).orElse(null);
        if (birth == null) return;

        List<LivestockOffspring> offspring =
                offspringRepository.findByBirthEventIdAndIsDeletedFalse(birthId);
        List<Livestock> children = offspring.stream()
                .map(LivestockOffspring::getChildLivestock)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        birth.setOffspringCount(children.size());
        birth.setOffspringGender(buildGenderSummary(children));
        birthRepository.save(birth);
    }

    private String buildGenderSummary(List<Livestock> children) {
        long male = children.stream()
                .filter(c -> "MALE".equalsIgnoreCase(c.getGender())).count();
        long female = children.stream()
                .filter(c -> "FEMALE".equalsIgnoreCase(c.getGender())).count();
        long unknown = children.size() - male - female;

        List<String> parts = new ArrayList<>();
        if (male > 0) parts.add("Male: " + male);
        if (female > 0) parts.add("Female: " + female);
        if (unknown > 0) parts.add("Unknown: " + unknown);
        return String.join(", ", parts);
    }

    private void validateOffspringCountConsistency(LivestockBirth birth, boolean hasLinkedChildren) {
        Integer bornAlive = birth.getNumberBornAlive();
        if (bornAlive == null) {
            return;
        }
        int stillborn = birth.getNumberStillborn() != null ? birth.getNumberStillborn() : 0;
        int total = bornAlive + stillborn;

        if (hasLinkedChildren) {
            if (birth.getOffspringCount() != null && birth.getOffspringCount() > total) {
                throw new IllegalArgumentException(
                        "Inconsistent data: " + birth.getOffspringCount() + " offspring are linked, but only "
                                + total + " total were reported (Born Alive " + bornAlive
                                + " + Stillborn " + stillborn + ").");
            }
            return;
        }

        if (birth.getOffspringCount() != null && birth.getOffspringCount() != 0
                && !birth.getOffspringCount().equals(total)) {
            throw new IllegalArgumentException(
                    "Number of Offspring must equal Born Alive + Stillborn. Got offspringCount="
                            + birth.getOffspringCount() + ", but Born Alive(" + bornAlive
                            + ") + Stillborn(" + stillborn + ") = " + total + ".");
        }

        if (birth.getOffspringCount() == null || birth.getOffspringCount() == 0) {
            birth.setOffspringCount(total);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // BIRTH ELIGIBILITY VALIDATION
    // ─────────────────────────────────────────────────────────────────────────────

    private void validateBirthEligibility(UUID motherId, LocalDate birthDate) {
        Livestock mother = livestockRepository.findById(motherId)
                .orElseThrow(() -> new IllegalArgumentException("Mother animal not found"));

        List<LivestockBreeding> activePregnancies = breedingRepository.findActiveByLivestockId(
                motherId,
                List.of(LivestockBreeding.STATUS_CONFIRMED_PREGNANT, LivestockBreeding.STATUS_PREGNANT)
        );

        boolean flaggedPregnant = Livestock.REPRO_PREGNANT.equals(mother.getReproductiveStatus());

        if (activePregnancies.isEmpty() && !flaggedPregnant) {
            throw new IllegalStateException(
                    mother.getTagNumber() + " is not currently marked as pregnant. "
                            + "Record a breeding event and confirm the pregnancy — or, for animals acquired "
                            + "already pregnant, set her reproductive status to PREGNANT — before recording a birth."
            );
        }

        LivestockBreeding breeding = activePregnancies.stream()
                .filter(b -> b.getBreedingDate() != null)
                .max(Comparator.comparing(LivestockBreeding::getBreedingDate))
                .orElse(null);

        if (breeding == null) {
            return;
        }

        LocalDate breedingDate = breeding.getBreedingDate();

        if (!birthDate.isAfter(breedingDate)) {
            throw new IllegalStateException(
                    "Birth date (" + birthDate + ") cannot be on or before the breeding date ("
                            + breedingDate + ") recorded for " + mother.getTagNumber() + "."
            );
        }

        long daysSinceBreeding = ChronoUnit.DAYS.between(breedingDate, birthDate);

        LivestockCategory category = mother.getLivestockCategory();
        Integer gestationDays = category != null ? category.getGestationPeriodDays() : null;

        if (gestationDays != null && gestationDays > 0) {
            long minAcceptableDays = Math.round(gestationDays * (1 - GESTATION_TOLERANCE_FRACTION));

            if (daysSinceBreeding < minAcceptableDays) {
                LocalDate earliestAcceptable = breedingDate.plusDays(minAcceptableDays);
                throw new IllegalStateException(
                        mother.getTagNumber() + " is not yet due to give birth. Only " + daysSinceBreeding
                                + " day(s) have passed since breeding on " + breedingDate + ", but the "
                                + (category.getName() != null ? category.getName() : "this category")
                                + " standard gestation period is " + gestationDays + " days (minimum plausible: "
                                + minAcceptableDays + " days). The earliest plausible birth date is "
                                + earliestAcceptable + ". Double-check the breeding date and birth date."
                );
            }
        } else if (breeding.getExpectedDueDate() != null) {
            LocalDate earliestAcceptable =
                    breeding.getExpectedDueDate().minusDays(EXPECTED_DUE_DATE_FALLBACK_TOLERANCE_DAYS);

            if (birthDate.isBefore(earliestAcceptable)) {
                throw new IllegalStateException(
                        mother.getTagNumber() + " is not yet due. Expected due date is "
                                + breeding.getExpectedDueDate() + ". The earliest plausible birth date is "
                                + earliestAcceptable + "; birth dates before that are not accepted. "
                                + "If this is a genuine early birth, please verify the dates."
                );
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // LOCK GUARD
    // ─────────────────────────────────────────────────────────────────────────────

    private void assertNotLocked(LivestockBirth birth) {
        if (birth.isLocked()) {
            throw new IllegalStateException(LOCKED_MESSAGE);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────────

    public List<LivestockBirth> getAll() {
        log.debug("Getting all birth records");
        return birthRepository.findByIsDeletedFalseOrderByBirthDateDesc();
    }

    public Page<LivestockBirth> getPaged(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        log.debug("Getting paged birth records: page={}, size={}", safePage, safeSize);

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "birthDate"));
        return birthRepository.findByIsDeletedFalseOrderByBirthDateDesc(pageable);
    }

    public long countPendingVerification() {
        return birthRepository.countByIsDeletedFalseAndCompletedAtIsNull();
    }

    public long countLocked() {
        return birthRepository.countByIsDeletedFalseAndCompletedAtIsNotNull();
    }

    public Map<String, CategoryBirthStats> getCategoryBirthStats() {
        log.debug("Getting category-level birth stats (total + locked) across all non-deleted birth records");

        List<LivestockBirth> allBirths = birthRepository.findByIsDeletedFalseOrderByBirthDateDesc();

        Set<UUID> motherIds = allBirths.stream()
                .map(LivestockBirth::getLivestockId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Livestock> mothersById = motherIds.isEmpty()
                ? Collections.emptyMap()
                : livestockRepository.findAllById(motherIds).stream()
                .collect(Collectors.toMap(Livestock::getId, m -> m, (a, b) -> a));

        Map<String, CategoryBirthStats> statsByCategory = new TreeMap<>();

        for (LivestockBirth birth : allBirths) {
            Livestock mother = birth.getLivestockId() != null ? mothersById.get(birth.getLivestockId()) : null;
            String categoryName = (mother != null && mother.getLivestockCategory() != null)
                    ? mother.getLivestockCategory().getName()
                    : "Unknown";

            CategoryBirthStats stats = statsByCategory.computeIfAbsent(categoryName, CategoryBirthStats::new);
            stats.incrementTotalBirths();
            if (birth.isLocked()) {
                stats.incrementLocked();
            }
        }

        return statsByCategory;
    }

    public List<LivestockBirth> getBirthsByMother(UUID motherId) {
        log.debug("Getting births for mother: {}", motherId);
        return birthRepository.findByLivestockIdAndIsDeletedFalse(motherId);
    }

    public List<LivestockBirth> getByLivestockId(UUID livestockId) {
        return getBirthsByMother(livestockId);
    }

    public Map<UUID, LivestockBirth> getBirthMapForIds(List<UUID> livestockIds) {
        log.debug("Getting batched birth map for {} livestock id(s)",
                livestockIds != null ? livestockIds.size() : 0);

        if (livestockIds == null || livestockIds.isEmpty()) {
            return new HashMap<>();
        }

        return birthRepository.findByLivestockIdInAndIsDeletedFalse(livestockIds).stream()
                .collect(Collectors.toMap(
                        LivestockBirth::getLivestockId,
                        b -> b,
                        (existing, duplicate) -> existing
                ));
    }

    public Optional<LivestockBirth> getBirthById(UUID birthId) {
        log.debug("Getting birth by ID: {}", birthId);
        return birthRepository.findById(birthId);
    }

    public Optional<LivestockBirth> getById(UUID birthId) {
        return getBirthById(birthId);
    }

    public List<Livestock> getDirectChildren(UUID livestockId) {
        log.debug("Getting direct children for livestock: {}", livestockId);
        return livestockRepository.findAll().stream()
                .filter(l -> l.getMother() != null && livestockId.equals(l.getMother().getId()))
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .collect(Collectors.toList());
    }

    public boolean hasChildren(UUID livestockId) {
        return !getDirectChildren(livestockId).isEmpty();
    }

    public Livestock getCommonFatherForBirth(UUID birthId) {
        return offspringRepository.findByBirthEventIdAndIsDeletedFalse(birthId).stream()
                .map(LivestockOffspring::getChildLivestock)
                .filter(Objects::nonNull)
                .map(Livestock::getFather)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────────────────────

    public LivestockBirth updateBirth(UUID birthId, LivestockBirth updatedBirth) {
        log.debug("Updating birth: {}", birthId);

        LivestockBirth existingBirth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));

        assertNotLocked(existingBirth);

        UUID effectiveLivestockId = updatedBirth.getLivestockId() != null
                ? updatedBirth.getLivestockId()
                : existingBirth.getLivestockId();
        LocalDate effectiveBirthDate = updatedBirth.getBirthDate() != null
                ? updatedBirth.getBirthDate()
                : existingBirth.getBirthDate();

        if (effectiveLivestockId != null && effectiveBirthDate != null) {
            validateBirthEligibility(effectiveLivestockId, effectiveBirthDate);
        }

        if (updatedBirth.getLivestockId() != null) {
            existingBirth.setLivestockId(updatedBirth.getLivestockId());
        }
        if (updatedBirth.getBirthDate() != null) {
            existingBirth.setBirthDate(updatedBirth.getBirthDate());
        }

        existingBirth.setVeterinarianId(updatedBirth.getVeterinarianId());

        boolean hasLinkedChildren = existingBirth.getChildren() != null
                && !existingBirth.getChildren().isEmpty();

        if (!hasLinkedChildren) {
            if (updatedBirth.getOffspringCount() != null) {
                existingBirth.setOffspringCount(updatedBirth.getOffspringCount());
            }
            if (updatedBirth.getOffspringGender() != null) {
                existingBirth.setOffspringGender(updatedBirth.getOffspringGender());
            }
        }

        if (updatedBirth.getWeaningDate() != null) {
            existingBirth.setWeaningDate(updatedBirth.getWeaningDate());
        }
        if (updatedBirth.getNextBreedingDate() != null) {
            existingBirth.setNextBreedingDate(updatedBirth.getNextBreedingDate());
        }
        if (updatedBirth.getNotes() != null) {
            existingBirth.setNotes(updatedBirth.getNotes());
        }
        if (updatedBirth.getSourceLocation() != null) {
            existingBirth.setSourceLocation(updatedBirth.getSourceLocation());
        }
        if (updatedBirth.getIsExternalBirth() != null) {
            existingBirth.setIsExternalBirth(updatedBirth.getIsExternalBirth());
        }

        if (updatedBirth.getBirthType() != null) {
            existingBirth.setBirthType(updatedBirth.getBirthType());
        }
        if (updatedBirth.getAssistanceRequired() != null) {
            existingBirth.setAssistanceRequired(updatedBirth.getAssistanceRequired());
        }
        if (updatedBirth.getNumberBornAlive() != null) {
            if (updatedBirth.getNumberBornAlive() < 0) {
                throw new IllegalArgumentException("Number born alive cannot be negative");
            }
            existingBirth.setNumberBornAlive(updatedBirth.getNumberBornAlive());
        }
        if (updatedBirth.getNumberStillborn() != null) {
            if (updatedBirth.getNumberStillborn() < 0) {
                throw new IllegalArgumentException("Number stillborn cannot be negative");
            }
            existingBirth.setNumberStillborn(updatedBirth.getNumberStillborn());
        }
        if (updatedBirth.getAttendantName() != null) {
            existingBirth.setAttendantName(updatedBirth.getAttendantName());
        }

        validateOffspringCountConsistency(existingBirth, hasLinkedChildren);

        if (existingBirth.getWeaningDate() == null
                && existingBirth.getBirthDate() != null
                && existingBirth.getLivestockId() != null) {
            Livestock motherForCalc = livestockRepository.findById(existingBirth.getLivestockId()).orElse(null);
            LivestockCategory category = motherForCalc != null ? motherForCalc.getLivestockCategory() : null;
            if (category != null) {
                existingBirth.setWeaningDate(
                        calculateRealWeaningDate(existingBirth.getBirthDate(), category));
            }
        }

        return birthRepository.save(existingBirth);
    }

    public LivestockBirth update(UUID birthId, LivestockBirth birth) {
        return update(birthId, birth, null);
    }

    public LivestockBirth update(UUID birthId, LivestockBirth birth, UUID fatherId) {
        log.debug("Updating birth (update): {}", birthId);
        resolveAndSetLivestock(birth);
        resolveAndSetVeterinarian(birth);
        LivestockBirth saved = updateBirth(birthId, birth);

        if (fatherId != null) {
            applyFatherToLinkedChildren(birthId, fatherId);
        }

        return saved;
    }

    private void applyFatherToLinkedChildren(UUID birthId, UUID fatherId) {
        Livestock father = livestockRepository.findById(fatherId).orElse(null);
        if (father == null) return;

        List<LivestockOffspring> links = offspringRepository.findByBirthEventIdAndIsDeletedFalse(birthId);
        for (LivestockOffspring link : links) {
            Livestock child = link.getChildLivestock();
            if (child != null) {
                child.setFather(father);
                livestockRepository.save(child);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────────────────────────

    public void deleteBirth(UUID birthId) {
        log.debug("Soft deleting birth: {}", birthId);

        LivestockBirth birth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));

        assertNotLocked(birth);

        birth.setIsDeleted(true);
        birthRepository.save(birth);

        List<LivestockOffspring> offspring = offspringRepository.findByBirthEventId(birthId);
        for (LivestockOffspring child : offspring) {
            child.setIsDeleted(true);
            offspringRepository.save(child);
        }
    }

    public void delete(UUID birthId) {
        deleteBirth(birthId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // VERIFY / LOCK LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────────

    public LivestockBirth completeBirth(UUID birthId) {
        log.debug("Completing (locking) birth: {}", birthId);

        LivestockBirth birth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));

        if (birth.isLocked()) {
            throw new IllegalStateException("This birth record is already completed.");
        }

        birth.setCompletedAt(LocalDateTime.now(APP_ZONE));
        birth.setNotes((birth.getNotes() != null ? birth.getNotes() + " " : "")
                + "[Verified/Completed on " + LocalDate.now(APP_ZONE) + "]");

        return birthRepository.save(birth);
    }

    public LivestockBirth complete(UUID birthId) {
        return completeBirth(birthId);
    }

    public LivestockBirth reopenBirth(UUID birthId) {
        log.debug("Reopening (unlocking) birth: {}", birthId);

        LivestockBirth birth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));

        if (!birth.isLocked()) {
            throw new IllegalStateException("This birth record is not currently locked.");
        }

        birth.setCompletedAt(null);
        birth.setNotes((birth.getNotes() != null ? birth.getNotes() + " " : "")
                + "[Reopened/Amended on " + LocalDate.now(APP_ZONE) + " for correction]");

        return birthRepository.save(birth);
    }

    public LivestockBirth reopen(UUID birthId) {
        return reopenBirth(birthId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CHILD LINKING
    // ─────────────────────────────────────────────────────────────────────────────

    public LivestockOffspring addOffspring(UUID birthId, UUID childLivestockId, Integer generation) {
        return addOffspring(birthId, childLivestockId, generation,
                LivestockOffspring.OUTCOME_ALIVE, null, null);
    }

    public LivestockOffspring addOffspring(UUID birthId, UUID childLivestockId, Integer generation,
                                           String outcome, BigDecimal birthWeightKg, UUID fatherId) {
        log.debug("Adding offspring to birth: {}", birthId);

        LivestockBirth birth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));

        assertNotLocked(birth);

        Livestock child = livestockRepository.findById(childLivestockId)
                .orElseThrow(() -> new RuntimeException("Child livestock not found"));

        Livestock mother = birth.getLivestockId() != null
                ? livestockRepository.findById(birth.getLivestockId()).orElse(null)
                : null;

        validateChildLinkage(birth, mother, child);

        String resolvedOutcome = (outcome != null && !outcome.isBlank())
                ? outcome : LivestockOffspring.OUTCOME_ALIVE;

        LivestockOffspring offspring = new LivestockOffspring();
        offspring.setBirthEvent(birth);
        offspring.setChildLivestock(child);
        offspring.setGeneration(generation != null ? generation : 1);
        offspring.setOutcome(resolvedOutcome);
        offspring.setBirthWeightKg(birthWeightKg);
        offspring.setIsDeleted(false);

        child.setMother(mother);
        if (fatherId != null) {
            Livestock father = livestockRepository.findById(fatherId).orElse(null);
            if (father != null) {
                child.setFather(father);
            }
        }

        child.setBirthDate(birth.getBirthDate());
        child.setStatus(LivestockOffspring.OUTCOME_STILLBORN.equals(resolvedOutcome)
                ? Livestock.STATUS_DEAD : Livestock.STATUS_ACTIVE);
        livestockRepository.save(child);

        LivestockOffspring savedOffspring = offspringRepository.save(offspring);

        recalcOffspringSummary(birthId);

        return savedOffspring;
    }

    private void validateChildLinkage(LivestockBirth birth, Livestock mother, Livestock child) {

        if (mother == null) {
            throw new IllegalStateException(
                    "This birth record's mother animal could not be found or is not linked. "
                            + "Run the mother-link repair before linking offspring.");
        }
        if (Boolean.TRUE.equals(mother.getIsDeleted())) {
            throw new IllegalStateException(
                    "Mother animal (" + mother.getTagNumber() + ") is deleted and can't receive new offspring links.");
        }
        if (Livestock.STATUS_DEAD.equals(mother.getStatus())) {
            throw new IllegalStateException(
                    "Mother animal (" + mother.getTagNumber() + ") is recorded as dead.");
        }

        if (Boolean.TRUE.equals(child.getIsDeleted())) {
            throw new IllegalStateException(
                    "Child animal (" + child.getTagNumber() + ") is deleted and can't be linked.");
        }
        if (Livestock.STATUS_SOLD.equals(child.getStatus())) {
            throw new IllegalStateException(
                    "Child animal (" + child.getTagNumber() + ") is already recorded as sold and can't be "
                            + "linked as new offspring.");
        }

        if (mother.getLivestockCategory() != null && child.getLivestockCategory() != null                && !mother.getLivestockCategory().getId().equals(child.getLivestockCategory().getId())) {
            throw new IllegalStateException(
                    "Category mismatch: mother " + mother.getTagNumber() + " is "
                            + mother.getLivestockCategory().getName() + " but " + child.getTagNumber() + " is "
                            + child.getLivestockCategory().getName() + ". Offspring must belong to the same "
                            + "livestock category/species as the mother.");
        }

        if (birth.getBirthDate() != null && child.getBirthDate() != null) {
            long diffDays = Math.abs(ChronoUnit.DAYS.between(birth.getBirthDate(), child.getBirthDate()));
            if (diffDays > CHILD_BIRTH_DATE_TOLERANCE_DAYS) {
                throw new IllegalStateException(
                        "Birth date mismatch: this birth record is dated " + birth.getBirthDate()
                                + ", but " + child.getTagNumber() + "'s own birth date is " + child.getBirthDate()
                                + " (" + diffDays + " day(s) apart, more than the " + CHILD_BIRTH_DATE_TOLERANCE_DAYS
                                + "-day tolerance). Correct one of the dates or select a different animal.");
            }
        }

        if (child.getMother() != null && !child.getMother().getId().equals(mother.getId())) {
            throw new IllegalStateException(
                    "Child animal (" + child.getTagNumber() + ") is already linked to a different mother ("
                            + child.getMother().getTagNumber() + "). Unlink it there first.");
        }

        List<LivestockOffspring> existingLinks =
                offspringRepository.findByChildLivestockIdAndIsDeletedFalse(child.getId());
        if (!existingLinks.isEmpty()) {
            LivestockOffspring existingLink = existingLinks.get(0);
            boolean sameBirth = existingLink.getBirthEvent() != null
                    && existingLink.getBirthEvent().getId().equals(birth.getId());
            if (sameBirth) {
                throw new IllegalStateException(
                        "Child animal (" + child.getTagNumber() + ") is already linked to this birth record.");
            }
            throw new IllegalStateException(
                    "Child animal (" + child.getTagNumber() + ") is already linked to another birth event"
                            + (existingLink.getBirthEvent() != null && existingLink.getBirthEvent().getBirthDate() != null
                            ? " (dated " + existingLink.getBirthEvent().getBirthDate() + ")" : "")
                            + ". An animal can only be linked to one birth record — unlink it there first.");
        }
    }

    public LivestockOffspring linkChild(UUID birthId, UUID childLivestockId) {
        return addOffspring(birthId, childLivestockId, 1);
    }

    public LivestockOffspring linkChild(UUID birthId, UUID childLivestockId,
                                        String outcome, BigDecimal birthWeightKg, UUID fatherId) {
        return addOffspring(birthId, childLivestockId, 1, outcome, birthWeightKg, fatherId);
    }

    public void removeOffspring(UUID offspringId) {
        log.debug("Removing offspring: {}", offspringId);

        LivestockOffspring offspring = offspringRepository.findById(offspringId)
                .orElseThrow(() -> new RuntimeException("Offspring record not found"));

        if (offspring.getBirthEvent() != null) {
            assertNotLocked(offspring.getBirthEvent());
        }

        offspring.setIsDeleted(true);
        offspringRepository.save(offspring);

        LivestockBirth birth = offspring.getBirthEvent();
        if (birth != null) {
            recalcOffspringSummary(birth.getId());
        }
    }

    public void unlinkChild(UUID childLivestockId) {
        log.debug("Unlinking child livestock: {}", childLivestockId);

        List<LivestockOffspring> links = offspringRepository
                .findByChildLivestockIdAndIsDeletedFalse(childLivestockId);

        for (LivestockOffspring link : links) {
            removeOffspring(link.getId());
        }

        livestockRepository.findById(childLivestockId).ifPresent(child -> {
            child.setMother(null);
            livestockRepository.save(child);
        });
    }

    public List<LivestockOffspring> getOffspringByBirth(UUID birthId) {
        log.debug("Getting offspring for birth: {}", birthId);
        return offspringRepository.findByBirthEventIdAndIsDeletedFalse(birthId);
    }

    public long countLiveOffspring(UUID birthId) {
        log.debug("Counting live offspring for birth: {}", birthId);
        return offspringRepository.countLiveOffspringByBirthEventId(birthId);
    }

    public Optional<LivestockBirth> getBirthByChildLivestock(UUID childLivestockId) {
        log.debug("Finding birth by child livestock ID: {}", childLivestockId);

        List<LivestockOffspring> offspring = offspringRepository.findByChildLivestockIdAndIsDeletedFalse(childLivestockId);

        if (offspring.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(offspring.get(0).getBirthEvent());
    }

    public boolean isChildLivestock(UUID livestockId) {
        log.debug("Checking if livestock is a child: {}", livestockId);
        List<LivestockOffspring> offspring = offspringRepository.findByChildLivestockIdAndIsDeletedFalse(livestockId);
        return !offspring.isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // REPORTS / QUERIES
    // ─────────────────────────────────────────────────────────────────────────────

    public List<LivestockBirth> getBirthsByDateRange(LocalDate startDate, LocalDate endDate) {
        log.debug("Getting births between {} and {}", startDate, endDate);
        return birthRepository.findByBirthDateBetween(startDate, endDate);
    }

    public Map<String, List<LivestockBirth>> getBirthsByCategory() {
        log.debug("Getting births grouped by category");

        List<LivestockBirth> births = birthRepository.findByIsDeletedFalseOrderByBirthDateDesc();
        Map<String, List<LivestockBirth>> birthsByCategory = new HashMap<>();

        for (LivestockBirth birth : births) {
            if (birth.getLivestock() != null && birth.getLivestock().getLivestockCategory() != null) {
                String categoryName = birth.getLivestock().getLivestockCategory().getName();
                birthsByCategory.computeIfAbsent(categoryName, k -> new ArrayList<>()).add(birth);
            } else {
                birthsByCategory.computeIfAbsent("Unknown", k -> new ArrayList<>()).add(birth);
            }
        }

        return birthsByCategory;
    }

    public BirthStatistics getBirthStatistics() {
        log.debug("Getting birth statistics");

        BirthStatistics stats = new BirthStatistics();

        List<LivestockBirth> births = birthRepository.findByIsDeletedFalseOrderByBirthDateDesc();
        stats.setTotalBirths(births.size());

        int totalOffspring = 0;
        int liveOffspring = 0;
        int stillborn = 0;

        for (LivestockBirth birth : births) {
            totalOffspring += birth.getOffspringCount() != null ? birth.getOffspringCount() : 0;

            List<LivestockOffspring> offspring = offspringRepository.findByBirthEventIdAndIsDeletedFalse(birth.getId());
            for (LivestockOffspring child : offspring) {
                if (child.isAlive()) {
                    liveOffspring++;
                } else {
                    stillborn++;
                }
            }
        }

        stats.setTotalOffspring(totalOffspring);
        stats.setLiveOffspring(liveOffspring);
        stats.setStillborn(stillborn);

        if (births.size() > 0) {
            stats.setAverageOffspringPerBirth((double) totalOffspring / births.size());
        }

        Map<Integer, Integer> birthsByYear = new TreeMap<>(Collections.reverseOrder());
        for (LivestockBirth birth : births) {
            if (birth.getBirthDate() != null) {
                int year = birth.getBirthDate().getYear();
                birthsByYear.merge(year, 1, Integer::sum);
            }
        }
        stats.setBirthsByYear(birthsByYear);

        return stats;
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // BIRTH REPORT
    // ═════════════════════════════════════════════════════════════════════════════

    public BirthReportResult generateBirthReport(LocalDate fromDate,
                                                 LocalDate toDate,
                                                 UUID categoryId,
                                                 UUID motherId,
                                                 String birthType,
                                                 String locationQuery) {

        log.debug("Generating birth report: from={}, to={}, categoryId={}, motherId={}, birthType={}, location={}",
                fromDate, toDate, categoryId, motherId, birthType, locationQuery);

        List<LivestockBirth> allBirths = birthRepository.findByIsDeletedFalseOrderByBirthDateDesc();

        Set<UUID> allMotherIds = allBirths.stream()
                .map(LivestockBirth::getLivestockId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Livestock> mothersById = allMotherIds.isEmpty()
                ? Collections.emptyMap()
                : livestockRepository.findAllById(allMotherIds).stream()
                .collect(Collectors.toMap(Livestock::getId, m -> m, (a, b) -> a));

        String locationNeedle = (locationQuery != null && !locationQuery.isBlank())
                ? locationQuery.trim().toLowerCase(Locale.ROOT) : null;
        String birthTypeNeedle = (birthType != null && !birthType.isBlank()) ? birthType.trim() : null;

        List<LivestockBirth> filtered = allBirths.stream()
                .filter(b -> fromDate == null || (b.getBirthDate() != null && !b.getBirthDate().isBefore(fromDate)))
                .filter(b -> toDate == null || (b.getBirthDate() != null && !b.getBirthDate().isAfter(toDate)))
                .filter(b -> motherId == null || motherId.equals(b.getLivestockId()))
                .filter(b -> birthTypeNeedle == null || birthTypeNeedle.equalsIgnoreCase(b.getBirthType()))
                .filter(b -> categoryId == null || matchesCategory(mothersById.get(b.getLivestockId()), categoryId))
                .filter(b -> locationNeedle == null || matchesLocation(b, mothersById.get(b.getLivestockId()), locationNeedle))
                .collect(Collectors.toList());

        List<BirthReportRow> rows = new ArrayList<>();
        Map<String, CategoryReportStats> categorySummary = new TreeMap<>();
        Map<UUID, MotherPerformanceAccumulator> motherAccumulators = new LinkedHashMap<>();
        Map<YearMonth, Integer> monthlyTrend = new TreeMap<>();
        Map<YearMonth, Integer> monthlyOffspringTrend = new TreeMap<>();

        int totalOffspringBorn = 0;
        int totalBornAlive = 0;
        int totalStillborn = 0;
        int totalLinkedChildren = 0;
        int totalMale = 0;
        int totalFemale = 0;

        int rowIndex = 1;
        for (LivestockBirth birth : filtered) {
            Livestock mother = mothersById.get(birth.getLivestockId());
            String motherTag = mother != null ? mother.getTagNumber() : "Unknown";
            String categoryName = (mother != null && mother.getLivestockCategory() != null)
                    ? mother.getLivestockCategory().getName() : "Unknown";

            List<LivestockOffspring> links = offspringRepository.findByBirthEventIdAndIsDeletedFalse(birth.getId());
            List<Livestock> linkedChildren = links.stream()
                    .map(LivestockOffspring::getChildLivestock)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            int linkedCount = linkedChildren.size();
            long maleCount = linkedChildren.stream().filter(c -> "MALE".equalsIgnoreCase(c.getGender())).count();
            long femaleCount = linkedChildren.stream().filter(c -> "FEMALE".equalsIgnoreCase(c.getGender())).count();

            int bornAlive = birth.getNumberBornAlive() != null ? birth.getNumberBornAlive() : 0;
            int stillborn = birth.getNumberStillborn() != null ? birth.getNumberStillborn() : 0;
            int totalBorn = birth.getOffspringCount() != null
                    ? birth.getOffspringCount()
                    : (bornAlive + stillborn);

            BirthReportRow row = new BirthReportRow();
            row.setIndex(rowIndex++);
            row.setBirthId(birth.getId());
            row.setMotherTag(motherTag);
            row.setCategory(categoryName);
            row.setBirthDate(birth.getBirthDate());
            row.setTotalBorn(totalBorn);
            row.setBornAlive(bornAlive);
            row.setStillborn(stillborn);
            row.setMaleCount((int) maleCount);
            row.setFemaleCount((int) femaleCount);
            row.setLinkedChildren(linkedCount);
            row.setBirthType(birth.getBirthType());
            row.setAssistanceRequired(Boolean.TRUE.equals(birth.getAssistanceRequired()));
            row.setOutcomeLabel(buildOutcomeLabel(birth, totalBorn, bornAlive, stillborn));
            row.setLocked(birth.isLocked());
            rows.add(row);

            totalOffspringBorn += totalBorn;
            totalBornAlive += bornAlive;
            totalStillborn += stillborn;
            totalLinkedChildren += linkedCount;
            totalMale += (int) maleCount;
            totalFemale += (int) femaleCount;

            CategoryReportStats catStats = categorySummary.computeIfAbsent(categoryName, CategoryReportStats::new);
            catStats.addBirth(totalBorn, bornAlive, stillborn, linkedCount);

            if (mother != null) {
                MotherPerformanceAccumulator acc = motherAccumulators.computeIfAbsent(mother.getId(),
                        id -> new MotherPerformanceAccumulator(motherTag));
                acc.addBirth(birth, totalBorn, bornAlive, stillborn);
            }

            if (birth.getBirthDate() != null) {
                YearMonth ym = YearMonth.from(birth.getBirthDate());
                monthlyTrend.merge(ym, 1, Integer::sum);
                monthlyOffspringTrend.merge(ym, totalBorn, Integer::sum);
            }
        }

        int totalBirths = filtered.size();
        long uniqueMothers = filtered.stream()
                .map(LivestockBirth::getLivestockId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        double survivalRate = (totalOffspringBorn > 0)
                ? round1((totalBornAlive * 100.0) / totalOffspringBorn)
                : 0.0;
        double liveBirthRate = survivalRate;
        double stillbirthRate = (totalOffspringBorn > 0)
                ? round1((totalStillborn * 100.0) / totalOffspringBorn)
                : 0.0;
        double registrationCompletionRate = (totalOffspringBorn > 0)
                ? round1((totalLinkedChildren * 100.0) / totalOffspringBorn)
                : 0.0;
        double avgOffspringPerBirth = (totalBirths > 0)
                ? round2((double) totalOffspringBorn / totalBirths)
                : 0.0;
        double avgOffspringPerMother = (uniqueMothers > 0)
                ? round2((double) totalOffspringBorn / uniqueMothers)
                : 0.0;

        List<MotherPerformanceRow> motherPerformance = motherAccumulators.values().stream()
                .map(MotherPerformanceAccumulator::toRow)
                .sorted(Comparator.comparing(MotherPerformanceRow::getMotherTag))
                .collect(Collectors.toList());

        String topMother = null;
        int topMotherOffspring = 0;
        MotherPerformanceRow topMotherRow = motherPerformance.stream()
                .max(Comparator.comparingInt(MotherPerformanceRow::getTotalOffspring))
                .filter(r -> r.getTotalOffspring() > 0)
                .orElse(null);
        if (topMotherRow != null) {
            topMotherRow.setTopPerformer(true);
            topMother = topMotherRow.getMotherTag();
            topMotherOffspring = topMotherRow.getTotalOffspring();
        }

        String topCategory = null;
        double topCategoryRate = 0.0;
        for (CategoryReportStats stats : categorySummary.values()) {
            if (stats.getTotalOffspring() <= 0) continue;
            double rate = round1((stats.getBornAlive() * 100.0) / stats.getTotalOffspring());
            if (topCategory == null || rate > topCategoryRate) {
                topCategory = stats.getCategory();
                topCategoryRate = rate;
            }
        }

        int maxMonthlyTrend = monthlyTrend.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int maxMonthlyOffspringTrend = monthlyOffspringTrend.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        PeriodComparison comparison = buildPeriodComparison(
                allBirths, mothersById, fromDate, toDate, categoryId, motherId, birthTypeNeedle, locationNeedle, totalBirths);

        BirthReportResult result = new BirthReportResult();
        result.setFromDate(fromDate);
        result.setToDate(toDate);
        result.setTotalBirths(totalBirths);
        result.setUniqueMothers((int) uniqueMothers);
        result.setTotalOffspringBorn(totalOffspringBorn);
        result.setTotalBornAlive(totalBornAlive);
        result.setTotalStillborn(totalStillborn);
        result.setLinkedChildren(totalLinkedChildren);
        result.setSurvivalRatePercent(survivalRate);
        result.setLiveBirthRate(liveBirthRate);
        result.setStillbirthRate(stillbirthRate);
        result.setRegistrationCompletionRate(registrationCompletionRate);
        result.setAverageOffspringPerBirth(avgOffspringPerBirth);
        result.setAverageOffspringPerMother(avgOffspringPerMother);
        result.setTotalMale(totalMale);
        result.setTotalFemale(totalFemale);
        result.setTopCategory(topCategory);
        result.setTopCategoryRate(topCategoryRate);
        result.setTopMother(topMother);
        result.setTopMotherOffspring(topMotherOffspring);
        result.setRows(rows);
        result.setCategorySummary(categorySummary);
        result.setMonthlyTrend(toDisplayTrend(monthlyTrend));
        result.setMonthlyOffspringTrend(toDisplayTrend(monthlyOffspringTrend));
        result.setMaxMonthlyTrend(maxMonthlyTrend);
        result.setMaxMonthlyOffspringTrend(maxMonthlyOffspringTrend);
        result.setMotherPerformance(motherPerformance);
        result.setPeriodComparison(comparison);
        return result;
    }

    private boolean matchesCategory(Livestock mother, UUID categoryId) {
        return mother != null
                && mother.getLivestockCategory() != null
                && categoryId.equals(mother.getLivestockCategory().getId());
    }

    private boolean matchesLocation(LivestockBirth birth, Livestock mother, String locationNeedle) {
        if (birth.getSourceLocation() != null
                && birth.getSourceLocation().toLowerCase(Locale.ROOT).contains(locationNeedle)) {
            return true;
        }
        if (mother != null && mother.getLocation() != null && mother.getLocation().getName() != null) {
            return mother.getLocation().getName().toLowerCase(Locale.ROOT).contains(locationNeedle);
        }
        return false;
    }

    private String buildOutcomeLabel(LivestockBirth birth, int totalBorn, int bornAlive, int stillborn) {
        List<String> parts = new ArrayList<>();

        String type = birth.getBirthType();
        if (type == null || type.isBlank()) {
            parts.add("Not Recorded");
        } else if (LivestockBirth.BIRTH_TYPE_DIFFICULT.equalsIgnoreCase(type)) {
            parts.add("Difficult Birth");
        } else if (LivestockBirth.BIRTH_TYPE_NORMAL.equalsIgnoreCase(type)) {
            parts.add("Normal");
        } else {
            parts.add(type);
        }

        if (Boolean.TRUE.equals(birth.getAssistanceRequired())) {
            parts.add("Assisted");
        }

        if (totalBorn > 0 && bornAlive == 0 && stillborn > 0) {
            parts.add("Total Stillbirth");
        } else if (stillborn > 0) {
            parts.add(stillborn + " Stillborn");
        }

        return String.join(" · ", parts);
    }

    private LinkedHashMap<String, Integer> toDisplayTrend(Map<YearMonth, Integer> trend) {
        LinkedHashMap<String, Integer> display = new LinkedHashMap<>();
        trend.forEach((ym, count) -> display.put(ym.toString(), count));
        return display;
    }

    private PeriodComparison buildPeriodComparison(List<LivestockBirth> allBirths,
                                                   Map<UUID, Livestock> mothersById,
                                                   LocalDate fromDate,
                                                   LocalDate toDate,
                                                   UUID categoryId,
                                                   UUID motherId,
                                                   String birthTypeNeedle,
                                                   String locationNeedle,
                                                   int currentPeriodBirths) {
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)) {
            return null;
        }

        long periodLengthDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        LocalDate prevTo = fromDate.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(periodLengthDays - 1);

        long previousPeriodBirths = allBirths.stream()
                .filter(b -> b.getBirthDate() != null
                        && !b.getBirthDate().isBefore(prevFrom)
                        && !b.getBirthDate().isAfter(prevTo))
                .filter(b -> motherId == null || motherId.equals(b.getLivestockId()))
                .filter(b -> birthTypeNeedle == null || birthTypeNeedle.equalsIgnoreCase(b.getBirthType()))
                .filter(b -> categoryId == null || matchesCategory(mothersById.get(b.getLivestockId()), categoryId))
                .filter(b -> locationNeedle == null || matchesLocation(b, mothersById.get(b.getLivestockId()), locationNeedle))
                .count();

        double changePercent;
        if (previousPeriodBirths == 0) {
            changePercent = currentPeriodBirths > 0 ? 100.0 : 0.0;
        } else {
            changePercent = round1(((currentPeriodBirths - previousPeriodBirths) * 100.0) / previousPeriodBirths);
        }

        PeriodComparison comparison = new PeriodComparison();
        comparison.setPreviousFromDate(prevFrom);
        comparison.setPreviousToDate(prevTo);
        comparison.setCurrentPeriodBirths(currentPeriodBirths);
        comparison.setPreviousPeriodBirths((int) previousPeriodBirths);
        comparison.setChangePercent(changePercent);
        return comparison;
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ADMIN — repair
    // ─────────────────────────────────────────────────────────────────────────────

    public int repairMissingMotherLinks() {
        log.debug("Repairing missing mother links");

        int fixed = 0;
        List<LivestockBirth> allBirths = getAll();

        for (LivestockBirth birth : allBirths) {
            if (birth.getLivestockId() == null) continue;

            Livestock mother = livestockRepository.findById(birth.getLivestockId()).orElse(null);
            if (mother == null) continue;

            List<LivestockOffspring> offspring =
                    offspringRepository.findByBirthEventIdAndIsDeletedFalse(birth.getId());

            for (LivestockOffspring o : offspring) {
                Livestock child = o.getChildLivestock();
                if (child == null) continue;

                boolean motherMissing = child.getMother() == null
                        || !mother.getId().equals(child.getMother().getId());
                boolean birthDateMissing = child.getBirthDate() == null && birth.getBirthDate() != null;

                if (motherMissing || birthDateMissing) {
                    child.setMother(mother);
                    if (birthDateMissing) {
                        child.setBirthDate(birth.getBirthDate());
                    }
                    livestockRepository.save(child);
                    fixed++;
                }
            }
        }

        log.debug("Repaired {} livestock record(s)", fixed);
        return fixed;
    }

    public int repairStalePregnanciesAfterBirth() {
        log.debug("Repairing stale post-birth pregnancy flags");

        int fixed = 0;
        Set<UUID> motherIdsWithBirths = getAll().stream()
                .map(LivestockBirth::getLivestockId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (UUID motherId : motherIdsWithBirths) {
            Livestock mother = livestockRepository.findById(motherId).orElse(null);
            if (mother == null) continue;

            boolean stillFlaggedPregnant = Livestock.REPRO_PREGNANT.equals(mother.getReproductiveStatus());

            List<LivestockBreeding> lingering = breedingRepository.findActiveByLivestockId(
                    motherId,
                    List.of(LivestockBreeding.STATUS_CONFIRMED_PREGNANT, LivestockBreeding.STATUS_PREGNANT)
            );

            if (stillFlaggedPregnant || !lingering.isEmpty()) {
                if (stillFlaggedPregnant) {
                    mother.setReproductiveStatus(Livestock.REPRO_OPEN);
                    mother.setExpectedDueDate(null);
                    livestockRepository.save(mother);
                }
                for (LivestockBreeding breeding : lingering) {
                    breeding.setStatus(LivestockBreeding.STATUS_COMPLETED);
                    breeding.setNotes((breeding.getNotes() != null ? breeding.getNotes() + " " : "")
                            + "Repaired: birth already on file for this animal.");
                    breedingRepository.save(breeding);
                }
                fixed++;
            }
        }

        log.debug("Repaired {} mother record(s)", fixed);
        return fixed;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // VETERINARIAN — quick-add
    // ─────────────────────────────────────────────────────────────────────────────

    public Veterinarian quickAddVeterinarian(String firstName, String lastName, String phone) {
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required.");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required.");
        }

        Veterinarian vet = new Veterinarian();
        vet.setFirstName(firstName.trim());
        vet.setLastName(lastName.trim());
        if (phone != null && !phone.trim().isEmpty()) {
            vet.setPhone(phone.trim());
        }
        vet.setIsActive(true);
        vet.setIsDeleted(false);
        vet.setNotes("Quick-added from birth recording form on " + LocalDate.now(APP_ZONE) + ".");

        return veterinarianRepository.save(vet);
    }

    public List<Veterinarian> findPossibleDuplicateVeterinarians(String firstName, String lastName) {
        if (firstName == null || lastName == null) return Collections.emptyList();
        return veterinarianRepository.findPossibleDuplicates(firstName.trim(), lastName.trim());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────────

    private void resolveAndSetLivestock(LivestockBirth birth) {
        String idStr = birth.getLivestockIdValue();
        if (idStr != null && !idStr.isBlank()) {
            birth.setLivestockId(UUID.fromString(idStr));
        }
    }

    private void resolveAndSetVeterinarian(LivestockBirth birth) {
        String idStr = birth.getVeterinarianIdValue();
        if (idStr != null && !idStr.isBlank()) {
            UUID vetId;
            try {
                vetId = UUID.fromString(idStr.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid veterinarian selection — please pick from the search results.");
            }
            Veterinarian vet = veterinarianRepository.findById(vetId)
                    .orElseThrow(() -> new IllegalArgumentException("Selected veterinarian was not found."));
            if (!Boolean.TRUE.equals(vet.getIsActive()) || Boolean.TRUE.equals(vet.getIsDeleted())) {
                throw new IllegalArgumentException(
                        "Selected veterinarian (" + vet.getFullName() + ") is not active. Please choose another.");
            }
            birth.setVeterinarianId(vetId);
        } else {
            birth.setVeterinarianId(null);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // INNER CLASSES
    // ─────────────────────────────────────────────────────────────────────────────

    public static class BirthStatistics {
        private int totalBirths;
        private int totalOffspring;
        private int liveOffspring;
        private int stillborn;
        private double averageOffspringPerBirth;
        private Map<Integer, Integer> birthsByYear = new HashMap<>();

        public int getTotalBirths() { return totalBirths; }
        public void setTotalBirths(int totalBirths) { this.totalBirths = totalBirths; }

        public int getTotalOffspring() { return totalOffspring; }
        public void setTotalOffspring(int totalOffspring) { this.totalOffspring = totalOffspring; }

        public int getLiveOffspring() { return liveOffspring; }
        public void setLiveOffspring(int liveOffspring) { this.liveOffspring = liveOffspring; }

        public int getStillborn() { return stillborn; }
        public void setStillborn(int stillborn) { this.stillborn = stillborn; }

        public double getAverageOffspringPerBirth() { return averageOffspringPerBirth; }
        public void setAverageOffspringPerBirth(double averageOffspringPerBirth) {
            this.averageOffspringPerBirth = averageOffspringPerBirth;
        }

        public Map<Integer, Integer> getBirthsByYear() { return birthsByYear; }
        public void setBirthsByYear(Map<Integer, Integer> birthsByYear) { this.birthsByYear = birthsByYear; }
    }

    public static class CategoryBirthStats {
        private final String categoryName;
        private long totalBirths;
        private long locked;

        public CategoryBirthStats(String categoryName) {
            this.categoryName = categoryName;
        }

        public void incrementTotalBirths() { this.totalBirths++; }
        public void incrementLocked() { this.locked++; }

        public String getCategoryName() { return categoryName; }
        public long getTotalBirths() { return totalBirths; }
        public long getLocked() { return locked; }
    }

    public static class BirthReportRow {
        private int index;
        private UUID birthId;
        private String motherTag;
        private String category;
        private LocalDate birthDate;
        private int totalBorn;
        private int bornAlive;
        private int stillborn;
        private int maleCount;
        private int femaleCount;
        private int linkedChildren;
        private String birthType;
        private boolean assistanceRequired;
        private String outcomeLabel;
        private boolean locked;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }

        public UUID getBirthId() { return birthId; }
        public void setBirthId(UUID birthId) { this.birthId = birthId; }

        public String getMotherTag() { return motherTag; }
        public void setMotherTag(String motherTag) { this.motherTag = motherTag; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public LocalDate getBirthDate() { return birthDate; }
        public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

        public int getTotalBorn() { return totalBorn; }
        public void setTotalBorn(int totalBorn) { this.totalBorn = totalBorn; }

        public int getBornAlive() { return bornAlive; }
        public void setBornAlive(int bornAlive) { this.bornAlive = bornAlive; }

        public int getStillborn() { return stillborn; }
        public void setStillborn(int stillborn) { this.stillborn = stillborn; }

        public int getMaleCount() { return maleCount; }
        public void setMaleCount(int maleCount) { this.maleCount = maleCount; }

        public int getFemaleCount() { return femaleCount; }
        public void setFemaleCount(int femaleCount) { this.femaleCount = femaleCount; }

        public int getLinkedChildren() { return linkedChildren; }
        public void setLinkedChildren(int linkedChildren) { this.linkedChildren = linkedChildren; }

        public String getBirthType() { return birthType; }
        public void setBirthType(String birthType) { this.birthType = birthType; }

        public boolean isAssistanceRequired() { return assistanceRequired; }
        public void setAssistanceRequired(boolean assistanceRequired) { this.assistanceRequired = assistanceRequired; }

        public String getOutcomeLabel() { return outcomeLabel; }
        public void setOutcomeLabel(String outcomeLabel) { this.outcomeLabel = outcomeLabel; }

        public boolean isLocked() { return locked; }
        public void setLocked(boolean locked) { this.locked = locked; }
    }

    public static class CategoryReportStats {
        private final String category;
        private int births;
        private int totalOffspring;
        private int bornAlive;
        private int stillborn;
        private int linkedChildren;

        public CategoryReportStats(String category) {
            this.category = category;
        }

        void addBirth(int totalBorn, int alive, int still, int linked) {
            this.births++;
            this.totalOffspring += totalBorn;
            this.bornAlive += alive;
            this.stillborn += still;
            this.linkedChildren += linked;
        }

        public String getCategory() { return category; }
        public int getBirths() { return births; }
        public int getTotalOffspring() { return totalOffspring; }
        public int getBornAlive() { return bornAlive; }
        public int getStillborn() { return stillborn; }
        public int getLinkedChildren() { return linkedChildren; }

        public double getAverageOffspring() {
            return births > 0 ? (double) totalOffspring / births : 0.0;
        }
    }

    public static class MotherPerformanceRow {
        private String motherTag;
        private int totalBirths;
        private int totalOffspring;
        private double averageOffspringPerBirth;
        private int bornAlive;
        private int stillborn;
        private LocalDate lastBirthDate;
        private LocalDate nextBreedingDate;
        private boolean firstTimeMother;
        private boolean topPerformer;

        public String getMotherTag() { return motherTag; }
        public void setMotherTag(String motherTag) { this.motherTag = motherTag; }

        public int getTotalBirths() { return totalBirths; }
        public void setTotalBirths(int totalBirths) { this.totalBirths = totalBirths; }

        public int getTotalOffspring() { return totalOffspring; }
        public void setTotalOffspring(int totalOffspring) { this.totalOffspring = totalOffspring; }

        public double getAverageOffspringPerBirth() { return averageOffspringPerBirth; }
        public void setAverageOffspringPerBirth(double averageOffspringPerBirth) {
            this.averageOffspringPerBirth = averageOffspringPerBirth;
        }

        public int getBornAlive() { return bornAlive; }
        public void setBornAlive(int bornAlive) { this.bornAlive = bornAlive; }

        public int getStillborn() { return stillborn; }
        public void setStillborn(int stillborn) { this.stillborn = stillborn; }

        public LocalDate getLastBirthDate() { return lastBirthDate; }
        public void setLastBirthDate(LocalDate lastBirthDate) { this.lastBirthDate = lastBirthDate; }

        public LocalDate getNextBreedingDate() { return nextBreedingDate; }
        public void setNextBreedingDate(LocalDate nextBreedingDate) { this.nextBreedingDate = nextBreedingDate; }

        public boolean isFirstTimeMother() { return firstTimeMother; }
        public void setFirstTimeMother(boolean firstTimeMother) { this.firstTimeMother = firstTimeMother; }

        public boolean isTopPerformer() { return topPerformer; }
        public void setTopPerformer(boolean topPerformer) { this.topPerformer = topPerformer; }
    }

    private static class MotherPerformanceAccumulator {
        private final String motherTag;
        private int totalBirths = 0;
        private int totalOffspring = 0;
        private int bornAlive = 0;
        private int stillborn = 0;
        private LocalDate lastBirthDate;
        private LocalDate nextBreedingDate;

        MotherPerformanceAccumulator(String motherTag) {
            this.motherTag = motherTag;
        }

        void addBirth(LivestockBirth birth, int totalBorn, int alive, int still) {
            totalBirths++;
            totalOffspring += totalBorn;
            bornAlive += alive;
            stillborn += still;
            if (birth.getBirthDate() != null
                    && (lastBirthDate == null || birth.getBirthDate().isAfter(lastBirthDate))) {
                lastBirthDate = birth.getBirthDate();
                nextBreedingDate = birth.getNextBreedingDate();
            }
        }

        MotherPerformanceRow toRow() {
            MotherPerformanceRow row = new MotherPerformanceRow();
            row.setMotherTag(motherTag);
            row.setTotalBirths(totalBirths);
            row.setTotalOffspring(totalOffspring);
            row.setBornAlive(bornAlive);
            row.setStillborn(stillborn);
            row.setAverageOffspringPerBirth(totalBirths > 0
                    ? BigDecimal.valueOf((double) totalOffspring / totalBirths)
                    .setScale(2, RoundingMode.HALF_UP).doubleValue()
                    : 0.0);
            row.setLastBirthDate(lastBirthDate);
            row.setNextBreedingDate(nextBreedingDate);
            row.setFirstTimeMother(totalBirths == 1);
            return row;
        }
    }

    public static class PeriodComparison {
        private LocalDate previousFromDate;
        private LocalDate previousToDate;
        private int currentPeriodBirths;
        private int previousPeriodBirths;
        private double changePercent;

        public LocalDate getPreviousFromDate() { return previousFromDate; }
        public void setPreviousFromDate(LocalDate previousFromDate) { this.previousFromDate = previousFromDate; }

        public LocalDate getPreviousToDate() { return previousToDate; }
        public void setPreviousToDate(LocalDate previousToDate) { this.previousToDate = previousToDate; }

        public int getCurrentPeriodBirths() { return currentPeriodBirths; }
        public void setCurrentPeriodBirths(int currentPeriodBirths) { this.currentPeriodBirths = currentPeriodBirths; }

        public int getPreviousPeriodBirths() { return previousPeriodBirths; }
        public void setPreviousPeriodBirths(int previousPeriodBirths) { this.previousPeriodBirths = previousPeriodBirths; }

        public double getChangePercent() { return changePercent; }
        public void setChangePercent(double changePercent) { this.changePercent = changePercent; }
    }

    public static class BirthReportResult {
        private LocalDate fromDate;
        private LocalDate toDate;

        private int totalBirths;
        private int uniqueMothers;
        private int totalOffspringBorn;
        private int totalBornAlive;
        private int totalStillborn;
        private int linkedChildren;
        private double survivalRatePercent;
        private double liveBirthRate;
        private double stillbirthRate;
        private double registrationCompletionRate;
        private double averageOffspringPerBirth;
        private double averageOffspringPerMother;
        private int totalMale;
        private int totalFemale;
        private String topCategory;
        private double topCategoryRate;
        private String topMother;
        private int topMotherOffspring;

        private List<BirthReportRow> rows;
        private Map<String, CategoryReportStats> categorySummary;
        private LinkedHashMap<String, Integer> monthlyTrend;
        private LinkedHashMap<String, Integer> monthlyOffspringTrend;
        private int maxMonthlyTrend;
        private int maxMonthlyOffspringTrend;
        private List<MotherPerformanceRow> motherPerformance;
        private PeriodComparison periodComparison;

        public LocalDate getFromDate() { return fromDate; }
        public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }

        public LocalDate getToDate() { return toDate; }
        public void setToDate(LocalDate toDate) { this.toDate = toDate; }

        public int getTotalBirths() { return totalBirths; }
        public void setTotalBirths(int totalBirths) { this.totalBirths = totalBirths; }

        public int getUniqueMothers() { return uniqueMothers; }
        public void setUniqueMothers(int uniqueMothers) { this.uniqueMothers = uniqueMothers; }

        public int getTotalOffspringBorn() { return totalOffspringBorn; }
        public void setTotalOffspringBorn(int totalOffspringBorn) { this.totalOffspringBorn = totalOffspringBorn; }

        public int getTotalBornAlive() { return totalBornAlive; }
        public void setTotalBornAlive(int totalBornAlive) { this.totalBornAlive = totalBornAlive; }

        public int getTotalStillborn() { return totalStillborn; }
        public void setTotalStillborn(int totalStillborn) { this.totalStillborn = totalStillborn; }

        public int getLinkedChildren() { return linkedChildren; }
        public void setLinkedChildren(int linkedChildren) { this.linkedChildren = linkedChildren; }

        public double getSurvivalRatePercent() { return survivalRatePercent; }
        public void setSurvivalRatePercent(double survivalRatePercent) { this.survivalRatePercent = survivalRatePercent; }

        public double getLiveBirthRate() { return liveBirthRate; }
        public void setLiveBirthRate(double liveBirthRate) { this.liveBirthRate = liveBirthRate; }

        public double getStillbirthRate() { return stillbirthRate; }
        public void setStillbirthRate(double stillbirthRate) { this.stillbirthRate = stillbirthRate; }

        public double getRegistrationCompletionRate() { return registrationCompletionRate; }
        public void setRegistrationCompletionRate(double registrationCompletionRate) {
            this.registrationCompletionRate = registrationCompletionRate;
        }

        public double getAverageOffspringPerBirth() { return averageOffspringPerBirth; }
        public void setAverageOffspringPerBirth(double averageOffspringPerBirth) {
            this.averageOffspringPerBirth = averageOffspringPerBirth;
        }

        public double getAverageOffspringPerMother() { return averageOffspringPerMother; }
        public void setAverageOffspringPerMother(double averageOffspringPerMother) {
            this.averageOffspringPerMother = averageOffspringPerMother;
        }

        public int getTotalMale() { return totalMale; }
        public void setTotalMale(int totalMale) { this.totalMale = totalMale; }

        public int getTotalFemale() { return totalFemale; }
        public void setTotalFemale(int totalFemale) { this.totalFemale = totalFemale; }

        public String getTopCategory() { return topCategory; }
        public void setTopCategory(String topCategory) { this.topCategory = topCategory; }

        public double getTopCategoryRate() { return topCategoryRate; }
        public void setTopCategoryRate(double topCategoryRate) { this.topCategoryRate = topCategoryRate; }

        public String getTopMother() { return topMother; }
        public void setTopMother(String topMother) { this.topMother = topMother; }

        public int getTopMotherOffspring() { return topMotherOffspring; }
        public void setTopMotherOffspring(int topMotherOffspring) { this.topMotherOffspring = topMotherOffspring; }

        public List<BirthReportRow> getRows() { return rows; }
        public void setRows(List<BirthReportRow> rows) { this.rows = rows; }

        public Map<String, CategoryReportStats> getCategorySummary() { return categorySummary; }
        public void setCategorySummary(Map<String, CategoryReportStats> categorySummary) {
            this.categorySummary = categorySummary;
        }

        public LinkedHashMap<String, Integer> getMonthlyTrend() { return monthlyTrend; }
        public void setMonthlyTrend(LinkedHashMap<String, Integer> monthlyTrend) { this.monthlyTrend = monthlyTrend; }

        public LinkedHashMap<String, Integer> getMonthlyOffspringTrend() { return monthlyOffspringTrend; }
        public void setMonthlyOffspringTrend(LinkedHashMap<String, Integer> monthlyOffspringTrend) {
            this.monthlyOffspringTrend = monthlyOffspringTrend;
        }

        public int getMaxMonthlyTrend() { return maxMonthlyTrend; }
        public void setMaxMonthlyTrend(int maxMonthlyTrend) { this.maxMonthlyTrend = maxMonthlyTrend; }

        public int getMaxMonthlyOffspringTrend() { return maxMonthlyOffspringTrend; }
        public void setMaxMonthlyOffspringTrend(int maxMonthlyOffspringTrend) {
            this.maxMonthlyOffspringTrend = maxMonthlyOffspringTrend;
        }

        public List<MotherPerformanceRow> getMotherPerformance() { return motherPerformance; }
        public void setMotherPerformance(List<MotherPerformanceRow> motherPerformance) {
            this.motherPerformance = motherPerformance;
        }

        public PeriodComparison getPeriodComparison() { return periodComparison; }
        public void setPeriodComparison(PeriodComparison periodComparison) { this.periodComparison = periodComparison; }
    }
}