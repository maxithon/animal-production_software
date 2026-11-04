package rw.animalproduct.animal.production.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.services.LifecycleEmailService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing livestock lifecycle stages and notifications.
 *
 * This service handles:
 * - Determining the lifecycle stage of an animal based on age and category
 * - Sending notifications when animals transition between stages
 * - Tracking pregnancy timelines and due dates
 *
 * @author HABANABAKIZE & NDAYIZEYE
 * @version 1.0.0
 */
@Service
public class LivestockLifecycleService {

    private static final Logger logger = LoggerFactory.getLogger(LivestockLifecycleService.class);

    // Stage transition thresholds (in months)
    public static final int NEWBORN_THRESHOLD_DAYS = 30;
    public static final int YOUNG_THRESHOLD_MONTHS = 6;
    public static final int PRE_BREEDING_THRESHOLD_MONTHS = 12;

    // Pregnancy timeline constants
    public static final int UPCOMING_BIRTH_LEAD_DAYS = 30;
    public static final int BIRTH_PREPARATION_LEAD_DAYS = 14;
    public static final int BIRTH_DUE_SOON_LEAD_DAYS = 7;

    private final LivestockRepository livestockRepository;
    private final LivestockBreedingRepository breedingRepository;
    private final LifecycleEmailService emailService;

    public LivestockLifecycleService(LivestockRepository livestockRepository,
                                     LivestockBreedingRepository breedingRepository,
                                     LifecycleEmailService emailService) {
        this.livestockRepository = livestockRepository;
        this.breedingRepository = breedingRepository;
        this.emailService = emailService;
    }

    // =====================================================================
    // STAGE DETERMINATION
    // =====================================================================

    /**
     * Determine the current lifecycle stage of an animal.
     */
    public String getCurrentStage(Livestock animal) {
        if (animal == null) {
            return "UNKNOWN";
        }

        // Check pregnancy first
        if (Livestock.REPRO_PREGNANT.equals(animal.getReproductiveStatus())) {
            return "PREGNANT";
        }

        // Check if animal is nursing
        if (Livestock.REPRO_NURSING.equals(animal.getReproductiveStatus())) {
            return "NURSING";
        }

        // Check if animal is in breeding
        if (Livestock.REPRO_BRED.equals(animal.getReproductiveStatus())) {
            return "IN_BREEDING";
        }

        LocalDate birthDate = animal.getBirthDate();
        if (birthDate == null) {
            return "UNKNOWN";
        }

        long ageDays = getAgeInDays(animal);
        long ageMonths = getAgeInMonths(animal);

        // Determine stage based on age
        if (ageDays <= NEWBORN_THRESHOLD_DAYS) {
            return "NEWBORN";
        }

        if (ageMonths < YOUNG_THRESHOLD_MONTHS) {
            return "YOUNG";
        }

        // Check if animal is ready to breed or in pre-breeding
        Integer minBreedingAge = getMinBreedingAge(animal);
        Integer maxBreedingAge = getMaxBreedingAge(animal);

        if (minBreedingAge != null && ageMonths < minBreedingAge) {
            return "PRE_BREEDING";
        }

        if (minBreedingAge != null && ageMonths >= minBreedingAge) {
            if (maxBreedingAge != null && ageMonths > maxBreedingAge) {
                return "RETIRED";
            }
            // Only return READY_TO_BREED if not pregnant and not in breeding
            if (!Livestock.REPRO_PREGNANT.equals(animal.getReproductiveStatus())
                    && !Livestock.REPRO_BRED.equals(animal.getReproductiveStatus())) {
                return "READY_TO_BREED";
            }
            return "MATURE";
        }

        return "MATURE";
    }

