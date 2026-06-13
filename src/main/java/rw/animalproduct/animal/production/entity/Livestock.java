package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "livestock")
public class Livestock {

    // ── Status constants ──────────────────────────────────────────────────────
    public static final String STATUS_ACTIVE   = "ACTIVE";
    public static final String STATUS_SOLD     = "SOLD";
    public static final String STATUS_DEAD     = "DEAD";
    public static final String STATUS_SICK     = "SICK";
    public static final String STATUS_PREGNANT = "PREGNANT";

    // ── Acquisition method constants ──────────────────────────────────────────
    public static final String ACQ_BIRTH    = "BIRTH";
    public static final String ACQ_PURCHASE = "PURCHASE";
    public static final String ACQ_DONATION = "DONATION";
    public static final String ACQ_TRANSFER = "TRANSFER";
    public static final String ACQ_OTHER    = "OTHER";

    // ── Insemination method constants ─────────────────────────────────────────
    public static final String INSEM_NATURAL = "NATURAL_MATING";
    public static final String INSEM_AI      = "ARTIFICIAL_INSEMINATION";
    public static final String INSEM_ET      = "EMBRYO_TRANSFER";
    public static final String INSEM_UNKNOWN = "UNKNOWN";

    private static final int DEFAULT_GESTATION_DAYS = 152;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tag_number", unique = true)
    private String tagNumber;

    @Column(name = "gender")
    private String gender;

    @Column(name = "status")
    private String status;

    @Column(name = "acquisition_method")
    private String acquisitionMethod;

    @Column(name = "acquisition_source", length = 255)
    private String acquisitionSource;

    @Column(name = "date_received")
    private LocalDate dateReceived;

    @Column(name = "current_value", precision = 12, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "last_birth_date")
    private LocalDate lastBirthDate;

    @Column(name = "offspring_count")
    private Integer offspringCount;

    @Column(name = "pregnancy_status")
    private String pregnancyStatus;

    @Column(name = "conception_date")
    private LocalDate conceptionDate;

    @Column(name = "last_breeding_date")
    private LocalDate lastBreedingDate;

    @Column(name = "first_breeding_date")
    private LocalDate firstBreedingDate;

    @Column(name = "expected_due_date")
    private LocalDate expectedDueDate;

    @Column(name = "photo")
    private String photo;

    @Column(name = "sold_price", precision = 12, scale = 2)
    private BigDecimal soldPrice;

