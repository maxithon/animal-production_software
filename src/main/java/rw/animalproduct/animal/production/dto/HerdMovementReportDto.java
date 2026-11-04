package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Data carrier for the Herd Movement / Reconciliation report.
 */
@Data
public class HerdMovementReportDto {

    // ── Period ──────────────────────────────────────────────────────────
    private LocalDate fromDate;
    private LocalDate toDate;
    private String periodLabel;

    // ── Counts ──────────────────────────────────────────────────────────
    private int openingCount;
    private int bornCount;
    private int purchasedCount;
    private int donatedCount;
    private int transferredInCount;
    private int soldCount;
    private int deadCount;
    private int transferredOutCount;
    private int closingCount;

    // ── Values ──────────────────────────────────────────────────────────
    private BigDecimal openingValue = BigDecimal.ZERO;
    private BigDecimal bornValue = BigDecimal.ZERO;
    private BigDecimal purchasedValue = BigDecimal.ZERO;
    private BigDecimal donatedValue = BigDecimal.ZERO;
    private BigDecimal transferredInValue = BigDecimal.ZERO;
    private BigDecimal soldRevenue = BigDecimal.ZERO;
    private BigDecimal deadValueLoss = BigDecimal.ZERO;
    private BigDecimal transferredOutValue = BigDecimal.ZERO;
    private BigDecimal closingValue = BigDecimal.ZERO;

    /** FAO Tropical Livestock Units across all categories (age-weighted). */
    private BigDecimal totalClosingTLU = BigDecimal.ZERO;

    // ── Category breakdown ──────────────────────────────────────────────
    private List<CategoryMovementRow> byCategory = new ArrayList<>();

    /** FAO-style herd structure: closing stock broken down by category + sex/age. */
    private List<HerdStructureRow> herdStructure = new ArrayList<>();

    // ── Previous-period comparison ──────────────────────────────────────
    private PreviousPeriodSummary previous = new PreviousPeriodSummary();

    // ── Data-quality flags ──────────────────────────────────────────────
    private List<DataQualityFlag> dataQualityFlags = new ArrayList<>();

    // ── FAO benchmark classification ────────────────────────────────────
    private String mortalityBenchmark = "good";
    private String offtakeBenchmark = "good";
    private String growthBenchmark = "good";

    // ═════════════════════════════════════════════════════════════════════
    //  DERIVED — count reconciliation
    // ═════════════════════════════════════════════════════════════════════

    public int getExpectedClosingCount() {
        return openingCount + bornCount + purchasedCount + donatedCount + transferredInCount
                - soldCount - deadCount - transferredOutCount;
    }

    public boolean isReconciled() {
        return getExpectedClosingCount() == closingCount;
    }

    public int getReconciliationGap() {
        return closingCount - getExpectedClosingCount();
    }

    public int getTotalIn() {
        return bornCount + purchasedCount + donatedCount + transferredInCount;
    }

    public int getTotalOut() {
        return soldCount + deadCount + transferredOutCount;
    }

    public int getNetChange() {
        return getTotalIn() - getTotalOut();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DERIVED — value reconciliation
    // ═════════════════════════════════════════════════════════════════════

    public BigDecimal getTotalInValue() {
        return nz(bornValue).add(nz(purchasedValue)).add(nz(donatedValue)).add(nz(transferredInValue));
    }

    public BigDecimal getTotalOutValue() {
        return nz(soldRevenue).add(nz(deadValueLoss)).add(nz(transferredOutValue));
    }

    public BigDecimal getNetFinancialChange() {
        return getTotalInValue().subtract(getTotalOutValue());
    }

    public BigDecimal getExpectedClosingValue() {
        return nz(openingValue).add(getTotalInValue()).subtract(getTotalOutValue());
    }

    public BigDecimal getValueReconciliationGap() {
        return nz(closingValue).subtract(getExpectedClosingValue());
    }

    public boolean isValueReconciled() {
        return getValueReconciliationGap().abs().compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    // ═════════════════════════════════════════════════════════════════════
    //  DERIVED — FAO indicators (delegates to LivestockMetrics)
    // ═════════════════════════════════════════════════════════════════════

    public double getMortalityRatePercent() {
        return rw.animalproduct.animal.production.util.LivestockMetrics
                .mortalityRate(deadCount, openingCount);
    }

    public double getOfftakeRatePercent() {
        return rw.animalproduct.animal.production.util.LivestockMetrics
                .offtakeRate(soldCount, deadCount, openingCount);
    }

    public double getHerdGrowthRatePercent() {
        return rw.animalproduct.animal.production.util.LivestockMetrics
                .herdGrowthRate(closingCount, openingCount);
    }

    public double getBirthRatePercent() {
        return rw.animalproduct.animal.production.util.LivestockMetrics
                .birthRate(bornCount, openingCount);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  NESTED TYPES
    // ═════════════════════════════════════════════════════════════════════

    @Data
    public static class PreviousPeriodSummary {
        private LocalDate fromDate;
        private LocalDate toDate;
        private int openingCount;
        private int closingCount;
        private int bornCount;
        private int soldCount;
        private int deadCount;
        private BigDecimal closingValue = BigDecimal.ZERO;
        private BigDecimal soldRevenue = BigDecimal.ZERO;
        private BigDecimal deadValueLoss = BigDecimal.ZERO;
        private double mortalityRatePercent;
        private double offtakeRatePercent;

        public int getNetChange() {
            return closingCount - openingCount;
        }
    }

    @Data
    public static class DataQualityFlag {
        private String severity;   // "WARN" | "ERROR"
        private String message;
        private String context;
    }
}