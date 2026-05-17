package rw.animalproduct.animal.production.dto;

import rw.animalproduct.animal.production.entity.Livestock;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Row DTO for the Pregnancy Tracking dashboard — purchased / external animals.
 *
 * Wraps a {@link Livestock} row where {@code is_pregnant = true} but no
 * CONFIRMED_PREGNANT breeding record exists yet (animal arrived already
 * pregnant, flagged at intake via the Register Livestock form).
 *
 * All date arithmetic happens here so Thymeleaf 3.1+ templates never need
 * {@code T(...)} or {@code new} expressions.
 *
 * Fields exposed:
 *   livestockId          – animal UUID (for links)
 *   tagNumber            – display tag
 *   categoryName         – species / breed
 *   acquisitionMethod    – PURCHASE / DONATION / TRANSFER / OTHER
 *   conceptionDate       – from livestock.conception_date
 *   expectedDueDate      – from livestock.expected_due_date
 *   daysPregnant         – days since conception (null if unknown)
 *   daysUntilDue         – days remaining (negative = overdue)
 *   dueSoon              – true when 0 ≤ daysUntilDue ≤ 14
 *   critical             – true when overdue
 *   inseminationMethod   – HOW the animal was made pregnant
 *   inseminationMethodLabel – human-readable label
 */
public class PurchasedPregnancyRowDTO {

    private final UUID      livestockId;
    private final String    tagNumber;
    private final String    categoryName;
    private final String    acquisitionMethod;
    private final LocalDate conceptionDate;
    private final LocalDate expectedDueDate;
    private final Long      daysPregnant;
    private final long      daysUntilDue;
    private final boolean   dueSoon;
    private final boolean   critical;
    private final String    inseminationMethod;
    private final String    inseminationMethodLabel;

    public PurchasedPregnancyRowDTO(Livestock ls, LocalDate today) {

        this.livestockId       = ls.getId();
        this.tagNumber         = ls.getTagNumber();
        this.acquisitionMethod = ls.getAcquisitionMethod();

        this.categoryName = (ls.getLivestockCategory() != null)
                ? ls.getLivestockCategory().getName()
                : null;

        this.conceptionDate  = ls.getConceptionDate();
        this.expectedDueDate = ls.getExpectedDueDate();

        this.daysPregnant = (conceptionDate != null)
                ? ChronoUnit.DAYS.between(conceptionDate, today)
                : null;

        this.daysUntilDue = (expectedDueDate != null)
                ? ChronoUnit.DAYS.between(today, expectedDueDate)
                : Long.MAX_VALUE;

        this.dueSoon  = expectedDueDate != null && daysUntilDue >= 0 && daysUntilDue <= 14;
        this.critical = expectedDueDate != null && daysUntilDue < 0;

        // ── Insemination method ───────────────────────────────────────────────
        this.inseminationMethod      = ls.getInseminationMethod();
        this.inseminationMethodLabel = resolveLabel(ls.getInseminationMethod());
    }

    private static String resolveLabel(String method) {
        if (method == null || method.isBlank()) return "Not recorded";
        return switch (method) {
            case "NATURAL_MATING"           -> "Natural Mating";
            case "ARTIFICIAL_INSEMINATION"  -> "Artificial Insemination (AI)";
            case "EMBRYO_TRANSFER"          -> "Embryo Transfer (ET)";
            case "UNKNOWN"                  -> "Unknown";
            default                         -> method;
        };
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID      getLivestockId()             { return livestockId; }
    public String    getTagNumber()               { return tagNumber; }
    public String    getCategoryName()            { return categoryName; }
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