package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read-only projection over {@code public.v_livestock_with_age}.
 *
 * <p><strong>Important:</strong> the underlying {@code livestock} table has
 * <em>no</em> soft-delete column ({@code is_deleted}, {@code deleted_at}, etc.).
 * "Removed" animals are represented via {@code status} in
 * {@code ACTIVE | SOLD | DEAD | TRANSFERRED}. Do not add an {@code isDeleted}
 * field here — it will cause:
 * <pre>ERROR: column vwa1_0.is_deleted does not exist</pre>
 */
@Entity
@Immutable
@Table(name = "v_livestock_with_age")
public class VLivestockWithAge {

    // ── Identity ─────────────────────────────────────────────────────────
    @Id
    private UUID id;

    @Column(name = "tag_number", nullable = false)
    private String tagNumber;

    // ── Core attributes ──────────────────────────────────────────────────
    @Column(name = "gender")
    private String gender;

    @Column(name = "photo")
    private String photo;

    @Column(name = "date_received")
    private LocalDate dateReceived;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "status")
    private String status;

    @Column(name = "health_status")
    private String healthStatus;

    @Column(name = "reproductive_status")
    private String reproductiveStatus;

    @Column(name = "is_group")
    private Boolean isGroup;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "is_draft")
    private Boolean isDraft;

    @Column(name = "source_location")
    private String sourceLocation;

    @Column(name = "notes")
    private String notes;

    @Column(name = "acquisition_method")
    private String acquisitionMethod;

    @Column(name = "acquisition_source")
    private String acquisitionSource;

    @Column(name = "current_value")
    private BigDecimal currentValue;

    @Column(name = "sold_price")
    private BigDecimal soldPrice;

    @Column(name = "offspring_count")
    private Integer offspringCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Breeding / pregnancy (from LEFT JOIN LATERAL on livestock_breeding) ──
    @Column(name = "conception_date")
    private LocalDate conceptionDate;

    @Column(name = "expected_due_date")
    private LocalDate expectedDueDate;

    @Column(name = "first_breeding_date")
    private LocalDate firstBreedingDate;

    @Column(name = "last_breeding_date")
    private LocalDate lastBreedingDate;

    @Column(name = "last_birth_date")
    private LocalDate lastBirthDate;

    @Column(name = "is_pregnant")
    private Boolean isPregnant;

    @Column(name = "pregnancy_status")
    private String pregnancyStatus;

    // ── View-calculated fields ───────────────────────────────────────────
    @Column(name = "age_in_days")
    private Integer ageInDays;

    @Column(name = "age_in_months")
    private Integer ageInMonths;

    @Column(name = "lifecycle_stage")
    private String lifecycleStage;

    @Column(name = "last_lifecycle_stage")
    private String lastLifecycleStage;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "category_code")
    private String categoryCode;

    @Column(name = "gestation_period_months")
    private Integer gestationPeriodMonths;

    // ── Foreign-key IDs (no @ManyToOne — the view is read-only) ──────────
    @Column(name = "livestock_category_id")
    private UUID livestockCategoryId;

    @Column(name = "beneficiary_id")
    private UUID beneficiaryId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "mother_id")
    private UUID motherId;

    @Column(name = "father_id")
    private UUID fatherId;

    @Column(name = "created_by")
    private UUID createdBy;

    // ── Constructors ─────────────────────────────────────────────────────
    public VLivestockWithAge() {}

    // ── Derived helpers (not persisted) ──────────────────────────────────

    /** True when this animal is a group head (herd/batch), not an individual. */
    @Transient
    public boolean isGrouped() {
        return Boolean.TRUE.equals(isGroup);
    }

    /** True if the row represents an active (non-draft, non-sold/dead) animal. */
    @Transient
    public boolean isActive() {
        return !Boolean.TRUE.equals(isDraft) && "ACTIVE".equalsIgnoreCase(status);
    }

    /**
     * Human-readable age, e.g. {@code "12 days"}, {@code "7 months"},
     * {@code "2 years 3 months"}. Returns {@code "Unknown"} if age is null.
     */
    @Transient
    public String getAgeDisplay() {
        if (ageInMonths == null || ageInDays == null) {
            return "Unknown";
        }
        if (ageInMonths < 1) {
            return ageInDays + " day" + (ageInDays == 1 ? "" : "s");
        }
        if (ageInMonths < 12) {
            return ageInMonths + " month" + (ageInMonths == 1 ? "" : "s");
        }
        int years = ageInMonths / 12;
        int remainingMonths = ageInMonths % 12;
        String yearPart = years + " year" + (years == 1 ? "" : "s");
        if (remainingMonths == 0) {
            return yearPart;
        }
        return yearPart + " " + remainingMonths + " month" + (remainingMonths == 1 ? "" : "s");
    }

    // ── Getters / setters ────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTagNumber() { return tagNumber; }
    public void setTagNumber(String tagNumber) { this.tagNumber = tagNumber; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public LocalDate getDateReceived() { return dateReceived; }
    public void setDateReceived(LocalDate dateReceived) { this.dateReceived = dateReceived; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }

    public String getReproductiveStatus() { return reproductiveStatus; }
    public void setReproductiveStatus(String reproductiveStatus) { this.reproductiveStatus = reproductiveStatus; }

    public Boolean getIsGroup() { return isGroup; }
    public void setIsGroup(Boolean isGroup) { this.isGroup = isGroup; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public Boolean getIsDraft() { return isDraft; }
    public void setIsDraft(Boolean isDraft) { this.isDraft = isDraft; }

    public String getSourceLocation() { return sourceLocation; }
    public void setSourceLocation(String sourceLocation) { this.sourceLocation = sourceLocation; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getAcquisitionMethod() { return acquisitionMethod; }
    public void setAcquisitionMethod(String acquisitionMethod) { this.acquisitionMethod = acquisitionMethod; }

    public String getAcquisitionSource() { return acquisitionSource; }
    public void setAcquisitionSource(String acquisitionSource) { this.acquisitionSource = acquisitionSource; }

    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }

    public BigDecimal getSoldPrice() { return soldPrice; }
    public void setSoldPrice(BigDecimal soldPrice) { this.soldPrice = soldPrice; }

    public Integer getOffspringCount() { return offspringCount; }
    public void setOffspringCount(Integer offspringCount) { this.offspringCount = offspringCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDate getConceptionDate() { return conceptionDate; }
    public void setConceptionDate(LocalDate conceptionDate) { this.conceptionDate = conceptionDate; }

    public LocalDate getExpectedDueDate() { return expectedDueDate; }
    public void setExpectedDueDate(LocalDate expectedDueDate) { this.expectedDueDate = expectedDueDate; }

    public LocalDate getFirstBreedingDate() { return firstBreedingDate; }
    public void setFirstBreedingDate(LocalDate firstBreedingDate) { this.firstBreedingDate = firstBreedingDate; }

    public LocalDate getLastBreedingDate() { return lastBreedingDate; }
    public void setLastBreedingDate(LocalDate lastBreedingDate) { this.lastBreedingDate = lastBreedingDate; }

    public LocalDate getLastBirthDate() { return lastBirthDate; }
    public void setLastBirthDate(LocalDate lastBirthDate) { this.lastBirthDate = lastBirthDate; }

    public Boolean getIsPregnant() { return isPregnant; }
    public void setIsPregnant(Boolean isPregnant) { this.isPregnant = isPregnant; }

    public String getPregnancyStatus() { return pregnancyStatus; }
    public void setPregnancyStatus(String pregnancyStatus) { this.pregnancyStatus = pregnancyStatus; }

    public Integer getAgeInDays() { return ageInDays; }
    public void setAgeInDays(Integer ageInDays) { this.ageInDays = ageInDays; }

    public Integer getAgeInMonths() { return ageInMonths; }
    public void setAgeInMonths(Integer ageInMonths) { this.ageInMonths = ageInMonths; }

    public String getLifecycleStage() { return lifecycleStage; }
    public void setLifecycleStage(String lifecycleStage) { this.lifecycleStage = lifecycleStage; }

    public String getLastLifecycleStage() { return lastLifecycleStage; }
    public void setLastLifecycleStage(String lastLifecycleStage) { this.lastLifecycleStage = lastLifecycleStage; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }

    public Integer getGestationPeriodMonths() { return gestationPeriodMonths; }
    public void setGestationPeriodMonths(Integer gestationPeriodMonths) { this.gestationPeriodMonths = gestationPeriodMonths; }

    public UUID getLivestockCategoryId() { return livestockCategoryId; }
    public void setLivestockCategoryId(UUID livestockCategoryId) { this.livestockCategoryId = livestockCategoryId; }

    public UUID getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(UUID beneficiaryId) { this.beneficiaryId = beneficiaryId; }

    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID locationId) { this.locationId = locationId; }

    public UUID getMotherId() { return motherId; }
    public void setMotherId(UUID motherId) { this.motherId = motherId; }

    public UUID getFatherId() { return fatherId; }
    public void setFatherId(UUID fatherId) { this.fatherId = fatherId; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    @Override
    public String toString() {
        return "VLivestockWithAge{" +
                "id=" + id +
                ", tagNumber='" + tagNumber + '\'' +
                ", gender='" + gender + '\'' +
                ", status='" + status + '\'' +
                ", lifecycleStage='" + lifecycleStage + '\'' +
                ", ageInMonths=" + ageInMonths +
                ", categoryName='" + categoryName + '\'' +
                '}';
    }
}