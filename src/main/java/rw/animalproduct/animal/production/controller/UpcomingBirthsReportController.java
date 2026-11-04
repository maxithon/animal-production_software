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
import rw.animalproduct.animal.production.dto.CategoryBreakdownDTO;
import rw.animalproduct.animal.production.dto.UpcomingBirthsKpiDTO;
import rw.animalproduct.animal.production.dto.UpcomingBirthsReportDTO;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.services.UpcomingBirthsReportService;
import rw.animalproduct.animal.production.util.PdfReportStyleUtil;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/livestock/upcoming-births-report")
@RequiredArgsConstructor
public class UpcomingBirthsReportController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final List<Integer> WINDOW_OPTIONS = List.of(7, 14, 30, 60, 90);

    private final UpcomingBirthsReportService reportService;
    private final LivestockCategoryRepository categoryRepository;

    @GetMapping
    public String report(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "30") Integer windowDays,
            @PageableDefault(size = 25) Pageable pageable,
            Model model) {

        Page<UpcomingBirthsReportDTO> page = reportService.getReport(categoryId, search, windowDays, pageable);
        UpcomingBirthsKpiDTO kpis = reportService.getKpis(categoryId, search);
        List<CategoryBreakdownDTO> breakdown = reportService.getCategoryBreakdown(categoryId, search);

        model.addAttribute("rows", page.getContent());
        model.addAttribute("currentPage", page.getNumber());
        model.addAttribute("totalPages", page.getTotalPages());
        model.addAttribute("totalItems", page.getTotalElements());
        model.addAttribute("pageSize", page.getSize());
        model.addAttribute("kpis", kpis);
        model.addAttribute("categoryBreakdown", breakdown);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("categoryIdFilter", categoryId);
        model.addAttribute("searchFilter", search);
        model.addAttribute("windowDaysFilter", windowDays);
        model.addAttribute("windowOptions", WINDOW_OPTIONS);

        return "livestock-upcoming-births-report";
    }

    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "30") Integer windowDays) {

        List<UpcomingBirthsReportDTO> rows = allRows(categoryId, search, windowDays);

        StringBuilder csv = new StringBuilder();
        csv.append("Tag,Category,Breeding Date,Expected Birth Date,Days Remaining,Status,")
                .append("Expected Offspring,Location,Gender,Animal Status\n");

        for (UpcomingBirthsReportDTO r : rows) {
            csv.append(csvEscape(r.getTagNumber())).append(',')
                    .append(csvEscape(r.getCategoryName())).append(',')
                    .append(r.getBreedingDate() != null ? r.getBreedingDate() : "").append(',')
                    .append(r.getExpectedDueDate() != null ? r.getExpectedDueDate() : "").append(',')
                    .append(r.getDaysUntilDue() != null ? r.getDaysUntilDue() : "").append(',')
                    .append(csvEscape(r.getStatusLabel())).append(',')
                    .append(r.getExpectedOffspring() != null ? r.getExpectedOffspring() : "").append(',')
                    .append(csvEscape(r.getLocation())).append(',')
                    .append(csvEscape(r.getGender())).append(',')
                    .append(csvEscape(r.getAnimalStatus())).append('\n');
        }

        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"upcoming-births-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    @GetMapping("/export/pdf")
    @ResponseBody
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "30") Integer windowDays) throws DocumentException, IOException {

        List<UpcomingBirthsReportDTO> rows = allRows(categoryId, search, windowDays);
        UpcomingBirthsKpiDTO kpis = reportService.getKpis(categoryId, search);

        List<String[]> kpiPairs = new ArrayList<>();
        kpiPairs.add(new String[]{"Total Pregnant Animals", String.valueOf(kpis.getTotalPregnant())});
        kpiPairs.add(new String[]{"Due Within 3 Days", String.valueOf(kpis.getDueWithin3Days())});
        kpiPairs.add(new String[]{"Due This Week", String.valueOf(kpis.getDueThisWeek())});
        kpiPairs.add(new String[]{"Due This Month", String.valueOf(kpis.getDueThisMonth())});
        kpiPairs.add(new String[]{"Overdue Pregnancies", String.valueOf(kpis.getOverduePregnancies())});
        kpiPairs.add(new String[]{"Avg Days Until Due", String.valueOf(kpis.getAverageDaysUntilDue())});

        byte[] pdf = PdfReportStyleUtil.buildReport(
                "Upcoming Births Report",
                "Pregnant animals due within the next " + windowDays + " days, including any already overdue, sorted soonest first. "
                        + "KPI cards above show totals across all pregnant animals regardless of this window.",
                buildFilterSummary(categoryId, search),
                List.of("Tag", "Category", "Breeding Date", "Due Date", "Days Left", "Status", "Offspring", "Location"),
                new float[]{9, 11, 9, 9, 8, 12, 8, 10},
                kpiPairs,
                table -> {
                    for (UpcomingBirthsReportDTO r : rows) {
                        Color statusColor = switch (r.getStatus()) {
                            case "OVERDUE" -> PdfReportStyleUtil.RED;
                            case "DUE_SOON" -> PdfReportStyleUtil.RED;
                            case "DUE_THIS_WEEK" -> PdfReportStyleUtil.AMBER;
                            default -> PdfReportStyleUtil.BRAND_GREEN;
                        };
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getTagNumber(), null, true));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getCategoryName(), null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(
                                r.getBreedingDate() != null ? r.getBreedingDate().format(DATE_FMT) : "-", null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(
                                r.getExpectedDueDate() != null ? r.getExpectedDueDate().format(DATE_FMT) : "-", null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(
                                r.getDaysUntilDue() != null ? r.getDaysUntilDue() + "d" : "-", statusColor, true));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getStatusLabel(), statusColor, true));
                        table.addCell(PdfReportStyleUtil.bodyCell(
                                r.getExpectedOffspring() != null ? String.valueOf(r.getExpectedOffspring()) : "-", null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(
                                r.getLocation() != null ? r.getLocation() : "-", null, false));
                    }
                    if (rows.isEmpty()) {
                        PdfPCell empty = new PdfPCell(new Phrase("No upcoming births match the selected filters."));
                        empty.setColspan(8);
                        empty.setPadding(12f);
                        table.addCell(empty);
                    }
                }
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"upcoming-births-report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private List<UpcomingBirthsReportDTO> allRows(UUID categoryId, String search, Integer windowDays) {
        Pageable unpaged = PageRequest.of(0, 5000);
        return reportService.getReport(categoryId, search, windowDays, unpaged).getContent();
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