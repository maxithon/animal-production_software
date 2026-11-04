package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "livestock_treatments")
public class LivestockTreatment {

    // ─── Enums ──────────────────────────────────────────────────────────────────
    public enum TreatmentStatus {
        SCHEDULED, ONGOING, COMPLETED, FAILED
    }

    public enum TreatmentCategory {
        CURATIVE, PREVENTIVE, VACCINATION, DEWORMING
    }

    public enum FrequencyType {
        ONCE_DAILY("Once daily"),
        TWICE_DAILY("Twice daily"),
        THREE_TIMES_DAILY("Three times daily"),
        EVERY_OTHER_DAY("Every other day"),
        WEEKLY("Weekly"),
        BIWEEKLY("Bi-weekly"),
        AS_NEEDED("As needed");

        private final String label;
        FrequencyType(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum PaymentStatus {
        UNPAID("Unpaid"),
        PARTIALLY_PAID("Partially Paid"),
        PAID("Paid"),
        OVERDUE("Overdue"),
        CANCELLED("Cancelled"),
        REFUNDED("Refunded");

        private final String label;
        PaymentStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public enum OutcomeStatus {
        RECOVERED, IMPROVING, FAILED, DISCONTINUED, FOLLOW_UP_REQUIRED
    }

    public enum ProductAffected {
        MEAT, MILK, EGGS, ALL
    }

    public enum Severity {
        MILD, MODERATE, SEVERE
    }

    // ─── Fields ─────────────────────────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "treatment_reference", unique = true, nullable = false, length = 50)
    private String treatmentReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livestock_id")
    private Livestock livestock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sick_livestock_id")
    private LivestockSick sickLivestock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id")
    private Medication medication;

    @ManyToOne
    @JoinColumn(name = "veterinarian_id", referencedColumnName = "id")
    private Veterinarian veterinarian;

    @NotNull(message = "Treatment date is required")
    @Column(name = "treatment_date", nullable = false)
    private LocalDate treatmentDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_treatment_date")
    private LocalDate nextTreatmentDate;

    @Column(name = "dosage", length = 30)
    private String dosage;

    @Enumerated(EnumType.STRING)
    @Column(name = "dosage_unit", length = 20)
    private Medication.DosageUnit dosageUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", length = 30)
    private FrequencyType frequency;

    @Column(name = "treatment_duration", length = 50)
    private String treatmentDuration;

    @Enumerated(EnumType.STRING)
    @Column(name = "treatment_type", length = 30)
    private TreatmentCategory treatmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "treatment_status", length = 20)
    private TreatmentStatus treatmentStatus = TreatmentStatus.ONGOING;

    @Column(name = "treatment_cost", precision = 12, scale = 2)
    private BigDecimal treatmentCost;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // ─── Diagnosis Fields ──
    @Column(name = "diagnosis_name", length = 255)
    private String diagnosisName;

    @Column(name = "symptoms", columnDefinition = "TEXT")
    private String symptoms;

    @Column(name = "clinical_observations", columnDefinition = "TEXT")
    private String clinicalObservations;

    @Column(name = "treatment_purpose", columnDefinition = "TEXT")
    private String treatmentPurpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private Severity severity;

    // ─── Payment Fields ──
    @Column(name = "is_paid")
    private Boolean isPaid = false;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "payment_due_date")
    private LocalDate paymentDueDate;

    @Column(name = "amount_paid", precision = 12, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "payment_notes", columnDefinition = "TEXT")
    private String paymentNotes;

    // ─── Outcome Fields ──
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome_status")
    private OutcomeStatus outcomeStatus;

    @Column(name = "outcome_notes", columnDefinition = "TEXT")
    private String outcomeNotes;

    @Column(name = "follow_up_instructions", columnDefinition = "TEXT")
    private String followUpInstructions;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    // ─── Withdrawal Fields ──
    @Column(name = "withdrawal_period_days")
    private Integer withdrawalPeriodDays;

    @Column(name = "withdrawal_end_date")
    private LocalDate withdrawalEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_affected")
    private ProductAffected productAffected;

    @Column(name = "batch_number", length = 100)
    private String batchNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "manufacturer", length = 255)
    private String manufacturer;

