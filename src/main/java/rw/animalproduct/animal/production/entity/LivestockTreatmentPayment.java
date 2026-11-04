package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "livestock_treatment_payments")
public class LivestockTreatmentPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "treatment_id", nullable = false)
    private LivestockTreatment treatment;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "payment_status", length = 20)
    private String paymentStatus = "COMPLETED";

    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @ManyToOne
    @JoinColumn(name = "recorded_by")
    private Users recordedBy;

    @Column(name = "recorded_at")
    private LocalDateTime recordedAt;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) recordedAt = LocalDateTime.now();
        if (paymentDate == null) paymentDate = LocalDate.now();
        if (paymentStatus == null || paymentStatus.isBlank()) paymentStatus = "COMPLETED";
        if (isDeleted == null) isDeleted = false;
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            invoiceNumber = "TXN-" + System.currentTimeMillis();
        }
        if (paymentReference == null || paymentReference.isBlank()) {
            paymentReference = "PAY-"
                    + LocalDate.now().toString().replace("-", "")
                    + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        }
        if (paymentMethod == null || paymentMethod.isBlank()) {
            paymentMethod = "CASH";
        }
    }

    // ─── Getters & Setters ─────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LivestockTreatment getTreatment() { return treatment; }
    public void setTreatment(LivestockTreatment treatment) { this.treatment = treatment; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Users getRecordedBy() { return recordedBy; }
    public void setRecordedBy(Users recordedBy) { this.recordedBy = recordedBy; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    // ─── Display helpers (used directly by Thymeleaf) ─────────

    @Transient
    public String getPaymentMethodLabel() {
        if (paymentMethod == null || paymentMethod.isBlank()) return "Not provided";
        return switch (paymentMethod.toUpperCase()) {
            case "CASH"          -> "Cash";
            case "BANK_TRANSFER" -> "Bank Transfer";
            case "MOBILE_MONEY"  -> "Mobile Money";
            case "CHEQUE"        -> "Cheque";
            default -> paymentMethod;
        };
    }

    @Transient
    public String getPaymentMethodBadgeClass() {
        if (paymentMethod == null) return "bg-secondary";
        return switch (paymentMethod.toUpperCase()) {
            case "CASH"          -> "bg-success";
            case "BANK_TRANSFER" -> "bg-primary";
            case "MOBILE_MONEY"  -> "bg-info text-dark";
            case "CHEQUE"        -> "bg-warning text-dark";
            default -> "bg-secondary";
        };
    }
}