    /**
     * Get the next milestone for an animal.
     */
    public String getNextMilestone(Livestock animal) {
        if (animal == null || animal.getBirthDate() == null) {
            return null;
        }

        long ageMonths = getAgeInMonths(animal);
        String currentStage = getCurrentStage(animal);

        return switch (currentStage) {
            case "NEWBORN" -> "Young stage at " + YOUNG_THRESHOLD_MONTHS + " months";
            case "YOUNG" -> {
                Integer minBreedingAge = getMinBreedingAge(animal);
                if (minBreedingAge != null) {
                    long monthsUntilReady = Math.max(0, minBreedingAge - ageMonths);
                    yield "Ready to breed in " + monthsUntilReady + " months";
                }
                yield "Category has no breeding age configured";
            }
            case "PRE_BREEDING" -> {
                Integer minBreedingAge = getMinBreedingAge(animal);
                if (minBreedingAge != null) {
                    long monthsUntilReady = Math.max(0, minBreedingAge - ageMonths);
                    yield "Ready to breed in " + monthsUntilReady + " months";
                }
                yield "Category has no breeding age configured";
            }
            case "READY_TO_BREED" -> "Ready for breeding";
            case "PREGNANT" -> {
                if (animal.getExpectedDueDate() != null) {
                    long daysUntilDue = daysUntilDue(animal.getExpectedDueDate());
                    yield "Due in " + Math.max(0, daysUntilDue) + " days";
                }
                yield "Pregnancy stage";
            }
            default -> null;
        };
    }

    // =====================================================================
    // AGE CALCULATIONS
    // =====================================================================

    public long getAgeInDays(Livestock animal) {
        if (animal == null || animal.getBirthDate() == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(animal.getBirthDate(), LocalDate.now());
    }

    public long getAgeInMonths(Livestock animal) {
        if (animal == null || animal.getBirthDate() == null) {
            return 0;
        }
        return ChronoUnit.MONTHS.between(animal.getBirthDate(), LocalDate.now());
    }

    public Integer getMinBreedingAge(Livestock animal) {
        if (animal == null || animal.getLivestockCategory() == null) {
            return null;
        }
        return animal.getLivestockCategory().getMinBreedingAgeMonths();
    }

    public Integer getMaxBreedingAge(Livestock animal) {
        if (animal == null || animal.getLivestockCategory() == null) {
            return null;
        }
        return animal.getLivestockCategory().getMaxBreedingAgeMonths();
    }

    // =====================================================================
    // PREGNANCY TIMELINE
    // =====================================================================

    public long daysUntilDue(LocalDate dueDate) {
        if (dueDate == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
    }

    public int overdueTierCrossed(long daysOverdue) {
        if (daysOverdue <= 0) return 0;
        if (daysOverdue <= 7) return 1;
        if (daysOverdue <= 14) return 2;
        return 3;
    }

    // =====================================================================
    // STAGE TRANSITION EVALUATION
    // =====================================================================

    @Transactional
    public void evaluateAndNotifyStageChange(Livestock animal) {
        if (animal == null) {
            return;
        }

        String currentStage = getCurrentStage(animal);
        String previousStage = animal.getLastLifecycleStage();

        // If stage has changed, send notification
        if (currentStage != null && !currentStage.equals(previousStage)) {
            animal.setLastLifecycleStage(currentStage);
            livestockRepository.save(animal);

            // Send notification for stage change
            try {
                sendStageNotification(currentStage, animal);
            } catch (Exception e) {
                logger.warn("Failed to send stage notification for {} (stage: {}): {}",
                        animal.getTagNumber(), currentStage, e.getMessage());
            }

            logger.info("Stage transition for {}: {} → {}", animal.getTagNumber(), previousStage, currentStage);
        }
    }

    /**
     * Send notification for a stage change.
     * ✅ FIXED: Using the correct method names from LifecycleEmailService
     */
    private void sendStageNotification(String stage, Livestock animal) {
        switch (stage) {
            case "NEWBORN":
                emailService.sendNewbornNotification(animal);
                break;
            case "YOUNG":
                emailService.sendYoungStageNotification(animal);
                break;
            case "PRE_BREEDING":
                emailService.sendPreBreedingNotification(animal);
                break;
            case "READY_TO_BREED":
                emailService.sendReadyToBreedNotification(animal);
                break;
            case "PREGNANT":
                // Find the breeding record and send pregnancy notification
                breedingRepository.findByLivestockIdAndIsDeletedFalse(animal.getId())
                        .stream()
                        .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus()))
                        .findFirst()
                        .ifPresent(breeding -> {
                            try {
                                emailService.sendPregnancyConfirmedNotification(breeding);
                            } catch (Exception e) {
                                logger.warn("Failed to send pregnancy notification for {}: {}",
                                        animal.getTagNumber(), e.getMessage());
                            }
                        });
                break;
            case "NURSING":
                // Optional: send nursing notification
                logger.debug("Animal {} entered nursing stage", animal.getTagNumber());
                break;
            case "IN_BREEDING":
                // Optional: send in-breeding notification
                logger.debug("Animal {} entered in-breeding stage", animal.getTagNumber());
                break;
            default:
                // No specific notification for other stages
                logger.debug("No specific notification for stage: {}", stage);
                break;
        }
    }

