package rw.animalproduct.animal.production.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.dto.OffspringProductionReportDTO;
import rw.animalproduct.animal.production.dto.OffspringTrendPointDTO;
import rw.animalproduct.animal.production.dto.MotherProductionDetailDTO;
import rw.animalproduct.animal.production.entity.DateRange;
import rw.animalproduct.animal.production.entity.ProductionPeriod;
import rw.animalproduct.animal.production.services.LivestockCategoryService;
import rw.animalproduct.animal.production.services.OffspringProductionReportService;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/livestock/offspring-production-report")
public class OffspringProductionReportController {

    private final OffspringProductionReportService reportService;
    private final LivestockCategoryService categoryService;

    private static final List<String> STATUSES = Arrays.asList(
            "ACTIVE", "SOLD", "DEAD", "TRANSFERRED"
    );

    @GetMapping
    public String report(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductionPeriod period,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        // Resolve date range
        DateRange range = reportService.resolvePeriod(period, fromDate, toDate);

        // Get report data
        Pageable pageable = PageRequest.of(page, size);
        Page<OffspringProductionReportDTO> reportPage = reportService.getReport(
                categoryId, status, search, range, pageable);

        // Get KPI data
        var kpis = reportService.getKpis(categoryId, status, range);

        // Get trend data
        List<OffspringTrendPointDTO> trend = reportService.getTrend(categoryId, status, search, range);

        // Get top 5 producers
        Page<OffspringProductionReportDTO> topProducers = reportService.getReport(
                categoryId, status, search, range, PageRequest.of(0, 5));

        // Add to model
        model.addAttribute("rows", reportPage.getContent());
        model.addAttribute("totalItems", reportPage.getTotalElements());
        model.addAttribute("totalPages", reportPage.getTotalPages());
        model.addAttribute("currentPage", reportPage.getNumber());
        model.addAttribute("pageSize", reportPage.getSize());

        model.addAttribute("kpis", kpis);
        model.addAttribute("trend", trend);
        model.addAttribute("topProducers", topProducers.getContent());

        model.addAttribute("categories", categoryService.getAll());
        model.addAttribute("statuses", STATUSES);
        model.addAttribute("periods", ProductionPeriod.values());

        // Preserve filter values
        model.addAttribute("categoryIdFilter", categoryId);
        model.addAttribute("statusFilter", status);
        model.addAttribute("searchFilter", search);
        model.addAttribute("periodFilter", period != null ? period : ProductionPeriod.ALL_TIME);
        model.addAttribute("fromDateFilter", fromDate);
        model.addAttribute("toDateFilter", toDate);

        log.info("Offspring production report: {} rows, {} trend points (categoryId={}, status={}, search={}, period={})",
                reportPage.getTotalElements(), trend.size(), categoryId, status, search, period);

        return "livestock-offspring-production-report";
    }

    @GetMapping("/mother/{motherId}")
    @ResponseBody
    public ResponseEntity<MotherProductionDetailDTO> getMotherDetail(@PathVariable UUID motherId) {
        MotherProductionDetailDTO detail = reportService.getMotherDetail(motherId);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
    }
}