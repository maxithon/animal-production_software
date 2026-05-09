package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Entity
@Table(name = "livestock_categories",
        indexes = {
                @Index(name = "idx_category_code", columnList = "code"),
                @Index(name = "idx_category_name", columnList = "name")
        })
public class LivestockCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotEmpty(message = "Category code is required")
    @Size(min = 1, max = 20, message = "Code must be 1–20 characters")
    @Column(name = "code", unique = true, nullable = false, length = 20)
    private String code;

    @NotEmpty(message = "Category name is required")
    @Size(min = 2, max = 100, message = "Name must be 2–100 characters")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Minimum months required between breeding date and birth date.
     * Used to validate:
     *   1. Whether a birth can be recorded (gestation complete check)
     *   2. Whether a new breeding can be recorded (interval since last birth)
     *   3. Whether a new breeding can be recorded (too soon since last breeding)
     *
     * From your DB: Goats = 5, Pigs = 4
     */
    @Column(name = "gestation_period_months")
    private Integer gestationPeriodMonths;

    /**
     * Optional: typical min age in months before first breeding.
     * Used in ready-to-breed eligibility checks.
     */
    @Column(name = "min_breeding_age_months")
    private Integer minBreedingAgeMonths;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    // NOTE: @OneToMany to Livestock intentionally removed.
    // Loading a category was loading the entire livestock table.
    // Use LivestockRepository.findByLivestockCategoryId() instead.

    public LivestockCategory() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getGestationPeriodMonths() { return gestationPeriodMonths; }
    public void setGestationPeriodMonths(Integer gestationPeriodMonths) {
        this.gestationPeriodMonths = gestationPeriodMonths;
    }

    public Integer getMinBreedingAgeMonths() { return minBreedingAgeMonths; }
    public void setMinBreedingAgeMonths(Integer minBreedingAgeMonths) {
        this.minBreedingAgeMonths = minBreedingAgeMonths;
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    @Override
    public String toString() {
        return "LivestockCategory{id=" + id + ", name='" + name
                + "', code='" + code
                + "', gestationMonths=" + gestationPeriodMonths + "}";
    }
}