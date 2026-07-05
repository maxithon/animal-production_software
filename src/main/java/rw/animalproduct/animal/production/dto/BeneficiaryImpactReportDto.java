package rw.animalproduct.animal.production.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
@Data
public class BeneficiaryImpactReportDto {
    private int totalBeneficiaries;
    private int totalOriginalAnimals;
    private BigDecimal totalOriginalValue = BigDecimal.ZERO;
    private int totalCurrentAnimals;
    private BigDecimal totalCurrentValue = BigDecimal.ZERO;
    private int totalBornCount;
    private int totalSoldCount;
    private BigDecimal totalSaleRevenue = BigDecimal.ZERO;
    private int totalDeadCount;
    private List<BeneficiaryImpactRow> rows = new ArrayList<>();
    public BigDecimal getTotalNetGrowth() {
        return totalCurrentValue.add(totalSaleRevenue).subtract(totalOriginalValue);
    }
    /** How many households have at least doubled their original herd value — a common program success threshold. */
    public long getHouseholdsDoubledCount() {
        return rows.stream()
                .filter(r -> r.getAssetGrowthRatePercent() >= 100.0)
                .count();
    }
}