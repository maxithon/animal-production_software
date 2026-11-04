package rw.animalproduct.animal.production.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import rw.animalproduct.animal.production.dto.HerdMovementReportDto;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.services.HerdMovementService;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livestock/herd-movement-report")
public class HerdMovementController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HerdMovementService herdMovementService;
    private final LivestockCategoryRepository livestockCategoryRepository;

    public HerdMovementController(HerdMovementService herdMovementService,
                                  LivestockCategoryRepository livestockCategoryRepository) {
        this.herdMovementService = herdMovementService;
        this.livestockCategoryRepository = livestockCategoryRepository;
    }

    /* ================================================================
     *  HTML report
     * ================================================================ */
    @GetMapping
    public String showReport(@RequestParam(required = false) String from,
                             @RequestParam(required = false) String to,
                             @RequestParam(required = false) UUID categoryId,
                             @RequestParam(required = false) List<UUID> categoryIds,
                             Model model) {

        LocalDate today = LocalDate.now();
        LocalDate fromDate = (from != null && !from.isBlank())
                ? LocalDate.parse(from) : today.withDayOfMonth(1);
        LocalDate toDate = (to != null && !to.isBlank())
                ? LocalDate.parse(to) : today;

        if (fromDate.isAfter(toDate)) {
            LocalDate tmp = fromDate; fromDate = toDate; toDate = tmp;
        }

        // Merge single-select (legacy) and multi-select into one list
        List<UUID> selectedCategoryIds;
        if (categoryIds != null && !categoryIds.isEmpty()) {
            selectedCategoryIds = categoryIds;
        } else if (categoryId != null) {
            selectedCategoryIds = List.of(categoryId);
        } else {
            selectedCategoryIds = List.of();
        }

        HerdMovementReportDto report =
                herdMovementService.generateReport(fromDate, toDate, selectedCategoryIds);

        List<LivestockCategory> allCategories = livestockCategoryRepository.findAll().stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .sorted(Comparator.comparing(LivestockCategory::getName))
                .collect(Collectors.toList());

        String selectedCategoryName;
        if (selectedCategoryIds.isEmpty()) {
            selectedCategoryName = "All Categories";
        } else if (selectedCategoryIds.size() == 1) {
            UUID only = selectedCategoryIds.get(0);
            selectedCategoryName = allCategories.stream()
                    .filter(c -> c.getId().equals(only))
                    .map(LivestockCategory::getName)
                    .findFirst()
                    .orElse("Unknown Category");
        } else {
            selectedCategoryName = selectedCategoryIds.size() + " categories";
        }

        model.addAttribute("report", report);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("allCategories", allCategories);
        model.addAttribute("selectedCategoryIds", selectedCategoryIds);
        model.addAttribute("selectedCategoryName", selectedCategoryName);

        return "herd-movement-report";
    }

    /* ================================================================
     *  CSV export
     * ================================================================ */
    @GetMapping("/csv")
    public void exportCsv(@RequestParam(required = false) String from,
                          @RequestParam(required = false) String to,
                          @RequestParam(required = false) UUID categoryId,
                          @RequestParam(required = false) List<UUID> categoryIds,
                          HttpServletResponse response) throws IOException {

        LocalDate today = LocalDate.now();
        LocalDate fromDate = (from != null && !from.isBlank()) ? LocalDate.parse(from) : today.withDayOfMonth(1);
        LocalDate toDate = (to != null && !to.isBlank()) ? LocalDate.parse(to) : today;

        List<UUID> selected;
        if (categoryIds != null && !categoryIds.isEmpty()) {
            selected = categoryIds;
        } else if (categoryId != null) {
            selected = List.of(categoryId);
        } else {
            selected = List.of();
        }

        HerdMovementReportDto r = herdMovementService.generateReport(fromDate, toDate, selected);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=herd-movement-"
                        + fromDate.format(ISO) + "-to-" + toDate.format(ISO) + ".csv");

        try (PrintWriter w = response.getWriter()) {
            w.println("Period," + fromDate.format(ISO) + " to " + toDate.format(ISO));
            w.println();
            w.println("Category,Opening,Born,Purchased,Donated,Sold,Died,Closing,TLU,"
                    + "Opening Value,Closing Value,Net Value Change,Avg Value/Animal");

            for (var row : r.getByCategory()) {
                w.printf("%s,%d,%d,%d,%d,%d,%d,%d,%s,%s,%s,%s,%s%n",
                        csv(row.getCategoryName()),
                        row.getOpeningCount(),
                        row.getBornCount(),
                        row.getPurchasedCount(),
                        row.getDonatedCount(),
                        row.getSoldCount(),
                        row.getDeadCount(),
                        row.getClosingCount(),
                        row.getClosingTLU() != null ? row.getClosingTLU().toPlainString() : "0",
                        csvBD(row.getOpeningValue()),
                        csvBD(row.getClosingValue()),
                        csvBD(row.getNetValueChange()),
                        csvBD(row.getAvgValuePerAnimal()));
            }

            w.println();
            w.printf("TOTAL,%d,%d,%d,%d,%d,%d,%d,%s,%s,%s,%s,%n",
                    r.getOpeningCount(), r.getBornCount(), r.getPurchasedCount(), r.getDonatedCount(),
                    r.getSoldCount(), r.getDeadCount(), r.getClosingCount(),
                    r.getTotalClosingTLU().toPlainString(),
                    csvBD(r.getOpeningValue()), csvBD(r.getClosingValue()),
                    csvBD(r.getNetFinancialChange()));

            w.println();
            w.println("Herd Structure");
            w.println("Category,Sex/Age Class,Count,Value,% of Category");
            for (var s : r.getHerdStructure()) {
                w.printf("%s,%s,%d,%s,%s%%%n",
                        csv(s.getCategoryName()), csv(s.getSexAgeLabel()),
                        s.getCount(), csvBD(s.getValue()), s.getPercentOfCategory());
            }
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        return s.contains(",") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static String csvBD(BigDecimal v) {
        return v != null ? v.toPlainString() : "0";
    }
}