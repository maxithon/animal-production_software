package rw.animalproduct.animal.production.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents an active female who is NOT currently eligible for breeding,
 * along with the specific reason and (where applicable) the date she'll
 * become eligible. Built by FemalesReadyToBreedService.getNotYetEligible(),
 * which reuses LivestockBreedingService.getIneligibilityReason() — the
 * same single source of truth used by the breeding form dropdown, the
 * server-side save guard, and the readiness panel — so this list can never
 * report a reason that disagrees with any of those.
 */
public class IneligibleFemaleDTO {
    private final UUID id;
    private final String tagNumber;
    private final String categoryName;
    private final String reason;
    private final LocalDate nextEligibleDate;

    public IneligibleFemaleDTO(UUID id, String tagNumber, String categoryName,
                               String reason, LocalDate nextEligibleDate) {
        this.id = id;
        this.tagNumber = tagNumber;
        this.categoryName = categoryName;
        this.reason = reason;
        this.nextEligibleDate = nextEligibleDate;
    }

    public UUID getId() { return id; }
    public String getTagNumber() { return tagNumber; }
    public String getCategoryName() { return categoryName; }
    public String getReason() { return reason; }
    public LocalDate getNextEligibleDate() { return nextEligibleDate; }
}