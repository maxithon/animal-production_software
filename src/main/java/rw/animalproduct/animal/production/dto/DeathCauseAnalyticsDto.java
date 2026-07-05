package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * ENHANCEMENT for your existing Deaths Report.
 * Breaks deaths down by cause, by category, and by month — the standard
 * FAO "cause-specific mortality" view that a flat death list doesn't give you.
 */
@Data
public class DeathCauseAnalyticsDto {
    private int totalDeaths;
    private BigDecimal totalValueLost = BigDecimal.ZERO;

    private Map<String, Long> countByCause = new HashMap<>();
    private Map<String, Long> countByCategory = new HashMap<>();
    private Map<String, Long> countByGender = new HashMap<>();
    /** Key = "yyyy-MM" for a simple monthly trend line */
    private Map<String, Long> countByMonth = new HashMap<>();
}
