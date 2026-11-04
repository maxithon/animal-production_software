package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "livestock_breeding")
@Data
public class LivestockBreeding {

    public static final String STATUS_PENDING            = "PENDING";
    public static final String STATUS_BRED               = "BRED";
    public static final String STATUS_CONFIRMED_PREGNANT = "CONFIRMED_PREGNANT";
    public static final String STATUS_PREGNANT           = "PREGNANT";
    public static final String STATUS_COMPLETED          = "COMPLETED";
    public static final String STATUS_FAILED             = "FAILED";
    public static final String STATUS_ABORTED            = "ABORTED";

    public static final String METHOD_NATURAL           = "NATURAL";
    public static final String METHOD_ARTIFICIAL        = "ARTIFICIAL";
    public static final String METHOD_EMBRYO_TRANSFER   = "EMBRYO_TRANSFER";
    public static final String METHOD_PURCHASE_PREGNANT = "PURCHASE_PREGNANT";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livestock_id", nullable = false)
    private Livestock livestock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "male_id")
    private Livestock male;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinarian_id")
    private Veterinarian veterinarian;

    @Column(name = "breeding_date")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate breedingDate;

    @Column(name = "first_breeding_date")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate firstBreedingDate;

    @Column(name = "last_breeding_date")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastBreedingDate;

    @Column(name = "expected_due_date")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectedDueDate;

    @Column(name = "expected_pregnancy_check_date")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectedPregnancyCheckDate;

    @Column(name = "conception_date")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate conceptionDate;

    @Column(name = "pregnancy_confirmed_date")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate pregnancyConfirmedDate;

    @Column(name = "breeding_attempt")
    private Integer breedingAttempt = 1;

    @Column(name = "insemination_method")
    private String inseminationMethod;

    @Column(name = "breeding_method")
    private String breedingMethod;

    @Column(name = "status")
    private String status = STATUS_PENDING;

    @Column(name = "pregnancy_status")
    private String pregnancyStatus;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "notes")
    private String notes;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient private UUID livestockIdValue;
    @Transient private UUID maleLivestockIdValue;
    @Transient private UUID veterinarianIdValue;

    public UUID getLivestockIdValue() { return livestock != null ? livestock.getId() : livestockIdValue; }
    public void setLivestockIdValue(UUID livestockIdValue) { this.livestockIdValue = livestockIdValue; }

    public UUID getMaleLivestockIdValue() { return male != null ? male.getId() : maleLivestockIdValue; }
    public void setMaleLivestockIdValue(UUID maleLivestockIdValue) { this.maleLivestockIdValue = maleLivestockIdValue; }

    public UUID getVeterinarianIdValue() { return veterinarian != null ? veterinarian.getId() : veterinarianIdValue; }
    public void setVeterinarianIdValue(UUID veterinarianIdValue) { this.veterinarianIdValue = veterinarianIdValue; }

    public Livestock getMaleLivestock() { return male; }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)       createdAt = LocalDateTime.now();
        if (isActive == null)        isActive = true;
        if (status == null)          status = STATUS_PENDING;
        if (breedingAttempt == null) breedingAttempt = 1;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public String getStatusLabel() {
        if (status == null) return "Unknown";
        return switch (status) {
            case STATUS_PENDING            -> "Pending";
            case STATUS_BRED               -> "Bred";
            case STATUS_CONFIRMED_PREGNANT -> "Confirmed Pregnant";
            case STATUS_PREGNANT           -> "Pregnant";
            case STATUS_COMPLETED          -> "Completed";
            case STATUS_FAILED             -> "Failed";
            case STATUS_ABORTED            -> "Aborted";
            default -> status;
        };
    }

    public String getPregnancyStatusLabel() {
        if (pregnancyStatus == null) return "Unknown";
        return switch (pregnancyStatus) {
            case "NOT_PREGNANT" -> "Not Pregnant";
            case "PREGNANT"     -> "Pregnant";
            case "CONFIRMED"    -> "Confirmed";
            case "FAILED"       -> "Failed";
            default -> pregnancyStatus;
        };
    }

    public String getInseminationMethodLabel() {
        if (inseminationMethod == null) return "Unknown";
        return switch (inseminationMethod) {
            case METHOD_NATURAL           -> "Natural";
            case METHOD_ARTIFICIAL        -> "Artificial";
            case METHOD_EMBRYO_TRANSFER   -> "Embryo Transfer";
            case METHOD_PURCHASE_PREGNANT -> "Purchase (Pregnant)";
            default -> inseminationMethod;
        };
    }

    public boolean isSuccessful()      { return STATUS_COMPLETED.equals(status) || STATUS_CONFIRMED_PREGNANT.equals(status); }
    public boolean isFailed()          { return STATUS_FAILED.equals(status) || STATUS_ABORTED.equals(status); }
    public boolean isActivePregnancy() { return STATUS_PREGNANT.equals(status) || STATUS_CONFIRMED_PREGNANT.equals(status); }

    public Long getDaysPregnant() {
        if (conceptionDate == null || !isActivePregnancy()) return null;
        return ChronoUnit.DAYS.between(conceptionDate, LocalDate.now());
    }
    public Long getWeeksPregnant() { Long d = getDaysPregnant(); return d != null ? d / 7 : null; }
    public Long getDaysUntilDue() {
        if (expectedDueDate == null) return null;
        return ChronoUnit.DAYS.between(LocalDate.now(), expectedDueDate);
    }
    public boolean isOverdue() { Long d = getDaysUntilDue(); return d != null && d < 0; }
}