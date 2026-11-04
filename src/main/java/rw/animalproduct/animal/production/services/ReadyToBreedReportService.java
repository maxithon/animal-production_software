package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.dto.ReadyToBreedKpiDTO;
import rw.animalproduct.animal.production.dto.ReadyToBreedReportDTO;
import rw.animalproduct.animal.production.repository.ReadyToBreedReportRepository;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReadyToBreedReportService {

    private final ReadyToBreedReportRepository repository;
    /** Unpaged — used by the Ready-to-Breed dashboard summary cards. */
    public java.util.List<ReadyToBreedReportDTO> getAllReady(UUID categoryId, String search) {
        return repository.findAllReadyToBreed(categoryId, blankToNull(search), null, "READY")
                .stream().map(this::toDto).collect(java.util.stream.Collectors.toList());
    }

    public java.util.List<ReadyToBreedReportDTO> getAllNotReady(UUID categoryId, String search) {
        return repository.findAllReadyToBreed(categoryId, blankToNull(search), null, "ALMOST_READY")
                .stream().map(this::toDto).collect(java.util.stream.Collectors.toList());
    }
    private static final Map<String, String> NOTIFY_LABELS = Map.of(
            "NOTIFIED", "\uD83D\uDFE2 Notified",
            "AWAITING", "\uD83D\uDFE1 Awaiting Notification",
            "OVERDUE",  "\uD83D\uDD34 Notification Overdue"
    );

    private static final Map<String, String> READINESS_LABELS = Map.of(
            "READY", "\uD83D\uDFE2 Ready to Breed",
            "ALMOST_READY", "\uD83D\uDFE1 Almost Ready"
    );

    public Page<ReadyToBreedReportDTO> getReport(UUID categoryId, String search, String notifyStatus,
                                                 String readiness, Pageable pageable) {
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return repository.findReadyToBreed(categoryId, blankToNull(search), blankToNull(notifyStatus),
                        blankToNull(readiness), unsorted)
                .map(this::toDto);
    }

    public ReadyToBreedKpiDTO getKpis(UUID categoryId, String search) {
        // KPI cards intentionally ignore the notification-status and readiness filters
        // (they show the full breakdown) — both passed null, same pattern as
        // BreedingEligibilityReportService.
        ReadyToBreedReportRepository.KpiRow row = repository.findKpis(categoryId, blankToNull(search), null, null);
        return ReadyToBreedKpiDTO.builder()
                .totalReadyToBreed(nz(row.getTotal_ready_to_breed()))
                .almostReady(nz(row.getAlmost_ready()))
                .notified(nz(row.getNotified()))
                .awaitingNotification(nz(row.getAwaiting_notification()))
                .notificationOverdue(nz(row.getNotification_overdue()))
                .firstTimeBreeders(nz(row.getFirst_time_breeders()))
                .averageDaysReady(row.getAverage_days_ready() != null ? round1(row.getAverage_days_ready()) : 0)
                .build();
    }

    @Transactional
    public boolean markNotified(UUID livestockId) {
        return repository.markNotified(livestockId) > 0;
    }

    private ReadyToBreedReportDTO toDto(ReadyToBreedReportRepository.ReadyRow r) {
        boolean reachedAge = Boolean.TRUE.equals(r.getReason_reached_breeding_age());
        boolean recoveryDone = Boolean.TRUE.equals(r.getReason_recovery_completed());
        boolean notPregnant = Boolean.TRUE.equals(r.getReason_not_pregnant());
        boolean noRestriction = Boolean.TRUE.equals(r.getReason_no_breeding_restriction());
        boolean noActiveBreeding = Boolean.TRUE.equals(r.getReason_no_active_breeding());
        String readinessStatus = r.getReadiness_status();

        return ReadyToBreedReportDTO.builder()
                .livestockId(r.getLivestock_id())
                .tagNumber(r.getTag_number())
                .categoryName(r.getCategory_name())
                .gender(r.getGender())
                .ageMonths(r.getAge_months())
                .ageDisplay(formatAge(r.getAge_months()))
                .minBreedingAgeMonths(r.getMin_breeding_age_months())
                .maxBreedingAgeMonths(r.getMax_breeding_age_months())
                .births(nz(r.getBirths()))
                .lastBreedingDate(r.getLast_breeding_date())
                .lastBirthDate(r.getLast_birth_date())
                .daysSinceLastBirth(r.getDays_since_last_birth())
                .postpartumRecoveryStatus(formatRecoveryStatus(r))
                .reproductiveStatus(r.getReproductive_status())
                .readinessStatus(readinessStatus)
                .readinessLabel(READINESS_LABELS.getOrDefault(readinessStatus, readinessStatus))
                .reasonReachedBreedingAge(reachedAge)
                .reasonRecoveryCompleted(recoveryDone)
                .reasonNotPregnant(notPregnant)
                .reasonNoBreedingRestriction(noRestriction)
                .reasonsMetCount(r.getReasons_met_count() != null ? r.getReasons_met_count() : 0)
                .readySince(r.getReady_since())
                .recommendedBreedingDate(r.getRecommended_breeding_date())
                .daysReadyForBreeding(r.getDays_ready_for_breeding())
                .daysUntilReady(r.getDays_until_ready())
                .beneficiaryName(r.getBeneficiary_name())
                .beneficiaryPhone(r.getBeneficiary_phone())
                .beneficiaryLocationName(r.getBeneficiary_location_name())
                .animalLocationProvince(r.getAnimal_location_province())
                .animalLocationDistrict(r.getAnimal_location_district())
                .animalLocationSector(r.getAnimal_location_sector())
                .animalLocationCell(r.getAnimal_location_cell())
                .animalLocationVillage(r.getAnimal_location_village())
                .notifiedAt(r.getNotified_at())
                .notified(r.getNotified_at() != null)
                .notificationStatus(r.getNotification_status())
                .reasonNoActiveBreeding(noActiveBreeding)
                .notificationStatusLabel(NOTIFY_LABELS.getOrDefault(r.getNotification_status(), r.getNotification_status()))
                .build();
    }

    private static String formatRecoveryStatus(ReadyToBreedReportRepository.ReadyRow r) {
        long births = nz(r.getBirths());
        if (births == 0) return "No births yet — N/A";
        Long daysSince = r.getDays_since_last_birth();
        Integer recoveryDays = r.getPostpartum_recovery_days();
        if (daysSince == null || recoveryDays == null) return "Unknown";
        if (daysSince >= recoveryDays) return "Recovered";
        long remaining = recoveryDays - daysSince;
        return "Recovering (" + remaining + "d left)";
    }

    private static String formatAge(Integer months) {
        if (months == null) return "-";
        return (months / 12) + "y " + (months % 12) + "m";
    }

    private static long nz(Long v) { return v == null ? 0L : v; }
    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}