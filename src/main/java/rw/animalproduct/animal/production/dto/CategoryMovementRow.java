package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One row per livestock category on the Herd Movement report.
 *
 * Reconciliation identity (FAO 2018):
 *   openingCount + bornCount + purchasedCount + donatedCount + transferredInCount
 *     − soldCount − deadCount − transferredOutCount
 *   = closingCount
 *
 * Value reconciliation uses the same shape with the *Value/*Revenue fields.
 */
@Data
public class CategoryMovementRow {

    // ── Identity ────────────────────────────────────────────────────────
    private String categoryName;

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

    // ── TLU ─────────────────────────────────────────────────────────────
    /** FAO Tropical Livestock Unit factor applied to this category (adult basis). */
    private double tluFactor;
    private BigDecimal closingTLU = BigDecimal.ZERO;

    // ═════════════════════════════════════════════════════════════════════
    //  DERIVED — reconciliation
    // ═════════════════════════════════════════════════════════════════════

    public int getExpectedClosingCount() {
        return openingCount + bornCount + purchasedCount + donatedCount + transferredInCount
                - soldCount - deadCount - transferredOutCount;
    }

    public int getReconciliationGap() {
        return closingCount - getExpectedClosingCount();
    }

    public boolean isReconciled() {
        return getReconciliationGap() == 0;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DERIVED — value metrics
    // ═════════════════════════════════════════════════════════════════════

    public BigDecimal getTotalInValue() {
        return nz(bornValue).add(nz(purchasedValue)).add(nz(donatedValue)).add(nz(transferredInValue));
    }

    public BigDecimal getTotalOutValue() {
        return nz(soldRevenue).add(nz(deadValueLoss)).add(nz(transferredOutValue));
    }

    /** Change in herd value from movements alone (excludes the opening balance). */
    public BigDecimal getNetValueChange() {
        return getTotalInValue().subtract(getTotalOutValue());
    }

    /** Expected closing value if the movement records are internally consistent. */
    public BigDecimal getExpectedClosingValue() {
        return nz(openingValue).add(getTotalInValue()).subtract(getTotalOutValue());
    }

    /** Difference between what the movement math predicts and what's on the books. */
    public BigDecimal getValueReconciliationGap() {
        return nz(closingValue).subtract(getExpectedClosingValue());
    }

    /** True when the value gap is within a rounding tolerance (RWF 100). */
    public boolean isValueReconciled() {
        return getValueReconciliationGap().abs().compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DERIVED — averages
    // ═════════════════════════════════════════════════════════════════════

    /** Average value per animal in the closing stock (0 if no animals). */
    public BigDecimal getAvgValuePerAnimal() {
        if (closingCount == 0) return BigDecimal.ZERO;
        return nz(closingValue).divide(BigDecimal.valueOf(closingCount), 0, RoundingMode.HALF_UP);
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Convenience
    // ═════════════════════════════════════════════════════════════════════

    public int getTotalIn() {
        return bornCount + purchasedCount + donatedCount + transferredInCount;
    }

    public int getTotalOut() {
        return soldCount + deadCount + transferredOutCount;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}