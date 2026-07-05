package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * FAO-style reproductive performance indicators:
 *  - Conception/confirmation rate
 *  - Success rate by breeding method
 *  - Average kidding/calving interval (days between successive births of the same dam)
 *  - On-time birth rate (actual birth date vs expected_due_date)
 */
@Data
public class BreedingPerformanceReportDto {

    private LocalDate fromDate;
    private LocalDate toDate;

    private int totalBreedingRecords;
    private int confirmedPregnant;
    private int completedBirths;
    private int pending;
    private int failedOrLost; // e.g. NOT_PREGNANT / abandoned attempts

    private double averageKiddingIntervalDays;
    private double onTimeBirthRatePercent;

    private List<BreedingMethodStatsRow> byMethod = new ArrayList<>();

    public double getOverallConceptionRatePercent() {
        if (totalBreedingRecords == 0) return 0.0;
        return Math.round((confirmedPregnant + completedBirths) * 1000.0 / totalBreedingRecords) / 10.0;
    }
}
