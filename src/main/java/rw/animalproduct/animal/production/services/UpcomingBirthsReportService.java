package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.CategoryBreakdownDTO;
import rw.animalproduct.animal.production.dto.UpcomingBirthsKpiDTO;
import rw.animalproduct.animal.production.dto.UpcomingBirthsReportDTO;
import rw.animalproduct.animal.production.repository.UpcomingBirthsReportRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpcomingBirthsReportService {

    private final UpcomingBirthsReportRepository repository;

    /**
     * KPI + breakdown queries use a very large window so they reflect the
     * whole pregnant population (both sources), independent of the table's
     * currently-selected lookahead window.
     */
    private static final int KPI_WINDOW_DAYS = 9999;

    public Page<UpcomingBirthsReportDTO> getReport(UUID categoryId, String search,
                                                   Integer windowDays, Pageable pageable) {
        Pageable unsortedPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return repository.findUpcomingBirths(categoryId, blankToNull(search),
                        normalizeWindow(windowDays), unsortedPage)
                .map(this::toDto);
    }

    public UpcomingBirthsKpiDTO getKpis(UUID categoryId, String search) {
        UpcomingBirthsReportRepository.KpiRow row =
                repository.findKpis(categoryId, blankToNull(search), KPI_WINDOW_DAYS);
        return UpcomingBirthsKpiDTO.builder()
                .totalPregnant(nz(row.getTotal_pregnant()))
                .dueWithin3Days(nz(row.getDue_within_3_days()))
                .dueThisWeek(nz(row.getDue_this_week()))
                .dueThisMonth(nz(row.getDue_this_month()))
                .overduePregnancies(nz(row.getOverdue_pregnancies()))
                .averageDaysUntilDue(row.getAverage_days_until_due() != null
                        ? round1(row.getAverage_days_until_due()) : 0)
                .build();
    }

    public List<CategoryBreakdownDTO> getCategoryBreakdown(UUID categoryId, String search) {
        return repository.findCategoryBreakdown(categoryId, blankToNull(search), KPI_WINDOW_DAYS)
                .stream()
                .map(r -> CategoryBreakdownDTO.builder()
                        .categoryName(r.getCategory_name())
                        .totalPregnant(nz(r.getTotal_pregnant()))
                        .dueWithin3Days(nz(r.getDue_within_3_days()))
                        .dueThisWeek(nz(r.getDue_this_week()))
                        .dueThisMonth(nz(r.getDue_this_month()))
                        .overduePregnancies(nz(r.getOverdue_pregnancies()))
                        .build())
                .toList();
    }

    private UpcomingBirthsReportDTO toDto(UpcomingBirthsReportRepository.UpcomingRow r) {
        return UpcomingBirthsReportDTO.builder()
                .breedingId(r.getBreeding_id())
                .livestockId(r.getLivestock_id())
                .tagNumber(r.getTag_number())
                .categoryName(r.getCategory_name())
                .gender(r.getGender())
                .animalStatus(r.getAnimal_status())
                .breedingStatus(r.getBreeding_status())
                .breedingDate(r.getBreeding_date())
                .conceptionDate(r.getConception_date())
                .expectedDueDate(r.getExpected_due_date())
                .pregnancyConfirmedDate(r.getPregnancy_confirmed_date())
                .breedingMethod(r.getBreeding_method())
                .inseminationMethod(r.getInsemination_method())
                .acquisitionMethod(r.getAcquisition_method())
                .location(r.getLocation())
                .expectedOffspring(r.getExpected_offspring())
                .daysUntilDue(r.getDays_until_due())
                .build();
    }

    private static Integer normalizeWindow(Integer windowDays) {
        if (windowDays == null) return 30;
        if (windowDays < 1) return 1;
        if (windowDays > 180) return 180;
        return windowDays;
    }

    private static long nz(Long v) { return v == null ? 0L : v; }
    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}