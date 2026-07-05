package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class HerdMovementReportDto {

    private LocalDate fromDate;
    private LocalDate toDate;

    private int openingCount;
    private BigDecimal openingValue = BigDecimal.ZERO;

    private int bornCount;
    private BigDecimal bornValue = BigDecimal.ZERO;

    private int purchasedCount;
    private BigDecimal purchasedValue = BigDecimal.ZERO;

    private int donatedCount;
    private BigDecimal donatedValue = BigDecimal.ZERO;

    private int soldCount;
    private BigDecimal soldRevenue = BigDecimal.ZERO;

    private int deadCount;
    private BigDecimal deadValueLoss = BigDecimal.ZERO;

    private int closingCount;
    private BigDecimal closingValue = BigDecimal.ZERO;

    /** Total closing herd size expressed in FAO Tropical Livestock Units, across all categories. */
    private BigDecimal totalClosingTLU = BigDecimal.ZERO;

    private List<CategoryMovementRow> byCategory = new ArrayList<>();

    /** FAO-style herd structure: closing stock broken down by category + sex/age class. */
    private List<HerdStructureRow> herdStructure = new ArrayList<>();

    // ── Reconciliation ──
    public int getExpectedClosingCount() {
        return openingCount + bornCount + purchasedCount + donatedCount - soldCount - deadCount;
    }

    public boolean isReconciled() {
        return getExpectedClosingCount() == closingCount;
    }

    public int getTotalIn() {
        return bornCount + purchasedCount + donatedCount;
    }

    public int getTotalOut() {
        return soldCount + deadCount;
    }

    public int getNetChange() {
        return getTotalIn() - getTotalOut();
    }

    public BigDecimal getTotalInValue() {
        return bornValue.add(purchasedValue).add(donatedValue);
    }

    public BigDecimal getTotalOutValue() {
        return soldRevenue.add(deadValueLoss);
    }

    public BigDecimal getNetFinancialChange() {
        return getTotalInValue().subtract(getTotalOutValue());
    }

    // ── FAO standard rate indicators ──

    /** Deaths as a % of opening stock — standard FAO/national livestock survey mortality indicator. */
    public double getMortalityRatePercent() {
        if (openingCount == 0) return 0.0;
        return round1(deadCount * 100.0 / openingCount);
    }

    /** (Sold + Died) as a % of opening stock — standard FAO offtake rate. */
    public double getOfftakeRatePercent() {
        if (openingCount == 0) return 0.0;
        return round1((soldCount + deadCount) * 100.0 / openingCount);
    }

    /** Net herd size change over the period as a % of opening stock. */
    public double getHerdGrowthRatePercent() {
        if (openingCount == 0) return closingCount > 0 ? 100.0 : 0.0;
        return round1((closingCount - openingCount) * 100.0 / openingCount);
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}