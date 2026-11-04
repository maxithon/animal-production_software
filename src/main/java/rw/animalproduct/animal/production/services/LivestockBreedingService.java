package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.dto.ActionRequiredItemDTO;
import rw.animalproduct.animal.production.dto.ActiveBreedingRowDTO;
import rw.animalproduct.animal.production.dto.BreedingManagementKpiDTO;
import rw.animalproduct.animal.production.dto.IneligibleFemaleDTO;
import rw.animalproduct.animal.production.dto.ReadyToBreedReportDTO;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBirth;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.LivestockBirthRepository;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.VeterinarianRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LivestockBreedingService {

    private static final Logger log = LoggerFactory.getLogger(LivestockBreedingService.class);

    private final LivestockBreedingRepository breedingRepository;
    private final VeterinarianRepository      veterinarianRepository;
    private final LivestockRepository         livestockRepository;
    private final LivestockBirthRepository    birthRepository;
    private final LifecycleEmailService       emailService;
    private final ReadyToBreedReportService   readyToBreedReportService;
    private final FemalesReadyToBreedService  femalesReadyToBreedService;

    private static final int MIN_PREGNANCY_CHECK_DAYS = 21;
    private static final double PREGNANCY_CHECK_FRACTION_OF_GESTATION = 0.16;
    private static final int FALLBACK_PREGNANCY_CHECK_DAYS = 30;
    private static final double AVG_DAYS_PER_MONTH = 30.44;

    private static final int FALLBACK_POSTPARTUM_RECOVERY_DAYS = 45;
    private static final String HEALTH_HEALTHY = "HEALTHY";
    private static final String HEALTH_SICK = "SICK";
    private static final String HEALTH_UNDER_TREATMENT = "UNDER_TREATMENT";

    private static final String FORM_METHOD_NATURAL = "NATURAL";
    private static final String FORM_METHOD_AI = "ARTIFICIAL_INSEMINATION";
    private static final String FORM_METHOD_EMBRYO_TRANSFER = "EMBRYO_TRANSFER";

    private static final int CONSECUTIVE_FAILURES_FLAG_THRESHOLD = 2;

    public LivestockBreedingService(LivestockBreedingRepository breedingRepository,
                                    VeterinarianRepository veterinarianRepository,
                                    LivestockRepository livestockRepository,
                                    LivestockBirthRepository birthRepository,
                                    LifecycleEmailService emailService,
                                    ReadyToBreedReportService readyToBreedReportService,
                                    FemalesReadyToBreedService femalesReadyToBreedService) {
        this.breedingRepository     = breedingRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.livestockRepository    = livestockRepository;
        this.birthRepository        = birthRepository;
        this.emailService           = emailService;
        this.readyToBreedReportService = readyToBreedReportService;
        this.femalesReadyToBreedService = femalesReadyToBreedService;
    }

    // --- READ ------------------------------------------------------------------

    public List<LivestockBreeding> getAll() {
        return breedingRepository.findByIsDeletedFalse(Pageable.unpaged()).getContent();
    }

    public Page<LivestockBreeding> getPaginated(int page, int size) {
        return breedingRepository.findByIsDeletedFalse(PageRequest.of(page, size));
    }

    public Optional<LivestockBreeding> getById(UUID id) {
        return breedingRepository.findById(id)
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()));
    }

    public LivestockBreeding findById(UUID id) {
        return breedingRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));
    }

    public Page<LivestockBreeding> findAll(Pageable pageable, String status, UUID livestockId) {
        if (status != null && livestockId != null) {
            return breedingRepository.findByLivestockIdAndStatusAndIsDeletedFalse(livestockId, status, pageable);
        } else if (status != null) {
            return breedingRepository.findByStatusAndIsDeletedFalse(status, pageable);
        } else if (livestockId != null) {
            return breedingRepository.findByLivestockIdAndIsDeletedFalse(livestockId, pageable);
        } else {
            return breedingRepository.findByIsDeletedFalse(pageable);
        }
    }

    // --- POSTPARTUM RECOVERY CHECK ------------------------------------------

    public LocalDate getLastBirthDate(Livestock female) {
        if (female == null || female.getId() == null) {
            return null;
        }
        return birthRepository
                .findFirstByLivestockIdAndIsDeletedFalseOrderByBirthDateDesc(female.getId())
                .map(LivestockBirth::getBirthDate)
                .orElse(female.getLastBirthDate());
    }

    public boolean isInPostpartumRecovery(Livestock female) {
        return getPostpartumReadyDate(female)
                .map(readyDate -> LocalDate.now().isBefore(readyDate))
                .orElse(false);
    }

    public Optional<LocalDate> getPostpartumReadyDate(Livestock female) {
        if (female == null) {
            return Optional.empty();
        }
        LocalDate lastBirth = getLastBirthDate(female);
        if (lastBirth == null) {
            return Optional.empty();
        }
        Integer recoveryDays = null;
        if (female.getLivestockCategory() != null) {
            recoveryDays = female.getLivestockCategory().getPostpartumRecoveryDays();
        }
        if (recoveryDays == null) {
            recoveryDays = FALLBACK_POSTPARTUM_RECOVERY_DAYS;
        }
        return Optional.of(lastBirth.plusDays(recoveryDays));
    }

    public boolean isHealthyForBreeding(Livestock animal) {
        return animal != null && HEALTH_HEALTHY.equalsIgnoreCase(animal.getHealthStatus());
    }

    private String getHealthIneligibilityReason(Livestock animal) {
        String health = animal.getHealthStatus();
        if (health == null) {
            return "Health status is not recorded — cannot confirm this animal is fit for breeding.";
        }
        if (HEALTH_HEALTHY.equalsIgnoreCase(health)) {
            return null;
        }
        if (HEALTH_SICK.equalsIgnoreCase(health)) {
            return "Animal is currently marked SICK — not fit for breeding until cleared healthy.";
        }
        if (HEALTH_UNDER_TREATMENT.equalsIgnoreCase(health)) {
            return "Animal is currently UNDER_TREATMENT — not fit for breeding until treatment concludes and health status returns to Healthy.";
        }
        return "Health status is " + health + " — not recognised as fit for breeding.";
    }

    // --- FEMALE BREEDING ELIGIBILITY ------------------------------

    public boolean isEligibleForBreeding(Livestock female) {
        return getIneligibilityReason(female) == null;
    }

    /**
     * Block only when the animal is BRED or PREGNANT.
     * All other values (OPEN, READY_TO_BREED, NOT_APPLICABLE, DRY,
     * NURSING, null) are treated as available.
     */
    public String getIneligibilityReason(Livestock female) {
        if (female == null) {
            return "Selected animal could not be found.";
        }
        if (!"FEMALE".equalsIgnoreCase(female.getGender())) {
            return "Selected animal is not female.";
        }
        if (!Livestock.STATUS_ACTIVE.equals(female.getStatus())) {
            return "Animal status is " + female.getStatus() + ", not Active.";
        }

        if (!isHealthyForBreeding(female)) {
            return getHealthIneligibilityReason(female);
        }

        String reproStatus = female.getReproductiveStatus();
        if (reproStatus != null) {
            String normalized = reproStatus.toUpperCase();
            if (Livestock.REPRO_BRED.equals(normalized)
                    || Livestock.REPRO_PREGNANT.equals(normalized)) {
                return "Reproductive status is " + female.reproductiveStatusLabel()
                        + " — not available for breeding right now.";
            }
        }

        if (isInPostpartumRecovery(female)) {
            LocalDate readyDate = getPostpartumReadyDate(female).orElse(null);
            long daysRemaining = readyDate != null
                    ? ChronoUnit.DAYS.between(LocalDate.now(), readyDate) : 0;
            return "Still recovering from giving birth on " + getLastBirthDate(female)
                    + " — ready again on " + readyDate + " (" + daysRemaining + " day(s) remaining).";
        }

        if (female.getAgeInMonths() == null) {
            return "Birth date not recorded — cannot verify breeding age.";
        }

        Integer minBreedingAge = female.getLivestockCategory() != null
                ? female.getLivestockCategory().getMinBreedingAgeMonths() : null;
        if (minBreedingAge != null && female.getAgeInMonths() < minBreedingAge) {
            long remaining = minBreedingAge - female.getAgeInMonths();
            return "Below minimum breeding age (" + minBreedingAge + " months) — "
                    + remaining + " month(s) remaining.";
        }

        Integer maxBreedingAge = female.getLivestockCategory() != null
                ? female.getLivestockCategory().getMaxBreedingAgeMonths() : null;
        if (maxBreedingAge != null && female.getAgeInMonths() > maxBreedingAge) {
            long overBy = female.getAgeInMonths() - maxBreedingAge;
            return "Above maximum breeding age (" + maxBreedingAge + " months) — retired from breeding, "
                    + overBy + " month(s) over the limit.";
        }

        return null;
    }

    // --- MALE BREEDING ELIGIBILITY ----------------

    public String getMaleIneligibilityReason(Livestock male) {
        if (male == null) {
            return null;
        }
        if (!"MALE".equalsIgnoreCase(male.getGender())) {
            return "Selected animal is not male.";
        }
        if (!Livestock.STATUS_ACTIVE.equals(male.getStatus())) {
            return "Male status is " + male.getStatus() + ", not Active.";
        }
        if (!isHealthyForBreeding(male)) {
            return getHealthIneligibilityReason(male);
        }
        if (male.getAgeInMonths() == null) {
            return "Male's birth date is not recorded — cannot verify breeding age.";
        }
        if (male.getLivestockCategory() != null) {
            Integer minAge = male.getLivestockCategory().getMinBreedingAgeMonths();
            Integer maxAge = male.getLivestockCategory().getMaxBreedingAgeMonths();
            if (minAge != null && male.getAgeInMonths() < minAge) {
                long remaining = minAge - male.getAgeInMonths();
                return "Below minimum breeding age (" + minAge + " months) for his category — "
                        + remaining + " month(s) remaining.";
            }
            if (maxAge != null && male.getAgeInMonths() > maxAge) {
                long overBy = male.getAgeInMonths() - maxAge;
                return "Above maximum breeding age (" + maxAge + " months) for his category — "
                        + overBy + " month(s) over the limit.";
            }
        }
        return null;
    }

    public String getMaleIneligibilityReason(Livestock male, Livestock female) {
        String baseReason = getMaleIneligibilityReason(male);
        if (baseReason != null) {
            return baseReason;
        }
        if (male == null || female == null) {
            return null;
        }

        UUID femaleCategoryId = female.getLivestockCategory() != null
                ? female.getLivestockCategory().getId() : null;
        if (femaleCategoryId == null) {
            return null;
        }

        UUID maleCategoryId = male.getLivestockCategory() != null
                ? male.getLivestockCategory().getId() : null;
        if (maleCategoryId == null) {
            return "Male's category is not set — cannot verify he is the same category ("
                    + female.getLivestockCategory().getName() + ") as the female.";
        }
        if (!maleCategoryId.equals(femaleCategoryId)) {
            return "Male's category (" + male.getLivestockCategory().getName()
                    + ") does not match the female's category (" + female.getLivestockCategory().getName()
                    + "). The male must belong to the same category as the female.";
        }
        return null;
    }

    public String getBreedingMethodCompatibilityReason(String breedingMethod, Livestock male) {
        if (breedingMethod == null || breedingMethod.isBlank()) {
            return "Please select a breeding method.";
        }
        boolean isNatural = FORM_METHOD_NATURAL.equals(breedingMethod);
        if (isNatural && male == null) {
            return "Natural breeding requires selecting a male animal.";
        }
        if (!isNatural && male != null) {
            return "A male animal should not be selected for " + labelizeMethod(breedingMethod)
                    + " — this method does not use an on-farm male record. Clear the male selection, "
                    + "or record donor/semen-batch details in Notes instead.";
        }
        return null;
    }

    private String labelizeMethod(String method) {
        if (method == null) return "";
        return switch (method) {
            case FORM_METHOD_AI -> "Artificial Insemination";
            case FORM_METHOD_EMBRYO_TRANSFER -> "Embryo Transfer";
            case FORM_METHOD_NATURAL -> "Natural";
            default -> method;
        };
    }

    public List<Livestock> getEligibleMales() {
        return livestockRepository.findAll().stream()
                .filter(ls -> "MALE".equalsIgnoreCase(ls.getGender()))
                .filter(ls -> !Livestock.STATUS_DEAD.equals(ls.getStatus())
                        && !Livestock.STATUS_SOLD.equals(ls.getStatus())
                        && !Livestock.STATUS_TRANSFERRED.equals(ls.getStatus()))
                .filter(ls -> getMaleIneligibilityReason(ls) == null)
                .collect(Collectors.toList());
    }

    public List<Livestock> getEligibleMalesForFemale(UUID femaleLivestockId) {
        if (femaleLivestockId == null) {
            return getEligibleMales();
        }
        Livestock female = livestockRepository.findById(femaleLivestockId).orElse(null);
        UUID femaleCategoryId = female != null && female.getLivestockCategory() != null
                ? female.getLivestockCategory().getId() : null;

        return getEligibleMales().stream()
                .filter(male -> femaleCategoryId == null
                        || (male.getLivestockCategory() != null
                        && femaleCategoryId.equals(male.getLivestockCategory().getId())))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getBreedingHistoryAdvisory(UUID livestockId) {
        Map<String, Object> advisory = new LinkedHashMap<>();

        if (livestockId == null) {
            advisory.put("level", "none");
            advisory.put("message", null);
            return advisory;
        }

        List<LivestockBreeding> history = breedingRepository
                .findByLivestockIdAndIsDeletedFalseOrderByFirstBreedingDateDesc(livestockId);

        int totalAttempts = history.size();
        long completedCount = history.stream().filter(LivestockBreeding::isSuccessful).count();
        long failedCount = history.stream().filter(LivestockBreeding::isFailed).count();

        advisory.put("totalAttempts", totalAttempts);
        advisory.put("completedCount", completedCount);
        advisory.put("failedCount", failedCount);

        if (totalAttempts == 0) {
            advisory.put("level", "info");
            advisory.put("message", "First breeding attempt on record for this animal — no prior history to consider.");
            return advisory;
        }

        int consecutiveFailures = 0;
        for (LivestockBreeding b : history) {
            if (b.isFailed()) {
                consecutiveFailures++;
            } else {
                break;
            }
        }

        if (consecutiveFailures >= CONSECUTIVE_FAILURES_FLAG_THRESHOLD) {
            advisory.put("level", "warning");
            advisory.put("message", consecutiveFailures + " consecutive failed breeding attempts — "
                    + "consider a veterinary reproductive check before trying again.");
        } else if (failedCount > 0) {
            advisory.put("level", "info");
            advisory.put("message", failedCount + " of " + totalAttempts
                    + " past attempt(s) failed. Most recent status: "
                    + history.get(0).getStatusLabel() + ".");
        } else {
            advisory.put("level", "good");
            advisory.put("message", "Clean breeding history — " + completedCount
                    + " of " + totalAttempts + " past attempt(s) completed successfully.");
        }

        return advisory;
    }

    // --- BREEDING READINESS / DECISION-SUPPORT ENGINE ----------------------

    public Map<String, Object> getBreedingContext(UUID livestockId) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (livestockId == null) {
            result.put("error", "Select a female animal to see her breeding readiness.");
            return result;
        }

        Livestock female = livestockRepository.findById(livestockId).orElse(null);
        if (female == null) {
            result.put("error", "Selected animal could not be found.");
            return result;
        }

        LocalDate today = LocalDate.now();

        result.put("tagNumber", female.getTagNumber());
        result.put("ageDisplay", female.getAgeDisplay());
        result.put("ageMonths", female.getAgeInMonths());
        result.put("categoryId", female.getLivestockCategory() != null
                ? female.getLivestockCategory().getId() : null);
        result.put("categoryName", female.getLivestockCategory() != null
                ? female.getLivestockCategory().getName() : null);

        result.put("healthStatus", female.getHealthStatus());
        result.put("healthyForBreeding", isHealthyForBreeding(female));

        Integer minBreedingAge = female.getLivestockCategory() != null
                ? female.getLivestockCategory().getMinBreedingAgeMonths() : null;
        Integer maxBreedingAge = female.getLivestockCategory() != null
                ? female.getLivestockCategory().getMaxBreedingAgeMonths() : null;
        result.put("minBreedingAgeMonths", minBreedingAge);
        result.put("maxBreedingAgeMonths", maxBreedingAge);

        Optional<LocalDate> postpartumReadyDate = getPostpartumReadyDate(female);
        boolean inRecovery = isInPostpartumRecovery(female);
        result.put("postpartumRecoveryDays", female.getLivestockCategory() != null
                ? female.getLivestockCategory().getPostpartumRecoveryDays() : null);
        result.put("postpartumReadyDate", postpartumReadyDate.orElse(null));
        result.put("inPostpartumRecovery", inRecovery);
        result.put("daysUntilPostpartumReady", postpartumReadyDate
                .map(d -> Math.max(0, ChronoUnit.DAYS.between(today, d)))
                .orElse(null));

        String ineligibilityReason = getIneligibilityReason(female);
        boolean readyToBreed = ineligibilityReason == null;
        String readyReason = readyToBreed
                ? buildReadyReason(female, minBreedingAge)
                : ineligibilityReason;

        result.put("readyToBreed", readyToBreed);
        result.put("readyToBreedReason", readyReason);

        LocalDate lastBirthDate = getLastBirthDate(female);
        result.put("lastBirthDate", lastBirthDate);
        result.put("daysSinceLastBirth", lastBirthDate != null
                ? ChronoUnit.DAYS.between(lastBirthDate, today) : null);

        List<LivestockBreeding> allBreedings = breedingRepository.findByLivestockIdAndIsDeletedFalse(livestockId);
        result.put("previousBreedingsCount", allBreedings.size());

        LivestockBreeding lastBreeding = breedingRepository
                .findFirstByLivestockIdAndIsDeletedFalseOrderByBreedingDateDesc(livestockId)
                .orElse(null);

        if (lastBreeding != null) {
            Map<String, Object> prev = new LinkedHashMap<>();
            prev.put("date", lastBreeding.getBreedingDate());
            prev.put("status", lastBreeding.getStatus());
            prev.put("statusLabel", lastBreeding.getStatusLabel());
            result.put("previousBreeding", prev);

            boolean isRetry = LivestockBreeding.STATUS_FAILED.equals(lastBreeding.getStatus())
                    || LivestockBreeding.STATUS_ABORTED.equals(lastBreeding.getStatus());
            int nextAttempt = isRetry
                    ? (lastBreeding.getBreedingAttempt() != null ? lastBreeding.getBreedingAttempt() : 1) + 1
                    : 1;
            result.put("nextBreedingAttempt", nextAttempt);
            result.put("isRetryAttempt", isRetry);
        } else {
            result.put("previousBreeding", null);
            result.put("nextBreedingAttempt", 1);
            result.put("isRetryAttempt", false);
        }

        result.put("historyAdvisory", getBreedingHistoryAdvisory(livestockId));
        result.put("currentHeatStatus", "NOT_TRACKED");

        return result;
    }

    private String buildReadyReason(Livestock female, Integer minBreedingAge) {
        if (minBreedingAge == null) {
            return "No minimum breeding age configured for her category — assumed ready.";
        }
        return "Meets the minimum breeding age (" + minBreedingAge + " months) for her category, "
                + "is healthy, not pregnant, and not in postpartum recovery.";
    }

    // --- AUTO-CALCULATION ENGINE --------------------------------------------

    /**
     * Calculates derived fields for a breeding record — including the
     * Breeding Attempt counter.
     *
     * <p><strong>FIXED — Breeding Attempt increment logic.</strong></p>
     *
     * <p>The counter now follows this rule set, in this order:</p>
     * <ol>
     *   <li>No prior record → <strong>#1</strong> (first breeding ever).</li>
     *   <li>Previous record was FAILED or ABORTED → same cycle retry,
     *       so <strong>previous + 1</strong>.</li>
     *   <li>Previous record was COMPLETED (birth delivered, cycle finished)
     *       → brand-new cycle, but her <em>lifetime</em> attempt count
     *       continues: <strong>previous + 1</strong>.</li>
     *   <li>Previous record was PENDING or CONFIRMED_PREGNANT → still
     *       in-flight; a new breeding shouldn't be happening. We fall
     *       through to <strong>#1</strong> and rely on the eligibility
     *       guard to block the save, but we also surface a clear
     *       warning in the preview so the farmer sees why.</li>
     * </ol>
     *
     * <p>Under this rule, breeding attempt #2 in an animal's life is
     * recorded as #2 regardless of whether the first cycle failed or
     * completed — matching the spec "increments if her previous attempt
     * failed" and extending it sensibly to cover successful prior cycles
     * that have already been closed out.</p>
     */
    public Map<String, Object> calculatePreview(UUID livestockId, String breedingMethod, LocalDate breedingDate) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (livestockId == null || breedingDate == null) {
            result.put("error", "Select the female animal and enter a breeding date to see calculated values.");
            return result;
        }

        Livestock female = livestockRepository.findById(livestockId).orElse(null);
        if (female == null) {
            result.put("error", "Selected animal could not be found.");
            return result;
        }

        if (breedingDate.isAfter(LocalDate.now())) {
            result.put("error", "Breeding date cannot be in the future.");
            return result;
        }

        // NOTE: eligibility is checked separately so the preview can still
        // show *why* the animal is ineligible even when the save will be blocked.
        String ineligibilityReason = getIneligibilityReason(female);
        if (ineligibilityReason != null) {
            result.put("error", female.getTagNumber() + " is not eligible for breeding: " + ineligibilityReason);
            return result;
        }

        LivestockBreeding previous = breedingRepository
                .findFirstByLivestockIdAndIsDeletedFalseOrderByBreedingDateDesc(livestockId)
                .orElse(null);

        // ────────────────────────────────────────────────────────────────
        //  FIXED: Breeding Attempt logic — see method Javadoc.
        // ────────────────────────────────────────────────────────────────
        LocalDate firstBreedingDate;
        int breedingAttempt;
        boolean isRetryAttempt;
        String attemptContextMessage = null;

        if (previous == null) {
            // Rule 1 — first breeding ever for this animal.
            firstBreedingDate = breedingDate;
            breedingAttempt = 1;
            isRetryAttempt = false;
        } else {
            String prevStatus = previous.getStatus();
            int prevAttemptNo = previous.getBreedingAttempt() != null
                    ? previous.getBreedingAttempt() : 1;

            boolean wasFailed  = LivestockBreeding.STATUS_FAILED.equals(prevStatus);
            boolean wasAborted = LivestockBreeding.STATUS_ABORTED.equals(prevStatus);
            boolean wasCompleted = LivestockBreeding.STATUS_COMPLETED.equals(prevStatus);

            if (wasFailed || wasAborted) {
                // Rule 2 — same cycle retry after a failed/aborted attempt.
                firstBreedingDate = previous.getFirstBreedingDate() != null
                        ? previous.getFirstBreedingDate()
                        : previous.getBreedingDate();
                breedingAttempt = prevAttemptNo + 1;
                isRetryAttempt = true;
                attemptContextMessage = "Retry #" + breedingAttempt
                        + " — previous attempt #" + prevAttemptNo + " on "
                        + previous.getBreedingDate() + " was marked "
                        + previous.getStatusLabel() + ".";
            } else if (wasCompleted) {
                // Rule 3 — new cycle after a successfully delivered pregnancy.
                // Her lifetime attempt count continues (prev + 1), but this
                // is NOT flagged as a "retry" because the prior cycle succeeded.
                firstBreedingDate = breedingDate;
                breedingAttempt = prevAttemptNo + 1;
                isRetryAttempt = false;
                attemptContextMessage = "New breeding cycle — this will be her attempt #"
                        + breedingAttempt + " (previous cycle #" + prevAttemptNo
                        + " completed with a birth on " + previous.getBreedingDate() + ").";
            } else {
                // Rule 4 — previous is PENDING or CONFIRMED_PREGNANT.
                // The eligibility guard above should already have blocked this,
                // but if we reach here for any reason, treat this as a fresh
                // cycle and surface a warning rather than silently mis-numbering.
                firstBreedingDate = breedingDate;
                breedingAttempt = prevAttemptNo + 1;
                isRetryAttempt = false;
                attemptContextMessage = "Warning — previous record (#" + prevAttemptNo
                        + " on " + previous.getBreedingDate()
                        + ") is still " + previous.getStatusLabel()
                        + ". Resolve it before saving this new breeding.";
            }
        }

        LocalDate lastBreedingDate = breedingDate;
        LocalDate conceptionDate = breedingDate;

        Integer gestationDays = null;
        String categoryName = null;
        UUID categoryId = null;
        if (female.getLivestockCategory() != null) {
            categoryName = female.getLivestockCategory().getName();
            categoryId = female.getLivestockCategory().getId();
            gestationDays = female.getLivestockCategory().getGestationPeriodDays();
            if (gestationDays == null && female.getLivestockCategory().getGestationPeriodMonths() != null) {
                gestationDays = (int) Math.round(
                        female.getLivestockCategory().getGestationPeriodMonths() * AVG_DAYS_PER_MONTH);
            }
        }

        LocalDate expectedDueDate = gestationDays != null
                ? conceptionDate.plusDays(gestationDays)
                : null;

        LocalDate expectedPregnancyCheckDate;
        if (gestationDays != null) {
            long offsetDays = Math.max(MIN_PREGNANCY_CHECK_DAYS,
                    Math.round(gestationDays * PREGNANCY_CHECK_FRACTION_OF_GESTATION));
            expectedPregnancyCheckDate = breedingDate.plusDays(offsetDays);
        } else {
            expectedPregnancyCheckDate = breedingDate.plusDays(FALLBACK_PREGNANCY_CHECK_DAYS);
        }

        String inseminationMethod = deriveInseminationMethod(breedingMethod);

        if (FORM_METHOD_NATURAL.equals(breedingMethod)) {
            result.put("maleRequired", true);
        } else {
            result.put("maleRequired", false);
        }

        result.put("firstBreedingDate", firstBreedingDate);
        result.put("lastBreedingDate", lastBreedingDate);
        result.put("breedingAttempt", breedingAttempt);
        result.put("isRetryAttempt", isRetryAttempt);
        result.put("conceptionDate", conceptionDate);
        result.put("expectedDueDate", expectedDueDate);
        result.put("expectedPregnancyCheckDate", expectedPregnancyCheckDate);
        result.put("inseminationMethod", inseminationMethod);
        result.put("status", LivestockBreeding.STATUS_PENDING);
        result.put("pregnancyStatus", "NOT_PREGNANT");
        result.put("categoryId", categoryId);
        result.put("categoryName", categoryName);
        result.put("gestationDaysUsed", gestationDays);

        // Add the human-readable context so the UI can show WHY the number
        // is what it is. Kept separate from "warning" so it doesn't look
        // like an error to the farmer.
        if (attemptContextMessage != null) {
            result.put("attemptContextMessage", attemptContextMessage);
        }

        if (gestationDays == null) {
            result.put("warning",
                    "No gestation period is configured for " + (categoryName != null ? categoryName : "this category")
                            + ", so Expected Due Date could not be calculated. A default 30-day pregnancy-check "
                            + "window was used instead.");
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    public void applyCalculatedFields(LivestockBreeding breeding) {
        UUID livestockId = breeding.getLivestock() != null
                ? breeding.getLivestock().getId()
                : breeding.getLivestockIdValue();

        Map<String, Object> calc = calculatePreview(livestockId, breeding.getBreedingMethod(), breeding.getBreedingDate());

        if (calc.containsKey("error")) {
            return;
        }

        breeding.setFirstBreedingDate((LocalDate) calc.get("firstBreedingDate"));
        breeding.setLastBreedingDate((LocalDate) calc.get("lastBreedingDate"));
        breeding.setBreedingAttempt((Integer) calc.get("breedingAttempt"));
        breeding.setConceptionDate((LocalDate) calc.get("conceptionDate"));
        breeding.setExpectedDueDate((LocalDate) calc.get("expectedDueDate"));
        breeding.setExpectedPregnancyCheckDate((LocalDate) calc.get("expectedPregnancyCheckDate"));
        breeding.setInseminationMethod((String) calc.get("inseminationMethod"));
        breeding.setStatus((String) calc.get("status"));
        breeding.setPregnancyStatus((String) calc.get("pregnancyStatus"));
    }

    private String deriveInseminationMethod(String breedingMethod) {
        if (breedingMethod == null || breedingMethod.isBlank()) return null;
        return switch (breedingMethod) {
            case "NATURAL" -> LivestockBreeding.METHOD_NATURAL;
            case "ARTIFICIAL_INSEMINATION" -> LivestockBreeding.METHOD_ARTIFICIAL;
            case "EMBRYO_TRANSFER" -> LivestockBreeding.METHOD_EMBRYO_TRANSFER;
            case "PURCHASE_PREGNANT" -> LivestockBreeding.METHOD_PURCHASE_PREGNANT;
            default -> null;
        };
    }

    // --- CREATE ----------------------------------------------------------------

    @Transactional
    public LivestockBreeding addNew(LivestockBreeding breeding) {
        Livestock female = breeding.getLivestock();
        if (female == null && breeding.getLivestockIdValue() != null) {
            female = livestockRepository.findById(breeding.getLivestockIdValue()).orElse(null);
        }
        String ineligibilityReason = getIneligibilityReason(female);
        if (ineligibilityReason != null) {
            throw new IllegalStateException(
                    (female != null ? female.getTagNumber() : "Selected animal")
                            + " is not eligible for breeding: " + ineligibilityReason);
        }

        Livestock male = breeding.getMale();
        if (male == null && breeding.getMaleLivestockIdValue() != null) {
            male = livestockRepository.findById(breeding.getMaleLivestockIdValue()).orElse(null);
        }

        String methodReason = getBreedingMethodCompatibilityReason(breeding.getBreedingMethod(), male);
        if (methodReason != null) {
            throw new IllegalStateException(methodReason);
        }

        String maleIneligibilityReason = getMaleIneligibilityReason(male, female);
        if (maleIneligibilityReason != null) {
            throw new IllegalStateException(
                    (male != null ? male.getTagNumber() : "Selected male")
                            + " is not eligible for breeding: " + maleIneligibilityReason);
        }

        breeding.setIsDeleted(false);
        applyCalculatedFields(breeding);
        LivestockBreeding saved = breedingRepository.save(breeding);
        emailService.sendBreedingStartedNotification(saved);
        return saved;
    }

    @Transactional
    public LivestockBreeding createBreeding(LivestockBreeding breeding) {
        return addNew(breeding);
    }

    // --- CREATE FOR PURCHASED PREGNANT ANIMAL ---------------------------------

    @Transactional
    public LivestockBreeding createForPurchasedPregnantAnimal(Livestock animal,
                                                              LocalDate conceptionDate,
                                                              LocalDate expectedDueDate,
                                                              String inseminationMethod) {

        if (!isEligibleForPurchasePregnantRecord(animal)) {
            throw new IllegalArgumentException(
                    "createForPurchasedPregnantAnimal() called for ineligible animal: "
                            + animal.getTagNumber()
                            + " (must be female, non-BIRTH, and pregnant)");
        }

        LivestockBreeding breeding = new LivestockBreeding();
        breeding.setLivestock(animal);
        breeding.setIsDeleted(false);
        breeding.setStatus(LivestockBreeding.STATUS_CONFIRMED_PREGNANT);
        breeding.setPregnancyConfirmedDate(LocalDate.now());

        LocalDate breedingDateProxy = conceptionDate;
        if (breedingDateProxy == null) {
            breedingDateProxy = animal.getDateReceived() != null
                    ? animal.getDateReceived()
                    : LocalDate.now();
        }
        breeding.setBreedingDate(breedingDateProxy);
        breeding.setConceptionDate(conceptionDate != null ? conceptionDate : breedingDateProxy);

        if (inseminationMethod != null && !inseminationMethod.isBlank()) {
            breeding.setBreedingMethod(inseminationMethod);
        } else {
            breeding.setBreedingMethod(LivestockBreeding.METHOD_PURCHASE_PREGNANT);
        }

        LocalDate dueDate = expectedDueDate;
        if (dueDate == null && animal.getLivestockCategory() != null
                && animal.getLivestockCategory().getGestationPeriodMonths() != null) {
            dueDate = breedingDateProxy.plusMonths(
                    animal.getLivestockCategory().getGestationPeriodMonths());
        }
        breeding.setExpectedDueDate(dueDate);
        breeding.setExpectedPregnancyCheckDate(LocalDate.now().plusDays(7));

        breeding.setNotes(
                "Animal purchased/received while already pregnant. "
                        + "Pregnancy tracking started on registration date: "
                        + LocalDate.now() + "."
        );

        LivestockBreeding saved = breedingRepository.save(breeding);

        animal.setReproductiveStatus(Livestock.REPRO_PREGNANT);
        animal.setIsPregnant(true);
        animal.setExpectedDueDate(dueDate);
        livestockRepository.save(animal);

        return saved;
    }

    private boolean isEligibleForPurchasePregnantRecord(Livestock animal) {
        return "FEMALE".equalsIgnoreCase(animal.getGender())
                && Boolean.TRUE.equals(animal.getIsPregnant())
                && !Livestock.ACQ_BIRTH.equals(animal.getAcquisitionMethod());
    }

    // --- UPDATE ----------------------------------------------------------------

    @Transactional
    public LivestockBreeding update(UUID id, LivestockBreeding updated) {
        LivestockBreeding existing = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));

        String oldStatus = existing.getStatus();

        existing.setLivestock(updated.getLivestock());
        existing.setMale(updated.getMale());
        existing.setBreedingDate(updated.getBreedingDate());
        existing.setBreedingMethod(updated.getBreedingMethod());
        existing.setStatus(updated.getStatus());
        if (updated.getExpectedPregnancyCheckDate() != null) {
            existing.setExpectedPregnancyCheckDate(updated.getExpectedPregnancyCheckDate());
        }
        if (updated.getExpectedDueDate() != null) {
            existing.setExpectedDueDate(updated.getExpectedDueDate());
        }
        if (updated.getPregnancyConfirmedDate() != null) {
            existing.setPregnancyConfirmedDate(updated.getPregnancyConfirmedDate());
        }
        existing.setNotes(updated.getNotes());
        existing.setVeterinarian(updated.getVeterinarian());

        LivestockBreeding saved = breedingRepository.save(existing);

        if (!saved.getStatus().equals(oldStatus)
                && LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(saved.getStatus())) {
            if (saved.getPregnancyConfirmedDate() == null) {
                saved.setPregnancyConfirmedDate(LocalDate.now());
            }
            saved = breedingRepository.save(saved);
            confirmPregnancyOnAnimal(saved);
            emailService.sendPregnancyConfirmedNotification(saved);
        }

        if (!saved.getStatus().equals(oldStatus)
                && (LivestockBreeding.STATUS_FAILED.equals(saved.getStatus())
                || LivestockBreeding.STATUS_COMPLETED.equals(saved.getStatus()))) {
            resetPregnancyOnAnimal(saved.getLivestock());
        }

        return saved;
    }

    @Transactional
    public LivestockBreeding updateBreeding(UUID id, LivestockBreeding updated) {
        return update(id, updated);
    }

    // --- DELETE (soft) ---------------------------------------------------------

    @Transactional
    public void delete(UUID id) {
        LivestockBreeding b = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));
        b.setIsDeleted(true);
        breedingRepository.save(b);
    }

    @Transactional
    public void softDeleteBreeding(UUID id, String deletedBy) {
        LivestockBreeding b = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));
        b.setIsDeleted(true);
        b.setDeletedBy(deletedBy);
        b.setDeletedAt(LocalDateTime.now());
        breedingRepository.save(b);
    }

    // --- PREGNANCY CHECK OUTCOME --------------------------------------------

    @Transactional
    public LivestockBreeding confirmPregnancy(UUID id, LocalDate expectedDueDate, LocalDate pregnancyConfirmedDate) {
        LivestockBreeding breeding = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));

        if (!LivestockBreeding.STATUS_PENDING.equals(breeding.getStatus())) {
            throw new IllegalStateException(
                    "This breeding record has already been resolved (status: " + breeding.getStatus()
                            + "). Its outcome cannot be changed here.");
        }

        LocalDate confirmedDate = pregnancyConfirmedDate != null ? pregnancyConfirmedDate : LocalDate.now();

        if (confirmedDate.isAfter(LocalDate.now())) {
            throw new IllegalStateException("Pregnancy Confirmation Date cannot be in the future.");
        }
        if (breeding.getBreedingDate() != null && confirmedDate.isBefore(breeding.getBreedingDate())) {
            throw new IllegalStateException(
                    "Pregnancy Confirmation Date (" + confirmedDate
                            + ") cannot be before the Breeding Date (" + breeding.getBreedingDate() + ").");
        }

        if (expectedDueDate != null && expectedDueDate.isBefore(confirmedDate)) {
            throw new IllegalStateException(
                    "Expected Due Date (" + expectedDueDate
                            + ") cannot be before the Pregnancy Confirmation Date (" + confirmedDate + ").");
        }

        breeding.setStatus(LivestockBreeding.STATUS_CONFIRMED_PREGNANT);
        breeding.setPregnancyStatus("CONFIRMED");
        breeding.setPregnancyConfirmedDate(confirmedDate);
        if (expectedDueDate != null) {
            breeding.setExpectedDueDate(expectedDueDate);
        }
        LivestockBreeding saved = breedingRepository.save(breeding);

        confirmPregnancyOnAnimal(saved);
        emailService.sendPregnancyConfirmedNotification(saved);

        return saved;
    }

    @Transactional
    public LivestockBreeding confirmPregnancy(UUID id, LocalDate expectedDueDate) {
        return confirmPregnancy(id, expectedDueDate, null);
    }

    @Transactional
    public LivestockBreeding markFailed(UUID id, String failureReason, LocalDate checkDate) {
        LivestockBreeding breeding = breedingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Breeding record not found: " + id));

        if (!LivestockBreeding.STATUS_PENDING.equals(breeding.getStatus())) {
            throw new IllegalStateException(
                    "This breeding record has already been resolved (status: " + breeding.getStatus()
                            + "). Its outcome cannot be changed here.");
        }

        LocalDate resolvedCheckDate = checkDate != null ? checkDate : LocalDate.now();

        if (resolvedCheckDate.isAfter(LocalDate.now())) {
            throw new IllegalStateException("Date Checked / Failed cannot be in the future.");
        }
        if (breeding.getBreedingDate() != null && resolvedCheckDate.isBefore(breeding.getBreedingDate())) {
            throw new IllegalStateException(
                    "Date Checked / Failed (" + resolvedCheckDate
                            + ") cannot be before the Breeding Date (" + breeding.getBreedingDate() + ").");
        }

        breeding.setStatus(LivestockBreeding.STATUS_FAILED);
        breeding.setPregnancyStatus("FAILED");
        breeding.setFailureReason(failureReason);

        String autoNote = "[Marked failed] Checked on: " + resolvedCheckDate
                + (failureReason != null && !failureReason.isBlank() ? " — reason: " + failureReason : "");
        breeding.setNotes(breeding.getNotes() == null || breeding.getNotes().isBlank()
                ? autoNote
                : breeding.getNotes() + "\n" + autoNote);

        LivestockBreeding saved = breedingRepository.save(breeding);

        resetPregnancyOnAnimal(saved.getLivestock());
        emailService.sendBreedingFailedNotification(saved);

        return saved;
    }

    @Transactional
    public LivestockBreeding markFailed(UUID id, String failureReason) {
        return markFailed(id, failureReason, null);
    }

    // --- AUTO-COMPLETE ON BIRTH ------------------------------------------------

    @Transactional
    public void completeBreedingOnBirth(UUID femaleLivestockId, LocalDate birthDate) {

        Optional<LivestockBreeding> activeOpt =
                breedingRepository.findMostRecentActiveBreeding(femaleLivestockId);

        activeOpt.ifPresent(breeding -> {
            breeding.setStatus(LivestockBreeding.STATUS_COMPLETED);
            String autoNote = "[Auto-completed] Birth recorded on: " + birthDate;
            breeding.setNotes(breeding.getNotes() == null || breeding.getNotes().isBlank()
                    ? autoNote
                    : breeding.getNotes() + "\n" + autoNote);
            breedingRepository.save(breeding);
        });

        livestockRepository.findById(femaleLivestockId).ifPresent(animal -> {
            animal.setIsPregnant(false);
            animal.setReproductiveStatus(Livestock.REPRO_OPEN);
            animal.setStatus(Livestock.STATUS_ACTIVE);
            animal.setLastBirthDate(birthDate);
            animal.setExpectedDueDate(null);
            animal.setBreedingReadyNotifiedAt(null);
            livestockRepository.save(animal);
            boolean stillRecovering = isInPostpartumRecovery(animal);
            boolean healthy = isHealthyForBreeding(animal);
            if (!stillRecovering && healthy) {
                try {
                    emailService.sendReadyToBreedNotification(animal);
                    animal.setBreedingReadyNotifiedAt(LocalDateTime.now());
                    livestockRepository.save(animal);
                    log.info("Sent immediate 'ready to breed again' notification for {} after birth on {}",
                            animal.getTagNumber(), birthDate);
                } catch (Exception e) {
                    log.warn("Failed to send post-birth ready-to-breed email for {} (id={}): {}",
                            animal.getTagNumber(), animal.getId(), e.getMessage(), e);
                }
            } else {
                log.info("{} gave birth on {} but is not yet ready (recovering={}, healthy={}) — "
                                + "'ready to breed' notification deferred to the daily readiness job.",
                        animal.getTagNumber(), birthDate, stillRecovering, healthy);
            }
        });
    }

    // --- DASHBOARD / REPORT HELPERS --------------------------------------------

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalBreedings", breedingRepository.countByIsDeletedFalse());
        stats.put("pending", breedingRepository.countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_PENDING));
        stats.put("confirmedPregnant", breedingRepository.countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_CONFIRMED_PREGNANT));
        stats.put("completed", breedingRepository.countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_COMPLETED));
        stats.put("failed", breedingRepository.countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_FAILED));
        stats.put("activePregnancies", breedingRepository.findAllActivePregnancies().size());
        stats.put("overdueChecks", breedingRepository.findOverduePregnancyChecks(LocalDate.now()).size());
        stats.put("overduePregnancies", breedingRepository.findOverduePregnancies(LocalDate.now()).size());
        stats.put("successRate", getSuccessRate());

        return stats;
    }

    public List<LivestockBreeding> getDueForPregnancyCheck() {
        return breedingRepository.findOverduePregnancyChecks(LocalDate.now());
    }

    public List<LivestockBreeding> findOverduePregnancyChecks() {
        return getDueForPregnancyCheck();
    }

    public List<LivestockBreeding> getApproachingDueDate() {
        LocalDate today = LocalDate.now();
        return breedingRepository.findApproachingDueDate(today, today.plusDays(30));
    }

    public List<LivestockBreeding> getOverduePregnancies() {
        return breedingRepository.findOverduePregnancies(LocalDate.now());
    }

    public List<LivestockBreeding> getAllActivePregnancies() {
        return breedingRepository.findAllActivePregnancies();
    }

    public List<LivestockBreeding> findActivePregnancies() {
        return getAllActivePregnancies();
    }

    public List<LivestockBreeding> getRecentBreedings() {
        return breedingRepository.findRecentBreedings(10);
    }

    public List<LivestockBreeding> findRecentBreedings(int limit) {
        return breedingRepository.findRecentBreedings(limit);
    }

    public double getSuccessRate() {
        long total = breedingRepository.countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_PENDING)
                + breedingRepository.countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_CONFIRMED_PREGNANT)
                + breedingRepository.countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_FAILED)
                + breedingRepository.countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_COMPLETED);
        if (total == 0) return 0.0;
        long confirmed = breedingRepository
                .countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_CONFIRMED_PREGNANT);
        return (confirmed * 100.0) / total;
    }

    public long countByStatus(String status) {
        return breedingRepository.countByStatusAndIsDeletedFalse(status);
    }

    // --- PRIVATE HELPERS -------------------------------------------------------

    private void confirmPregnancyOnAnimal(LivestockBreeding saved) {
        if (saved.getLivestock() == null) return;
        Livestock animal = saved.getLivestock();
        animal.setReproductiveStatus(Livestock.REPRO_PREGNANT);
        animal.setIsPregnant(true);
        animal.setExpectedDueDate(saved.getExpectedDueDate());
        livestockRepository.save(animal);
    }

    private void resetPregnancyOnAnimal(Livestock animal) {
        if (animal == null) return;
        animal.setIsPregnant(false);
        animal.setReproductiveStatus(Livestock.REPRO_OPEN);
        animal.setExpectedDueDate(null);
        animal.setBreedingReadyNotifiedAt(null);
        livestockRepository.save(animal);
    }

    // --- Actions Required (spec D.2) ---------------------------------

    public List<ActionRequiredItemDTO> getActionsRequired() {
        LocalDate today = LocalDate.now();
        List<ActionRequiredItemDTO> items = new ArrayList<>();

        for (LivestockBreeding b : breedingRepository.findOverduePregnancyChecks(
                LivestockBreeding.STATUS_PENDING, today)) {
            if (b.getLivestock() == null) continue;
            long daysOverdue = ChronoUnit.DAYS.between(b.getExpectedPregnancyCheckDate(), today);
            items.add(ActionRequiredItemDTO.builder()
                    .type(ActionRequiredItemDTO.Type.OVERDUE_PREGNANCY_CHECK)
                    .severity(ActionRequiredItemDTO.Severity.RED)
                    .icon("??")
                    .title("Overdue pregnancy check")
                    .message(b.getLivestock().getTagNumber() + " — pregnancy check was due "
                            + daysOverdue + " day(s) ago")
                    .livestockId(b.getLivestock().getId())
                    .tagNumber(b.getLivestock().getTagNumber())
                    .relevantDate(b.getExpectedPregnancyCheckDate())
                    .daysValue(daysOverdue)
                    .actionUrl("/livestock/breeding/view/" + b.getId())
                    .build());
        }

        for (LivestockBreeding b : breedingRepository.findOverduePregnancies(today)) {
            if (b.getLivestock() == null) continue;
            long daysOverdue = ChronoUnit.DAYS.between(b.getExpectedDueDate(), today);
            items.add(ActionRequiredItemDTO.builder()
                    .type(ActionRequiredItemDTO.Type.OVERDUE_DUE_DATE)
                    .severity(ActionRequiredItemDTO.Severity.RED)
                    .icon("??")
                    .title("Overdue due date")
                    .message(b.getLivestock().getTagNumber() + " — expected due date passed "
                            + daysOverdue + " day(s) ago")
                    .livestockId(b.getLivestock().getId())
                    .tagNumber(b.getLivestock().getTagNumber())
                    .relevantDate(b.getExpectedDueDate())
                    .daysValue(daysOverdue)
                    .actionUrl("/livestock/breeding/view/" + b.getId())
                    .build());
        }

        for (ReadyToBreedReportDTO f : readyToBreedReportService.getAllNotReady(null, null)) {
            if (f.getDaysUntilReady() != null && f.getDaysUntilReady() > 0 && f.getDaysUntilReady() <= 14) {
                items.add(ActionRequiredItemDTO.builder()
                        .type(ActionRequiredItemDTO.Type.BECOMING_READY_SOON)
                        .severity(ActionRequiredItemDTO.Severity.YELLOW)
                        .icon("??")
                        .title("Becoming ready soon")
                        .message(f.getTagNumber() + " — ready to breed in " + f.getDaysUntilReady() + " day(s)")
                        .livestockId(f.getLivestockId())
                        .tagNumber(f.getTagNumber())
                        .relevantDate(f.getRecommendedBreedingDate())
                        .daysValue(f.getDaysUntilReady())
                        .actionUrl("/livestock/view/" + f.getLivestockId())
                        .build());
            }
        }

        for (IneligibleFemaleDTO f : femalesReadyToBreedService.getNotYetEligible()) {
            items.add(ActionRequiredItemDTO.builder()
                    .type(ActionRequiredItemDTO.Type.NOT_READY)
                    .severity(ActionRequiredItemDTO.Severity.YELLOW)
                    .icon("??")
                    .title("Not ready for breeding")
                    .message(f.getTagNumber() + " — " + f.getReason())
                    .livestockId(f.getId())
                    .tagNumber(f.getTagNumber())
                    .relevantDate(f.getNextEligibleDate())
                    .actionUrl("/livestock/view/" + f.getId())
                    .build());
        }

        items.sort(Comparator
                .comparing((ActionRequiredItemDTO i) -> i.getSeverity() == ActionRequiredItemDTO.Severity.RED ? 0 : 1)
                .thenComparing(i -> i.getDaysValue() == null ? 0L : -i.getDaysValue()));

        return items;
    }

    // --- Active Breedings with overdue flag & days since breeding ----

    public List<ActiveBreedingRowDTO> getActiveBreedingRows() {
        LocalDate today = LocalDate.now();
        return breedingRepository.findByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_PENDING).stream()
                .map(b -> {
                    boolean overdue = b.getExpectedPregnancyCheckDate() != null
                            && b.getExpectedPregnancyCheckDate().isBefore(today);
                    return ActiveBreedingRowDTO.builder()
                            .breedingId(b.getId())
                            .livestockId(b.getLivestock() != null ? b.getLivestock().getId() : null)
                            .femaleTag(b.getLivestock() != null ? b.getLivestock().getTagNumber() : "N/A")
                            .maleId(b.getMale() != null ? b.getMale().getId() : null)
                            .maleTag(b.getMale() != null ? b.getMale().getTagNumber() : null)
                            .categoryName(b.getLivestock() != null && b.getLivestock().getLivestockCategory() != null
                                    ? b.getLivestock().getLivestockCategory().getName() : "Uncategorized")
                            .breedingMethod(b.getBreedingMethod())
                            .breedingDate(b.getBreedingDate())
                            .expectedPregnancyCheckDate(b.getExpectedPregnancyCheckDate())
                            .daysSinceBreeding(b.getBreedingDate() != null
                                    ? ChronoUnit.DAYS.between(b.getBreedingDate(), today) : null)
                            .status(b.getStatus())
                            .overdue(overdue)
                            .build();
                })
                .sorted(Comparator.comparing(ActiveBreedingRowDTO::isOverdue).reversed()
                        .thenComparing(r -> r.getDaysSinceBreeding() == null ? 0 : -r.getDaysSinceBreeding()))
                .collect(Collectors.toList());
    }

    // --- Management KPI cards (spec D.1) ------------------------------

    public BreedingManagementKpiDTO getManagementKpis() {
        long activeBreedings = breedingRepository.countByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_PENDING);
        long femalesReady = readyToBreedReportService.getAllReady(null, null).size();
        long pregnantAnimals = breedingRepository.countActivePregnancies();
        long pendingChecks = breedingRepository
                .findOverduePregnancyChecks(LivestockBreeding.STATUS_PENDING, LocalDate.now()).size();
        double successRate = getSuccessRate();

        return BreedingManagementKpiDTO.builder()
                .activeBreedings(activeBreedings)
                .femalesReady(femalesReady)
                .pregnantAnimals(pregnantAnimals)
                .pendingPregnancyChecks(pendingChecks)
                .breedingSuccessRate(Math.round(successRate * 10.0) / 10.0)
                .build();
    }
}   