    // =====================================================================
    // QUERY METHODS
    // =====================================================================

    public Livestock getAnimalById(UUID id) {
        return livestockRepository.findByIdAndIsDeletedFalse(id).orElse(null);
    }

    public List<Livestock> getActiveAnimals() {
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE);
    }

    public List<Livestock> getActiveAnimalsByAgeRange(int minDays, int maxDays) {
        LocalDate today = LocalDate.now();
        LocalDate minDate = today.minusDays(maxDays);
        LocalDate maxDate = today.minusDays(minDays);

        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> !a.getBirthDate().isBefore(minDate))
                .filter(a -> !a.getBirthDate().isAfter(maxDate))
                .collect(Collectors.toList());
    }

    public List<Livestock> getOffspring(UUID motherId) {
        return livestockRepository.findByMotherIdAndIsDeletedFalse(motherId);
    }

    // ─── COUNTS ────────────────────────────────────────────────────────────────

    public long countAll() {
        return livestockRepository.countByStatus(Livestock.STATUS_ACTIVE);
    }

    public long countNewborns() {
        LocalDate threshold = LocalDate.now().minusDays(NEWBORN_THRESHOLD_DAYS);
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> !a.getBirthDate().isBefore(threshold))
                .count();
    }

    public long countYoung() {
        LocalDate threshold = LocalDate.now().minusMonths(YOUNG_THRESHOLD_MONTHS);
        LocalDate newbornThreshold = LocalDate.now().minusDays(NEWBORN_THRESHOLD_DAYS);
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> !a.getBirthDate().isAfter(threshold))
                .filter(a -> a.getBirthDate().isBefore(newbornThreshold))
                .filter(a -> !Livestock.REPRO_PREGNANT.equals(a.getReproductiveStatus()))
                .filter(a -> !Livestock.REPRO_BRED.equals(a.getReproductiveStatus()))
                .count();
    }

    public long countPreBreeding() {
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> {
                    long ageMonths = getAgeInMonths(a);
                    Integer minBreedingAge = getMinBreedingAge(a);
                    return minBreedingAge != null && ageMonths < minBreedingAge && ageMonths >= YOUNG_THRESHOLD_MONTHS;
                })
                .count();
    }

    public long countReadyToBreed() {
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> {
                    long ageMonths = getAgeInMonths(a);
                    Integer minBreedingAge = getMinBreedingAge(a);
                    Integer maxBreedingAge = getMaxBreedingAge(a);
                    boolean isReady = minBreedingAge != null && ageMonths >= minBreedingAge;
                    boolean notTooOld = maxBreedingAge == null || ageMonths <= maxBreedingAge;
                    boolean notPregnant = !Livestock.REPRO_PREGNANT.equals(a.getReproductiveStatus());
                    boolean notInBreeding = !Livestock.REPRO_BRED.equals(a.getReproductiveStatus());
                    return isReady && notTooOld && notPregnant && notInBreeding;
                })
                .count();
    }

    public long countInBreeding() {
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> Livestock.REPRO_BRED.equals(a.getReproductiveStatus()))
                .count();
    }

    public long countPregnant() {
        return livestockRepository.countByStatusAndReproductiveStatus(
                Livestock.STATUS_ACTIVE, Livestock.REPRO_PREGNANT);
    }

    public long countDueSoon(int days) {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(days);
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> a.getExpectedDueDate() != null)
                .filter(a -> !a.getExpectedDueDate().isBefore(today))
                .filter(a -> !a.getExpectedDueDate().isAfter(threshold))
                .count();
    }

    public long countAllNotifications() {
        return countNewborns() + countYoung() + countPreBreeding()
                + countReadyToBreed() + countDueSoon(14) + getOverdue().size();
    }

    // ─── LISTS ──────────────────────────────────────────────────────────────────

    public List<Livestock> getRecentlyBorn(int days) {
        LocalDate threshold = LocalDate.now().minusDays(days);
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> !a.getBirthDate().isBefore(threshold))
                .sorted(Comparator.comparing(Livestock::getBirthDate).reversed())
                .collect(Collectors.toList());
    }

    public List<Livestock> getYoungFemales() {
        LocalDate threshold = LocalDate.now().minusMonths(YOUNG_THRESHOLD_MONTHS);
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> "FEMALE".equalsIgnoreCase(a.getGender()))
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> !a.getBirthDate().isBefore(threshold))
                .sorted(Comparator.comparing(Livestock::getBirthDate).reversed())
                .collect(Collectors.toList());
    }

    public List<Livestock> getYoungMales() {
        LocalDate threshold = LocalDate.now().minusMonths(YOUNG_THRESHOLD_MONTHS);
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> "MALE".equalsIgnoreCase(a.getGender()))
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> !a.getBirthDate().isBefore(threshold))
                .sorted(Comparator.comparing(Livestock::getBirthDate).reversed())
                .collect(Collectors.toList());
    }

    public List<Livestock> getApproachingBreedingAge() {
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> {
                    long ageMonths = getAgeInMonths(a);
                    Integer minBreedingAge = getMinBreedingAge(a);
                    return minBreedingAge != null
                            && ageMonths >= (minBreedingAge - 3)
                            && ageMonths < minBreedingAge;
                })
                .sorted(Comparator.comparing(Livestock::getBirthDate).reversed())
                .collect(Collectors.toList());
    }

    public List<Livestock> getReadyToBreed() {
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> {
                    long ageMonths = getAgeInMonths(a);
                    Integer minBreedingAge = getMinBreedingAge(a);
                    Integer maxBreedingAge = getMaxBreedingAge(a);
                    boolean isReady = minBreedingAge != null && ageMonths >= minBreedingAge;
                    boolean notTooOld = maxBreedingAge == null || ageMonths <= maxBreedingAge;
                    boolean notPregnant = !Livestock.REPRO_PREGNANT.equals(a.getReproductiveStatus());
                    boolean notInBreeding = !Livestock.REPRO_BRED.equals(a.getReproductiveStatus());
                    return isReady && notTooOld && notPregnant && notInBreeding;
                })
                .sorted(Comparator.comparing(Livestock::getBirthDate))
                .collect(Collectors.toList());
    }

    public List<Livestock> getAllMales() {
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> "MALE".equalsIgnoreCase(a.getGender()))
                .collect(Collectors.toList());
    }

    public List<Livestock> getAllFemales() {
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> "FEMALE".equalsIgnoreCase(a.getGender()))
                .collect(Collectors.toList());
    }

    public List<Livestock> getMalesReadyToBreed() {
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> "MALE".equalsIgnoreCase(a.getGender()))
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> {
                    long ageMonths = getAgeInMonths(a);
                    Integer minBreedingAge = getMinBreedingAge(a);
                    return minBreedingAge != null && ageMonths >= minBreedingAge;
                })
                .collect(Collectors.toList());
    }

    public List<Livestock> getPregnantAnimals() {
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> Livestock.REPRO_PREGNANT.equals(a.getReproductiveStatus()))
                .sorted(Comparator.comparing(Livestock::getExpectedDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    public List<Livestock> getNursingAnimals() {
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> Livestock.REPRO_NURSING.equals(a.getReproductiveStatus()))
                .collect(Collectors.toList());
    }

    public List<Livestock> getDueSoon(int days) {
        LocalDate today = LocalDate.now();
        LocalDate threshold = today.plusDays(days);
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> a.getExpectedDueDate() != null)
                .filter(a -> !a.getExpectedDueDate().isBefore(today))
                .filter(a -> !a.getExpectedDueDate().isAfter(threshold))
                .sorted(Comparator.comparing(Livestock::getExpectedDueDate))
                .collect(Collectors.toList());
    }

    public List<Livestock> getOverdue() {
        LocalDate today = LocalDate.now();
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE)
                .stream()
                .filter(a -> a.getExpectedDueDate() != null)
                .filter(a -> a.getExpectedDueDate().isBefore(today))
                .sorted(Comparator.comparing(Livestock::getExpectedDueDate))
                .collect(Collectors.toList());
    }

    public List<LivestockBreeding> getActiveBreedings() {
        return breedingRepository.findByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_PENDING);
    }

    // ─── STATISTICS ────────────────────────────────────────────────────────────

    public Map<String, Long> getStageDistribution() {
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("NEWBORN", countNewborns());
        distribution.put("YOUNG", countYoung());
        distribution.put("PRE_BREEDING", countPreBreeding());
        distribution.put("READY_TO_BREED", countReadyToBreed());
        distribution.put("PREGNANT", countPregnant());
        distribution.put("IN_BREEDING", countInBreeding());
        return distribution;
    }

    public Map<String, Long> getAgeBandBreakdown() {
        Map<String, Long> bands = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        List<Livestock> allActive = livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE);

        bands.put("0-3 months", allActive.stream()
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> ChronoUnit.MONTHS.between(a.getBirthDate(), today) < 3)
                .count());

        bands.put("3-6 months", allActive.stream()
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> {
                    long months = ChronoUnit.MONTHS.between(a.getBirthDate(), today);
                    return months >= 3 && months < 6;
                })
                .count());

        bands.put("6-12 months", allActive.stream()
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> {
                    long months = ChronoUnit.MONTHS.between(a.getBirthDate(), today);
                    return months >= 6 && months < 12;
                })
                .count());

        bands.put("1-2 years", allActive.stream()
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> {
                    long months = ChronoUnit.MONTHS.between(a.getBirthDate(), today);
                    return months >= 12 && months < 24;
                })
                .count());

        bands.put("2-5 years", allActive.stream()
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> {
                    long months = ChronoUnit.MONTHS.between(a.getBirthDate(), today);
                    return months >= 24 && months < 60;
                })
                .count());

        bands.put("5+ years", allActive.stream()
                .filter(a -> a.getBirthDate() != null)
                .filter(a -> {
                    long months = ChronoUnit.MONTHS.between(a.getBirthDate(), today);
                    return months >= 60;
                })
                .count());

        return bands;
    }

    public Map<String, Object> getMaleBreedingStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        List<Livestock> males = getMalesReadyToBreed();
        stats.put("totalMales", males.size());

        // Get breeding success rate for each male
        Map<String, Object> maleDetails = new LinkedHashMap<>();
        for (Livestock male : males) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("tagNumber", male.getTagNumber());
            detail.put("age", male.getAgeDisplay());
            detail.put("category", male.getLivestockCategory() != null
                    ? male.getLivestockCategory().getName() : "Unknown");

            // Count successful breedings for this male
            long successful = breedingRepository.findByIsDeletedFalse()
                    .stream()
                    .filter(b -> b.getMale() != null && b.getMale().getId().equals(male.getId()))
                    .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                            || LivestockBreeding.STATUS_COMPLETED.equals(b.getStatus()))
                    .count();

            long total = breedingRepository.findByIsDeletedFalse()
                    .stream()
                    .filter(b -> b.getMale() != null && b.getMale().getId().equals(male.getId()))
                    .count();

            detail.put("totalBreedings", total);
            detail.put("successfulBreedings", successful);
            detail.put("successRate", total > 0 ? (double) successful / total * 100 : 0);

            maleDetails.put(male.getId().toString(), detail);
        }
        stats.put("maleDetails", maleDetails);

        return stats;
    }

    public double getBreedingSuccessRate() {
        List<LivestockBreeding> allBreedings = breedingRepository.findByIsDeletedFalse();
        if (allBreedings.isEmpty()) {
            return 0.0;
        }

        long successful = allBreedings.stream()
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                        || LivestockBreeding.STATUS_COMPLETED.equals(b.getStatus()))
                .count();

        return (double) successful / allBreedings.size() * 100;
    }

    // ─── END OF CLASS ──────────────────────────────────────────────────────────
}