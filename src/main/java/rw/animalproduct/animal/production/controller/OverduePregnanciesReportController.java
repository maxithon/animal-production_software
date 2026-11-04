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
import rw.animalproduct.animal.production.dto.OverduePregnanciesKpiDTO;
import rw.animalproduct.animal.production.dto.OverduePregnanciesReportDTO;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.services.OverduePregnanciesReportService;
import rw.animalproduct.animal.production.util.PdfReportStyleUtil;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/livestock/overdue-pregnancies-report")
@RequiredArgsConstructor
public class OverduePregnanciesReportController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final OverduePregnanciesReportService reportService;
    private final LivestockCategoryRepository categoryRepository;

    @GetMapping
    public String report(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            // No sort configured on purpose — the ORDER BY is fixed inside the
            // native query itself. See OverduePregnanciesReportService.getReport().
            @PageableDefault(size = 25) Pageable pageable,
            Model model) {

        Page<OverduePregnanciesReportDTO> page = reportService.getReport(categoryId, search, pageable);
        OverduePregnanciesKpiDTO kpis = reportService.getKpis(categoryId, search);

        model.addAttribute("rows", page.getContent());
        model.addAttribute("currentPage", page.getNumber());
        model.addAttribute("totalPages", page.getTotalPages());
        model.addAttribute("totalItems", page.getTotalElements());
        model.addAttribute("pageSize", page.getSize());
        model.addAttribute("kpis", kpis);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("categoryIdFilter", categoryId);
        model.addAttribute("searchFilter", search);

        return "livestock-overdue-pregnancies-report";
    }

    /**
     * CSV export honoring the current filters. Same caveat as
     * OffspringProductionReportController.export(): for very large herds this
     * should become an async export job instead of one big in-memory pull.
     */
    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search) {

        List<OverduePregnanciesReportDTO> rows = allRows(categoryId, search);

        StringBuilder csv = new StringBuilder();
        csv.append("Tag,Category,Gender,Conception Date,Expected Due Date,Days Overdue,Severity,")
                .append("Breeding Method,Insemination Method,Acquisition,Animal Status\n");

        for (OverduePregnanciesReportDTO r : rows) {
            csv.append(csvEscape(r.getTagNumber())).append(',')
                    .append(csvEscape(r.getCategoryName())).append(',')
                    .append(csvEscape(r.getGender())).append(',')
                    .append(r.getConceptionDate() != null ? r.getConceptionDate() : "").append(',')
                    .append(r.getExpectedDueDate() != null ? r.getExpectedDueDate() : "").append(',')
                    .append(r.getDaysOverdue() != null ? r.getDaysOverdue() : "").append(',')
                    .append(csvEscape(r.getSeverityLabel())).append(',')
                    .append(csvEscape(r.getBreedingMethod())).append(',')
                    .append(csvEscape(r.getInseminationMethod())).append(',')
                    .append(csvEscape(r.getAcquisitionMethod())).append(',')
                    .append(csvEscape(r.getAnimalStatus())).append('\n');
        }

        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"overdue-pregnancies-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    /** PDF export honoring the current filters, styled via PdfReportStyleUtil (OpenPDF). */
    @GetMapping("/export/pdf")
    @ResponseBody
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search) throws DocumentException, IOException {

        List<OverduePregnanciesReportDTO> rows = allRows(categoryId, search);
        OverduePregnanciesKpiDTO kpis = reportService.getKpis(categoryId, search);

        List<String[]> kpiPairs = new ArrayList<>();
        kpiPairs.add(new String[]{"Total Overdue", String.valueOf(kpis.getTotalOverdue())});
        kpiPairs.add(new String[]{"Watch (1-7d)", String.valueOf(kpis.getWatchCount())});
        kpiPairs.add(new String[]{"Concern (8-14d)", String.valueOf(kpis.getConcernCount())});
        kpiPairs.add(new String[]{"Critical (15d+)", String.valueOf(kpis.getCriticalCount())});
        kpiPairs.add(new String[]{"Avg Days Overdue", String.valueOf(kpis.getAverageDaysOverdue())});

        byte[] pdf = PdfReportStyleUtil.buildReport(
                "Overdue Pregnancies Report",
                "Pregnant animals past their expected due date, sorted oldest overdue first.",
                buildFilterSummary(categoryId, search),
                List.of("Tag", "Category", "Gender", "Due Date", "Days Overdue", "Severity", "Method", "Status"),
                new float[]{9, 11, 6, 9, 8, 8, 10, 8},
                kpiPairs,
                table -> {
                    for (OverduePregnanciesReportDTO r : rows) {
                        Color sevColor = switch (r.getSeverity()) {
                            case "CRITICAL" -> PdfReportStyleUtil.RED;
                            case "CONCERN" -> PdfReportStyleUtil.AMBER;
                            default -> PdfReportStyleUtil.BRAND_GREEN;
                        };
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getTagNumber(), null, true));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getCategoryName(), null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getGender(), null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(
                                r.getExpectedDueDate() != null ? r.getExpectedDueDate().format(DATE_FMT) : "-", null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(
                                r.getDaysOverdue() != null ? r.getDaysOverdue() + "d" : "-", sevColor, true));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getSeverityLabel(), sevColor, true));
                        table.addCell(PdfReportStyleUtil.bodyCell(
                                r.getInseminationMethod() != null ? r.getInseminationMethod() : "-", null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getAnimalStatus(), null, false));
                    }
                    if (rows.isEmpty()) {
                        PdfPCell empty = new PdfPCell(new Phrase("No overdue pregnancies match the selected filters."));
                        empty.setColspan(8);
                        empty.setPadding(12f);
                        table.addCell(empty);
                    }
                }
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"overdue-pregnancies-report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private List<OverduePregnanciesReportDTO> allRows(UUID categoryId, String search) {
        Pageable unpaged = PageRequest.of(0, 5000);
        return reportService.getReport(categoryId, search, unpaged).getContent();
    }

    private String buildFilterSummary(UUID categoryId, String search) {
        List<String> parts = new ArrayList<>();
        if (categoryId != null) {
            categoryRepository.findById(categoryId).ifPresent(c -> parts.add("Category: " + c.getName()));
        }
        if (search != null && !search.isBlank()) parts.add("Search: \"" + search + "\"");
        return parts.isEmpty() ? "All categories" : String.join("  \u00b7  ", parts);
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(",") || escaped.contains("\"") ? "\"" + escaped + "\"" : escaped;
    }
}
