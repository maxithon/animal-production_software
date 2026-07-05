package rw.animalproduct.animal.production.dto;

import lombok.Data;

/**
 * ENHANCEMENT for your existing Birth Report.
 * Adds standard FAO reproduction indicators the current report is missing:
 * average litter size, sex ratio at birth, and average birth interval.
 */
@Data
public class BirthPerformanceAnalyticsDto {
    private int totalBirthEvents;
    private int totalOffspring;
    private double averageLitterSize;

    private long maleOffspringCount;
    private long femaleOffspringCount;

    /** Females per 100 males — standard sex-ratio-at-birth expression. */
    public double getSexRatioFemalesPer100Males() {
        if (maleOffspringCount == 0) return femaleOffspringCount > 0 ? 100.0 : 0.0;
        return Math.round(femaleOffspringCount * 10000.0 / maleOffspringCount) / 100.0;
    }

    /** Average days between successive births of the same dam (kidding/calving interval). */
    private double averageBirthIntervalDays;
}
