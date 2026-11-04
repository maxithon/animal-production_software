package rw.animalproduct.animal.production.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only DTO — all age and lifecycle fields are computed
 * by LivestockWithAgeService; nothing here is stored in the DB.
 */
public class LivestockWithAgeDTO {

    // ── Identity ──────────────────────────────────────────────────────────────
    private UUID      id;
    private String    tagNumber;
    private String    gender;
    private String    status;
    private String    categoryName;

    // ── Health & Reproductive Status (from livestock table) ──────────────────
    private String    healthStatus;
    private String    reproductiveStatus;

    // ── Raw dates (from livestock table) ─────────────────────────────────────
    private LocalDate dateReceived;      // always present for purchased animals
    private LocalDate birthDate;         // present for farm-born animals

    // ── Computed age (set by service) ─────────────────────────────────────────
    private Integer   ageInDays;         // today − COALESCE(birth_date, date_received)
    private Integer   ageInMonths;       // ageInDays / 30
    private Integer   daysToBreedingAge; // max(0, 365 − ageInDays)

    // ── Lifecycle stage (set by service) ──────────────────────────────────────
    // Values: NEWBORN | YOUNG | PRE_BREEDING | READY_TO_BREED
    //         PREGNANT | BREEDING_MALE | MATURE | DEAD | SOLD | UNKNOWN
    private String    lifecycleStage;

    // ── Breeding / pregnancy fields (from livestock table) ───────────────────
    private Boolean   isPregnant;
    private String    pregnancyStatus;
    private LocalDate conceptionDate;
    private LocalDate expectedDueDate;
    private LocalDate firstBreedingDate;
    private LocalDate lastBreedingDate;
    private LocalDate lastBirthDate;
    private Integer   offspringCount;

    // ── Financial ────────────────────────────────────────────────────────────
    private BigDecimal currentValue;

    // ══════════════════════════════════════════════════════════════════════════
    // Getters & Setters
    // ══════════════════════════════════════════════════════════════════════════

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTagNumber() { return tagNumber; }
    public void setTagNumber(String tagNumber) { this.tagNumber = tagNumber; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }

    public String getReproductiveStatus() { return reproductiveStatus; }
    public void setReproductiveStatus(String reproductiveStatus) { this.reproductiveStatus = reproductiveStatus; }

    public LocalDate getDateReceived() { return dateReceived; }
    public void setDateReceived(LocalDate dateReceived) { this.dateReceived = dateReceived; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public Integer getAgeInDays() { return ageInDays; }
    public void setAgeInDays(Integer ageInDays) { this.ageInDays = ageInDays; }

    public Integer getAgeInMonths() { return ageInMonths; }
    public void setAgeInMonths(Integer ageInMonths) { this.ageInMonths = ageInMonths; }

    public Integer getDaysToBreedingAge() { return daysToBreedingAge; }
    public void setDaysToBreedingAge(Integer daysToBreedingAge) { this.daysToBreedingAge = daysToBreedingAge; }

    public String getLifecycleStage() { return lifecycleStage; }
    public void setLifecycleStage(String lifecycleStage) { this.lifecycleStage = lifecycleStage; }

    public Boolean getIsPregnant() { return isPregnant; }
    public void setIsPregnant(Boolean isPregnant) { this.isPregnant = isPregnant; }

    public String getPregnancyStatus() { return pregnancyStatus; }
    public void setPregnancyStatus(String pregnancyStatus) { this.pregnancyStatus = pregnancyStatus; }

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

    public Integer getOffspringCount() { return offspringCount; }
    public void setOffspringCount(Integer offspringCount) { this.offspringCount = offspringCount; }

    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }

    // ══════════════════════════════════════════════════════════════════════════
    // Derived helpers — used directly in Thymeleaf templates
    // ══════════════════════════════════════════════════════════════════════════
    private String locationName;       // leaf location name (e.g. "Nyagatare Village"), or null if unset
    private String locationFullPath;   // "Province › District › Sector › Cell › Village", or null if unset

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public String getLocationFullPath() { return locationFullPath; }
    public void setLocationFullPath(String locationFullPath) { this.locationFullPath = locationFullPath; }
    /** True for the "Ready In" column — animal is under 12 months old. */
    public boolean isApproachingBreedingAge() {
        return ageInDays != null && ageInDays >= 180 && ageInDays < 365;
    }

