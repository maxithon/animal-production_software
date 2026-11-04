package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class BreedingReadinessNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(BreedingReadinessNotificationService.class);

    private final LivestockRepository livestockRepository;
    private final LifecycleEmailService emailService;

    public BreedingReadinessNotificationService(LivestockRepository livestockRepository,
                                                LifecycleEmailService emailService) {
        this.livestockRepository = livestockRepository;
        this.emailService = emailService;
    }

    /**
     * Check for animals that are ready to breed and send notifications
     * Runs daily at 6:00 AM
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void checkReadyToBreedAnimals() {
        logger.info("Starting breeding readiness check...");

        try {
            int readyCount = notifyReadyToBreedFemales(true);
            logger.info("Breeding readiness check completed. Sent {} notifications.", readyCount);
        } catch (Exception e) {
            logger.error("Error in breeding readiness check", e);
        }
    }

    /**
     * Check for animals approaching breeding age (1 month before)
     * Runs daily at 6:30 AM
     */
    @Scheduled(cron = "0 30 6 * * *")
    public void checkApproachingBreedingAge() {
        logger.info("Starting approaching breeding age check...");

        try {
            List<Livestock> activeFemales = livestockRepository.findByGenderAndStatusAndReproductiveStatusAndIsDeletedFalse(
                    "FEMALE", "ACTIVE", "OPEN"
            );

            int approachingCount = 0;
            LocalDate today = LocalDate.now();

            for (Livestock animal : activeFemales) {
                if (animal.getBirthDate() == null || animal.getLivestockCategory() == null) {
                    continue;
                }

                // Fix: Convert Long to Integer properly
                Long ageInMonthsLong = ChronoUnit.MONTHS.between(animal.getBirthDate(), today);
                Integer ageInMonths = ageInMonthsLong.intValue(); // Convert Long to Integer

                Integer minBreedingAge = animal.getLivestockCategory().getMinBreedingAgeMonths();

                // Check if animal is within 1 month of breeding age
                if (minBreedingAge != null && ageInMonths >= minBreedingAge - 1 && ageInMonths < minBreedingAge) {
                    // Send pre-breeding notification
                    emailService.sendPreBreedingNotification(animal);
                    approachingCount++;
                    logger.info("Sent pre-breeding notification for animal: {} ({} months old, will be ready in {} month)",
                            animal.getTagNumber(), ageInMonths, minBreedingAge - ageInMonths);
                }
            }

            logger.info("Approaching breeding age check completed. Sent {} notifications.", approachingCount);

        } catch (Exception e) {
            logger.error("Error in approaching breeding age check", e);
        }
    }

    /**
     * Check for females that have recently given birth and are ready to breed again
     * Runs daily at 7:00 AM
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void checkPostBirthReadyToBreed() {
        logger.info("Starting post-birth breeding readiness check...");

        try {
            // Find all ACTIVE females
            List<Livestock> activeFemales = livestockRepository.findByGenderAndStatusAndReproductiveStatusAndIsDeletedFalse(
                    "FEMALE", "ACTIVE", "OPEN"
            );

            int readyCount = 0;
            LocalDate today = LocalDate.now();

            for (Livestock animal : activeFemales) {
                if (animal.getLastBirthDate() == null) {
                    continue;
                }

                // Fix: Convert Long to Integer properly
                Long monthsSinceLastBirthLong = ChronoUnit.MONTHS.between(animal.getLastBirthDate(), today);
                Integer monthsSinceLastBirth = monthsSinceLastBirthLong.intValue(); // Convert Long to Integer

                // Check if it's been at least 3 months since last birth
                if (monthsSinceLastBirth >= 3) {
                    // Check if animal meets minimum breeding age
                    if (animal.getBirthDate() != null && animal.getLivestockCategory() != null) {
                        Long ageInMonthsLong = ChronoUnit.MONTHS.between(animal.getBirthDate(), today);
                        Integer ageInMonths = ageInMonthsLong.intValue(); // Convert Long to Integer

                        Integer minBreedingAge = animal.getLivestockCategory().getMinBreedingAgeMonths();

                        if (minBreedingAge != null && ageInMonths >= minBreedingAge) {
                            emailService.sendReadyToBreedNotification(animal);
                            readyCount++;
                            logger.info("Sent post-birth breeding notification for animal: {} ({} months since last birth)",
                                    animal.getTagNumber(), monthsSinceLastBirth);
                        }
                    }
                }
            }

            logger.info("Post-birth breeding readiness check completed. Sent {} notifications.", readyCount);

        } catch (Exception e) {
            logger.error("Error in post-birth breeding readiness check", e);
        }
    }

    /**
     * Manual trigger for testing
     */
    public void runManualCheck() {
        logger.info("Manual trigger: Running breeding readiness check...");
        checkReadyToBreedAnimals();
    }

    /**
     * Used by LivestockBreedingController's "/api/trigger-notifications"
     * endpoint. Unlike checkReadyToBreedAnimals() (the scheduled job, which
     * swallows exceptions and returns void), this variant lets exceptions
     * propagate to the controller and returns the number of notifications
     * actually sent, so the API response can report a real count back to
     * the caller.
     *
     * @return the number of ready-to-breed notification emails queued
     */
    public int sendImmediateNotifications() {
        logger.info("Manual trigger: sending immediate breeding readiness notifications...");
        return notifyReadyToBreedFemales(false);
    }

    /**
     * Used by LivestockBreedingController's "/api/check-ready/{id}"
     * endpoint. Answers "is this specific animal ready to breed right now?"
     * without sending any notification — a read-only check, unlike the
     * scheduled/immediate methods above which send emails as a side effect.
     *
     * Mirrors the same eligibility rule used elsewhere in this class:
     * female, birth date known, category known, and age in months >= the
     * category's minimum breeding age.
     *
     * @param livestockId the animal to check
     * @return true if the animal meets the breeding-readiness criteria
     */
    public boolean isReadyToBreed(UUID livestockId) {
        return livestockRepository.findById(livestockId)
                .filter(animal -> "FEMALE".equalsIgnoreCase(animal.getGender()))
                .filter(animal -> animal.getBirthDate() != null)
                .filter(animal -> animal.getLivestockCategory() != null)
                .map(animal -> {
                    Integer minBreedingAge = animal.getLivestockCategory().getMinBreedingAgeMonths();
                    if (minBreedingAge == null) {
                        return false;
                    }
                    long ageInMonths = ChronoUnit.MONTHS.between(animal.getBirthDate(), LocalDate.now());
                    return ageInMonths >= minBreedingAge;
                })
                .orElse(false);
    }

    /**
     * Shared logic behind checkReadyToBreedAnimals() (scheduled) and
     * sendImmediateNotifications() (manual/API). Finds all ACTIVE, OPEN
     * females that meet their category's minimum breeding age and sends a
     * ready-to-breed email for each.
     *
     * NOTE: LifecycleEmailService.sendReadyToBreedNotification() only takes
     * a single Livestock argument in this codebase — there is no
     * (animal, scheduled) overload, so the `scheduled` flag below is used
     * only for logging, not passed to the email. If you want the email
     * itself to distinguish scheduled vs. manual runs (the HTML template
     * already has a ${scheduled} conditional for this), that requires
     * adding an overload to LifecycleEmailService — paste me its current
     * full contents and I'll add it cleanly.
     *
     * @param scheduled whether this run originated from the scheduled job
     *                  (true) or a manual/API trigger (false)
     * @return number of notifications sent
     */
    private int notifyReadyToBreedFemales(boolean scheduled) {
        List<Livestock> activeFemales = livestockRepository.findByGenderAndStatusAndReproductiveStatusAndIsDeletedFalse(
                "FEMALE", "ACTIVE", "OPEN"
        );

        int readyCount = 0;
        LocalDate today = LocalDate.now();

        for (Livestock animal : activeFemales) {
            if (animal.getBirthDate() == null || animal.getLivestockCategory() == null) {
                continue;
            }

            Long ageInMonthsLong = ChronoUnit.MONTHS.between(animal.getBirthDate(), today);
            Integer ageInMonths = ageInMonthsLong.intValue();

            Integer minBreedingAge = animal.getLivestockCategory().getMinBreedingAgeMonths();

            if (minBreedingAge != null && ageInMonths >= minBreedingAge) {
                emailService.sendReadyToBreedNotification(animal);
                readyCount++;
                logger.info("Sent ready-to-breed notification for animal: {} ({} months old, scheduled={})",
                        animal.getTagNumber(), ageInMonths, scheduled);
            }
        }

        return readyCount;
    }
}
