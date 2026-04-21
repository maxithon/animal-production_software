package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "livestock_sales")
public class LivestockSale {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull(message = "Sale date is required")
    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "sale_location")
    private String saleLocation;

    @Column(name = "sale_price", precision = 12, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "sale_reason", columnDefinition = "TEXT")
    private String saleReason;

    @ManyToOne
    @JoinColumn(name = "livestock_id", referencedColumnName = "id", nullable = false)
    private Livestock livestock;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "buyer_id", referencedColumnName = "id")
    private Buyer buyer;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "user_id")
    private Users createdBy;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Transient
    private String livestockIdValue;

    @Transient
    private String buyerIdValue;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public LivestockSale() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LocalDate getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDate saleDate) { this.saleDate = saleDate; }

    public String getSaleLocation() { return saleLocation; }
    public void setSaleLocation(String saleLocation) { this.saleLocation = saleLocation; }

    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }

    public String getSaleReason() { return saleReason; }
    public void setSaleReason(String saleReason) { this.saleReason = saleReason; }

    public Livestock getLivestock() { return livestock; }
    public void setLivestock(Livestock livestock) { this.livestock = livestock; }

    public Buyer getBuyer() { return buyer; }
    public void setBuyer(Buyer buyer) { this.buyer = buyer; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Users getCreatedBy() { return createdBy; }
    public void setCreatedBy(Users createdBy) { this.createdBy = createdBy; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String livestockIdValue) { this.livestockIdValue = livestockIdValue; }

    public String getBuyerIdValue() { return buyerIdValue; }
    public void setBuyerIdValue(String buyerIdValue) { this.buyerIdValue = buyerIdValue; }
}
