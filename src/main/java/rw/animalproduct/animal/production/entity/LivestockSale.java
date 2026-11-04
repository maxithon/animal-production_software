// ================================================================
// LivestockSale.java - Enhanced entity with separate statuses, audit trail, and auto-calculation
// ================================================================
package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "livestock_sales")
public class LivestockSale {

    // SALE STATUS Constants (separate from payment status)
    public static final String SALE_DRAFT = "DRAFT";
    public static final String SALE_CONFIRMED = "CONFIRMED";
    public static final String SALE_CANCELLED = "CANCELLED";
    public static final String SALE_REFUNDED = "REFUNDED";

    // PAYMENT STATUS Constants
    public static final String PAYMENT_UNPAID = "UNPAID";
    public static final String PAYMENT_PARTIAL = "PARTIAL";
    public static final String PAYMENT_PAID = "PAID";

    // Payment Method Constants
    public static final String METHOD_CASH = "CASH";
    public static final String METHOD_BANK_TRANSFER = "BANK_TRANSFER";
    public static final String METHOD_MOBILE_MONEY = "MOBILE_MONEY";
    public static final String METHOD_CHEQUE = "CHEQUE";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "sale_reference", length = 50, unique = true, nullable = false)
    private String saleReference;

    @NotNull(message = "Sale date is required")
    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "sale_location")
    private String saleLocation;

    @DecimalMin(value = "0.0", message = "Sale price cannot be negative")
    @Column(name = "sale_price", precision = 12, scale = 2)
    private BigDecimal salePrice;

    @DecimalMin(value = "0.0", message = "Acquisition cost cannot be negative")
    @Column(name = "acquisition_cost", precision = 12, scale = 2)
    private BigDecimal acquisitionCost;

    @DecimalMin(value = "0.0", message = "Amount paid cannot be negative")
    @Column(name = "amount_paid", precision = 12, scale = 2)
    private BigDecimal amountPaid;

    // SALE STATUS (DRAFT, CONFIRMED, CANCELLED, REFUNDED)
    @Column(name = "sale_status", length = 30)
    private String saleStatus;

    // PAYMENT STATUS (UNPAID, PARTIAL, PAID) - AUTO-CALCULATED
    @Column(name = "payment_status", length = 30)
    private String paymentStatus;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "sale_reason", columnDefinition = "TEXT")
    private String saleReason;

    // ─── Audit Trail Fields ────────────────────────────────────────
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "user_id")
    private Users createdBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @ManyToOne
    @JoinColumn(name = "confirmed_by", referencedColumnName = "user_id")
    private Users confirmedBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @ManyToOne
    @JoinColumn(name = "cancelled_by", referencedColumnName = "user_id")
    private Users cancelledBy;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @ManyToOne
    @JoinColumn(name = "refunded_by", referencedColumnName = "user_id")
    private Users refundedBy;

    @Column(name = "refund_reason")
    private String refundReason;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @ManyToOne
    @JoinColumn(name = "livestock_id", referencedColumnName = "id", nullable = false)
    private Livestock livestock;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "buyer_id", referencedColumnName = "id")
    private Buyer buyer;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    // ─── Payment History (separate table) ─────────────────────────
    // This is managed via the LivestockSalePayment entity

    // ─── Transient fields for form binding ────────────────────────
    @Transient
    private String livestockIdValue;

    @Transient
    private String buyerIdValue;

    @Transient
    private String createdByIdValue;

    @Transient
    private String confirmedByIdValue;

    // ─── Lifecycle Callbacks ──────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isDeleted == null) {
            isDeleted = false;
        }
        if (amountPaid == null) {
            amountPaid = BigDecimal.ZERO;
        }
        if (saleStatus == null) {
            saleStatus = SALE_DRAFT;
        }
        if (saleReference == null || saleReference.isEmpty()) {
            saleReference = generateSaleReference();
        }
        // Validate sale date is not in future
        validateSaleDate();
        validatePaymentAmount();
        calculatePaymentStatus();
    }

    @PreUpdate
    protected void onUpdate() {
        validateSaleDate();
        validatePaymentAmount();
        calculatePaymentStatus();
    }

    // ─── Validations ──────────────────────────────────────────────

    private void validateSaleDate() {
        if (saleDate != null && saleDate.isAfter(LocalDate.now())) {
            throw new IllegalStateException("Sale date cannot be in the future.");
        }
    }

    private void validatePaymentAmount() {
        if (salePrice != null && amountPaid != null) {
            if (amountPaid.compareTo(salePrice) > 0) {
                throw new IllegalStateException(
                        "Amount paid (" + amountPaid +
                                ") cannot exceed sale price (" + salePrice + ")"
                );
            }
        }
    }

    private String generateSaleReference() {
        String year = String.valueOf(LocalDate.now().getYear());
        String sequence = String.format("%04d", (int) (Math.random() * 10000));
        return "SAL-" + year + "-" + sequence;
    }

    /**
     * Auto-calculate payment status based on amountPaid vs salePrice
     * Does NOT override CANCELLED or REFUNDED sale statuses
     */
    private void calculatePaymentStatus() {
        // Only calculate if sale is not cancelled or refunded
        if (SALE_CANCELLED.equals(this.saleStatus) || SALE_REFUNDED.equals(this.saleStatus)) {
            return;
        }

        if (salePrice == null || amountPaid == null) {
            this.paymentStatus = PAYMENT_UNPAID;
            return;
        }

        int comparison = amountPaid.compareTo(salePrice);
        if (comparison >= 0) {
            this.paymentStatus = PAYMENT_PAID;
        } else if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            this.paymentStatus = PAYMENT_PARTIAL;
        } else {
            this.paymentStatus = PAYMENT_UNPAID;
        }
    }

    // ─── Getters and Setters ─────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSaleReference() { return saleReference; }
    public void setSaleReference(String saleReference) { this.saleReference = saleReference; }

    public LocalDate getSaleDate() { return saleDate; }
    public void setSaleDate(LocalDate saleDate) { this.saleDate = saleDate; }

    public String getSaleLocation() { return saleLocation; }
    public void setSaleLocation(String saleLocation) { this.saleLocation = saleLocation; }

    public BigDecimal getSalePrice() { return salePrice; }
    public void setSalePrice(BigDecimal salePrice) { this.salePrice = salePrice; }

    public BigDecimal getAcquisitionCost() { return acquisitionCost; }
    public void setAcquisitionCost(BigDecimal acquisitionCost) { this.acquisitionCost = acquisitionCost; }

    public BigDecimal getAmountPaid() { return amountPaid; }
    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid != null ? amountPaid : BigDecimal.ZERO;
    }

    public String getSaleStatus() { return saleStatus; }
    public void setSaleStatus(String saleStatus) { this.saleStatus = saleStatus; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public String getSaleReason() { return saleReason; }
    public void setSaleReason(String saleReason) { this.saleReason = saleReason; }

    // ─── Audit Getters/Setters ────────────────────────────────────

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Users getCreatedBy() { return createdBy; }
    public void setCreatedBy(Users createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }

    public Users getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Users confirmedBy) { this.confirmedBy = confirmedBy; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public Users getCancelledBy() { return cancelledBy; }
    public void setCancelledBy(Users cancelledBy) { this.cancelledBy = cancelledBy; }

    public String getCancellationReason() { return cancellationReason; }
    public void setCancellationReason(String cancellationReason) { this.cancellationReason = cancellationReason; }

    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }

    public Users getRefundedBy() { return refundedBy; }
    public void setRefundedBy(Users refundedBy) { this.refundedBy = refundedBy; }

    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Livestock getLivestock() { return livestock; }
    public void setLivestock(Livestock livestock) { this.livestock = livestock; }

    public Buyer getBuyer() { return buyer; }
    public void setBuyer(Buyer buyer) { this.buyer = buyer; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    // ─── Transient Helpers ────────────────────────────────────────

    @Transient
    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String livestockIdValue) { this.livestockIdValue = livestockIdValue; }

    @Transient
    public String getBuyerIdValue() { return buyerIdValue; }
    public void setBuyerIdValue(String buyerIdValue) { this.buyerIdValue = buyerIdValue; }

    @Transient
    public String getCreatedByIdValue() { return createdByIdValue; }
    public void setCreatedByIdValue(String createdByIdValue) { this.createdByIdValue = createdByIdValue; }

    @Transient
    public String getConfirmedByIdValue() { return confirmedByIdValue; }
    public void setConfirmedByIdValue(String confirmedByIdValue) { this.confirmedByIdValue = confirmedByIdValue; }

    @Transient
    public BigDecimal getBalance() {
        if (salePrice == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal paid = amountPaid != null ? amountPaid : BigDecimal.ZERO;
        return salePrice.subtract(paid).max(BigDecimal.ZERO);
    }

    @Transient
    public BigDecimal getGrossProfit() {
        if (salePrice != null && acquisitionCost != null) {
            return salePrice.subtract(acquisitionCost);
        }
        return null;
    }

    // ─── Status Badge Helpers ─────────────────────────────────────

    @Transient
    public String getSaleStatusBadgeClass() {
        return switch (saleStatus != null ? saleStatus : SALE_DRAFT) {
            case SALE_DRAFT -> "badge-draft";
            case SALE_CONFIRMED -> "badge-confirmed";
            case SALE_CANCELLED -> "badge-cancelled";
            case SALE_REFUNDED -> "badge-refunded";
            default -> "badge-secondary";
        };
    }

    @Transient
    public String getSaleStatusLabel() {
        return switch (saleStatus != null ? saleStatus : SALE_DRAFT) {
            case SALE_DRAFT -> "Draft";
            case SALE_CONFIRMED -> "Confirmed";
            case SALE_CANCELLED -> "Cancelled";
            case SALE_REFUNDED -> "Refunded";
            default -> "Unknown";
        };
    }

    @Transient
    public String getPaymentStatusBadgeClass() {
        return switch (paymentStatus != null ? paymentStatus : PAYMENT_UNPAID) {
            case PAYMENT_PAID -> "badge-paid";
            case PAYMENT_PARTIAL -> "badge-partial";
            case PAYMENT_UNPAID -> "badge-unpaid";
            default -> "badge-secondary";
        };
    }

    @Transient
    public String getPaymentStatusLabel() {
        return switch (paymentStatus != null ? paymentStatus : PAYMENT_UNPAID) {
            case PAYMENT_PAID -> "Paid";
            case PAYMENT_PARTIAL -> "Partial";
            case PAYMENT_UNPAID -> "Unpaid";
            default -> "Unknown";
        };
    }

    @Transient
    public boolean isConfirmable() {
        return SALE_DRAFT.equals(saleStatus);
    }

    @Transient
    public boolean isCancellable() {
        return !SALE_CANCELLED.equals(saleStatus) &&
                !SALE_REFUNDED.equals(saleStatus);
    }

    @Transient
    public boolean isRefundable() {
        return SALE_CONFIRMED.equals(saleStatus) &&
                (PAYMENT_PAID.equals(paymentStatus) || PAYMENT_PARTIAL.equals(paymentStatus));
    }

    @Transient
    public boolean isEditable() {
        return SALE_DRAFT.equals(saleStatus);
    }

    @Transient
    public boolean isPaid() {
        return PAYMENT_PAID.equals(paymentStatus);
    }
}