package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "buyers")
public class Buyer {

    @Id
    @GeneratedValue(generator = "UUID")
    @org.hibernate.annotations.GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Buyer name is required")
    @Size(min = 2, max = 100)
    @Column(name = "buyer_name", nullable = false, length = 100)
    private String buyerName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$")
    @Column(name = "buyer_phone", nullable = false, length = 20)
    private String buyerPhone;

    @Column(name = "buyer_address", length = 255)
    private String buyerAddress;

    @Size(min = 16, max = 16)
    @Column(name = "buyer_national_id", unique = true, length = 16)
    private String buyerNationalId;

    @Email
    @Column(name = "buyer_email", length = 100)
    private String buyerEmail;

    @Column(name = "buyer_type", length = 50)
    private String buyerType;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "user_id")
    private Users createdBy;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @OneToMany(mappedBy = "buyer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("saleDate DESC")
    private List<LivestockSale> sales = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public String getBuyerPhone() { return buyerPhone; }
    public void setBuyerPhone(String buyerPhone) { this.buyerPhone = buyerPhone; }

    public String getBuyerAddress() { return buyerAddress; }
    public void setBuyerAddress(String buyerAddress) { this.buyerAddress = buyerAddress; }

    public String getBuyerNationalId() { return buyerNationalId; }
    public void setBuyerNationalId(String buyerNationalId) { this.buyerNationalId = buyerNationalId; }

    public String getBuyerEmail() { return buyerEmail; }
    public void setBuyerEmail(String buyerEmail) { this.buyerEmail = buyerEmail; }

    public String getBuyerType() { return buyerType; }
    public void setBuyerType(String buyerType) { this.buyerType = buyerType; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public Users getCreatedBy() { return createdBy; }
    public void setCreatedBy(Users createdBy) { this.createdBy = createdBy; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public List<LivestockSale> getSales() { return sales; }
    public void setSales(List<LivestockSale> sales) { this.sales = sales; }

    @Transient
    public int getTotalPurchases() {
        return sales != null ? sales.size() : 0;
    }

    @Transient
    public String getDisplayName() {
        StringBuilder display = new StringBuilder(buyerName);
        if (buyerPhone != null && !buyerPhone.isEmpty()) {
            display.append(" (").append(buyerPhone).append(")");
        }
        return display.toString();
    }
}