    /** Human-readable health status label. */
    public String getHealthStatusLabel() {
        if (healthStatus == null) return "Unknown";
        return switch (healthStatus) {
            case "HEALTHY" -> "Healthy";
            case "SICK" -> "Sick";
            case "UNDER_TREATMENT" -> "Under Treatment";
            default -> healthStatus;
        };
    }

    /** Human-readable reproductive status label. */
    public String getReproductiveStatusLabel() {
        if (reproductiveStatus == null) return "Unknown";
        return switch (reproductiveStatus) {
            case "READY_TO_BREED" -> "Ready to Breed";
            case "BRED" -> "Bred";
            case "PREGNANT" -> "Pregnant";
            case "OPEN" -> "Open";
            case "NOT_APPLICABLE" -> "Not Applicable";
            default -> reproductiveStatus;
        };
    }

    /** Human-readable stage label for display. */
    public String getStageBadgeLabel() {
        if (lifecycleStage == null) return "Unknown";
        return switch (lifecycleStage) {
            case "NEWBORN"        -> "Newborn";
            case "YOUNG"          -> "Young";
            case "PRE_BREEDING"   -> "Pre-Breeding";
            case "READY_TO_BREED" -> "Ready to Breed";
            case "PREGNANT"       -> "Pregnant";
            case "BREEDING_MALE"  -> "Breeding Male";
            case "MATURE"         -> "Mature";
            case "DEAD"           -> "Dead";
            case "SOLD"           -> "Sold";
            default               -> lifecycleStage;
        };
    }

    /** Bootstrap color class for the stage badge (used with th:classappend). */
    public String getStageBadgeClass() {
        if (lifecycleStage == null) return "bg-secondary";
        return switch (lifecycleStage) {
            case "NEWBORN"        -> "bg-warning text-dark";
            case "YOUNG"          -> "bg-primary text-white";
            case "PRE_BREEDING"   -> "bg-info text-dark";
            case "READY_TO_BREED" -> "bg-success text-white";
            case "PREGNANT"       -> "bg-danger text-white";
            case "BREEDING_MALE"  -> "bg-primary text-white";
            case "MATURE"         -> "bg-success text-white";
            case "DEAD"           -> "bg-dark text-white";
            case "SOLD"           -> "bg-secondary text-white";
            default               -> "bg-secondary text-white";
        };
    }

    /** Bootstrap color class for health status badge. */
    public String getHealthBadgeClass() {
        if (healthStatus == null) return "bg-secondary";
        return switch (healthStatus) {
            case "HEALTHY" -> "bg-success text-white";
            case "SICK" -> "bg-danger text-white";
            case "UNDER_TREATMENT" -> "bg-warning text-dark";
            default -> "bg-secondary";
        };
    }

    /** Bootstrap color class for reproductive status badge. */
    public String getReproBadgeClass() {
        if (reproductiveStatus == null) return "bg-secondary";
        return switch (reproductiveStatus) {
            case "READY_TO_BREED" -> "bg-purple text-white";
            case "BRED" -> "bg-info text-white";
            case "PREGNANT" -> "bg-pink text-white";
            case "OPEN" -> "bg-light text-dark";
            case "NOT_APPLICABLE" -> "bg-secondary text-white";
            default -> "bg-secondary";
        };
    }

    /** Age display string (e.g., "2y 6m", "8m 15d", "45d"). */
    public String getAgeDisplay() {
        if (ageInDays == null || ageInDays == 0) return "Unknown";
        if (ageInDays < 30) return ageInDays + " days";
        if (ageInDays < 365) {
            int months = ageInDays / 30;
            int days = ageInDays % 30;
            return months + "m" + (days > 0 ? " " + days + "d" : "");
        }
        int years = ageInDays / 365;
        int months = (ageInDays % 365) / 30;
        return years + "y" + (months > 0 ? " " + months + "m" : "");
    }

    public boolean isReadyToBreed() {
        return "READY_TO_BREED".equals(lifecycleStage);
    }
}