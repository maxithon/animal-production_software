package rw.animalproduct.animal.production.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "livestock_categories"
)
public class LivestockCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotEmpty(message = "Category code is required")
    @Size(
            min = 1,
            max = 20,
            message = "Code must be 1–20 characters"
    )
    @Column(
            name = "code",
            unique = true,
            nullable = false,
            length = 20
    )
    private String code;

    @NotEmpty(message = "Category name is required")
    @Size(
            min = 2,
            max = 100,
            message = "Name must be 2–100 characters"
    )
    @Column(
            name = "name",
            nullable = false,
            length = 100
    )
    private String name;

    /**
     * Gestation period in months.
     *
     * This is retained because it already exists in the database
     * and may be used by existing reports or legacy business logic.
     *
     * The more precise value is gestationPeriodDays.
     */
    @Min(
            value = 1,
            message = "Gestation period must be at least 1 month"
    )
    @Max(
            value = 24,
            message = "Gestation period looks too long — please check the value"
    )
    @Column(name = "gestation_period_months")
    private Integer gestationPeriodMonths;

    /**
     * Precise gestation period in days.
     *
     * Example:
     * Cow  = 283 days
     * Goat = 150 days
     */
    @Positive(
            message = "Gestation period (days) must be greater than 0"
    )
    @Column(name = "gestation_period_days")
    private Integer gestationPeriodDays;

    /**
     * Minimum age at which animals of this category
     * are considered ready for breeding.
     */
    @Min(
            value = 1,
            message = "Minimum breeding age must be at least 1 month"
    )
    @Max(
            value = 120,
            message = "Minimum breeding age looks too high — please check the value"
    )
    @Column(name = "min_breeding_age_months")
    private Integer minBreedingAgeMonths;

    /**
     * Maximum recommended breeding age.
     */
    @Min(
            value = 1,
            message = "Maximum breeding age must be at least 1 month"
    )
    @Max(
            value = 240,
            message = "Maximum breeding age looks too high — please check the value"
    )
    @Column(name = "max_breeding_age_months")
    private Integer maxBreedingAgeMonths;

    /**
     * Minimum number of days after giving birth before
     * the female is considered recovered enough to breed again.
     */
    @PositiveOrZero(
            message = "Postpartum recovery days cannot be negative"
    )
    @Column(
            name = "postpartum_recovery_days",
            nullable = false
    )
    private Integer postpartumRecoveryDays = 45;

    /**
     * Typical age (in days) at which offspring of this category are
     * weaned off the mother. This is a biological/management constant
     * per category — NOT something entered per birth record. It's used
     * by LivestockBirthService to auto-calculate each birth's Weaning
     * Date (birthDate + weaningPeriodDays), the same way
     * postpartumRecoveryDays is used to suggest the Next Breeding Date.
     *
     * Cow  ≈ 90 days
     * Goat ≈ 60-90 days
     */
    @PositiveOrZero(
            message = "Weaning period cannot be negative"
    )
    @Column(
            name = "weaning_period_days",
            nullable = false
    )
    private Integer weaningPeriodDays = 90;

    /**
     * Typical number of offspring expected per birth.
     *
     * Cow  = 1
     * Goat = 2
     */
    @Min(
            value = 1,
            message = "Typical litter size must be at least 1"
    )
    @Column(
            name = "typical_litter_size",
            nullable = false
    )
    private Integer typicalLitterSize = 1;

    @Column(
            name = "description",
            columnDefinition = "TEXT"
    )
    private String description;

    /**
     * Soft-delete flag.
     *
     * false = active
     * true  = deleted
     */
    @Column(
            name = "is_deleted",
            nullable = false
    )
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ---------------------------------------------------------
    // Constructors
    // ---------------------------------------------------------

    public LivestockCategory() {
    }

    // ---------------------------------------------------------
    // Business validation
    // ---------------------------------------------------------

    /**
     * Maximum breeding age must be greater than minimum breeding age
     * when both values are provided.
     */
    @AssertTrue(
            message = "Maximum breeding age must be greater than minimum breeding age"
    )
    public boolean isBreedingAgeRangeValid() {

        if (minBreedingAgeMonths == null ||
                maxBreedingAgeMonths == null) {
            return true;
        }

        return maxBreedingAgeMonths > minBreedingAgeMonths;
    }

    // ---------------------------------------------------------
    // Getters and Setters
    // ---------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getGestationPeriodMonths() {
        return gestationPeriodMonths;
    }

    public void setGestationPeriodMonths(Integer gestationPeriodMonths) {
        this.gestationPeriodMonths = gestationPeriodMonths;
    }

    public Integer getGestationPeriodDays() {
        return gestationPeriodDays;
    }

    public void setGestationPeriodDays(Integer gestationPeriodDays) {
        this.gestationPeriodDays = gestationPeriodDays;
    }

    public Integer getMinBreedingAgeMonths() {
        return minBreedingAgeMonths;
    }

    public void setMinBreedingAgeMonths(Integer minBreedingAgeMonths) {
        this.minBreedingAgeMonths = minBreedingAgeMonths;
    }

    public Integer getMaxBreedingAgeMonths() {
        return maxBreedingAgeMonths;
    }

    public void setMaxBreedingAgeMonths(Integer maxBreedingAgeMonths) {
        this.maxBreedingAgeMonths = maxBreedingAgeMonths;
    }

    public Integer getPostpartumRecoveryDays() {
        return postpartumRecoveryDays;
    }

    public void setPostpartumRecoveryDays(Integer postpartumRecoveryDays) {
        this.postpartumRecoveryDays = postpartumRecoveryDays;
    }

    public Integer getWeaningPeriodDays() {
        return weaningPeriodDays;
    }

    public void setWeaningPeriodDays(Integer weaningPeriodDays) {
        this.weaningPeriodDays = weaningPeriodDays;
    }

    public Integer getTypicalLitterSize() {
        return typicalLitterSize;
    }

    public void setTypicalLitterSize(Integer typicalLitterSize) {
        this.typicalLitterSize = typicalLitterSize;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // ---------------------------------------------------------
    // toString
    // ---------------------------------------------------------

    @Override
    public String toString() {
        return "LivestockCategory{" +
                "id=" + id +
                ", code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", gestationPeriodDays=" + gestationPeriodDays +
                ", minBreedingAgeMonths=" + minBreedingAgeMonths +
                ", maxBreedingAgeMonths=" + maxBreedingAgeMonths +
                ", postpartumRecoveryDays=" + postpartumRecoveryDays +
                ", weaningPeriodDays=" + weaningPeriodDays +
                ", typicalLitterSize=" + typicalLitterSize +
                ", isDeleted=" + isDeleted +
                '}';
    }
}