    // ─── Audit Fields ──
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "user_id")
    private Users createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "updated_by", referencedColumnName = "user_id")
    private Users updatedBy;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    // ─── Relationships ──
    @OneToMany(mappedBy = "treatment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LivestockTreatmentPayment> payments = new ArrayList<>();

    // ─── Transient fields for form binding ──
    @Transient
    private String livestockIdValue;

    @Transient
    private String medicationIdValue;

    @Transient
    private String veterinarianIdValue;

    // ─── NEW: Transient field for location path ──
    @Transient
    private String locationPath;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (paymentStatus == null) {
            paymentStatus = PaymentStatus.UNPAID;
        }
        if (isPaid == null) {
            isPaid = false;
        }
        if (amountPaid == null) {
            amountPaid = BigDecimal.ZERO;
        }
        if (treatmentStatus == null) {
            treatmentStatus = TreatmentStatus.ONGOING;
        }
        if (startDate == null && treatmentDate != null) {
            startDate = treatmentDate;
        }
        if (treatmentReference == null || treatmentReference.isEmpty()) {
            treatmentReference = generateTreatmentReference();
        }
        updatePaymentStatus();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        updatePaymentStatus();
    }

    public LivestockTreatment() {}

    // ─── Reference Generator ──────────────────────────────────────────

    private String generateTreatmentReference() {
        String year = String.valueOf(LocalDate.now().getYear());
        String month = String.format("%02d", LocalDate.now().getMonthValue());
        String day = String.format("%02d", LocalDate.now().getDayOfMonth());
        String sequence = String.format("%04d", (int)(Math.random() * 10000));
        return "TXN-" + year + month + day + "-" + sequence;
    }

    // ─── Payment Helper Methods ──────────────────────────────────

    public BigDecimal getRemainingBalance() {
        if (treatmentCost == null) return BigDecimal.ZERO;
        if (amountPaid == null) return treatmentCost;
        return treatmentCost.subtract(amountPaid).max(BigDecimal.ZERO);
    }

    public boolean isFullyPaid() {
        return isPaid != null && isPaid && getRemainingBalance().compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isOverdue() {
        if (paymentDueDate == null) return false;
        if (isPaid != null && isPaid) return false;
        return LocalDate.now().isAfter(paymentDueDate);
    }

    public boolean isWithdrawalActive() {
        if (withdrawalEndDate == null) return false;
        return LocalDate.now().isBefore(withdrawalEndDate);
    }

    public long getDaysUntilWithdrawalEnds() {
        if (withdrawalEndDate == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), withdrawalEndDate);
    }

    // ─── Auto-update payment status ──────────────────────────────────

    public void updatePaymentStatus() {
        if (treatmentCost == null || treatmentCost.compareTo(BigDecimal.ZERO) == 0) {
            paymentStatus = PaymentStatus.UNPAID;
            isPaid = false;
            return;
        }

        BigDecimal paid = amountPaid != null ? amountPaid : BigDecimal.ZERO;
        if (paid.compareTo(treatmentCost) >= 0) {
            paymentStatus = PaymentStatus.PAID;
            isPaid = true;
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            paymentStatus = PaymentStatus.PARTIALLY_PAID;
            isPaid = false;
        } else {
            paymentStatus = PaymentStatus.UNPAID;
            isPaid = false;
        }
    }

    // ─── Payment Status Display Methods ──────────────────────────────────

    public String getPaymentStatusLabel() {
        if (isOverdue()) return "⚠️ Overdue";
        if (isFullyPaid()) return "✅ Paid";
        if (amountPaid != null && amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            return "🔄 Partially Paid";
        }
        return "⭕ Unpaid";
    }

    public String getPaymentStatusBadgeClass() {
        if (isOverdue()) return "badge bg-danger";
        if (isFullyPaid()) return "badge bg-success";
        if (amountPaid != null && amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            return "badge bg-warning text-dark";
        }
        return "badge bg-secondary";
    }

    public String getMedicationName() {
        return medication != null ? medication.getName() : null;
    }

    // ─── Status Badge Helpers ────────────────────────────────────

    @Transient
    public String getTreatmentStatusBadgeClass() {
        return switch (treatmentStatus != null ? treatmentStatus : TreatmentStatus.ONGOING) {
            case SCHEDULED -> "badge-status badge-scheduled";
            case ONGOING -> "badge-status badge-ongoing";
            case COMPLETED -> "badge-status badge-completed";
            case FAILED -> "badge-status badge-failed";
        };
    }

    @Transient
    public String getTreatmentStatusLabel() {
        return switch (treatmentStatus != null ? treatmentStatus : TreatmentStatus.ONGOING) {
            case SCHEDULED -> "Scheduled";
            case ONGOING -> "Ongoing";
            case COMPLETED -> "Completed";
            case FAILED -> "Failed";
        };
    }

    @Transient
    public String getOutcomeStatusLabel() {
        return switch (outcomeStatus != null ? outcomeStatus : OutcomeStatus.IMPROVING) {
            case RECOVERED -> "✅ Recovered";
            case IMPROVING -> "🔄 Improving";
            case FAILED -> "❌ Failed";
            case DISCONTINUED -> "⏸ Discontinued";
            case FOLLOW_UP_REQUIRED -> "📋 Follow-up Required";
        };
    }

    @Transient
    public String getSeverityLabel() {
        return switch (severity != null ? severity : Severity.MILD) {
            case MILD -> "🟢 Mild";
            case MODERATE -> "🟡 Moderate";
            case SEVERE -> "🔴 Severe";
        };
    }

    // ─── Getters & Setters ─────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTreatmentReference() { return treatmentReference; }
    public void setTreatmentReference(String treatmentReference) { this.treatmentReference = treatmentReference; }

    public Livestock getLivestock() { return livestock; }
    public void setLivestock(Livestock livestock) { this.livestock = livestock; }

    public LivestockSick getSickLivestock() { return sickLivestock; }
    public void setSickLivestock(LivestockSick sickLivestock) { this.sickLivestock = sickLivestock; }

    public Medication getMedication() { return medication; }
    public void setMedication(Medication medication) { this.medication = medication; }

    public Veterinarian getVeterinarian() { return veterinarian; }
    public void setVeterinarian(Veterinarian veterinarian) { this.veterinarian = veterinarian; }

    public LocalDate getTreatmentDate() { return treatmentDate; }
    public void setTreatmentDate(LocalDate treatmentDate) { this.treatmentDate = treatmentDate; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public LocalDate getNextTreatmentDate() { return nextTreatmentDate; }
    public void setNextTreatmentDate(LocalDate nextTreatmentDate) { this.nextTreatmentDate = nextTreatmentDate; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public Medication.DosageUnit getDosageUnit() { return dosageUnit; }
    public void setDosageUnit(Medication.DosageUnit dosageUnit) { this.dosageUnit = dosageUnit; }

    public FrequencyType getFrequency() { return frequency; }
    public void setFrequency(FrequencyType frequency) { this.frequency = frequency; }

    public String getTreatmentDuration() { return treatmentDuration; }
    public void setTreatmentDuration(String treatmentDuration) { this.treatmentDuration = treatmentDuration; }

    public TreatmentCategory getTreatmentType() { return treatmentType; }
    public void setTreatmentType(TreatmentCategory treatmentType) { this.treatmentType = treatmentType; }

    public TreatmentStatus getTreatmentStatus() { return treatmentStatus; }
    public void setTreatmentStatus(TreatmentStatus treatmentStatus) { this.treatmentStatus = treatmentStatus; }

    public BigDecimal getTreatmentCost() { return treatmentCost; }
    public void setTreatmentCost(BigDecimal treatmentCost) { this.treatmentCost = treatmentCost; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDiagnosisName() { return diagnosisName; }
    public void setDiagnosisName(String diagnosisName) { this.diagnosisName = diagnosisName; }

    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }

    public String getClinicalObservations() { return clinicalObservations; }
    public void setClinicalObservations(String clinicalObservations) { this.clinicalObservations = clinicalObservations; }

    public String getTreatmentPurpose() { return treatmentPurpose; }
    public void setTreatmentPurpose(String treatmentPurpose) { this.treatmentPurpose = treatmentPurpose; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public Boolean getIsPaid() { return isPaid; }
    public void setIsPaid(Boolean isPaid) { this.isPaid = isPaid; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }

    public LocalDate getPaymentDueDate() { return paymentDueDate; }
    public void setPaymentDueDate(LocalDate paymentDueDate) { this.paymentDueDate = paymentDueDate; }

    public BigDecimal getAmountPaid() { return amountPaid; }
    public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }

    public String getPaymentNotes() { return paymentNotes; }
    public void setPaymentNotes(String paymentNotes) { this.paymentNotes = paymentNotes; }

    public OutcomeStatus getOutcomeStatus() { return outcomeStatus; }
    public void setOutcomeStatus(OutcomeStatus outcomeStatus) { this.outcomeStatus = outcomeStatus; }

    public String getOutcomeNotes() { return outcomeNotes; }
    public void setOutcomeNotes(String outcomeNotes) { this.outcomeNotes = outcomeNotes; }

    public String getFollowUpInstructions() { return followUpInstructions; }
    public void setFollowUpInstructions(String followUpInstructions) { this.followUpInstructions = followUpInstructions; }

    public LocalDate getFollowUpDate() { return followUpDate; }
    public void setFollowUpDate(LocalDate followUpDate) { this.followUpDate = followUpDate; }

    public Integer getWithdrawalPeriodDays() { return withdrawalPeriodDays; }
    public void setWithdrawalPeriodDays(Integer withdrawalPeriodDays) { this.withdrawalPeriodDays = withdrawalPeriodDays; }

    public LocalDate getWithdrawalEndDate() { return withdrawalEndDate; }
    public void setWithdrawalEndDate(LocalDate withdrawalEndDate) { this.withdrawalEndDate = withdrawalEndDate; }

    public ProductAffected getProductAffected() { return productAffected; }
    public void setProductAffected(ProductAffected productAffected) { this.productAffected = productAffected; }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Users getCreatedBy() { return createdBy; }
    public void setCreatedBy(Users createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Users getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Users updatedBy) { this.updatedBy = updatedBy; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public List<LivestockTreatmentPayment> getPayments() { return payments; }
    public void setPayments(List<LivestockTreatmentPayment> payments) { this.payments = payments; }

    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String livestockIdValue) { this.livestockIdValue = livestockIdValue; }

    public String getMedicationIdValue() { return medicationIdValue; }
    public void setMedicationIdValue(String medicationIdValue) { this.medicationIdValue = medicationIdValue; }

    public String getVeterinarianIdValue() { return veterinarianIdValue; }
    public void setVeterinarianIdValue(String veterinarianIdValue) { this.veterinarianIdValue = veterinarianIdValue; }

    // ─── NEW: Location Path Getters & Setters ─────────────────────

    public String getLocationPath() { return locationPath; }
    public void setLocationPath(String locationPath) { this.locationPath = locationPath; }
}