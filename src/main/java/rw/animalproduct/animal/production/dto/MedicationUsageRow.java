package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MedicationUsageRow {
    private String medicationId;
    private String medicationName;
    private String genericName;
    /** ANTIBIOTIC, ANTIPARASITIC, DEWORMER, VACCINE, VITAMIN, ANTI_INFLAMMATORY, ... */
    private String category;
    private String manufacturer;

    private int timesUsed;
    private BigDecimal totalCost = BigDecimal.ZERO;
    private int distinctAnimalsTreated;

    public BigDecimal getAverageCostPerUse() {
        if (timesUsed == 0) return BigDecimal.ZERO;
        return totalCost.divide(BigDecimal.valueOf(timesUsed), 0, java.math.RoundingMode.HALF_UP);
    }
}
