package rw.animalproduct.animal.production.controller;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import rw.animalproduct.animal.production.dto.BreedingEligibilityKpiDTO;
import rw.animalproduct.animal.production.dto.BreedingEligibilityReportDTO;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.services.BreedingEligibilityReportService;
import rw.animalproduct.animal.production.util.PdfReportStyleUtil;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/livestock/breeding-eligibility-report")
@RequiredArgsConstructor
public class BreedingEligibilityReportController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final String REPORT_TITLE = "Breeding Eligibility & Reproductive Performance Report";

    private final BreedingEligibilityReportService reportService;
    private final LivestockCategoryRepository categoryRepository;

    @GetMapping
    public String report(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ageStatus,
            @RequestParam(required = false) String retirementStatus,
            // No sort configured on purpose — fixed inside the native query.
            @PageableDefault(size = 25) Pageable pageable,
            Model model) {

        Page<BreedingEligibilityReportDTO> page =
                reportService.getReport(categoryId, search, status, ageStatus, retirementStatus, pageable);
        BreedingEligibilityKpiDTO kpis = reportService.getKpis(categoryId, search);

        model.addAttribute("rows", page.getContent());
        model.addAttribute("currentPage", page.getNumber());
        model.addAttribute("totalPages", page.getTotalPages());
        model.addAttribute("totalItems", page.getTotalElements());
        model.addAttribute("pageSize", page.getSize());
        model.addAttribute("kpis", kpis);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("categoryIdFilter", categoryId);
        model.addAttribute("searchFilter", search);
        model.addAttribute("statusFilter", status);
        model.addAttribute("ageStatusFilter", ageStatus);
        model.addAttribute("retirementStatusFilter", retirementStatus);

        return "livestock-breeding-eligibility-report";
    }

    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ageStatus,
            @RequestParam(required = false) String retirementStatus) {

        List<BreedingEligibilityReportDTO> rows = allRows(categoryId, search, status, ageStatus, retirementStatus);

        StringBuilder csv = new StringBuilder();
        csv.append("Tag,Category,Age,Min Breeding Age (m),Max Breeding Age (m),Age Status,Time Remaining / Exceeded By,")
                .append("Births,Successful Pregnancies,Failed Pregnancies,Abortions,Last Birth Date,")
                .append("Days Since Last Birth,Currently Pregnant,Eligibility Status,Ineligibility Reason,Retirement Status,")
                .append("Beneficiary Name,Beneficiary Phone,Province,District,Sector,Cell,Village\n");

        for (BreedingEligibilityReportDTO r : rows) {
            csv.append(csvEscape(r.getTagNumber())).append(',')
                    .append(csvEscape(r.getCategoryName())).append(',')
                    .append(csvEscape(r.getAgeDisplay())).append(',')
                    .append(r.getMinBreedingAgeMonths() != null ? r.getMinBreedingAgeMonths() : "").append(',')
                    .append(r.getMaxBreedingAgeMonths() != null ? r.getMaxBreedingAgeMonths() : "").append(',')
                    .append(csvEscape(r.getAgeStatusLabel())).append(',')
                    .append(csvEscape(r.getRemainingDisplay())).append(',')
                    .append(r.getBirths()).append(',')
                    .append(r.getSuccessfulPregnancies()).append(',')
                    .append(r.getFailedPregnancies()).append(',')
                    .append(r.getAbortions()).append(',')
                    .append(r.getLastBirthDate() != null ? r.getLastBirthDate() : "").append(',')
                    .append(r.getDaysSinceLastBirth() != null ? r.getDaysSinceLastBirth() : "").append(',')
                    .append(r.isCurrentlyPregnant() ? "Pregnant" : "Not Pregnant").append(',')
                    .append(csvEscape(r.getBreedingStatusLabel())).append(',')
                    .append(csvEscape(r.getIneligibilityReason() != null ? r.getIneligibilityReason() : "-")).append(',')
                    .append(csvEscape(r.getRetirementStatusLabel())).append(',')
                    .append(csvEscape(r.getBeneficiaryName())).append(',')
                    .append(csvEscape(r.getBeneficiaryPhone())).append(',')
                    .append(csvEscape(r.getProvince())).append(',')
                    .append(csvEscape(r.getDistrict())).append(',')
                    .append(csvEscape(r.getSector())).append(',')
                    .append(csvEscape(r.getCell())).append(',')
                    .append(csvEscape(r.getVillage())).append('\n');
        }

        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"breeding-eligibility-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    @GetMapping("/export/pdf")
    @ResponseBody
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ageStatus,
            @RequestParam(required = false) String retirementStatus) throws DocumentException, IOException {

        List<BreedingEligibilityReportDTO> rows = allRows(categoryId, search, status, ageStatus, retirementStatus);
        BreedingEligibilityKpiDTO kpis = reportService.getKpis(categoryId, search);

        List<String[]> kpiPairs = new ArrayList<>();
        kpiPairs.add(new String[]{"Total Animals", String.valueOf(kpis.getTotalAnimals())});
        kpiPairs.add(new String[]{"Eligible", String.valueOf(kpis.getEligibleForBreeding())});
        kpiPairs.add(new String[]{"Too Young", String.valueOf(kpis.getTooYoung())});
        kpiPairs.add(new String[]{"Near Maximum Age", String.valueOf(kpis.getNearMaximumAge())});
        kpiPairs.add(new String[]{"Maximum Age Exceeded", String.valueOf(kpis.getMaximumAgeExceeded())});
        kpiPairs.add(new String[]{"Retirement Recommended", String.valueOf(kpis.getRetirementRecommended())});
        kpiPairs.add(new String[]{"Pregnant", String.valueOf(kpis.getCurrentlyPregnant())});
        kpiPairs.add(new String[]{"Retired", String.valueOf(kpis.getRetired())});

        byte[] pdf = PdfReportStyleUtil.buildReport(
                REPORT_TITLE,
                "Breeding females evaluated by age and reproductive history — not birth count alone.",
                buildFilterSummary(categoryId, search, status, ageStatus, retirementStatus),
                List.of("Tag", "Category", "Age", "Age Status", "Remaining", "Eligibility", "Reason", "Retirement",
                        "Last Birth", "Beneficiary", "Location"),
                new float[]{7, 8, 5, 8, 9, 8, 13, 9, 8, 12, 13},
                kpiPairs,
                table -> {
                    for (BreedingEligibilityReportDTO r : rows) {
                        Color ageColor = switch (r.getAgeStatus()) {
                            case "EXCEEDED" -> PdfReportStyleUtil.RED;
                            case "NEAR_MAXIMUM" -> PdfReportStyleUtil.AMBER;
                            case "WITHIN_RANGE" -> PdfReportStyleUtil.BRAND_GREEN;
                            default -> PdfReportStyleUtil.TEXT_MUTED;
                        };
                        Color retirementColor = switch (r.getRetirementStatus()) {
                            case "RETIREMENT_RECOMMENDED" -> PdfReportStyleUtil.RED;
                            case "APPROACHING_RETIREMENT" -> PdfReportStyleUtil.AMBER;
                            case "RETIRED" -> PdfReportStyleUtil.TEXT_MUTED;
                            default -> null;
                        };
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getTagNumber(), null, true));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getCategoryName(), null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getAgeDisplay(), null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getAgeStatusLabel(), ageColor, true));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getRemainingDisplay(), null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getBreedingStatusLabel(), null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(
                                r.getIneligibilityReason() != null ? r.getIneligibilityReason() : "-", null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getRetirementStatusLabel(), retirementColor, true));
                        table.addCell(PdfReportStyleUtil.bodyCell(
                                r.getLastBirthDate() != null ? r.getLastBirthDate().format(DATE_FMT) : "-", null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(
                                r.getBeneficiaryName() + (r.getBeneficiaryPhone() != null ? "\n" + r.getBeneficiaryPhone() : ""),
                                null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getFullLocationDisplay(), null, false));
                    }
                    if (rows.isEmpty()) {
                        PdfPCell empty = new PdfPCell(new Phrase("No animals match the selected filters."));
                        empty.setColspan(11);
                        empty.setPadding(12f);
                        table.addCell(empty);
                    }
                }
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"breeding-eligibility-report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private List<BreedingEligibilityReportDTO> allRows(UUID categoryId, String search, String status,
                                                         String ageStatus, String retirementStatus) {
        Pageable unpaged = PageRequest.of(0, 5000);
        return reportService.getReport(categoryId, search, status, ageStatus, retirementStatus, unpaged).getContent();
    }

    private String buildFilterSummary(UUID categoryId, String search, String status,
                                       String ageStatus, String retirementStatus) {
        List<String> parts = new ArrayList<>();
        if (categoryId != null) {
            categoryRepository.findById(categoryId).ifPresent(c -> parts.add("Category: " + c.getName()));
        }
        if (status != null && !status.isBlank()) parts.add("Eligibility: " + status);
        if (ageStatus != null && !ageStatus.isBlank()) parts.add("Age Status: " + ageStatus);
        if (retirementStatus != null && !retirementStatus.isBlank()) parts.add("Retirement: " + retirementStatus);
        if (search != null && !search.isBlank()) parts.add("Search: \"" + search + "\"");
        return parts.isEmpty() ? "All breeding females" : String.join("  \u00b7  ", parts);
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(",") || escaped.contains("\"") ? "\"" + escaped + "\"" : escaped;
    }
}
