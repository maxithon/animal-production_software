package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "livestock_treatments")
public class LivestockTreatment {

    // ── Enums ──────────────────────────────────────────────────────────────────
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

    // ── Fields ─────────────────────────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

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

    @Column(name = "is_paid")
    private Boolean isPaid = false;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "user_id")
    private Users createdBy;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    // Note: 'medication' field from database is now the TEXT 'description' field above
    // The 'medication_id' is the FK to the medications table

    @Transient
    private String livestockIdValue;

    @Transient
    private String medicationIdValue;

    @Transient
    private String veterinarianIdValue;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public LivestockTreatment() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

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

    public Boolean getIsPaid() { return isPaid; }
    public void setIsPaid(Boolean isPaid) { this.isPaid = isPaid; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Users getCreatedBy() { return createdBy; }
    public void setCreatedBy(Users createdBy) { this.createdBy = createdBy; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String v) { this.livestockIdValue = v; }

    public String getMedicationIdValue() { return medicationIdValue; }
    public void setMedicationIdValue(String v) { this.medicationIdValue = v; }

    public String getVeterinarianIdValue() { return veterinarianIdValue; }
    public void setVeterinarianIdValue(String veterinarianIdValue) { this.veterinarianIdValue = veterinarianIdValue; }

    public String getMedicationName() {
        return medication != null ? medication.getName() : null;
    }

    // Backward compatibility
    @Deprecated
    @Transient
    public String getVetName() {
        return veterinarian != null ? veterinarian.getFullName() : null;
    }

    @Deprecated
    @Transient
    public void setVetName(String vetName) {
        // Ignore - use veterinarian entity instead
    }
}
