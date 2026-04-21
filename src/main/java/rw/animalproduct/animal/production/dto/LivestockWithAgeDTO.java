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

    public UUID      getId()                  { return id; }
    public void      setId(UUID id)           { this.id = id; }

    public String    getTagNumber()           { return tagNumber; }
    public void      setTagNumber(String v)   { this.tagNumber = v; }

    public String    getGender()              { return gender; }
    public void      setGender(String v)      { this.gender = v; }

    public String    getStatus()              { return status; }
    public void      setStatus(String v)      { this.status = v; }

    public String    getCategoryName()        { return categoryName; }
    public void      setCategoryName(String v){ this.categoryName = v; }

    public LocalDate getDateReceived()        { return dateReceived; }
    public void      setDateReceived(LocalDate v)  { this.dateReceived = v; }

    public LocalDate getBirthDate()           { return birthDate; }
    public void      setBirthDate(LocalDate v){ this.birthDate = v; }

    public Integer   getAgeInDays()           { return ageInDays; }
    public void      setAgeInDays(Integer v)  { this.ageInDays = v; }

    public Integer   getAgeInMonths()         { return ageInMonths; }
    public void      setAgeInMonths(Integer v){ this.ageInMonths = v; }

    public Integer   getDaysToBreedingAge()   { return daysToBreedingAge; }
    public void      setDaysToBreedingAge(Integer v) { this.daysToBreedingAge = v; }

    public String    getLifecycleStage()      { return lifecycleStage; }
    public void      setLifecycleStage(String v) { this.lifecycleStage = v; }

    public Boolean   getIsPregnant()          { return isPregnant; }
    public void      setIsPregnant(Boolean v) { this.isPregnant = v; }

    public String    getPregnancyStatus()          { return pregnancyStatus; }
    public void      setPregnancyStatus(String v)  { this.pregnancyStatus = v; }

    public LocalDate getConceptionDate()           { return conceptionDate; }
    public void      setConceptionDate(LocalDate v){ this.conceptionDate = v; }

    public LocalDate getExpectedDueDate()          { return expectedDueDate; }
    public void      setExpectedDueDate(LocalDate v){ this.expectedDueDate = v; }

    public LocalDate getFirstBreedingDate()        { return firstBreedingDate; }
    public void      setFirstBreedingDate(LocalDate v){ this.firstBreedingDate = v; }

    public LocalDate getLastBreedingDate()         { return lastBreedingDate; }
    public void      setLastBreedingDate(LocalDate v){ this.lastBreedingDate = v; }

    public LocalDate getLastBirthDate()            { return lastBirthDate; }
    public void      setLastBirthDate(LocalDate v) { this.lastBirthDate = v; }

    public Integer   getOffspringCount()           { return offspringCount; }
    public void      setOffspringCount(Integer v)  { this.offspringCount = v; }

    public BigDecimal getCurrentValue()            { return currentValue; }
    public void       setCurrentValue(BigDecimal v){ this.currentValue = v; }

    // ══════════════════════════════════════════════════════════════════════════
    // Derived helpers — used directly in Thymeleaf templates
    // ══════════════════════════════════════════════════════════════════════════

    /** True for the "Ready In" column — animal is under 12 months old. */
    public boolean isApproachingBreedingAge() {
        return ageInDays != null && ageInDays >= 180 && ageInDays < 365;
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
            case "YOUNG"          -> "bg-primary";
            case "PRE_BREEDING"   -> "bg-info text-dark";
            case "READY_TO_BREED" -> "bg-success";
            case "PREGNANT"       -> "bg-danger";
            case "BREEDING_MALE"  -> "bg-primary";
            case "MATURE"         -> "bg-success";
            case "DEAD"           -> "bg-dark";
            case "SOLD"           -> "bg-secondary";
            default               -> "bg-secondary";
        };
    }
}
