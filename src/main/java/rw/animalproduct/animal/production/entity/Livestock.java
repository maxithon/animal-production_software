package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    public static final String INSEM_NATURAL              = "NATURAL_MATING";
    public static final String INSEM_AI                   = "ARTIFICIAL_INSEMINATION";
    public static final String INSEM_ET                   = "EMBRYO_TRANSFER";
    public static final String INSEM_UNKNOWN              = "UNKNOWN";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tag_number", unique = true)
    private String tagNumber;

    @Column(name = "gender")
    private String gender;

    @Column(name = "status")
    private String status;

    @Column(name = "acquisition_method")
    private String acquisitionMethod;

    /**
     * Human-readable description of where this animal came from.
     */
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

    /**
     * The method used to make this animal pregnant (or the method used at last breeding).
     *
     * Populated at registration for purchased/donated/transferred animals
     * that arrived already pregnant, and also recordable for any animal
     * regardless of current pregnancy status (historical breeding record).
     *
     * Values (use the constants above):
     *   NATURAL_MATING           – bull/ram/buck/boar mounted naturally
     *   ARTIFICIAL_INSEMINATION  – AI technician inserted semen
     *   EMBRYO_TRANSFER          – ET procedure
     *   UNKNOWN                  – method not recorded
     *
     * This field is surfaced in:
     *   • Pregnancy Tracking dashboard (livestock-pregnancy-tracking.html)
     *   • PregnancyRowDTO  (farm-bred animals)
     *   • PurchasedPregnancyRowDTO (purchased/external animals)
     *
     * DB column: insemination_method VARCHAR(50)
     * Migration: ALTER TABLE livestock ADD COLUMN insemination_method VARCHAR(50);
     */
    @Column(name = "insemination_method", length = 50)
    private String inseminationMethod;

    /**
     * Legacy field — kept for backward compatibility.
     */
    @Column(name = "source_location", length = 255)
    private String sourceLocation;

    @Column(name = "is_draft", nullable = false)
    private Boolean isDraft = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_birth_id")
    private LivestockBirth draftBirthEvent;

    @Transient
    private Integer pregnancyMonths;

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

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    // ── Transient fields for form binding ─────────────────────────────────────
    @Transient
    private String livestockCategoryIdValue;

    @Transient
    private String beneficiaryIdValue;

    @PrePersist
    protected void onCreate() {
        if (createdAt  == null) createdAt  = LocalDateTime.now();
        if (isDraft    == null) isDraft    = false;
        if (isPregnant == null) isPregnant = false;
        if (isDeleted  == null) isDeleted  = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Livestock() {}

    // ── Convenience: build acquisition source label ───────────────────────────
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

    /**
     * Human-readable label for the insemination method.
     * Safe to call from Thymeleaf templates.
     */
    public String inseminationMethodLabel() {
        if (inseminationMethod == null || inseminationMethod.isBlank()) return "Not recorded";
        return switch (inseminationMethod) {
            case INSEM_NATURAL -> "Natural Mating";
            case INSEM_AI      -> "Artificial Insemination (AI)";
            case INSEM_ET      -> "Embryo Transfer (ET)";
            case INSEM_UNKNOWN -> "Unknown";
            default            -> inseminationMethod;
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
    public void setAcquisitionMethod(String acquisitionMethod) {
        this.acquisitionMethod = acquisitionMethod;
    }

    public String getAcquisitionSource() { return acquisitionSource; }
    public void setAcquisitionSource(String acquisitionSource) {
        this.acquisitionSource = acquisitionSource;
    }

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
    public void setConceptionDate(LocalDate conceptionDate) { this.conceptionDate = conceptionDate; }

    public LocalDate getLastBreedingDate() { return lastBreedingDate; }
    public void setLastBreedingDate(LocalDate lastBreedingDate) {
        this.lastBreedingDate = lastBreedingDate;
    }

    public LocalDate getFirstBreedingDate() { return firstBreedingDate; }
    public void setFirstBreedingDate(LocalDate firstBreedingDate) {
        this.firstBreedingDate = firstBreedingDate;
    }

    public LocalDate getExpectedDueDate() { return expectedDueDate; }
    public void setExpectedDueDate(LocalDate expectedDueDate) {
        this.expectedDueDate = expectedDueDate;
    }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public BigDecimal getSoldPrice() { return soldPrice; }
    public void setSoldPrice(BigDecimal soldPrice) { this.soldPrice = soldPrice; }

    public Boolean getIsPregnant() { return isPregnant; }
    public void setIsPregnant(Boolean isPregnant) { this.isPregnant = isPregnant; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getInseminationMethod() { return inseminationMethod; }
    public void setInseminationMethod(String inseminationMethod) {
        this.inseminationMethod = inseminationMethod;
    }

    public String getSourceLocation() { return sourceLocation; }
    public void setSourceLocation(String sourceLocation) { this.sourceLocation = sourceLocation; }

    public Boolean getIsDraft() { return isDraft; }
    public void setIsDraft(Boolean isDraft) { this.isDraft = isDraft; }

    public LivestockBirth getDraftBirthEvent() { return draftBirthEvent; }
    public void setDraftBirthEvent(LivestockBirth draftBirthEvent) {
        this.draftBirthEvent = draftBirthEvent;
    }

    public Integer getPregnancyMonths() { return pregnancyMonths; }
    public void setPregnancyMonths(Integer pregnancyMonths) { this.pregnancyMonths = pregnancyMonths; }

    public LivestockCategory getLivestockCategory() { return livestockCategory; }
    public void setLivestockCategory(LivestockCategory cat) { this.livestockCategory = cat; }

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

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public String getLivestockCategoryIdValue() { return livestockCategoryIdValue; }
    public void setLivestockCategoryIdValue(String v) { this.livestockCategoryIdValue = v; }

    public String getBeneficiaryIdValue() { return beneficiaryIdValue; }
    public void setBeneficiaryIdValue(String v) { this.beneficiaryIdValue = v; }
}