    @Column(name = "is_pregnant")
    private Boolean isPregnant = false;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "insemination_method", length = 50)
    private String inseminationMethod;

    @Column(name = "source_location", length = 255)
    private String sourceLocation;

    @Column(name = "is_draft", nullable = false)
    private Boolean isDraft = false;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_birth_id")
    private LivestockBirth draftBirthEvent;

    @ManyToOne
    @JoinColumn(name = "livestock_category_id")
    private LivestockCategory livestockCategory;

    @ManyToOne
    @JoinColumn(name = "beneficiary_id")
    private Beneficiary beneficiary;

    @ManyToOne
    @JoinColumn(name = "mother_id")
    private Livestock mother;

    @ManyToOne
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Transient
    private Integer pregnancyMonths;

    @Transient
    private String livestockCategoryIdValue;

    @Transient
    private String beneficiaryIdValue;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (isDraft == null) isDraft = false;
        if (isPregnant == null) isPregnant = false;
        if (isDeleted == null) isDeleted = false;
        recalculateDueDate();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        recalculateDueDate();
    }

    public Livestock() {}

    // ── Computed Methods ──────────────────────────────────────────────────────

    public int gestationDays() {
        if (livestockCategory != null
                && livestockCategory.getGestationPeriodMonths() != null
                && livestockCategory.getGestationPeriodMonths() > 0) {
            return (int) Math.round(livestockCategory.getGestationPeriodMonths() * 30.4375);
        }
        return DEFAULT_GESTATION_DAYS;
    }

    public Integer getWeeksPregnant() {
        if (conceptionDate == null) return null;
        long days = ChronoUnit.DAYS.between(conceptionDate, LocalDate.now());
        if (days < 0) return 0;
        return (int) (days / 7);
    }

    public Integer getDaysRemaining() {
        LocalDate due = resolvedDueDate();
        if (due == null) return null;
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), due);
    }

    public Integer getGestationProgressPercent() {
        if (conceptionDate == null) return null;
        long daysElapsed = ChronoUnit.DAYS.between(conceptionDate, LocalDate.now());
        if (daysElapsed < 0) return 0;
        int total = gestationDays();
        return (int) Math.min(100, Math.round(daysElapsed * 100.0 / total));
    }

    public String getGestationStageLabel() {
        Integer pct = getGestationProgressPercent();
        if (pct == null) return "Unknown";
        if (pct < 33) return "Early";
        if (pct < 66) return "Mid";
        if (pct < 90) return "Late";
        return "Near Term";
    }

    public LocalDate resolvedDueDate() {
        if (expectedDueDate != null) return expectedDueDate;
        if (conceptionDate != null) return conceptionDate.plusDays(gestationDays());
        return null;
    }

    public void recalculateDueDate() {
        if (conceptionDate != null) {
            this.expectedDueDate = conceptionDate.plusDays(gestationDays());
        }
    }

    public String resolvedAcquisitionSource() {
        if (acquisitionSource != null && !acquisitionSource.isBlank()) {
            return acquisitionSource;
        }
        if (ACQ_BIRTH.equals(acquisitionMethod)) {
            if (mother != null) {
                return "Born on this farm — Mother: " + mother.getTagNumber();
            }
            return "Born on this farm";
        }
        if (sourceLocation != null && !sourceLocation.isBlank()) {
            return sourceLocation;
        }
        if (ACQ_PURCHASE.equals(acquisitionMethod)) return "Purchased (source not recorded)";
        if (ACQ_DONATION.equals(acquisitionMethod)) return "Donated (source not recorded)";
        if (ACQ_TRANSFER.equals(acquisitionMethod)) return "Transferred (source not recorded)";
        return "Unknown origin";
    }

    public String inseminationMethodLabel() {
        if (inseminationMethod == null || inseminationMethod.isBlank()) return "Not recorded";
        return switch (inseminationMethod) {
            case INSEM_NATURAL -> "Natural Mating";
            case INSEM_AI -> "Artificial Insemination (AI)";
            case INSEM_ET -> "Embryo Transfer (ET)";
            case INSEM_UNKNOWN -> "Unknown";
            default -> inseminationMethod;
        };
    }

    // ── Getters and Setters ───────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTagNumber() { return tagNumber; }
    public void setTagNumber(String tagNumber) { this.tagNumber = tagNumber; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAcquisitionMethod() { return acquisitionMethod; }
    public void setAcquisitionMethod(String acquisitionMethod) { this.acquisitionMethod = acquisitionMethod; }

    public String getAcquisitionSource() { return acquisitionSource; }
    public void setAcquisitionSource(String acquisitionSource) { this.acquisitionSource = acquisitionSource; }

    public LocalDate getDateReceived() { return dateReceived; }
    public void setDateReceived(LocalDate dateReceived) { this.dateReceived = dateReceived; }

    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }

    public LocalDate getLastBirthDate() { return lastBirthDate; }
    public void setLastBirthDate(LocalDate lastBirthDate) { this.lastBirthDate = lastBirthDate; }

    public Integer getOffspringCount() { return offspringCount; }
    public void setOffspringCount(Integer offspringCount) { this.offspringCount = offspringCount; }

    public String getPregnancyStatus() { return pregnancyStatus; }
    public void setPregnancyStatus(String pregnancyStatus) { this.pregnancyStatus = pregnancyStatus; }

    public LocalDate getConceptionDate() { return conceptionDate; }
    public void setConceptionDate(LocalDate conceptionDate) {
        this.conceptionDate = conceptionDate;
        recalculateDueDate();
    }

    public LocalDate getLastBreedingDate() { return lastBreedingDate; }
    public void setLastBreedingDate(LocalDate lastBreedingDate) { this.lastBreedingDate = lastBreedingDate; }

    public LocalDate getFirstBreedingDate() { return firstBreedingDate; }
    public void setFirstBreedingDate(LocalDate firstBreedingDate) { this.firstBreedingDate = firstBreedingDate; }

    public LocalDate getExpectedDueDate() { return expectedDueDate; }
    public void setExpectedDueDate(LocalDate expectedDueDate) { this.expectedDueDate = expectedDueDate; }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public BigDecimal getSoldPrice() { return soldPrice; }
    public void setSoldPrice(BigDecimal soldPrice) { this.soldPrice = soldPrice; }

    public Boolean getIsPregnant() { return isPregnant; }
    public void setIsPregnant(Boolean isPregnant) { this.isPregnant = isPregnant; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getInseminationMethod() { return inseminationMethod; }
    public void setInseminationMethod(String inseminationMethod) { this.inseminationMethod = inseminationMethod; }

    public String getSourceLocation() { return sourceLocation; }
    public void setSourceLocation(String sourceLocation) { this.sourceLocation = sourceLocation; }

    public Boolean getIsDraft() { return isDraft; }
    public void setIsDraft(Boolean isDraft) { this.isDraft = isDraft; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

    public LivestockBirth getDraftBirthEvent() { return draftBirthEvent; }
    public void setDraftBirthEvent(LivestockBirth draftBirthEvent) { this.draftBirthEvent = draftBirthEvent; }

    public LivestockCategory getLivestockCategory() { return livestockCategory; }
    public void setLivestockCategory(LivestockCategory livestockCategory) {
        this.livestockCategory = livestockCategory;
        recalculateDueDate();
    }

    public Beneficiary getBeneficiary() { return beneficiary; }
    public void setBeneficiary(Beneficiary beneficiary) { this.beneficiary = beneficiary; }

    public Livestock getMother() { return mother; }
    public void setMother(Livestock mother) { this.mother = mother; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Deprecated
    public Integer getPregnancyMonths() { return pregnancyMonths; }
    @Deprecated
    public void setPregnancyMonths(Integer pregnancyMonths) { this.pregnancyMonths = pregnancyMonths; }

    public String getLivestockCategoryIdValue() { return livestockCategoryIdValue; }
    public void setLivestockCategoryIdValue(String livestockCategoryIdValue) { this.livestockCategoryIdValue = livestockCategoryIdValue; }

    public String getBeneficiaryIdValue() { return beneficiaryIdValue; }
    public void setBeneficiaryIdValue(String beneficiaryIdValue) { this.beneficiaryIdValue = beneficiaryIdValue; }
}