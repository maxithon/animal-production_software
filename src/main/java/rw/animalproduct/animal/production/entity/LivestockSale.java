package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "livestock_sales")
public class LivestockSale {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "sale_reason")
    private String saleReason;

    @Column(name = "sale_price")
    private BigDecimal salePrice;

    @NotNull(message = "Sale date is required")
    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "sale_location")
    private String saleLocation;

    @ManyToOne
    @JoinColumn(name = "livestock_id", referencedColumnName = "id", nullable = false)
    private Livestock livestock;

    @Transient
    private String livestockIdValue;

    public LivestockSale() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSaleReason() { return saleReason; }
    public void setSaleReason(String saleReason) { this.saleReason = saleReason; }

    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }

    public LocalDate getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDate saleDate) { this.saleDate = saleDate; }

    public String getSaleLocation() { return saleLocation; }
    public void setSaleLocation(String saleLocation) { this.saleLocation = saleLocation; }

    public Livestock getLivestock() { return livestock; }
    public void setLivestock(Livestock livestock) { this.livestock = livestock; }

    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String livestockIdValue) { this.livestockIdValue = livestockIdValue; }
}
