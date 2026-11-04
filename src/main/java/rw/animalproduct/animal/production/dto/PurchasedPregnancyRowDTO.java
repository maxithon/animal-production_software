package rw.animalproduct.animal.production.dto;

import rw.animalproduct.animal.production.entity.Livestock;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Row DTO for the Pregnancy Tracking dashboard — purchased / external animals.
 *
 * Wraps a {@link Livestock} row where reproductive_status = 'PREGNANT' but no
 * CONFIRMED_PREGNANT breeding record exists yet (animal arrived already
 * pregnant, flagged at intake via the Register Livestock form).
 *
 * All date arithmetic happens here so Thymeleaf 3.1+ templates never need
 * {@code T(...)} or {@code new} expressions.
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
    private final String    reproductiveStatus;
    private final String    reproductiveStatusLabel;

    // ✅ NEW: IDs used by the controller to look up beneficiary + location
    private final UUID      beneficiaryId;
    private final UUID      locationId;

    public PurchasedPregnancyRowDTO(Livestock ls, LocalDate today) {

        this.livestockId       = ls.getId();
        this.tagNumber         = ls.getTagNumber();
        this.acquisitionMethod = ls.getAcquisitionMethod();

        this.categoryName = (ls.getLivestockCategory() != null)
                ? ls.getLivestockCategory().getName()
                : null;

        // FIXED: Livestock no longer has a conceptionDate field/column (it
        // now lives only on LivestockBreeding). This DTO wraps animals with
        // no breeding record by definition, so there's no source to pull a
        // conception date from here — always null.
        this.conceptionDate  = null;
        this.expectedDueDate = ls.getExpectedDueDate();

        this.daysPregnant = (conceptionDate != null)
                ? ChronoUnit.DAYS.between(conceptionDate, today)
                : null;

        this.daysUntilDue = (expectedDueDate != null)
                ? ChronoUnit.DAYS.between(today, expectedDueDate)
                : Long.MAX_VALUE;

        this.dueSoon  = expectedDueDate != null && daysUntilDue >= 0 && daysUntilDue <= 14;
        this.critical = expectedDueDate != null && daysUntilDue < 0;

        // ── Reproductive Status (from Livestock entity) ──────────────────────
        this.reproductiveStatus = ls.getReproductiveStatus();
        this.reproductiveStatusLabel = resolveReproLabel(ls.getReproductiveStatus());

        // ✅ NEW: capture the linked entity IDs for the controller's lookup maps
        this.beneficiaryId = (ls.getBeneficiary() != null) ? ls.getBeneficiary().getId() : null;
        this.locationId    = (ls.getLocation()    != null) ? ls.getLocation().getId()    : null;
    }

    /**
     * Resolve reproductive status to a human-readable label
     */
    private static String resolveReproLabel(String status) {
        if (status == null || status.isBlank()) return "Unknown";
        return switch (status) {
            case "READY_TO_BREED" -> "Ready to Breed";
            case "BRED" -> "Bred";
            case "PREGNANT" -> "Pregnant";
            case "OPEN" -> "Open";
            case "NOT_APPLICABLE" -> "Not Applicable";
            default -> status;
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
    public String    getReproductiveStatus()      { return reproductiveStatus; }
    public String    getReproductiveStatusLabel() { return reproductiveStatusLabel; }

    // ✅ NEW
    public UUID getBeneficiaryId() { return beneficiaryId; }
    public UUID getLocationId()    { return locationId; }
}