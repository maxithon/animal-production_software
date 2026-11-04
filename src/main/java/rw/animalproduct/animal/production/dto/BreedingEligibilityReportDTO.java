package rw.animalproduct.animal.production.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class BreedingEligibilityReportDTO {
    private UUID livestockId;
    private String tagNumber;
    private String categoryName;
    private String gender;
    private String animalStatus;          // livestock.status (ACTIVE, SOLD, etc.)
    private String healthStatus;          // livestock.health_status
    private String reproductiveStatus;    // livestock.reproductive_status (raw value)

    private Integer ageMonths;
    private String ageDisplay;            // e.g. "6y 2m"
    private Integer minBreedingAgeMonths;
    private Integer maxBreedingAgeMonths;

    private Integer remainingMonths;      // positive = months left, negative/zero = exceeded by
    private String remainingDisplay;      // "1y 10m remaining" / "Exceeded by 1y 2m"

    private long births;                  // total historical births (offspring_count)
    private long successfulPregnancies;
    private long failedPregnancies;
    private long abortions;

    private LocalDate lastBirthDate;
    private Long daysSinceLastBirth;

    private boolean currentlyPregnant;

    // Pure age-based classification — never derived from birth count.
    private String ageStatus;             // TOO_YOUNG / WITHIN_RANGE / NEAR_MAXIMUM / EXCEEDED / NOT_CONFIGURED
    private String ageStatusLabel;

    // Full eligibility verdict (age + health + animal status + pregnancy)
    private String breedingStatus;        // ELIGIBLE / TOO_YOUNG / APPROACHING / EXCEEDED / PREGNANT / NOT_AVAILABLE / NOT_CONFIGURED
    private String breedingStatusLabel;   // "🟢 Eligible for Breeding" etc.
    private String ineligibilityReason;   // human text, null when eligible/pregnant

    // Retirement recommendation, distinct from "actually retired in the system"
    private String retirementStatus;      // NOT_APPLICABLE / APPROACHING_RETIREMENT / RETIREMENT_RECOMMENDED / RETIRED
    private String retirementStatusLabel;

    // Beneficiary (owner) — resolved from livestock.beneficiary_id
    private String beneficiaryFirstName;
    private String beneficiaryLastName;
    private String beneficiaryPhone;

    // Full administrative location of the ANIMAL — resolved from
    // livestock.location_id by walking up a_location.parent_id
    private String province;
    private String district;
    private String sector;
    private String cell;
    private String village;

    /**
     * Convenience for templates/exports: eligibility text combined with the
     * reason, so we never just print "Not Eligible" on its own.
     * e.g. "Not Eligible — Maximum breeding age exceeded"
     */
    public String getEligibilityWithReason() {
        boolean eligible = "ELIGIBLE".equals(breedingStatus) || "APPROACHING".equals(breedingStatus);
        if (eligible) return "Eligible";
        if ("PREGNANT".equals(breedingStatus)) return "Eligible (Currently Pregnant)";
        return ineligibilityReason != null ? "Not Eligible — " + ineligibilityReason : "Not Eligible";
    }

    /** "Claudette MUKAMANA" — blank-safe, never returns "null null". */
    public String getBeneficiaryName() {
        List<String> parts = new ArrayList<>();
        if (beneficiaryFirstName != null && !beneficiaryFirstName.isBlank()) parts.add(beneficiaryFirstName.trim());
        if (beneficiaryLastName != null && !beneficiaryLastName.isBlank()) parts.add(beneficiaryLastName.trim());
        return parts.isEmpty() ? "-" : String.join(" ", parts);
    }

    /** "Kigali / Gasabo / Remera / Rukiri I / Ingenzi" — skips any missing levels. */
    public String getFullLocationDisplay() {
        List<String> parts = new ArrayList<>();
        if (province != null && !province.isBlank()) parts.add(province.trim());
        if (district != null && !district.isBlank()) parts.add(district.trim());
        if (sector != null && !sector.isBlank()) parts.add(sector.trim());
        if (cell != null && !cell.isBlank()) parts.add(cell.trim());
        if (village != null && !village.isBlank()) parts.add(village.trim());
        return parts.isEmpty() ? "-" : String.join(" / ", parts);
    }
}
