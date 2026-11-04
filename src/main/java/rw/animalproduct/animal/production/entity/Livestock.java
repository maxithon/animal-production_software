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

    public static final String STATUS_ACTIVE      = "ACTIVE";
    public static final String STATUS_SOLD        = "SOLD";
    public static final String STATUS_DEAD        = "DEAD";
    public static final String STATUS_SICK        = "SICK";
    public static final String STATUS_PREGNANT    = "PREGNANT";
    public static final String STATUS_TRANSFERRED = "TRANSFERRED";
    public static final String STATUS_INACTIVE    = "INACTIVE";
    public static final String REPRO_OPEN           = "OPEN";
    public static final String REPRO_PREGNANT       = "PREGNANT";
    public static final String REPRO_NURSING        = "NURSING";
    public static final String REPRO_DRY            = "DRY";
    public static final String REPRO_INACTIVE       = "INACTIVE";
    public static final String REPRO_NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String REPRO_READY_TO_BREED = "READY_TO_BREED";
    public static final String REPRO_BRED            = "BRED";
    public static final String HEALTH_GOOD   = "GOOD";
    public static final String HEALTH_FAIR   = "FAIR";
    public static final String HEALTH_SICK   = "SICK";
    public static final String HEALTH_POOR   = "POOR";
    public static final String HEALTH_CRITICAL = "CRITICAL";
    public static final String HEALTH_UNDER_TREATMENT = "UNDER_TREATMENT";
    public static final String HEALTH_HEALTHY = "HEALTHY";

    public static final String ACQ_BIRTH    = "BIRTH";
    public static final String ACQ_PURCHASE = "PURCHASE";
    public static final String ACQ_DONATION = "DONATION";
    public static final String ACQ_TRANSFER = "TRANSFER";
    public static final String ACQ_OTHER    = "OTHER";

    public static final String INSEM_NATURAL = "NATURAL_MATING";
    public static final String INSEM_AI      = "ARTIFICIAL_INSEMINATION";
    public static final String INSEM_ET      = "EMBRYO_TRANSFER";
    public static final String INSEM_UNKNOWN = "UNKNOWN";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tag_number", unique = true)
    private String tagNumber;

    @Column(name = "gender")
    private String gender;

    @Column(name = "status")
    private String status;

    @Column(name = "reproductive_status")
    private String reproductiveStatus;

    @Column(name = "health_status")
    private String healthStatus;

    @Column(name = "acquisition_method")
    private String acquisitionMethod;

    @Column(name = "acquisition_source", length = 255)
    private String acquisitionSource;

    @Column(name = "date_received")
    private LocalDate dateReceived;

    @Column(name = "current_value", precision = 15, scale = 2)
    private BigDecimal currentValue;

    @Transient
    private LocalDate lastBirthDate;

    @Column(name = "offspring_count")
    private Integer offspringCount;

    @Transient
    private String pregnancyStatus;

    @Transient
    private LocalDate lastBreedingDate;

    @Transient
    private LocalDate firstBreedingDate;

    @Transient
    private LocalDate expectedDueDate;

    @Column(name = "photo")
    private String photo;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Transient
    private BigDecimal soldPrice;

    @Transient
    private Boolean isPregnant = false;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Transient
    private String inseminationMethod;

    @Column(name = "source_location", length = 255)
    private String sourceLocation;

    @Column(name = "is_draft", nullable = false)
    private Boolean isDraft = false;

    @Column(name = "is_group", nullable = false)
    private Boolean isGroup = false;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "breeding_ready_notified_at")
    private LocalDateTime breedingReadyNotifiedAt;

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
    @JoinColumn(name = "father_id")
    private Livestock father;

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

    @Column(name = "last_lifecycle_stage", length = 30)
    private String lastLifecycleStage;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (isDraft == null) isDraft = false;
        if (isPregnant == null) isPregnant = false;
        if (isGroup == null) isGroup = false;
        if (reproductiveStatus == null) reproductiveStatus = REPRO_OPEN;
        if (healthStatus == null) healthStatus = HEALTH_HEALTHY;
        if (status == null) status = STATUS_ACTIVE;
        if (Boolean.FALSE.equals(isGroup) && (quantity == null || quantity < 1)) {
            quantity = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (REPRO_PREGNANT.equals(reproductiveStatus)) {
            isPregnant = true;
        } else if (isPregnant != null && isPregnant) {
            reproductiveStatus = REPRO_PREGNANT;
        }
    }

    public Livestock() {}

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

    public boolean isReproductivelyActive() {
        return REPRO_OPEN.equals(reproductiveStatus)
                || REPRO_NURSING.equals(reproductiveStatus)
                || REPRO_PREGNANT.equals(reproductiveStatus);
    }

    public boolean isBreedingEligible() {
        return "FEMALE".equalsIgnoreCase(gender)
                && REPRO_OPEN.equals(reproductiveStatus)
                && !STATUS_DEAD.equals(status)
                && !STATUS_SOLD.equals(status)
                && !STATUS_TRANSFERRED.equals(status);
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public boolean isOnFarm() {
        return STATUS_ACTIVE.equals(status)
                || STATUS_SICK.equals(status)
                || STATUS_PREGNANT.equals(status);
    }

    public boolean hasCurrentValue() {
        return currentValue != null && currentValue.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getCurrentValueSafe() {
        return currentValue != null ? currentValue : BigDecimal.ZERO;
    }

    public Long getAgeInMonths() {
        if (birthDate == null) return null;
        return ChronoUnit.MONTHS.between(birthDate, LocalDate.now());
    }

    public Long getAgeInDays() {
        if (birthDate == null) return null;
        return ChronoUnit.DAYS.between(birthDate, LocalDate.now());
    }

    public String getAgeDisplay() {
        Long days = getAgeInDays();
        if (days == null) return "Unknown";
        if (days < 30) return days + " day" + (days == 1 ? "" : "s");
        long months = days / 30;
        if (months < 12) return months + " month" + (months == 1 ? "" : "s");
        long years = days / 365;
        long remMonths = (days % 365) / 30;
        String s = years + " year" + (years == 1 ? "" : "s");
        if (remMonths > 0) s += ", " + remMonths + " month" + (remMonths == 1 ? "" : "s");
        return s;
    }

    public String healthStatusLabel() {
        if (healthStatus == null) return "Unknown";
        return switch (healthStatus) {
            case HEALTH_HEALTHY -> "Healthy";
            case HEALTH_GOOD -> "Healthy";
            case HEALTH_FAIR -> "Fair";
            case HEALTH_SICK -> "Sick";
            case HEALTH_UNDER_TREATMENT -> "Under Treatment";
            case HEALTH_POOR -> "Poor";
            case HEALTH_CRITICAL -> "Critical";
            default -> healthStatus;
        };
    }

    public String reproductiveStatusLabel() {
        if (reproductiveStatus == null) return "Unknown";
        return switch (reproductiveStatus) {
            case REPRO_OPEN -> "Open";
            case REPRO_PREGNANT -> "Pregnant";
            case REPRO_NURSING -> "Nursing";
            case REPRO_DRY -> "Dry";
            case REPRO_INACTIVE -> "Inactive";
            case REPRO_NOT_APPLICABLE -> "Not Applicable";
            case REPRO_READY_TO_BREED -> "Ready to Breed";
            case REPRO_BRED -> "Bred";
            default -> reproductiveStatus;
        };
    }

    public boolean isBatch() { return Boolean.TRUE.equals(isGroup); }

    public int getHeadCount() {
        if (Boolean.TRUE.equals(isGroup) && quantity != null && quantity > 0) {
            return quantity;
        }
        return 1;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTagNumber() { return tagNumber; }
    public void setTagNumber(String tagNumber) { this.tagNumber = tagNumber; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReproductiveStatus() { return reproductiveStatus; }
    public void setReproductiveStatus(String reproductiveStatus) {
        this.reproductiveStatus = reproductiveStatus;
        if (REPRO_PREGNANT.equals(reproductiveStatus)) {
            this.isPregnant = true;
        } else if (!REPRO_PREGNANT.equals(reproductiveStatus)) {
            this.isPregnant = false;
        }
    }
    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }

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

    public LocalDate getLastBreedingDate() { return lastBreedingDate; }
    public void setLastBreedingDate(LocalDate lastBreedingDate) { this.lastBreedingDate = lastBreedingDate; }

    public LocalDate getFirstBreedingDate() { return firstBreedingDate; }
    public void setFirstBreedingDate(LocalDate firstBreedingDate) { this.firstBreedingDate = firstBreedingDate; }

    public LocalDate getExpectedDueDate() { return expectedDueDate; }
    public void setExpectedDueDate(LocalDate expectedDueDate) { this.expectedDueDate = expectedDueDate; }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getSoldPrice() { return soldPrice; }
    public void setSoldPrice(BigDecimal soldPrice) { this.soldPrice = soldPrice; }

    public Boolean getIsPregnant() { return isPregnant; }
    public void setIsPregnant(Boolean isPregnant) {
        this.isPregnant = isPregnant;
        if (isPregnant != null && isPregnant) {
            this.reproductiveStatus = REPRO_PREGNANT;
        } else if (isPregnant != null && !isPregnant) {
            if (!REPRO_NURSING.equals(this.reproductiveStatus)
                    && !REPRO_DRY.equals(this.reproductiveStatus)) {
                this.reproductiveStatus = REPRO_OPEN;
            }
        }
    }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getInseminationMethod() { return inseminationMethod; }
    public void setInseminationMethod(String inseminationMethod) { this.inseminationMethod = inseminationMethod; }

    public String getSourceLocation() { return sourceLocation; }
    public void setSourceLocation(String sourceLocation) { this.sourceLocation = sourceLocation; }

    public Boolean getIsDraft() { return isDraft; }
    public void setIsDraft(Boolean isDraft) { this.isDraft = isDraft; }

    public Boolean getIsGroup() { return isGroup; }
    public void setIsGroup(Boolean isGroup) { this.isGroup = isGroup; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getBreedingReadyNotifiedAt() { return breedingReadyNotifiedAt; }
    public void setBreedingReadyNotifiedAt(LocalDateTime breedingReadyNotifiedAt) { this.breedingReadyNotifiedAt = breedingReadyNotifiedAt; }

    public LivestockBirth getDraftBirthEvent() { return draftBirthEvent; }
    public void setDraftBirthEvent(LivestockBirth draftBirthEvent) { this.draftBirthEvent = draftBirthEvent; }

    public LivestockCategory getLivestockCategory() { return livestockCategory; }
    public void setLivestockCategory(LivestockCategory livestockCategory) { this.livestockCategory = livestockCategory; }

    public Beneficiary getBeneficiary() { return beneficiary; }
    public void setBeneficiary(Beneficiary beneficiary) { this.beneficiary = beneficiary; }

    public Livestock getMother() { return mother; }
    public void setMother(Livestock mother) { this.mother = mother; }

    public Livestock getFather() { return father; }
    public void setFather(Livestock father) { this.father = father; }

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

    public String getLastLifecycleStage() { return lastLifecycleStage; }
    public void setLastLifecycleStage(String lastLifecycleStage) { this.lastLifecycleStage = lastLifecycleStage; }

    @Override
    public String toString() {
        return "Livestock{" +
                "id=" + id +
                ", tagNumber='" + tagNumber + '\'' +
                ", gender='" + gender + '\'' +
                ", status='" + status + '\'' +
                ", reproductiveStatus='" + reproductiveStatus + '\'' +
                ", healthStatus='" + healthStatus + '\'' +
                ", livestockCategory=" + (livestockCategory != null ? livestockCategory.getName() : null) +
                '}';
    }
}