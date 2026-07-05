package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ENHANCEMENT for your existing Sales Report.
 * Add a BuyerRow per buyer + a sale-reason breakdown map — surfaces who is
 * actually buying your stock and why (COMMERCIAL vs restock, etc.), which
 * the current sales report doesn't show at all.
 */
@Data
public class SalesBuyerAnalyticsDto {

    @Data
    public static class BuyerRow {
        private String buyerId;       // null-safe: "Unrecorded / Walk-in" when buyer_id is null
        private String buyerName;
        private String buyerType;     // INDIVIDUAL, COMPANY, ...
        private int animalsBought;
        private BigDecimal totalSpent = BigDecimal.ZERO;

        public BigDecimal getAveragePricePaid() {
            if (animalsBought == 0) return BigDecimal.ZERO;
            return totalSpent.divide(BigDecimal.valueOf(animalsBought), 0, java.math.RoundingMode.HALF_UP);
        }
    }

    private List<BuyerRow> byBuyer = new ArrayList<>();
    private Map<String, Long> countBySaleReason = new HashMap<>();
    private Map<String, BigDecimal> revenueByCategory = new HashMap<>();
}
