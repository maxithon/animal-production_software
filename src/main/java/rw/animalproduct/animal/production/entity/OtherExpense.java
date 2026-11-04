package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * OtherExpense entity.
 *
 * Records any miscellaneous money spent on the livestock operation that
 * isn't already captured by a dedicated module (e.g. feed purchases, hired
 * labor, transport, equipment, repairs/maintenance, utilities, permits,
 * insurance, etc.). This feeds directly into the Financial Summary report
 * alongside herd value and health costs.
 *
 * Follows the same conventions used across the rest of the system:
 *  - UUID primary key via the modern GenerationType.UUID strategy
 *  - soft-delete (isDeleted) instead of hard delete, for audit history
 *  - createdBy links back to the acting Users record
 *  - PrePersist/PreUpdate timestamps + light normalization
 */
@Entity
@Table(name = "other_expenses")
public class OtherExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotNull(message = "Expense date is required")
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    /**
     * Kept as a plain String (not a JPA enum) to match the existing
     * pattern used for Buyer.buyerType — easy to extend from the UI
     * dropdown without a migration. Suggested values are enforced at
     * the form level: FEED, LABOR, TRANSPORT, FUEL, UTILITIES,
     * EQUIPMENT, MAINTENANCE, VETERINARY_SUPPLIES, RENT, INSURANCE,
     * PERMITS_LICENSES, OTHER.
     */
    @NotBlank(message = "Category is required")
    @Size(max = 50)
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @NotBlank(message = "Description is required")
    @Size(min = 3, max = 255, message = "Description must be between 3 and 255 characters")
    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 12, fraction = 2, message = "Amount format is invalid")
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Size(max = 30)
    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Size(max = 150)
    @Column(name = "paid_to", length = 150)
    private String paidTo;

    @Size(max = 100)
    @Column(name = "receipt_reference", length = 100)
    private String receiptReference;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Stored as a plain string (the Spring Security principal name) rather
     * than a @ManyToOne to Users. This sidesteps depending on whatever
     * Users' actual display-name field is called, and matches how the rest
     * of the app (e.g. BuyerController.getCurrentUsername()) already treats
     * "who did this" as a string for audit purposes, not a live FK join.
     */
    @Column(name = "created_by_username", length = 100)
    private String createdByUsername;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isDeleted == null) isDeleted = false;
        normalize();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        normalize();
    }

    private void normalize() {
        if (description != null) description = description.trim();
        if (paidTo != null) {
            paidTo = paidTo.trim();
            if (paidTo.isBlank()) paidTo = null;
        }
        if (receiptReference != null) {
            receiptReference = receiptReference.trim();
            if (receiptReference.isBlank()) receiptReference = null;
        }
        if (category != null) category = category.trim().toUpperCase();
    }

    // ── Getters / Setters ───────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaidTo() { return paidTo; }
    public void setPaidTo(String paidTo) { this.paidTo = paidTo; }

    public String getReceiptReference() { return receiptReference; }
    public void setReceiptReference(String receiptReference) { this.receiptReference = receiptReference; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public String getCreatedByUsername() { return createdByUsername; }
    public void setCreatedByUsername(String createdByUsername) { this.createdByUsername = createdByUsername; }

    // ── Derived / convenience fields ────────────────────────────────────

    @Transient
    public String getCategoryLabel() {
        if (category == null) return "—";
        String[] parts = category.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
