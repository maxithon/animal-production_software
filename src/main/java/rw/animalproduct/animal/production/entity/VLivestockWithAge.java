package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "v_livestock_with_age")
public class VLivestockWithAge {

    @Id
    private UUID id;

    @Column(name = "tag_number")
    private String tagNumber;

    @Column(name = "gender")
    private String gender;

    @Column(name = "photo")
    private String photo;

    @Column(name = "date_received")
    private LocalDate dateReceived;

    @Column(name = "last_birth_date")
    private LocalDate lastBirthDate;

    @Column(name = "offspring_count")
    private Integer offspringCount;

    @Column(name = "current_value")
    private BigDecimal currentValue;

    @Column(name = "acquisition_method")
    private String acquisitionMethod;

    @Column(name = "sold_price")
    private BigDecimal soldPrice;

    @Column(name = "status")
    private String status;

    @Column(name = "conception_date")
    private LocalDate conceptionDate;

    @Column(name = "last_breeding_date")
    private LocalDate lastBreedingDate;

    @Column(name = "pregnancy_status")
    private String pregnancyStatus;

    @Column(name = "first_breeding_date")
    private LocalDate firstBreedingDate;

    @Column(name = "expected_due_date")
    private LocalDate expectedDueDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "is_deleted")
    private Boolean isDeleted;

    @Column(name = "is_pregnant")
    private Boolean isPregnant;

    // View-specific calculated fields
    @Column(name = "age_in_days")
    private Integer ageInDays;

    @Column(name = "age_in_months")
    private Integer ageInMonths;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "category_code")
    private String categoryCode;

    @Column(name = "gestation_period_months")
    private Integer gestationPeriodMonths;

    @Column(name = "lifecycle_stage")
    private String lifecycleStage;

    // ✅ ONLY IDs (NO RELATIONSHIPS)
    @Column(name = "livestock_category_id")
    private UUID livestockCategoryId;

    @Column(name = "abaragizwa_amatungo_id")
    private UUID abaragizwaAmatungoId;

    @Column(name = "location_id")
    private UUID locationId;

    @Column(name = "mother_id")
    private UUID motherId;

    // Constructors
    public VLivestockWithAge() {}

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTagNumber() {
        return tagNumber;
    }

    public void setTagNumber(String tagNumber) {
        this.tagNumber = tagNumber;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public LocalDate getDateReceived() {
        return dateReceived;
    }

    public void setDateReceived(LocalDate dateReceived) {
        this.dateReceived = dateReceived;
    }

    public LocalDate getLastBirthDate() {
        return lastBirthDate;
    }

    public void setLastBirthDate(LocalDate lastBirthDate) {
        this.lastBirthDate = lastBirthDate;
    }

    public Integer getOffspringCount() {
        return offspringCount;
    }

    public void setOffspringCount(Integer offspringCount) {
        this.offspringCount = offspringCount;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public String getAcquisitionMethod() {
        return acquisitionMethod;
    }

    public void setAcquisitionMethod(String acquisitionMethod) {
        this.acquisitionMethod = acquisitionMethod;
    }

    public BigDecimal getSoldPrice() {
        return soldPrice;
    }

    public void setSoldPrice(BigDecimal soldPrice) {
        this.soldPrice = soldPrice;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getConceptionDate() {
        return conceptionDate;
    }

    public void setConceptionDate(LocalDate conceptionDate) {
        this.conceptionDate = conceptionDate;
    }

    public LocalDate getLastBreedingDate() {
        return lastBreedingDate;
    }

    public void setLastBreedingDate(LocalDate lastBreedingDate) {
        this.lastBreedingDate = lastBreedingDate;
    }

    public String getPregnancyStatus() {
        return pregnancyStatus;
    }

    public void setPregnancyStatus(String pregnancyStatus) {
        this.pregnancyStatus = pregnancyStatus;
    }

    public LocalDate getFirstBreedingDate() {
        return firstBreedingDate;
    }

    public void setFirstBreedingDate(LocalDate firstBreedingDate) {
        this.firstBreedingDate = firstBreedingDate;
    }

    public LocalDate getExpectedDueDate() {
        return expectedDueDate;
    }

    public void setExpectedDueDate(LocalDate expectedDueDate) {
        this.expectedDueDate = expectedDueDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public Boolean getIsPregnant() {
        return isPregnant;
    }

    public void setIsPregnant(Boolean isPregnant) {
        this.isPregnant = isPregnant;
    }

    public Integer getAgeInDays() {
        return ageInDays;
    }

    public void setAgeInDays(Integer ageInDays) {
        this.ageInDays = ageInDays;
    }

    public Integer getAgeInMonths() {
        return ageInMonths;
    }

    public void setAgeInMonths(Integer ageInMonths) {
        this.ageInMonths = ageInMonths;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public Integer getGestationPeriodMonths() {
        return gestationPeriodMonths;
    }

    public void setGestationPeriodMonths(Integer gestationPeriodMonths) {
        this.gestationPeriodMonths = gestationPeriodMonths;
    }

    public String getLifecycleStage() {
        return lifecycleStage;
    }

    public void setLifecycleStage(String lifecycleStage) {
        this.lifecycleStage = lifecycleStage;
    }

    public UUID getLivestockCategoryId() {
        return livestockCategoryId;
    }

    public void setLivestockCategoryId(UUID livestockCategoryId) {
        this.livestockCategoryId = livestockCategoryId;
    }

    public UUID getAbaragizwaAmatungoId() {
        return abaragizwaAmatungoId;
    }

    public void setAbaragizwaAmatungoId(UUID abaragizwaAmatungoId) {
        this.abaragizwaAmatungoId = abaragizwaAmatungoId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public UUID getMotherId() {
        return motherId;
    }

    public void setMotherId(UUID motherId) {
        this.motherId = motherId;
    }

    // Helper method
    public String getAgeDisplay() {
        if (ageInMonths == null || ageInDays == null) {
            return "Unknown";
        }
        if (ageInMonths < 1) {
            return ageInDays + " days";
        } else if (ageInMonths < 12) {
            return ageInMonths + " months";
        } else {
            int years = ageInMonths / 12;
            int remainingMonths = ageInMonths % 12;
            if (remainingMonths == 0) {
                return years + " year" + (years > 1 ? "s" : "");
            }
            return years + " year" + (years > 1 ? "s" : "") + " " +
                    remainingMonths + " month" + (remainingMonths > 1 ? "s" : "");
        }
    }

    @Override
    public String toString() {
        return "VLivestockWithAge{" +
                "id=" + id +
                ", tagNumber='" + tagNumber + '\'' +
                ", gender='" + gender + '\'' +
                ", ageInMonths=" + ageInMonths +
                ", lifecycleStage='" + lifecycleStage + '\'' +
                ", categoryName='" + categoryName + '\'' +
                '}';
    }
}