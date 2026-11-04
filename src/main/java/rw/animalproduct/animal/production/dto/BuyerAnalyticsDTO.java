package rw.animalproduct.animal.production.dto;

import rw.animalproduct.animal.production.entity.Buyer;
import java.math.BigDecimal;

public class BuyerAnalyticsDTO {
    private Buyer buyer;
    private long totalAnimalsPurchased;
    private BigDecimal totalSpent;
    private BigDecimal averagePricePaid;

    public BuyerAnalyticsDTO() {
    }

    public BuyerAnalyticsDTO(Buyer buyer, long totalAnimalsPurchased,
                             BigDecimal totalSpent, BigDecimal averagePricePaid) {
        this.buyer = buyer;
        this.totalAnimalsPurchased = totalAnimalsPurchased;
        this.totalSpent = totalSpent;
        this.averagePricePaid = averagePricePaid;
    }

    public Buyer getBuyer() {
        return buyer;
    }

    public void setBuyer(Buyer buyer) {
        this.buyer = buyer;
    }

    public long getTotalAnimalsPurchased() {
        return totalAnimalsPurchased;
    }

    public void setTotalAnimalsPurchased(long totalAnimalsPurchased) {
        this.totalAnimalsPurchased = totalAnimalsPurchased;
    }

    public BigDecimal getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(BigDecimal totalSpent) {
        this.totalSpent = totalSpent;
    }

    public BigDecimal getAveragePricePaid() {
        return averagePricePaid;
    }

    public void setAveragePricePaid(BigDecimal averagePricePaid) {
        this.averagePricePaid = averagePricePaid;
    }
}