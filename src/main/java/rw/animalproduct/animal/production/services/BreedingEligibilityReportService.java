package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.BreedingEligibilityKpiDTO;
import rw.animalproduct.animal.production.dto.BreedingEligibilityReportDTO;
import rw.animalproduct.animal.production.repository.BreedingEligibilityReportRepository;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BreedingEligibilityReportService {

    private final BreedingEligibilityReportRepository repository;

    private static final Map<String, String> STATUS_LABELS = Map.of(
            "ELIGIBLE",       "\uD83D\uDFE2 Eligible for Breeding",
            "TOO_YOUNG",      "\uD83D\uDD35 Too Young",
            "APPROACHING",    "\uD83D\uDFE1 Approaching Maximum Age",
            "EXCEEDED",       "\uD83D\uDD34 Maximum Breeding Age Exceeded",
            "PREGNANT",       "\uD83D\uDFE3 Currently Pregnant",
            "NOT_AVAILABLE",  "\u26AA Not Available for Breeding",
            "NOT_CONFIGURED", "\u26A0\uFE0F Breeding Age Not Configured"
    );

    private static final Map<String, String> AGE_STATUS_LABELS = Map.of(
            "TOO_YOUNG",      "\uD83D\uDD35 Too Young",
            "WITHIN_RANGE",   "\uD83D\uDFE2 Within Breeding Age",
            "NEAR_MAXIMUM",   "\uD83D\uDFE1 Near Maximum Age",
            "EXCEEDED",       "\uD83D\uDD34 Exceeded",
            "NOT_CONFIGURED", "\u26A0\uFE0F Not Configured"
    );

    private static final Map<String, String> RETIREMENT_STATUS_LABELS = Map.of(
            "NOT_APPLICABLE",         "Not Applicable",
            "APPROACHING_RETIREMENT", "\uD83D\uDFE1 Approaching Retirement",
            "RETIREMENT_RECOMMENDED", "\uD83D\uDD34 Retirement Recommended",
            "RETIRED",                "\u26AB Retired"
    );

    public Page<BreedingEligibilityReportDTO> getReport(UUID categoryId, String search, String status,
                                                          String ageStatus, String retirementStatus,
                                                          Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return repository.findEligibility(categoryId, blankToNull(search), blankToNull(status),
                        blankToNull(ageStatus), blankToNull(retirementStatus), unsorted)
                .map(this::toDto);
    }

    public BreedingEligibilityKpiDTO getKpis(UUID categoryId, String search) {
        // KPI cards intentionally ignore all status filters (they show the full
        // breakdown regardless of what's selected in the table filters below).
        BreedingEligibilityReportRepository.KpiRow row =
                repository.findKpis(categoryId, blankToNull(search), null, null, null);
        return BreedingEligibilityKpiDTO.builder()
                .totalAnimals(nz(row.getTotal_animals()))
                .eligibleForBreeding(nz(row.getEligible_for_breeding()))
                .tooYoung(nz(row.getToo_young()))
                .nearMaximumAge(nz(row.getNear_maximum_age()))
                .maximumAgeExceeded(nz(row.getMaximum_age_exceeded()))
                .retirementRecommended(nz(row.getRetirement_recommended()))
                .currentlyPregnant(nz(row.getCurrently_pregnant()))
                .notAvailable(nz(row.getNot_available()))
                .retired(nz(row.getRetired()))
                .build();
    }

    private BreedingEligibilityReportDTO toDto(BreedingEligibilityReportRepository.EligibilityRow r) {
        return BreedingEligibilityReportDTO.builder()
                .livestockId(r.getLivestock_id())
                .tagNumber(r.getTag_number())
                .categoryName(r.getCategory_name())
                .gender(r.getGender())
                .animalStatus(r.getAnimal_status())
                .healthStatus(r.getHealth_status())
                .reproductiveStatus(r.getReproductive_status())
                .ageMonths(r.getAge_months())
                .ageDisplay(formatAge(r.getAge_months()))
                .minBreedingAgeMonths(r.getMin_breeding_age_months())
                .maxBreedingAgeMonths(r.getMax_breeding_age_months())
                .remainingMonths(r.getRemaining_months())
                .remainingDisplay(formatRemaining(r.getRemaining_months()))
                .births(nz(r.getBirths()))
                .successfulPregnancies(nz(r.getSuccessful_pregnancies()))
                .failedPregnancies(nz(r.getFailed_pregnancies()))
                .abortions(nz(r.getAbortions()))
                .lastBirthDate(r.getLast_birth_date())
                .daysSinceLastBirth(r.getDays_since_last_birth())
                .currentlyPregnant("PREGNANT".equals(r.getBreeding_status()))
                .ageStatus(r.getAge_status())
                .ageStatusLabel(AGE_STATUS_LABELS.getOrDefault(r.getAge_status(), r.getAge_status()))
                .breedingStatus(r.getBreeding_status())
                .breedingStatusLabel(STATUS_LABELS.getOrDefault(r.getBreeding_status(), r.getBreeding_status()))
                .ineligibilityReason(r.getIneligibility_reason())
                .retirementStatus(r.getRetirement_status())
                .retirementStatusLabel(RETIREMENT_STATUS_LABELS.getOrDefault(r.getRetirement_status(), r.getRetirement_status()))
                .beneficiaryFirstName(r.getBeneficiary_first_name())
                .beneficiaryLastName(r.getBeneficiary_last_name())
                .beneficiaryPhone(r.getBeneficiary_phone())
                .province(r.getProvince())
                .district(r.getDistrict())
                .sector(r.getSector())
                .cell(r.getCell())
                .village(r.getVillage())
                .build();
    }

    private static String formatAge(Integer months) {
        if (months == null) return "-";
        int y = months / 12;
        int m = months % 12;
        return y + "y " + m + "m";
    }

    /**
     * remainingMonths > 0  -> "1y 10m remaining"
     * remainingMonths == 0 -> "Exceeded (at max age)"
     * remainingMonths < 0  -> "Exceeded by 1y 2m"
     * null (age not configured) -> "-"
     */
    private static String formatRemaining(Integer remainingMonths) {
        if (remainingMonths == null) return "-";
        if (remainingMonths > 0) {
            return formatDuration(remainingMonths) + " remaining";
        }
        int over = Math.abs(remainingMonths);
        return over == 0 ? "Exceeded (at max age)" : "Exceeded by " + formatDuration(over);
    }

    private static String formatDuration(int totalMonths) {
        int y = totalMonths / 12;
        int m = totalMonths % 12;
        if (y > 0 && m > 0) return y + "y " + m + "m";
        if (y > 0) return y + "y";
        return m + "m";
    }

    private static long nz(Long v) { return v == null ? 0L : v; }
    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}
