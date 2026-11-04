package rw.animalproduct.animal.production.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * All DTOs for the Veterinarian Activity Report.
 */
public final class VeterinarianActivityDtos {

    private VeterinarianActivityDtos() {}

    /* ============================================================
     *  TOP-LEVEL REPORT
     * ============================================================ */
    @Data
    public static class Report {
        private LocalDate fromDate;
        private LocalDate toDate;
        private LocalDate previousFromDate;
        private LocalDate previousToDate;

        private int activeVets;
        private int totalTreatmentsInPeriod;
        private int distinctAnimalsTreated;
        private int totalSickCasesInPeriod;
        private BigDecimal totalTreatmentCost = BigDecimal.ZERO;
        private BigDecimal avgCostPerAnimal;
        private BigDecimal avgCostPerTreatment;
        private int totalRecovered;
        private int totalDeaths;
        private int followUpsDue;
        private int followUpsOverdue;

        private double recoveryRatePercent;
        private double successRatePercent;
        private double caseClosureRatePercent;

        private Double treatmentsDeltaPercent;
        private Double costDeltaPercent;
        private Double recoveryRateDeltaPp;
        private Double distinctAnimalsDeltaPercent;

        private List<VetRow> rows = new ArrayList<>();
        private Outcome outcomes;
        private Analysis analysis;
        private List<FollowUp> casesRequiringAttention = new ArrayList<>();
        private List<FollowUp> followUpsDueList = new ArrayList<>();
        private Trends trends;
        private Headline mostImportantChange;
    }

    /* ============================================================
     *  PER-VET ROW
     * ============================================================ */
    @Data
    public static class VetRow {
        private String veterinarianId;
        private String fullName;
        private String licenseNumber;
        private String clinicName;
        private String specialization;
        private boolean active;

        private int treatmentRecords;
        private int distinctAnimalsTreated;
        private BigDecimal totalTreatmentCost = BigDecimal.ZERO;

        private int sickCasesAttended;
        private int sickCasesRecovered;
        private int sickCasesRecovering;
        private int sickCasesStillSick;
        private int sickCasesCritical;
        private int sickCasesDead;

        private int followUpsDue;
        private int followUpsOverdue;
        private int rankByTreatments;

        private Integer breedingsAssisted;
        private Integer abortionsAttended;

        public double getRecoveryRatePercent() {
            int completed = sickCasesRecovered + sickCasesDead;
            if (completed == 0) return 0.0;
            return round1(sickCasesRecovered * 100.0 / completed);
        }

        public double getSuccessRatePercent() {
            if (sickCasesAttended == 0) return 0.0;
            return round1((sickCasesRecovered + sickCasesRecovering) * 100.0 / sickCasesAttended);
        }

        public BigDecimal getAvgCostPerAnimal() {
            if (distinctAnimalsTreated == 0) return null;
            return totalTreatmentCost.divide(
                    BigDecimal.valueOf(distinctAnimalsTreated), 2, RoundingMode.HALF_UP);
        }

        private static double round1(double v) {
            return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }
    }

    /* ============================================================
     *  OUTCOMES
     * ============================================================ */
    @Data
    public static class Outcome {
        private int recovered;
        private int recovering;
        private int sick;
        private int critical;
        private int dead;

        private int total;
        private int completed;
        private int positiveProgress;

        private double recoveredPct, recoveringPct, sickPct, criticalPct, deadPct;
        private Double avgDaysToRecovery;
    }

    /* ============================================================
     *  ANALYSIS
     * ============================================================ */
    @Data
    public static class Analysis {
        private List<Dimension> byLivestockCategory = new ArrayList<>();
        private List<Dimension> byDisease = new ArrayList<>();
        private List<Dimension> byLocation = new ArrayList<>();
        private List<Dimension> topCostDrivers = new ArrayList<>();
        private List<Dimension> topDiseaseBurdens = new ArrayList<>();
    }

    @Data
    public static class Dimension {
        private String label;
        private int count;
        private int distinctAnimals;
        private BigDecimal totalCost = BigDecimal.ZERO;
        private BigDecimal avgCost;

        public Dimension() {}

        public Dimension(String label, long count, long distinctAnimals, BigDecimal totalCost) {
            this.label = label;
            this.count = (int) count;
            this.distinctAnimals = (int) distinctAnimals;
            this.totalCost = totalCost == null ? BigDecimal.ZERO : totalCost;
        }

        public void computeAvg() {
            this.avgCost = distinctAnimals == 0 ? null
                    : totalCost.divide(BigDecimal.valueOf(distinctAnimals), 2, RoundingMode.HALF_UP);
        }
    }

    /* ============================================================
     *  FOLLOW-UP
     * ============================================================ */
    @Data
    public static class FollowUp {
        private String priority;
        private String animalTag;
        private String animalId;
        private String veterinarianName;
        private String issue;
        private LocalDate dueDate;
        private long daysOverdue;
        private String status;
    }

    /* ============================================================
     *  TRENDS
     * ============================================================ */
    @Data
    public static class Trends {
        private String granularity = "DAY";
        private List<Point> treatments = new ArrayList<>();
        private List<Point> sickCases  = new ArrayList<>();
        private List<Point> costs      = new ArrayList<>();
        private List<Point> recovery   = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    public static class Point {
        private LocalDate bucket;
        private BigDecimal value;
    }

    /* ============================================================
     *  HEADLINE
     * ============================================================ */
    @Data
    public static class Headline {
        private String metricName;
        private String fromValue;
        private String toValue;
        private String direction;
        private String whyItMatters;
        private String recommendedAction;
    }

    /* ============================================================
     *  JPQL PROJECTION
     * ============================================================ */
    @Data
    @AllArgsConstructor
    public static class VetAggregate {
        private UUID id;
        private String firstName;
        private String lastName;
        private String licenseNumber;
        private String clinicName;
        private String specialization;
        private Boolean active;

        private long treatmentRecords;
        private long distinctAnimalsTreated;
        private BigDecimal totalTreatmentCost;

        private long sickCasesAttended;
        private long sickCasesRecovered;
        private long sickCasesRecovering;
        private long sickCasesStillSick;
        private long sickCasesCritical;
        private long sickCasesDead;
        private long followUpsDue;
        private long followUpsOverdue;
    }
}