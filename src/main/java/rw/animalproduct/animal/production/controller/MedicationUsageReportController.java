package rw.animalproduct.animal.production.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import rw.animalproduct.animal.production.dto.MedicationUsageReportDto;
import rw.animalproduct.animal.production.dto.MedicationUsageRow;
import rw.animalproduct.animal.production.entity.LivestockTreatment;
import rw.animalproduct.animal.production.entity.Medication;
import rw.animalproduct.animal.production.repository.LivestockTreatmentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serves /livestock/medication-usage-report — the HTML dashboard.
 *
 * MedicationUsageController is a @RestController under /api/medication-usage
 * and only returns JSON, so nothing was mapped to this page URL, which is why
 * it 404'd as a missing static resource.
 */
@Controller
@RequestMapping("/livestock")
public class MedicationUsageReportController {

    private final LivestockTreatmentRepository livestockTreatmentRepository;

    public MedicationUsageReportController(LivestockTreatmentRepository livestockTreatmentRepository) {
        this.livestockTreatmentRepository = livestockTreatmentRepository;
    }

    @GetMapping("/medication-usage-report")
    public String medicationUsageReport(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        LocalDate fromDate = (from != null) ? from : LocalDate.now().minusDays(30);
        LocalDate toDate = (to != null) ? to : LocalDate.now();

        List<LivestockTreatment> treatments =
                livestockTreatmentRepository.findByTreatmentDateBetweenAndIsDeletedFalse(fromDate, toDate);

        MedicationUsageReportDto report = buildReport(treatments, fromDate, toDate);

        model.addAttribute("report", report);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);

        return "medication-usage-report";
    }

    private MedicationUsageReportDto buildReport(List<LivestockTreatment> treatments,
                                                 LocalDate fromDate,
                                                 LocalDate toDate) {

        MedicationUsageReportDto report = new MedicationUsageReportDto();
        report.setFromDate(fromDate);
        report.setToDate(toDate);
        report.setTotalTreatments(treatments.size());

        BigDecimal totalSpend = BigDecimal.ZERO;
        Map<String, Long> countByCategory = new LinkedHashMap<>();
        Map<String, BigDecimal> spendByCategory = new LinkedHashMap<>();

        // Group treatments by medication id
        Map<UUID, List<LivestockTreatment>> byMedication = treatments.stream()
                .filter(t -> t.getMedication() != null)
                .collect(Collectors.groupingBy(t -> t.getMedication().getId(), LinkedHashMap::new, Collectors.toList()));

        List<MedicationUsageRow> rows = new ArrayList<>();

        for (List<LivestockTreatment> group : byMedication.values()) {
            Medication medication = group.get(0).getMedication();

            String categoryName = medication.getCategory() != null
                    ? medication.getCategory().name()
                    : "UNCATEGORIZED";

            BigDecimal medicationTotalCost = group.stream()
                    .map(t -> t.getTreatmentCost() == null ? BigDecimal.ZERO : t.getTreatmentCost())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long distinctAnimals = group.stream()
                    .filter(t -> t.getLivestock() != null)
                    .map(t -> t.getLivestock().getId())
                    .distinct()
                    .count();

            MedicationUsageRow row = new MedicationUsageRow();
            row.setMedicationId(medication.getId().toString());
            row.setMedicationName(medication.getName());
            row.setGenericName(medication.getGenericName());
            row.setCategory(categoryName);
            row.setManufacturer(medication.getManufacturer());
            row.setTimesUsed(group.size());
            row.setTotalCost(medicationTotalCost);
            row.setDistinctAnimalsTreated((int) distinctAnimals);
            rows.add(row);

            totalSpend = totalSpend.add(medicationTotalCost);
            countByCategory.merge(categoryName, (long) group.size(), Long::sum);
            spendByCategory.merge(categoryName, medicationTotalCost, BigDecimal::add);
        }

        // Sort rows by total cost descending, so the "Top Medications" chart
        // and table show the highest-spend items first.
        rows.sort((a, b) -> b.getTotalCost().compareTo(a.getTotalCost()));

        report.setTotalSpend(totalSpend);
        report.setCountByCategory(countByCategory);
        report.setSpendByCategory(spendByCategory);
        report.setRows(rows);

        return report;
    }
}