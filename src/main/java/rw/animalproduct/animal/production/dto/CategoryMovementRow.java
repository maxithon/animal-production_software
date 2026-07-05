package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CategoryMovementRow {

    private String categoryName;

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

    /** FAO Tropical Livestock Unit conversion factor for this species (e.g. 0.10 for goats). */
    private double tluFactor;

    /** Closing headcount converted to Tropical Livestock Units (closingCount * tluFactor). */
    private BigDecimal closingTLU = BigDecimal.ZERO;

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

    public BigDecimal getTotalInValue() {
        return bornValue.add(purchasedValue).add(donatedValue);
    }

    public BigDecimal getTotalOutValue() {
        return soldRevenue.add(deadValueLoss);
    }

    public BigDecimal getNetValueChange() {
        return getTotalInValue().subtract(getTotalOutValue());
    }

    public BigDecimal getAvgValuePerAnimal() {
        if (closingCount == 0) return BigDecimal.ZERO;
        return closingValue.divide(BigDecimal.valueOf(closingCount), 0, java.math.RoundingMode.HALF_UP);
    }
}