package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBirth;
import rw.animalproduct.animal.production.repository.LivestockBirthRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Single source of truth for breeding-eligibility rules (female and male).
 *
 * <p>Extracted out of {@link LivestockBreedingService} so it can be shared
 * with {@link FemalesReadyToBreedService} <strong>without</strong> those two
 * services depending on each other. Previously
 * {@code LivestockBreedingService} injected {@code FemalesReadyToBreedService}
 * (for {@code getActionsRequired()}) while {@code FemalesReadyToBreedService}
 * injected {@code LivestockBreedingService} (just to call
 * {@code getIneligibilityReason()} / {@code getPostpartumReadyDate()}) — a
 * circular bean dependency that fails to start.
 */
@Service
public class BreedingEligibilityService {

    private final LivestockRepository livestockRepository;
    private final LivestockBirthRepository birthRepository;

    private static final int FALLBACK_POSTPARTUM_RECOVERY_DAYS = 45;
    private static final String HEALTH_HEALTHY = "HEALTHY";
    private static final String HEALTH_SICK = "SICK";
    private static final String HEALTH_UNDER_TREATMENT = "UNDER_TREATMENT";

    private static final String FORM_METHOD_NATURAL = "NATURAL";
    private static final String FORM_METHOD_AI = "ARTIFICIAL_INSEMINATION";
    private static final String FORM_METHOD_EMBRYO_TRANSFER = "EMBRYO_TRANSFER";

    public BreedingEligibilityService(LivestockRepository livestockRepository,
                                      LivestockBirthRepository birthRepository) {
        this.livestockRepository = livestockRepository;
        this.birthRepository = birthRepository;
    }

    // ─── POSTPARTUM RECOVERY CHECK ──────────────────────────────────────────

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

    // ─── HEALTH STATUS HELPERS ───────────────────────────────────────────────

    /** True if the animal's current health_status permits breeding (HEALTHY only). */
    public boolean isHealthyForBreeding(Livestock animal) {
        return animal != null && HEALTH_HEALTHY.equalsIgnoreCase(animal.getHealthStatus());
    }

    /** Human-readable reason the animal is not fit for breeding on health grounds, or null if fine. */
    private String getHealthIneligibilityReason(Livestock animal) {
        if (animal == null) return "Animal is null";
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

    // ─── FEMALE BREEDING ELIGIBILITY (single source of truth) ──────────────

    public boolean isEligibleForBreeding(Livestock female) {
        return getIneligibilityReason(female) == null;
    }

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

        boolean reproductiveStatusOk = Livestock.REPRO_OPEN.equals(female.getReproductiveStatus())
                || Livestock.REPRO_READY_TO_BREED.equals(female.getReproductiveStatus());
        if (!reproductiveStatusOk) {
            return "Reproductive status is " + female.reproductiveStatusLabel()
                    + ", not Open or Ready to Breed.";
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

    // ─── MALE BREEDING ELIGIBILITY (single source of truth) ────────────────

    public String getMaleIneligibilityReason(Livestock male) {
        if (male == null) {
            return null; // male is optional (AI / embryo transfer)
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

    // ─── BREEDING METHOD COMPATIBILITY ──────────────────────────────────────

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

    // ─── ELIGIBLE MALES LIST ─────────────────────────────────────────────────

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
}