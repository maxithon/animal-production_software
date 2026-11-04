package rw.animalproduct.animal.production.controller;

import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import rw.animalproduct.animal.production.dto.ReadyToBreedKpiDTO;
import rw.animalproduct.animal.production.dto.ReadyToBreedReportDTO;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.services.ReadyToBreedReportService;
import rw.animalproduct.animal.production.util.PdfReportStyleUtil;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/livestock/ready-to-breed-report")
@RequiredArgsConstructor
public class ReadyToBreedReportController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final String REPORT_TITLE = "Ready to Breed Report";
    private static final String REPORT_SUBTITLE =
            "Which animals can be bred now, and why. Not a retirement or reproductive-performance report — see the "
                    + "Breeding Eligibility & Reproductive Performance Report for that.";

    private final ReadyToBreedReportService reportService;
    private final LivestockCategoryRepository categoryRepository;

    @GetMapping
    public String report(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String notifyStatus,
            @RequestParam(required = false) String readiness,
            // No sort configured on purpose — fixed inside the native query.
            @PageableDefault(size = 25) Pageable pageable,
            Model model) {

        Page<ReadyToBreedReportDTO> page = reportService.getReport(categoryId, search, notifyStatus, readiness, pageable);
        ReadyToBreedKpiDTO kpis = reportService.getKpis(categoryId, search);

        model.addAttribute("rows", page.getContent());
        model.addAttribute("currentPage", page.getNumber());
        model.addAttribute("totalPages", page.getTotalPages());
        model.addAttribute("totalItems", page.getTotalElements());
        model.addAttribute("pageSize", page.getSize());
        model.addAttribute("kpis", kpis);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("categoryIdFilter", categoryId);
        model.addAttribute("searchFilter", search);
        model.addAttribute("notifyStatusFilter", notifyStatus);
        model.addAttribute("readinessFilter", readiness);
        model.addAttribute("today", LocalDate.now());

        return "livestock-ready-to-breed-report";
    }

    /**
     * Marks a single animal as notified (sets breeding_ready_notified_at = now)
     * and redirects back to the report preserving whatever filters/page were active.
     */
    @PostMapping("/{id}/notify")
    public String markNotified(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String notifyStatus,
            @RequestParam(required = false) String readiness,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            RedirectAttributes redirectAttributes) {

        boolean updated = reportService.markNotified(id);
        redirectAttributes.addFlashAttribute(
                updated ? "successMessage" : "errorMessage",
                updated ? "Animal marked as notified." : "Could not find that animal to update.");

        UriComponentsBuilder redirect = UriComponentsBuilder.fromPath("/livestock/ready-to-breed-report")
                .queryParamIfPresent("categoryId", java.util.Optional.ofNullable(categoryId))
                .queryParamIfPresent("search", java.util.Optional.ofNullable(search))
                .queryParamIfPresent("notifyStatus", java.util.Optional.ofNullable(notifyStatus))
                .queryParamIfPresent("readiness", java.util.Optional.ofNullable(readiness))
                .queryParam("page", page);

        return "redirect:" + redirect.toUriString();
    }

    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String notifyStatus,
            @RequestParam(required = false) String readiness) {

        List<ReadyToBreedReportDTO> rows = allRows(categoryId, search, notifyStatus, readiness);

        StringBuilder csv = new StringBuilder();
        csv.append("Tag,Category,Gender,Age,Min/Max Breeding Age (m),Readiness Status,")
                .append("Reason: Min Breeding Age,Reason: Recovery Completed,Reason: Not Pregnant,Reason: No Breeding Restriction,")
                .append("Last Birth Date,Days Since Last Birth,Postpartum Recovery Status,Last Breeding Date,")
                .append("Ready Since,Recommended Breeding Date,Days Ready,Days Until Ready,")
                .append("Beneficiary,Beneficiary Phone,Beneficiary Address,")
                .append("Province,District,Sector,Cell,Village,")
                .append("Notified,Notified At,Notification Status\n");

        for (ReadyToBreedReportDTO r : rows) {
            csv.append(csvEscape(r.getTagNumber())).append(',')
                    .append(csvEscape(r.getCategoryName())).append(',')
                    .append(csvEscape(r.getGender())).append(',')
                    .append(csvEscape(r.getAgeDisplay())).append(',')
                    .append(r.getMinBreedingAgeMonths() != null ? r.getMinBreedingAgeMonths() : "")
                    .append('-').append(r.getMaxBreedingAgeMonths() != null ? r.getMaxBreedingAgeMonths() : "").append(',')
                    .append(csvEscape(r.getReadinessLabel())).append(',')
                    .append(r.isReasonReachedBreedingAge() ? "Yes" : "No").append(',')
                    .append(r.isReasonRecoveryCompleted() ? "Yes" : "No").append(',')
                    .append(r.isReasonNotPregnant() ? "Yes" : "No").append(',')
                    .append(r.isReasonNoBreedingRestriction() ? "Yes" : "No").append(',')
                    .append(r.getLastBirthDate() != null ? r.getLastBirthDate() : "").append(',')
                    .append(r.getDaysSinceLastBirth() != null ? r.getDaysSinceLastBirth() : "").append(',')
                    .append(csvEscape(r.getPostpartumRecoveryStatus())).append(',')
                    .append(r.getLastBreedingDate() != null ? r.getLastBreedingDate() : "").append(',')
                    .append(r.getReadySince() != null ? r.getReadySince() : "").append(',')
                    .append(r.getRecommendedBreedingDate() != null ? r.getRecommendedBreedingDate() : "").append(',')
                    .append(r.getDaysReadyForBreeding() != null ? r.getDaysReadyForBreeding() : "").append(',')
                    .append(r.getDaysUntilReady() != null ? r.getDaysUntilReady() : "").append(',')
                    .append(csvEscape(r.getBeneficiaryName())).append(',')
                    .append(csvEscape(r.getBeneficiaryPhone())).append(',')
                    .append(csvEscape(r.getBeneficiaryLocationName())).append(',')
                    .append(csvEscape(r.getAnimalLocationProvince())).append(',')
                    .append(csvEscape(r.getAnimalLocationDistrict())).append(',')
                    .append(csvEscape(r.getAnimalLocationSector())).append(',')
                    .append(csvEscape(r.getAnimalLocationCell())).append(',')
                    .append(csvEscape(r.getAnimalLocationVillage())).append(',')
                    .append(r.isNotified() ? "Yes" : "No").append(',')
                    .append(r.getNotifiedAt() != null ? r.getNotifiedAt() : "").append(',')
                    .append(csvEscape(r.getNotificationStatusLabel())).append('\n');
        }

        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ready-to-breed-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    @GetMapping("/export/pdf")
    @ResponseBody
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String notifyStatus,
            @RequestParam(required = false) String readiness) throws DocumentException, IOException {

        List<ReadyToBreedReportDTO> rows = allRows(categoryId, search, notifyStatus, readiness);
        ReadyToBreedKpiDTO kpis = reportService.getKpis(categoryId, search);

        List<String[]> kpiPairs = new ArrayList<>();
        kpiPairs.add(new String[]{"Ready (4/4)", String.valueOf(kpis.getTotalReadyToBreed())});
        kpiPairs.add(new String[]{"Almost Ready", String.valueOf(kpis.getAlmostReady())});
        kpiPairs.add(new String[]{"Notified", String.valueOf(kpis.getNotified())});
        kpiPairs.add(new String[]{"Awaiting", String.valueOf(kpis.getAwaitingNotification())});
        kpiPairs.add(new String[]{"Overdue", String.valueOf(kpis.getNotificationOverdue())});
        kpiPairs.add(new String[]{"First-Time", String.valueOf(kpis.getFirstTimeBreeders())});
        kpiPairs.add(new String[]{"Avg Days Ready", String.valueOf(kpis.getAverageDaysReady())});

        // Reason checklist and full location breakdown are summarized per row for
        // print legibility (A4/letter portrait) — full detail is in the CSV export.
        byte[] pdf = PdfReportStyleUtil.buildReport(
                REPORT_TITLE,
                REPORT_SUBTITLE,
                buildFilterSummary(categoryId, search, notifyStatus, readiness),
                List.of("Tag", "Category", "Gender", "Readiness", "Reasons Met", "Last Birth",
                        "Ready Since", "Recommended Date", "Beneficiary / Location", "Notification"),
                new float[]{7, 8, 5, 9, 15, 8, 8, 9, 18, 13},
                kpiPairs,
                table -> {
                    for (ReadyToBreedReportDTO r : rows) {
                        boolean isReady = "READY".equals(r.getReadinessStatus());
                        Color readinessColor = isReady ? PdfReportStyleUtil.BRAND_GREEN : PdfReportStyleUtil.AMBER;
                        Color notifyColor = switch (r.getNotificationStatus()) {
                            case "OVERDUE" -> PdfReportStyleUtil.RED;
                            case "AWAITING" -> PdfReportStyleUtil.AMBER;
                            default -> PdfReportStyleUtil.BRAND_GREEN;
                        };
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getTagNumber(), null, true));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getCategoryName(), null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getGender(), null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(isReady ? "Ready" : "Almost Ready", readinessColor, true));
                        table.addCell(PdfReportStyleUtil.bodyCell(reasonChecklistSummary(r), null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(
                                r.getLastBirthDate() != null ? r.getLastBirthDate().format(DATE_FMT) : "No births yet",
                                null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(
                                r.getReadySince() != null ? r.getReadySince().format(DATE_FMT) : "-", null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(
                                r.getRecommendedBreedingDate() != null ? r.getRecommendedBreedingDate().format(DATE_FMT) : "-",
                                readinessColor, true));
                        table.addCell(PdfReportStyleUtil.bodyCell(beneficiaryLocationSummary(r), null, false));
                        table.addCell(PdfReportStyleUtil.bodyCell(r.getNotificationStatusLabel(), notifyColor, true));
                    }
                    if (rows.isEmpty()) {
                        PdfPCell empty = new PdfPCell(new Phrase("No animals match the current filters.",
                                FontFactory.getFont(FontFactory.HELVETICA, 9, Font.ITALIC, Color.GRAY)));
                        empty.setColspan(10);
                        empty.setPadding(12f);
                        table.addCell(empty);
                    }
                }
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ready-to-breed-report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // NOTE: these two helpers embed "\n" for a two-line cell. Whether that renders as
    // a real line break depends on how PdfReportStyleUtil.bodyCell() builds its Phrase
    // — I don't have that file's source. If it renders as a literal backslash-n instead
    // of a line break, replace "\n" with " · " for a single-line fallback, or split into
    // two stacked bodyCell() calls inside a nested PdfPTable.
    private String reasonChecklistSummary(ReadyToBreedReportDTO r) {
        StringBuilder sb = new StringBuilder();
        for (ReadyToBreedReportDTO.ReasonItem item : r.getReasonChecklist()) {
            sb.append(item.isMet() ? "\u2713 " : "\u2717 ").append(item.getLabel()).append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private String beneficiaryLocationSummary(ReadyToBreedReportDTO r) {
        String beneficiary = r.getBeneficiaryName() != null ? r.getBeneficiaryName() : "-";
        String phone = r.getBeneficiaryPhone() != null ? " (" + r.getBeneficiaryPhone() + ")" : "";
        return beneficiary + phone + "\n" + r.getAnimalLocationBreadcrumb();
    }

    private List<ReadyToBreedReportDTO> allRows(UUID categoryId, String search, String notifyStatus, String readiness) {
        Pageable unpaged = PageRequest.of(0, 5000);
        return reportService.getReport(categoryId, search, notifyStatus, readiness, unpaged).getContent();
    }

    private String buildFilterSummary(UUID categoryId, String search, String notifyStatus, String readiness) {
        List<String> parts = new ArrayList<>();
        if (categoryId != null) {
            categoryRepository.findById(categoryId).ifPresent(c -> parts.add("Category: " + c.getName()));
        }
        if (readiness != null && !readiness.isBlank()) parts.add("Readiness: " + readiness);
        if (notifyStatus != null && !notifyStatus.isBlank()) parts.add("Status: " + notifyStatus);
        if (search != null && !search.isBlank()) parts.add("Search: \"" + search + "\"");
        return parts.isEmpty() ? "All ready-to-breed animals" : String.join("  \u00b7  ", parts);
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return escaped.contains(",") || escaped.contains("\"") ? "\"" + escaped + "\"" : escaped;
    }
}