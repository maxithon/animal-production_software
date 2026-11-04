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

    // ── Status Constants ──────────────────────────────────────────────────────
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_BRED = "BRED";
    public static final String STATUS_CONFIRMED_PREGNANT = "CONFIRMED_PREGNANT";
    public static final String STATUS_PREGNANT = "PREGNANT";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_ABORTED = "ABORTED";

    // ── Insemination Method Constants ──────────────────────────────────────
    public static final String METHOD_NATURAL = "NATURAL";
    public static final String METHOD_ARTIFICIAL = "ARTIFICIAL";
    public static final String METHOD_EMBRYO_TRANSFER = "EMBRYO_TRANSFER";
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

    // NEW — set by LivestockBreedingService.confirmPregnancy() the moment a
    // pregnancy is confirmed. This is the real "when did we find out" date,
    // as distinct from conceptionDate (presumed = breeding date) and
    // expectedDueDate (calculated). Powers the "Pregnancy Confirmation Date"
    // field on the View page.
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

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    // FIXED: DB column `created_by` is `uuid NULL` (see schema), but this
    // field was previously typed as String, which made Hibernate bind the
    // hidden form value ("00000000-0000-0000-0000-000000000000") as a
    // varchar parameter against a uuid column -> Postgres error:
    // "column created_by is of type uuid but expression is of type
    // character varying". Changed to UUID so it matches the column type.
    @Column(name = "created_by")
    private UUID createdBy;

    // updated_by is `varchar(100)` in the schema, so String stays correct here.
    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Transient Form-Binding Bridges ──────────────────────────────────────
    // Thymeleaf's th:field="*{X}" needs a readable/writable JavaBean property
    // named X on the model attribute. Our real relations (livestock, male,
    // veterinarian) are JPA associations, not plain UUID fields, so plain
    // <select th:field="*{livestockId}"> etc. would 500 with
    // NotReadablePropertyException unless a bridge property exists.
    //
    // IMPORTANT: none of these bridge fields may be named exactly the same
    // as the implied "<association>Id" path Spring Data derives from
    // repository method names (e.g. "livestockId" for findByLivestockId...).
    // If they collide, Spring Data stops at the @Transient field instead of
    // falling back to traversing livestock.id, and Hibernate throws
    // "Could not resolve attribute 'livestockId'" at startup. That's why
    // these are suffixed "...Value" rather than mirroring the association
    // name directly.
    //
    // These are NOT persisted (no @Column) — they only exist so Thymeleaf can
    // render/bind a UUID value in <select> options. The controller is
    // responsible for resolving them back into real entities before saving.

    @Transient
    private UUID livestockIdValue;

    @Transient
    private UUID maleLivestockIdValue;

    @Transient
    private UUID veterinarianIdValue;

    public UUID getLivestockIdValue() {
        return livestock != null ? livestock.getId() : livestockIdValue;
    }

    public void setLivestockIdValue(UUID livestockIdValue) {
        this.livestockIdValue = livestockIdValue;
    }

    public UUID getMaleLivestockIdValue() {
        return male != null ? male.getId() : maleLivestockIdValue;
    }

    public void setMaleLivestockIdValue(UUID maleLivestockIdValue) {
        this.maleLivestockIdValue = maleLivestockIdValue;
    }

    public UUID getVeterinarianIdValue() {
        return veterinarian != null ? veterinarian.getId() : veterinarianIdValue;
    }

    public void setVeterinarianIdValue(UUID veterinarianIdValue) {
        this.veterinarianIdValue = veterinarianIdValue;
    }

    // Compatibility alias — livestock-breeding-list.html reads "b.maleLivestock",
    // which Thymeleaf resolves via getMaleLivestock(). Keeping this avoids
    // having to touch the template.
    public Livestock getMaleLivestock() {
        return male;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
        if (isDeleted == null) isDeleted = false;
        if (status == null) status = STATUS_PENDING;
        if (breedingAttempt == null) breedingAttempt = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Helper Methods ──────────────────────────────────────────────────────

    public String getStatusLabel() {
        if (status == null) return "Unknown";
        return switch (status) {
            case STATUS_PENDING -> "Pending";
            case STATUS_BRED -> "Bred";
            case STATUS_CONFIRMED_PREGNANT -> "Confirmed Pregnant";
            case STATUS_PREGNANT -> "Pregnant";
            case STATUS_COMPLETED -> "Completed";
            case STATUS_FAILED -> "Failed";
            case STATUS_ABORTED -> "Aborted";
            default -> status;
        };
    }

    public String getPregnancyStatusLabel() {
        if (pregnancyStatus == null) return "Unknown";
        return switch (pregnancyStatus) {
            case "NOT_PREGNANT" -> "Not Pregnant";
            case "PREGNANT" -> "Pregnant";
            case "CONFIRMED" -> "Confirmed";
            case "FAILED" -> "Failed";
            default -> pregnancyStatus;
        };
    }

    public String getInseminationMethodLabel() {
        if (inseminationMethod == null) return "Unknown";
        return switch (inseminationMethod) {
            case METHOD_NATURAL -> "Natural";
            case METHOD_ARTIFICIAL -> "Artificial";
            case METHOD_EMBRYO_TRANSFER -> "Embryo Transfer";
            case METHOD_PURCHASE_PREGNANT -> "Purchase (Pregnant)";
            default -> inseminationMethod;
        };
    }

    public boolean isSuccessful() {
        return STATUS_COMPLETED.equals(status) || STATUS_CONFIRMED_PREGNANT.equals(status);
    }

    public boolean isFailed() {
        return STATUS_FAILED.equals(status) || STATUS_ABORTED.equals(status);
    }

    public boolean isActivePregnancy() {
        return STATUS_PREGNANT.equals(status) || STATUS_CONFIRMED_PREGNANT.equals(status);
    }

    // ── NEW: Pregnancy tracking computed fields ─────────────────────────────
    // Not persisted — derived on the fly from conceptionDate/expectedDueDate
    // whenever this record represents an active pregnancy. Used by
    // livestock-breeding-view.html's "Pregnancy Tracking" card.

    /** Days elapsed since presumed conception, or null if not an active pregnancy. */
    public Long getDaysPregnant() {
        if (conceptionDate == null || !isActivePregnancy()) return null;
        return ChronoUnit.DAYS.between(conceptionDate, LocalDate.now());
    }

    /** Whole weeks elapsed since presumed conception, or null if not applicable. */
    public Long getWeeksPregnant() {
        Long days = getDaysPregnant();
        return days != null ? days / 7 : null;
    }

    /** Days remaining until the expected due date. Negative means overdue. Null if no due date set. */
    public Long getDaysUntilDue() {
        if (expectedDueDate == null) return null;
        return ChronoUnit.DAYS.between(LocalDate.now(), expectedDueDate);
    }

    public boolean isOverdue() {
        Long d = getDaysUntilDue();
        return d != null && d < 0;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Livestock getLivestock() {
        return livestock;
    }

    public void setLivestock(Livestock livestock) {
        this.livestock = livestock;
    }

    public Livestock getMale() {
        return male;
    }

    public void setMale(Livestock male) {
        this.male = male;
    }

    public Veterinarian getVeterinarian() {
        return veterinarian;
    }

    public void setVeterinarian(Veterinarian veterinarian) {
        this.veterinarian = veterinarian;
    }

    public LocalDate getBreedingDate() {
        return breedingDate;
    }

    public void setBreedingDate(LocalDate breedingDate) {
        this.breedingDate = breedingDate;
    }

    public LocalDate getFirstBreedingDate() {
        return firstBreedingDate;
    }

    public void setFirstBreedingDate(LocalDate firstBreedingDate) {
        this.firstBreedingDate = firstBreedingDate;
    }

    public LocalDate getLastBreedingDate() {
        return lastBreedingDate;
    }

    public void setLastBreedingDate(LocalDate lastBreedingDate) {
        this.lastBreedingDate = lastBreedingDate;
    }

    public LocalDate getExpectedDueDate() {
        return expectedDueDate;
    }

    public void setExpectedDueDate(LocalDate expectedDueDate) {
        this.expectedDueDate = expectedDueDate;
    }

    public LocalDate getExpectedPregnancyCheckDate() {
        return expectedPregnancyCheckDate;
    }

    public void setExpectedPregnancyCheckDate(LocalDate expectedPregnancyCheckDate) {
        this.expectedPregnancyCheckDate = expectedPregnancyCheckDate;
    }

    public LocalDate getConceptionDate() {
        return conceptionDate;
    }

    public void setConceptionDate(LocalDate conceptionDate) {
        this.conceptionDate = conceptionDate;
    }

    public LocalDate getPregnancyConfirmedDate() {
        return pregnancyConfirmedDate;
    }

    public void setPregnancyConfirmedDate(LocalDate pregnancyConfirmedDate) {
        this.pregnancyConfirmedDate = pregnancyConfirmedDate;
    }

    public Integer getBreedingAttempt() {
        return breedingAttempt;
    }

    public void setBreedingAttempt(Integer breedingAttempt) {
        this.breedingAttempt = breedingAttempt;
    }

    public String getInseminationMethod() {
        return inseminationMethod;
    }

    public void setInseminationMethod(String inseminationMethod) {
        this.inseminationMethod = inseminationMethod;
    }

    public String getBreedingMethod() {
        return breedingMethod;
    }

    public void setBreedingMethod(String breedingMethod) {
        this.breedingMethod = breedingMethod;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPregnancyStatus() {
        return pregnancyStatus;
    }

    public void setPregnancyStatus(String pregnancyStatus) {
        this.pregnancyStatus = pregnancyStatus;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    public Boolean getDeleted() {
        return isDeleted;
    }

    public void setDeleted(Boolean deleted) {
        isDeleted = deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}