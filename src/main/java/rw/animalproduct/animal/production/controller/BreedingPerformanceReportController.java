package rw.animalproduct.animal.production.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import rw.animalproduct.animal.production.dto.BreedingMethodStatsRow;
import rw.animalproduct.animal.production.dto.BreedingPerformanceReportDto;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serves /livestock/breeding-performance-report — the HTML dashboard.
 *
 * This was missing entirely (the existing BreedingPerformanceController is a
 * @RestController under /api/breeding-performance and returns JSON), which is
 * why GET /livestock/breeding-performance-report fell through to Spring's
 * static-resource handler and produced a 404.
 */
@Controller
@RequestMapping("/livestock")
public class BreedingPerformanceReportController {

    private final LivestockBreedingRepository livestockBreedingRepository;

    public BreedingPerformanceReportController(LivestockBreedingRepository livestockBreedingRepository) {
        this.livestockBreedingRepository = livestockBreedingRepository;
    }

    @GetMapping("/breeding-performance-report")
    public String breedingPerformanceReport(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        LocalDate fromDate = (from != null) ? from : LocalDate.now().minusMonths(6);
        LocalDate toDate = (to != null) ? to : LocalDate.now();

        List<LivestockBreeding> records =
                livestockBreedingRepository.findByBreedingDateBetweenAndIsDeletedFalse(fromDate, toDate);

        BreedingPerformanceReportDto report = buildReport(records, fromDate, toDate);

        model.addAttribute("report", report);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);

        return "breeding-performance-report";
    }

    private BreedingPerformanceReportDto buildReport(List<LivestockBreeding> records,
                                                     LocalDate fromDate,
                                                     LocalDate toDate) {

        BreedingPerformanceReportDto report = new BreedingPerformanceReportDto();
        report.setFromDate(fromDate);
        report.setToDate(toDate);
        report.setTotalBreedingRecords(records.size());

        int confirmedPregnant = 0;
        int completed = 0;
        int pending = 0;
        int failed = 0;

        // On-time birth rate: NOTE — LivestockBreeding has no explicit "actualBirthDate"
        // field, so this uses updatedAt (the timestamp the record was last moved to
        // COMPLETED) as a proxy for the real birth date, within a +/-3 day tolerance
        // of expectedDueDate. If you add a proper actualBirthDate column later, swap
        // it in here for a more accurate figure.
        int onTime = 0;
        int completedWithDueDate = 0;

        // method -> [total, confirmedPregnant, completed, failed]
        Map<String, int[]> methodStats = new LinkedHashMap<>();

        for (LivestockBreeding b : records) {
            String status = b.getStatus();
            String method = (b.getBreedingMethod() == null || b.getBreedingMethod().isBlank())
                    ? "Unspecified" : b.getBreedingMethod();

            int[] stats = methodStats.computeIfAbsent(method, k -> new int[4]);
            stats[0]++; // total attempts

            if (LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(status)
                    || LivestockBreeding.STATUS_CONFIRMED.equals(status)) {
                confirmedPregnant++;
                stats[1]++;
            } else if (LivestockBreeding.STATUS_COMPLETED.equals(status)) {
                completed++;
                stats[2]++;

                if (b.getExpectedDueDate() != null && b.getUpdatedAt() != null) {
                    completedWithDueDate++;
                    LocalDate approxActual = b.getUpdatedAt().toLocalDate();
                    boolean withinWindow = !approxActual.isBefore(b.getExpectedDueDate().minusDays(3))
                            && !approxActual.isAfter(b.getExpectedDueDate().plusDays(3));
                    if (withinWindow) {
                        onTime++;
                    }
                }
            } else if (LivestockBreeding.STATUS_FAILED.equals(status)) {
                failed++;
                stats[3]++;
            } else {
                pending++;
            }
        }

        report.setConfirmedPregnant(confirmedPregnant);
        report.setCompletedBirths(completed);
        report.setPending(pending);
        report.setFailedOrLost(failed);
        report.setOnTimeBirthRatePercent(
                completedWithDueDate == 0 ? 0.0 : Math.round(onTime * 1000.0 / completedWithDueDate) / 10.0);

        // Average kidding/calving interval: days between successive COMPLETED
        // breeding dates for the same dam.
        Map<UUID, List<LocalDate>> completedDatesByDam = records.stream()
                .filter(b -> LivestockBreeding.STATUS_COMPLETED.equals(b.getStatus())
                        && b.getLivestock() != null
                        && b.getBreedingDate() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getLivestock().getId(),
                        Collectors.mapping(LivestockBreeding::getBreedingDate, Collectors.toList())));

        List<Long> intervals = new ArrayList<>();
        for (List<LocalDate> dates : completedDatesByDam.values()) {
            List<LocalDate> sorted = dates.stream()
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.toList());
            for (int i = 1; i < sorted.size(); i++) {
                intervals.add(ChronoUnit.DAYS.between(sorted.get(i - 1), sorted.get(i)));
            }
        }
        double avgInterval = intervals.stream().mapToLong(Long::longValue).average().orElse(0.0);
        report.setAverageKiddingIntervalDays(Math.round(avgInterval * 10.0) / 10.0);

        List<BreedingMethodStatsRow> byMethod = methodStats.entrySet().stream()
                .map(e -> new BreedingMethodStatsRow(
                        e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .sorted((a, c) -> Integer.compare(c.getTotalAttempts(), a.getTotalAttempts()))
                .collect(Collectors.toList());
        report.setByMethod(byMethod);

        return report;
    }
}
