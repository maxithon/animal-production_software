package rw.animalproduct.animal.production.dto;

import rw.animalproduct.animal.production.entity.LivestockBreeding;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Row DTO for the Pregnancy Tracking dashboard — farm-bred animals.
 *
 * Wraps a {@link LivestockBreeding} record with status CONFIRMED_PREGNANT.
 * All date arithmetic happens here so Thymeleaf 3.1+ templates never need
 * {@code T(...)} or {@code new} expressions.
 *
 * Fields exposed:
 *   breedingId           – breeding record UUID
 *   livestockId          – animal UUID  (for links)
 *   tagNumber            – display tag
 *   categoryName         – species / breed
 *   gender               – always FEMALE here
 *   conceptionDate       – date breeding happened
 *   expectedDueDate      – calculated due date
 *   daysPregnant         – days since conception (null if conceptionDate unknown)
 *   daysUntilDue         – days remaining (negative = overdue)
 *   dueSoon              – true when daysUntilDue is 0–14
 *   critical             – true when overdue (daysUntilDue < 0)
 *   overdue              – alias for critical
 *   inseminationMethod   – HOW the animal was made pregnant
 *   inseminationMethodLabel – human-readable label for inseminationMethod
 */
public class PregnancyRowDTO {

    private final UUID      breedingId;
    private final UUID      livestockId;
    private final String    tagNumber;
    private final String    categoryName;
    private final String    gender;
    private final LocalDate conceptionDate;
    private final LocalDate expectedDueDate;
    private final Long      daysPregnant;
    private final long      daysUntilDue;
    private final boolean   dueSoon;
    private final boolean   critical;
    private final String    inseminationMethod;
    private final String    inseminationMethodLabel;
    private final String    acquisitionMethod;

    public PregnancyRowDTO(LivestockBreeding b, LocalDate today) {

        this.breedingId   = b.getId();
        this.livestockId  = b.getLivestock() != null ? b.getLivestock().getId()        : null;
        this.tagNumber    = b.getLivestock() != null ? b.getLivestock().getTagNumber() : "—";
        this.gender       = b.getLivestock() != null ? b.getLivestock().getGender()    : null;
        this.acquisitionMethod = b.getLivestock() != null
                ? b.getLivestock().getAcquisitionMethod()
                : null;

        this.categoryName = (b.getLivestock() != null
                && b.getLivestock().getLivestockCategory() != null)
                ? b.getLivestock().getLivestockCategory().getName()
                : null;

        this.conceptionDate  = b.getBreedingDate();
        this.expectedDueDate = b.getExpectedDueDate();

        this.daysPregnant = (conceptionDate != null)
                ? ChronoUnit.DAYS.between(conceptionDate, today)
                : null;

        this.daysUntilDue = (expectedDueDate != null)
                ? ChronoUnit.DAYS.between(today, expectedDueDate)
                : Long.MAX_VALUE;

        this.dueSoon  = expectedDueDate != null && daysUntilDue >= 0 && daysUntilDue <= 14;
        this.critical = expectedDueDate != null && daysUntilDue < 0;

        // ── Insemination method ───────────────────────────────────────────────
        // Prefer the breeding method stored on the breeding record (getBreedingMethod).
        // Fall back to the insemination method stored on the livestock row itself
        // (set at registration time for purchased/donated animals).
        String rawMethod = b.getBreedingMethod();
        if ((rawMethod == null || rawMethod.isBlank()) && b.getLivestock() != null) {
            rawMethod = b.getLivestock().getInseminationMethod();
        }
        this.inseminationMethod      = rawMethod;
        this.inseminationMethodLabel = resolveLabel(rawMethod);
    }

    // ── Static label resolver (safe to call with null) ────────────────────────
    private static String resolveLabel(String method) {
        if (method == null || method.isBlank()) return "Not recorded";
        return switch (method) {
            case "NATURAL",
                 "NATURAL_MATING"           -> "Natural Mating";
            case "ARTIFICIAL_INSEMINATION"  -> "Artificial Insemination (AI)";
            case "EMBRYO_TRANSFER"          -> "Embryo Transfer (ET)";
            case "PURCHASE_PREGNANT"        -> "Purchased Pregnant";
            case "UNKNOWN"                  -> "Unknown";
            default                         -> method;
        };
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID      getBreedingId()              { return breedingId; }
    public UUID      getLivestockId()             { return livestockId; }
    public String    getTagNumber()               { return tagNumber; }
    public String    getCategoryName()            { return categoryName; }
    public String    getGender()                  { return gender; }
    public String    getAcquisitionMethod()       { return acquisitionMethod; }
    public LocalDate getConceptionDate()          { return conceptionDate; }
    public LocalDate getExpectedDueDate()         { return expectedDueDate; }
    public Long      getDaysPregnant()            { return daysPregnant; }
    public long      getDaysUntilDue()            { return daysUntilDue; }
    public boolean   isDueSoon()                  { return dueSoon; }
    public boolean   isCritical()                 { return critical; }
    public boolean   isOverdue()                  { return critical; }
    public String    getInseminationMethod()      { return inseminationMethod; }
    public String    getInseminationMethodLabel() { return inseminationMethodLabel; }
}