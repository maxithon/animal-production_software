package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.OverduePregnanciesKpiDTO;
import rw.animalproduct.animal.production.dto.OverduePregnanciesReportDTO;
import rw.animalproduct.animal.production.repository.OverduePregnanciesReportRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OverduePregnanciesReportService {

    private final OverduePregnanciesReportRepository repository;

    public Page<OverduePregnanciesReportDTO> getReport(UUID categoryId, String search, Pageable pageable) {
        // Sort lives inside the native query (expected_due_date ASC) — strip any
        // incoming Sort the same way OffspringProductionReportService does, so
        // Spring doesn't try to layer a second ORDER BY on top of the native SQL.
        Pageable unsortedPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return repository.findOverduePregnancies(categoryId, blankToNull(search), unsortedPage)
                .map(this::toDto);
    }

    public OverduePregnanciesKpiDTO getKpis(UUID categoryId, String search) {
        OverduePregnanciesReportRepository.KpiRow row = repository.findKpis(categoryId, blankToNull(search));
        return OverduePregnanciesKpiDTO.builder()
                .totalOverdue(nz(row.getTotal_overdue()))
                .watchCount(nz(row.getWatch_count()))
                .concernCount(nz(row.getConcern_count()))
                .criticalCount(nz(row.getCritical_count()))
                .averageDaysOverdue(row.getAverage_days_overdue() != null ? round1(row.getAverage_days_overdue()) : 0)
                .maxDaysOverdue(row.getMax_days_overdue())
                .build();
    }

    private OverduePregnanciesReportDTO toDto(OverduePregnanciesReportRepository.OverdueRow r) {
        return OverduePregnanciesReportDTO.builder()
                .breedingId(r.getBreeding_id())
                .livestockId(r.getLivestock_id())
                .tagNumber(r.getTag_number())
                .categoryName(r.getCategory_name())
                .gender(r.getGender())
                .animalStatus(r.getAnimal_status())
                .breedingStatus(r.getBreeding_status())
                .conceptionDate(r.getConception_date())
                .expectedDueDate(r.getExpected_due_date())
                .pregnancyConfirmedDate(r.getPregnancy_confirmed_date())
                .breedingMethod(r.getBreeding_method())
                .inseminationMethod(r.getInsemination_method())
                .acquisitionMethod(r.getAcquisition_method())
                .daysOverdue(r.getDays_overdue())
                .build();
    }

    private static long nz(Long v) { return v == null ? 0L : v; }
    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}
