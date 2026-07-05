package rw.animalproduct.animal.production.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class LocationDistributionReportDto {
    private int totalLocations;
    private int totalAnimals;
    private BigDecimal totalValue = BigDecimal.ZERO;
    private int totalActiveAnimals;
    private int totalSickAnimals;
    private int totalSoldAnimals;
    private int totalDeadAnimals;
    private List<LocationDistributionRow> rows = new ArrayList<>();

    // Global category breakdown across ALL matching locations (not just current page),
    // sorted descending by count so the biggest categories show first.
    private Map<String, Long> categoryTotals = new LinkedHashMap<>();

    // Pagination metadata
    private int page = 0;
    private int size = 20;
    private long totalElements = 0;
    private int totalPages = 0;
    private boolean first = true;
    private boolean last = true;

    // FAO standard metrics
    private double livestockDensityPerLocation;
    private double averageValuePerAnimal;
    private double activeRate;
    private double sickRate;
}