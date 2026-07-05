package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class BreedingPerformanceService {

    private static final Logger log = LoggerFactory.getLogger(BreedingPerformanceService.class);

    private final LivestockBreedingRepository breedingRepository;
    private final LivestockBirthRepository birthRepository;
    private final LivestockOffspringRepository offspringRepository;
    private final LivestockRepository livestockRepository;
    private final LivestockCategoryRepository categoryRepository;

    public BreedingPerformanceService(
            LivestockBreedingRepository breedingRepository,
            LivestockBirthRepository birthRepository,
            LivestockOffspringRepository offspringRepository,
            LivestockRepository livestockRepository,
            LivestockCategoryRepository categoryRepository) {
        this.breedingRepository = breedingRepository;
        this.birthRepository = birthRepository;
        this.offspringRepository = offspringRepository;
        this.livestockRepository = livestockRepository;
        this.categoryRepository = categoryRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. BREEDING SUCCESS RATE
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Calculate breeding success rate for a specific livestock animal.
     * Success = confirmed pregnancies that resulted in live births.
     */
    public BreedingPerformanceMetrics getBreedingPerformance(UUID livestockId) {
        log.debug("Calculating breeding performance for livestock ID: {}", livestockId);

        BreedingPerformanceMetrics metrics = new BreedingPerformanceMetrics();
        metrics.setLivestockId(livestockId);

        // Get all breeding records for this animal
        List<LivestockBreeding> breedings = breedingRepository.findByLivestockIdAndIsDeletedFalse(livestockId);
        metrics.setTotalBreedingAttempts(breedings.size());

        // Count by status
        long confirmedPregnancies = breedings.stream()
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                        || LivestockBreeding.STATUS_COMPLETED.equals(b.getStatus()))
                .count();
        metrics.setConfirmedPregnancies((int) confirmedPregnancies);

        long failedAttempts = breedings.stream()
                .filter(b -> LivestockBreeding.STATUS_FAILED.equals(b.getStatus()))
                .count();
        metrics.setFailedAttempts((int) failedAttempts);

        long completedPregnancies = breedings.stream()
                .filter(b -> LivestockBreeding.STATUS_COMPLETED.equals(b.getStatus()))
                .count();
        metrics.setCompletedPregnancies((int) completedPregnancies);

        // Calculate success rate
        if (metrics.getTotalBreedingAttempts() > 0) {
            double successRate = (double) confirmedPregnancies / metrics.getTotalBreedingAttempts() * 100;
            metrics.setSuccessRate(Math.round(successRate * 100.0) / 100.0);
        } else {
            metrics.setSuccessRate(0.0);
        }

        // Get births for this livestock
        List<LivestockBirth> births = birthRepository.findByLivestockIdAndIsDeletedFalse(livestockId);
        metrics.setTotalBirths(births.size());

        // Calculate average offspring per birth
        if (!births.isEmpty()) {
            int totalOffspring = births.stream()
                    .mapToInt(birth -> birth.getOffspringCount() != null ? birth.getOffspringCount() : 0)
                    .sum();
            metrics.setAverageOffspringPerBirth(
                    Math.round((double) totalOffspring / births.size() * 100.0) / 100.0
            );
        }

        // Get offspring count from livestock entity
        livestockRepository.findById(livestockId).ifPresent(livestock -> {
            metrics.setTotalOffspringProduced(livestock.getOffspringCount() != null
                    ? livestock.getOffspringCount() : 0);
        });

        // Calculate conception rate (if we have breeding dates)
        calculateConceptionMetrics(breedings, metrics);

        // Calculate breeding interval
        calculateBreedingInterval(breedings, metrics);

        return metrics;
    }

    /**
     * Get breeding performance for all animals by category.
     */
    public Map<String, BreedingPerformanceMetrics> getBreedingPerformanceByCategory() {
        log.debug("Calculating breeding performance by category");

        Map<String, BreedingPerformanceMetrics> categoryMetrics = new HashMap<>();
        List<LivestockCategory> categories = categoryRepository.findAllByIsDeletedFalse();

        for (LivestockCategory category : categories) {
            // Get all livestock in this category
            List<Livestock> livestockList = livestockRepository.findByLivestockCategoryIdAndIsDeletedFalse(category.getId());

            if (!livestockList.isEmpty()) {
                BreedingPerformanceMetrics aggregated = new BreedingPerformanceMetrics();
                aggregated.setCategoryName(category.getName());

                int totalBreedings = 0;
                int totalConfirmed = 0;
                int totalCompleted = 0;
                int totalFailed = 0;
                int totalBirths = 0;
                int totalOffspring = 0;

                for (Livestock livestock : livestockList) {
                    // Get breeding records
                    List<LivestockBreeding> breedings = breedingRepository
                            .findByLivestockIdAndIsDeletedFalse(livestock.getId());

                    totalBreedings += breedings.size();
                    totalConfirmed += breedings.stream()
                            .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                                    || LivestockBreeding.STATUS_COMPLETED.equals(b.getStatus()))
                            .count();
                    totalCompleted += breedings.stream()
                            .filter(b -> LivestockBreeding.STATUS_COMPLETED.equals(b.getStatus()))
                            .count();
                    totalFailed += breedings.stream()
                            .filter(b -> LivestockBreeding.STATUS_FAILED.equals(b.getStatus()))
                            .count();

                    // Get births
                    List<LivestockBirth> births = birthRepository
                            .findByLivestockIdAndIsDeletedFalse(livestock.getId());
                    totalBirths += births.size();

                    for (LivestockBirth birth : births) {
                        totalOffspring += birth.getOffspringCount() != null ? birth.getOffspringCount() : 0;
                    }
                }

                aggregated.setTotalBreedingAttempts(totalBreedings);
                aggregated.setConfirmedPregnancies((int) totalConfirmed);
                aggregated.setCompletedPregnancies((int) totalCompleted);
                aggregated.setFailedAttempts((int) totalFailed);
                aggregated.setTotalBirths(totalBirths);
                aggregated.setTotalOffspringProduced(totalOffspring);

                if (totalBreedings > 0) {
                    aggregated.setSuccessRate(Math.round((double) totalConfirmed / totalBreedings * 10000.0) / 100.0);
                }

                if (totalBirths > 0) {
                    aggregated.setAverageOffspringPerBirth(
                            Math.round((double) totalOffspring / totalBirths * 100.0) / 100.0
                    );
                }

                // Set count of animals in this category
                aggregated.setAnimalCount(livestockList.size());

                categoryMetrics.put(category.getName(), aggregated);
            }
        }

        return categoryMetrics;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. PREGNANCY TRACKING
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Get all currently pregnant animals with expected due dates.
     */
    public List<PregnancyTrackingDto> getCurrentPregnancies() {
        log.debug("Fetching current pregnancies");

        // Find all confirmed pregnant animals
        List<Livestock> pregnantAnimals = livestockRepository
                .findByStatusAndIsDeletedFalse(Livestock.STATUS_PREGNANT);

        List<PregnancyTrackingDto> results = new ArrayList<>();

        for (Livestock animal : pregnantAnimals) {
            // Get the latest breeding record
            List<LivestockBreeding> breedings = breedingRepository
                    .findByLivestockIdAndStatusAndIsDeletedFalse(
                            animal.getId(),
                            LivestockBreeding.STATUS_CONFIRMED_PREGNANT
                    );

            if (!breedings.isEmpty()) {
                LivestockBreeding latestBreeding = breedings.get(breedings.size() - 1);
                PregnancyTrackingDto dto = createPregnancyDto(animal, latestBreeding);
                results.add(dto);
            } else {
                // Fallback: create DTO from livestock data
                PregnancyTrackingDto dto = createPregnancyDtoFromLivestock(animal);
                results.add(dto);
            }
        }

        return results;
    }

    /**
     * Get upcoming births (expected in next days).
     */
    public List<PregnancyTrackingDto> getUpcomingBirths(int days) {
        log.debug("Fetching upcoming births within {} days", days);

        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(days);

        List<LivestockBreeding> upcomingBreedings = breedingRepository
                .findByStatusAndExpectedDueDateBetweenAndIsDeletedFalse(
                        LivestockBreeding.STATUS_CONFIRMED_PREGNANT,
                        today,
                        cutoff
                );

        List<PregnancyTrackingDto> results = new ArrayList<>();

        for (LivestockBreeding breeding : upcomingBreedings) {
            Livestock animal = breeding.getLivestock();
            if (animal != null && !Boolean.TRUE.equals(animal.getIsDeleted())) {
                PregnancyTrackingDto dto = createPregnancyDto(animal, breeding);
                // Calculate days until due
                long daysUntilDue = ChronoUnit.DAYS.between(today, breeding.getExpectedDueDate());
                dto.setDaysUntilDue((int) daysUntilDue);
                results.add(dto);
            }
        }

        return results;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. CALVING/BIRTHING REPORTS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Get birthing report for a specific time period.
     */
    public BirthingReportDto getBirthingReport(LocalDate startDate, LocalDate endDate) {
        log.debug("Generating birthing report from {} to {}", startDate, endDate);

        BirthingReportDto report = new BirthingReportDto();
        report.setStartDate(startDate);
        report.setEndDate(endDate);

        // Get births in the date range
        List<LivestockBirth> births = birthRepository
                .findByBirthDateBetweenAndIsDeletedFalse(startDate, endDate);

        report.setTotalBirths(births.size());

        int totalOffspring = 0;
        int liveOffspring = 0;
        int stillborn = 0;
        Map<String, Integer> birthsByCategory = new HashMap<>();
        Map<String, Integer> offspringByGender = new HashMap<>();

        for (LivestockBirth birth : births) {
            // Get offspring count
            int count = birth.getOffspringCount() != null ? birth.getOffspringCount() : 0;
            totalOffspring += count;

            // Get offspring details from the children list
            if (birth.getChildren() != null && !birth.getChildren().isEmpty()) {
                for (LivestockOffspring child : birth.getChildren()) {
                    // FIX: Use the isAlive() method which checks both flag and child status
                    if (child.isAlive()) {
                        liveOffspring++;
                    } else {
                        stillborn++;
                    }

                    // FIX: Get gender from child livestock entity
                    String gender = child.getGender();
                    if (gender != null) {
                        offspringByGender.merge(gender, 1, Integer::sum);
                    }
                }
            }

            // Count by category
            Livestock mother = birth.getLivestock();
            if (mother != null && mother.getLivestockCategory() != null) {
                String categoryName = mother.getLivestockCategory().getName();
                birthsByCategory.merge(categoryName, 1, Integer::sum);
            }
        }

        report.setTotalOffspring(totalOffspring);
        report.setLiveOffspring(liveOffspring);
        report.setStillborn(stillborn);
        report.setBirthsByCategory(birthsByCategory);
        report.setOffspringByGender(offspringByGender);

        // Calculate average offspring per birth
        if (births.size() > 0) {
            report.setAverageOffspringPerBirth(
                    Math.round((double) totalOffspring / births.size() * 100.0) / 100.0
            );
        }

        return report;
    }

    /**
     * Get calving interval for a specific animal.
     * Calving interval = time between successive births.
     */
    public CalvingIntervalDto getCalvingInterval(UUID livestockId) {
        log.debug("Calculating calving interval for livestock ID: {}", livestockId);

        CalvingIntervalDto dto = new CalvingIntervalDto();
        dto.setLivestockId(livestockId);

        // Get all births for this animal
        List<LivestockBirth> births = birthRepository
                .findByLivestockIdAndIsDeletedFalseOrderByBirthDateAsc(livestockId);

        if (births.size() < 2) {
            dto.setMessage("Animal has " + births.size() + " birth(s) recorded. Need at least 2 for calving interval.");
            dto.setHasSufficientData(false);
            return dto;
        }

        List<Integer> intervals = new ArrayList<>();
        List<LocalDate> birthDates = births.stream()
                .map(LivestockBirth::getBirthDate)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

        for (int i = 1; i < birthDates.size(); i++) {
            int daysBetween = (int) ChronoUnit.DAYS.between(birthDates.get(i - 1), birthDates.get(i));
            intervals.add(daysBetween);
        }

        // Calculate statistics
        int totalDays = intervals.stream().mapToInt(Integer::intValue).sum();
        double averageDays = (double) totalDays / intervals.size();

        dto.setNumberOfBirths(births.size());
        dto.setAverageIntervalDays(Math.round(averageDays * 100.0) / 100.0);
        dto.setMinIntervalDays(intervals.stream().min(Integer::compareTo).orElse(0));
        dto.setMaxIntervalDays(intervals.stream().max(Integer::compareTo).orElse(0));
        dto.setIntervals(intervals);
        dto.setHasSufficientData(true);

        // Get the animal's category for reference
        livestockRepository.findById(livestockId).ifPresent(livestock -> {
            if (livestock.getLivestockCategory() != null) {
                dto.setCategoryName(livestock.getLivestockCategory().getName());
            }
            dto.setTagNumber(livestock.getTagNumber());
        });

        return dto;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4. COMBINED REPORT
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Generate a comprehensive breeding performance report for a date range.
     * This method combines multiple metrics into one report.
     */
    public BreedingPerformanceReport generateReport(LocalDate startDate, LocalDate endDate) {
        log.debug("Generating comprehensive breeding report from {} to {}", startDate, endDate);

        BreedingPerformanceReport report = new BreedingPerformanceReport();
        report.setStartDate(startDate);
        report.setEndDate(endDate);

        // Get birthing report
        BirthingReportDto birthingReport = getBirthingReport(startDate, endDate);
        report.setBirthingReport(birthingReport);

        // Get performance by category
        Map<String, BreedingPerformanceMetrics> categoryPerformance = getBreedingPerformanceByCategory();
        report.setCategoryPerformance(categoryPerformance);

        // Get current pregnancies
        List<PregnancyTrackingDto> pregnancies = getCurrentPregnancies();
        report.setCurrentPregnancies(pregnancies);

        // Get upcoming births (within the date range)
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        List<PregnancyTrackingDto> upcoming = getUpcomingBirths((int) Math.max(daysBetween, 30));
        report.setUpcomingBirths(upcoming);

        // Calculate summary statistics
        int totalBirths = birthingReport.getTotalBirths();
        int totalOffspring = birthingReport.getTotalOffspring();
        double avgOffspring = totalBirths > 0 ? (double) totalOffspring / totalBirths : 0.0;
        report.setAverageOffspringPerBirth(Math.round(avgOffspring * 100.0) / 100.0);

        report.setTotalActivePregnancies(pregnancies.size());

        // Calculate overall success rate
        double overallSuccessRate = categoryPerformance.values().stream()
                .mapToDouble(BreedingPerformanceMetrics::getSuccessRate)
                .average()
                .orElse(0.0);
        report.setOverallSuccessRate(Math.round(overallSuccessRate * 100.0) / 100.0);

        // Calculate total breeding attempts
        int totalBreedingAttempts = categoryPerformance.values().stream()
                .mapToInt(BreedingPerformanceMetrics::getTotalBreedingAttempts)
                .sum();
        report.setTotalBreedingAttempts(totalBreedingAttempts);

        return report;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPER METHODS
    // ─────────────────────────────────────────────────────────────────────────────

    private void calculateConceptionMetrics(List<LivestockBreeding> breedings,
                                            BreedingPerformanceMetrics metrics) {
        // Calculate average conception time (days between breeding and confirmation)
        List<Integer> conceptionTimes = new ArrayList<>();

        for (LivestockBreeding breeding : breedings) {
            if (breeding.getExpectedPregnancyCheckDate() != null
                    && breeding.getBreedingDate() != null
                    && (LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(breeding.getStatus())
                    || LivestockBreeding.STATUS_COMPLETED.equals(breeding.getStatus()))) {

                int days = (int) ChronoUnit.DAYS.between(
                        breeding.getBreedingDate(),
                        breeding.getExpectedPregnancyCheckDate()
                );
                conceptionTimes.add(days);
            }
        }

        if (!conceptionTimes.isEmpty()) {
            double avg = conceptionTimes.stream().mapToInt(Integer::intValue).average().orElse(0);
            metrics.setAverageConceptionDays(Math.round(avg * 100.0) / 100.0);
        }
    }

    private void calculateBreedingInterval(List<LivestockBreeding> breedings,
                                           BreedingPerformanceMetrics metrics) {
        // Calculate average time between breedings
        List<LocalDate> breedingDates = breedings.stream()
                .map(LivestockBreeding::getBreedingDate)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());

        if (breedingDates.size() >= 2) {
            List<Integer> intervals = new ArrayList<>();
            for (int i = 1; i < breedingDates.size(); i++) {
                int days = (int) ChronoUnit.DAYS.between(
                        breedingDates.get(i - 1),
                        breedingDates.get(i)
                );
                intervals.add(days);
            }

            double avg = intervals.stream().mapToInt(Integer::intValue).average().orElse(0);
            metrics.setAverageBreedingIntervalDays(Math.round(avg * 100.0) / 100.0);
        }
    }

    private PregnancyTrackingDto createPregnancyDto(Livestock animal, LivestockBreeding breeding) {
        PregnancyTrackingDto dto = new PregnancyTrackingDto();
        dto.setAnimalId(animal.getId());
        dto.setTagNumber(animal.getTagNumber());
        dto.setCategoryName(animal.getLivestockCategory() != null
                ? animal.getLivestockCategory().getName() : "Unknown");
        dto.setConceptionDate(breeding.getBreedingDate());
        dto.setExpectedDueDate(breeding.getExpectedDueDate());
        dto.setBreedingMethod(breeding.getBreedingMethod());
        dto.setStatus(breeding.getStatus());

        if (breeding.getExpectedDueDate() != null) {
            LocalDate today = LocalDate.now();
            long daysRemaining = ChronoUnit.DAYS.between(today, breeding.getExpectedDueDate());
            dto.setDaysRemaining((int) daysRemaining);

            // Calculate gestation progress
            if (breeding.getBreedingDate() != null) {
                long totalDays = ChronoUnit.DAYS.between(breeding.getBreedingDate(), breeding.getExpectedDueDate());
                long elapsed = ChronoUnit.DAYS.between(breeding.getBreedingDate(), today);
                if (totalDays > 0) {
                    int progress = (int) Math.min(100, Math.round((double) elapsed / totalDays * 100));
                    dto.setGestationProgress(progress);
                }
            }
        }

        // Get pregnancy check date
        dto.setPregnancyCheckDate(breeding.getExpectedPregnancyCheckDate());

        return dto;
    }

    private PregnancyTrackingDto createPregnancyDtoFromLivestock(Livestock animal) {
        PregnancyTrackingDto dto = new PregnancyTrackingDto();
        dto.setAnimalId(animal.getId());
        dto.setTagNumber(animal.getTagNumber());
        dto.setCategoryName(animal.getLivestockCategory() != null
                ? animal.getLivestockCategory().getName() : "Unknown");
        dto.setConceptionDate(animal.getConceptionDate());
        dto.setExpectedDueDate(animal.getExpectedDueDate());
        dto.setStatus("PREGNANT (from livestock record)");
        dto.setBreedingMethod("UNKNOWN");

        if (animal.getExpectedDueDate() != null) {
            LocalDate today = LocalDate.now();
            long daysRemaining = ChronoUnit.DAYS.between(today, animal.getExpectedDueDate());
            dto.setDaysRemaining((int) daysRemaining);
        }

        return dto;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // INNER CLASSES (DTOs)
    // ─────────────────────────────────────────────────────────────────────────────

    public static class BreedingPerformanceMetrics {
        private UUID livestockId;
        private String categoryName;
        private int animalCount;
        private int totalBreedingAttempts;
        private int confirmedPregnancies;
        private int completedPregnancies;
        private int failedAttempts;
        private int totalBirths;
        private int totalOffspringProduced;
        private double successRate;
        private double averageOffspringPerBirth;
        private double averageConceptionDays;
        private double averageBreedingIntervalDays;

        // Getters and Setters
        public UUID getLivestockId() { return livestockId; }
        public void setLivestockId(UUID livestockId) { this.livestockId = livestockId; }

        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

        public int getAnimalCount() { return animalCount; }
        public void setAnimalCount(int animalCount) { this.animalCount = animalCount; }

        public int getTotalBreedingAttempts() { return totalBreedingAttempts; }
        public void setTotalBreedingAttempts(int totalBreedingAttempts) {
            this.totalBreedingAttempts = totalBreedingAttempts;
        }

        public int getConfirmedPregnancies() { return confirmedPregnancies; }
        public void setConfirmedPregnancies(int confirmedPregnancies) {
            this.confirmedPregnancies = confirmedPregnancies;
        }

        public int getCompletedPregnancies() { return completedPregnancies; }
        public void setCompletedPregnancies(int completedPregnancies) {
            this.completedPregnancies = completedPregnancies;
        }

        public int getFailedAttempts() { return failedAttempts; }
        public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }

        public int getTotalBirths() { return totalBirths; }
        public void setTotalBirths(int totalBirths) { this.totalBirths = totalBirths; }

        public int getTotalOffspringProduced() { return totalOffspringProduced; }
        public void setTotalOffspringProduced(int totalOffspringProduced) {
            this.totalOffspringProduced = totalOffspringProduced;
        }

        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }

        public double getAverageOffspringPerBirth() { return averageOffspringPerBirth; }
        public void setAverageOffspringPerBirth(double averageOffspringPerBirth) {
            this.averageOffspringPerBirth = averageOffspringPerBirth;
        }

        public double getAverageConceptionDays() { return averageConceptionDays; }
        public void setAverageConceptionDays(double averageConceptionDays) {
            this.averageConceptionDays = averageConceptionDays;
        }

        public double getAverageBreedingIntervalDays() { return averageBreedingIntervalDays; }
        public void setAverageBreedingIntervalDays(double averageBreedingIntervalDays) {
            this.averageBreedingIntervalDays = averageBreedingIntervalDays;
        }
    }

    public static class PregnancyTrackingDto {
        private UUID animalId;
        private String tagNumber;
        private String categoryName;
        private LocalDate conceptionDate;
        private LocalDate expectedDueDate;
        private LocalDate pregnancyCheckDate;
        private String breedingMethod;
        private String status;
        private int daysRemaining;
        private int daysUntilDue;
        private int gestationProgress;

        // Getters and Setters
        public UUID getAnimalId() { return animalId; }
        public void setAnimalId(UUID animalId) { this.animalId = animalId; }

        public String getTagNumber() { return tagNumber; }
        public void setTagNumber(String tagNumber) { this.tagNumber = tagNumber; }

        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

        public LocalDate getConceptionDate() { return conceptionDate; }
        public void setConceptionDate(LocalDate conceptionDate) { this.conceptionDate = conceptionDate; }

        public LocalDate getExpectedDueDate() { return expectedDueDate; }
        public void setExpectedDueDate(LocalDate expectedDueDate) { this.expectedDueDate = expectedDueDate; }

        public LocalDate getPregnancyCheckDate() { return pregnancyCheckDate; }
        public void setPregnancyCheckDate(LocalDate pregnancyCheckDate) {
            this.pregnancyCheckDate = pregnancyCheckDate;
        }

        public String getBreedingMethod() { return breedingMethod; }
        public void setBreedingMethod(String breedingMethod) { this.breedingMethod = breedingMethod; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getDaysRemaining() { return daysRemaining; }
        public void setDaysRemaining(int daysRemaining) { this.daysRemaining = daysRemaining; }

        public int getDaysUntilDue() { return daysUntilDue; }
        public void setDaysUntilDue(int daysUntilDue) { this.daysUntilDue = daysUntilDue; }

        public int getGestationProgress() { return gestationProgress; }
        public void setGestationProgress(int gestationProgress) { this.gestationProgress = gestationProgress; }
    }

    public static class BirthingReportDto {
        private LocalDate startDate;
        private LocalDate endDate;
        private int totalBirths;
        private int totalOffspring;
        private int liveOffspring;
        private int stillborn;
        private double averageOffspringPerBirth;
        private Map<String, Integer> birthsByCategory = new HashMap<>();
        private Map<String, Integer> offspringByGender = new HashMap<>();

        // Getters and Setters
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

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

        public Map<String, Integer> getBirthsByCategory() { return birthsByCategory; }
        public void setBirthsByCategory(Map<String, Integer> birthsByCategory) {
            this.birthsByCategory = birthsByCategory;
        }

        public Map<String, Integer> getOffspringByGender() { return offspringByGender; }
        public void setOffspringByGender(Map<String, Integer> offspringByGender) {
            this.offspringByGender = offspringByGender;
        }
    }

    public static class CalvingIntervalDto {
        private UUID livestockId;
        private String tagNumber;
        private String categoryName;
        private int numberOfBirths;
        private double averageIntervalDays;
        private int minIntervalDays;
        private int maxIntervalDays;
        private List<Integer> intervals = new ArrayList<>();
        private boolean hasSufficientData;
        private String message;

        // Getters and Setters
        public UUID getLivestockId() { return livestockId; }
        public void setLivestockId(UUID livestockId) { this.livestockId = livestockId; }

        public String getTagNumber() { return tagNumber; }
        public void setTagNumber(String tagNumber) { this.tagNumber = tagNumber; }

        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

        public int getNumberOfBirths() { return numberOfBirths; }
        public void setNumberOfBirths(int numberOfBirths) { this.numberOfBirths = numberOfBirths; }

        public double getAverageIntervalDays() { return averageIntervalDays; }
        public void setAverageIntervalDays(double averageIntervalDays) {
            this.averageIntervalDays = averageIntervalDays;
        }

        public int getMinIntervalDays() { return minIntervalDays; }
        public void setMinIntervalDays(int minIntervalDays) { this.minIntervalDays = minIntervalDays; }

        public int getMaxIntervalDays() { return maxIntervalDays; }
        public void setMaxIntervalDays(int maxIntervalDays) { this.maxIntervalDays = maxIntervalDays; }

        public List<Integer> getIntervals() { return intervals; }
        public void setIntervals(List<Integer> intervals) { this.intervals = intervals; }

        public boolean isHasSufficientData() { return hasSufficientData; }
        public void setHasSufficientData(boolean hasSufficientData) {
            this.hasSufficientData = hasSufficientData;
        }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class BreedingPerformanceReport {
        private LocalDate startDate;
        private LocalDate endDate;
        private BirthingReportDto birthingReport;
        private Map<String, BreedingPerformanceMetrics> categoryPerformance;
        private List<PregnancyTrackingDto> currentPregnancies;
        private List<PregnancyTrackingDto> upcomingBirths;
        private double averageOffspringPerBirth;
        private int totalActivePregnancies;
        private double overallSuccessRate;
        private int totalBreedingAttempts;

        // Getters and Setters
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

        public BirthingReportDto getBirthingReport() { return birthingReport; }
        public void setBirthingReport(BirthingReportDto birthingReport) {
            this.birthingReport = birthingReport;
        }

        public Map<String, BreedingPerformanceMetrics> getCategoryPerformance() {
            return categoryPerformance;
        }
        public void setCategoryPerformance(Map<String, BreedingPerformanceMetrics> categoryPerformance) {
            this.categoryPerformance = categoryPerformance;
        }

        public List<PregnancyTrackingDto> getCurrentPregnancies() { return currentPregnancies; }
        public void setCurrentPregnancies(List<PregnancyTrackingDto> currentPregnancies) {
            this.currentPregnancies = currentPregnancies;
        }

        public List<PregnancyTrackingDto> getUpcomingBirths() { return upcomingBirths; }
        public void setUpcomingBirths(List<PregnancyTrackingDto> upcomingBirths) {
            this.upcomingBirths = upcomingBirths;
        }

        public double getAverageOffspringPerBirth() { return averageOffspringPerBirth; }
        public void setAverageOffspringPerBirth(double averageOffspringPerBirth) {
            this.averageOffspringPerBirth = averageOffspringPerBirth;
        }

        public int getTotalActivePregnancies() { return totalActivePregnancies; }
        public void setTotalActivePregnancies(int totalActivePregnancies) {
            this.totalActivePregnancies = totalActivePregnancies;
        }

        public double getOverallSuccessRate() { return overallSuccessRate; }
        public void setOverallSuccessRate(double overallSuccessRate) {
            this.overallSuccessRate = overallSuccessRate;
        }

        public int getTotalBreedingAttempts() { return totalBreedingAttempts; }
        public void setTotalBreedingAttempts(int totalBreedingAttempts) {
            this.totalBreedingAttempts = totalBreedingAttempts;
        }
    }
}