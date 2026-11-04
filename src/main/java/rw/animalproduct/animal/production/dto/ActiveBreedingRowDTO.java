package rw.animalproduct.animal.production.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Row for the "Active Breedings" table on the Breeding Management dashboard.
 * Wraps a PENDING LivestockBreeding record with the derived fields the UI
 * needs (Days Since Breeding, and an auto-computed Overdue flag) so the
 * template never has to do date math or guess about staleness.
 */
@Getter
@Builder
public class ActiveBreedingRowDTO {
    private UUID breedingId;
    private UUID livestockId;
    private String femaleTag;
    private UUID maleId;
    private String maleTag;
    private String categoryName;
    private String breedingMethod;
    private LocalDate breedingDate;
    private LocalDate expectedPregnancyCheckDate;
    private Long daysSinceBreeding;

    /** Raw stored status (almost always PENDING for this list). */
    private String status;

    /**
     * True when expectedPregnancyCheckDate is in the past and the record is
     * still PENDING. This is the fix for the "01 Jan 2025 breeding with a
     * 25 Jan 2025 check, shown on 28 Aug 2026" bug — such a row is OVERDUE,
     * not PENDING.
     */
    private boolean overdue;

    /** "OVERDUE" or "PENDING" — what the badge should actually show. */
    public String getDisplayStatus() {
        return overdue ? "OVERDUE" : "PENDING";
    }
}