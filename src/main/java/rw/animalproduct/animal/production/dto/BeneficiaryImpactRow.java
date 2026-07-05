package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row in the beneficiary impact report — represents a single
 * beneficiary's herd growth over the reporting period.
 */
@Data
public class BeneficiaryImpactRow {

    private UUID beneficiaryId;
    private String beneficiaryName;
    private String locationName;

    private int originalAnimalCount;
    private BigDecimal originalValue = BigDecimal.ZERO;

    private int currentAnimalCount;
    private BigDecimal currentValue = BigDecimal.ZERO;

    private int bornCount;
    private int soldCount;
    private BigDecimal saleRevenue = BigDecimal.ZERO;
    private int deadCount;

    public BigDecimal getNetGrowth() {
        return currentValue.add(saleRevenue).subtract(originalValue);
    }

    public double getAssetGrowthRatePercent() {
        if (originalValue == null || originalValue.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        BigDecimal netGrowth = getNetGrowth();
        return netGrowth
                .divide(originalValue, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }
}