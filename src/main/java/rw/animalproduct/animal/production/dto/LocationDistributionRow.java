package rw.animalproduct.animal.production.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Data
public class LocationDistributionRow {
    private String locationId;
    private String locationName;
    private String locationCode; // FAO standard location code
    private String locationType; // Village, Sector, District, Province

    // Full administrative hierarchy (Province -> District -> Sector -> Cell -> Village)
    private String province;
    private String district;
    private String sector;
    private String cell;
    private String village;
    private String fullLocationPath; // e.g. "Kigali City › Gasabo › Remera › Nyabisindu › Mwokora"

    // Livestock metrics
    private int totalAnimals;
    private BigDecimal totalValue = BigDecimal.ZERO;
    private int beneficiaryCount;
    private int representativeCount;

    // Health status distribution (FAO standard)
    private int activeCount;
    private int sickCount;
    private int soldCount;
    private int deadCount;

    // FAO standard metrics
    private double healthRate;
    private double mortalityRate;
    private double productivityIndex;
    private double locationContributionPercentage;

    // Category distribution
    private Map<String, Long> byCategory = new HashMap<>();

    // Calculated fields
    public BigDecimal getAverageValuePerAnimal() {
        if (totalAnimals == 0) return BigDecimal.ZERO;
        return totalValue.divide(BigDecimal.valueOf(totalAnimals), 0, RoundingMode.HALF_UP);
    }

    public double getActivePercentage() {
        if (totalAnimals == 0) return 0.0;
        return (activeCount * 100.0) / totalAnimals;
    }

    public double getSickPercentage() {
        if (totalAnimals == 0) return 0.0;
        return (sickCount * 100.0) / totalAnimals;
    }
}