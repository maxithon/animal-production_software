package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class BirthPerformanceAnalyticsDto {

    private int totalBirthEvents;
    private int totalOffspring;
    private double averageLitterSize;

    private long maleOffspringCount;
    private long femaleOffspringCount;

    public double getSexRatioFemalesPer100Males() {
        if (maleOffspringCount == 0) return femaleOffspringCount > 0 ? 100.0 : 0.0;
        return Math.round(femaleOffspringCount * 10000.0 / maleOffspringCount) / 100.0;
    }

    private double averageBirthIntervalDays;

    private Map<String, CategoryMetrics> byCategory = new LinkedHashMap<>();

    @Data
    public static class CategoryMetrics {
        private int totalBirthEvents;
        private int totalOffspring;
        private long maleOffspringCount;
        private long femaleOffspringCount;

        public double getAverageLitterSize() {
            return totalBirthEvents == 0 ? 0.0 : (double) totalOffspring / totalBirthEvents;
        }
    }
}