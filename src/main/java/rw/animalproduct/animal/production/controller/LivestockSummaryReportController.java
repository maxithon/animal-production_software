package rw.animalproduct.animal.production.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import rw.animalproduct.animal.production.entity.Beneficiary;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.entity.LivestockTreatment;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.services.FinancialCalculationService;
import rw.animalproduct.animal.production.services.LivestockSaleService;
import rw.animalproduct.animal.production.services.LivestockTreatmentService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serves the printable / shareable Livestock Summary Report.
 *
 * This was previously missing entirely — GET /livestock/summary-report had
 * no @GetMapping anywhere in the app, so Spring's DispatcherServlet fell
 * through to the static-resource handler and produced a 404
 * (NoResourceFoundException). This controller adds that mapping and feeds
 * the report every figure the redesigned template needs.
 *
 * Financial figures are delegated to FinancialCalculationService (the
 * single source of truth also used by DashboardController) so this report
 * can never disagree with the admin dashboard.
 */
@Controller
public class LivestockSummaryReportController {

    private final LivestockRepository livestockRepository;
    private final LivestockTreatmentService treatmentService;
    private final LivestockSaleService saleService;
    private final FinancialCalculationService financialCalculationService;

    private static final ObjectMapper MAPPER;
    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public LivestockSummaryReportController(LivestockRepository livestockRepository,
                                            LivestockTreatmentService treatmentService,
                                            LivestockSaleService saleService,
                                            FinancialCalculationService financialCalculationService) {
        this.livestockRepository = livestockRepository;
        this.treatmentService = treatmentService;
        this.saleService = saleService;
        this.financialCalculationService = financialCalculationService;
    }

    @GetMapping("/livestock/summary-report")
    public String summaryReport(Authentication authentication, Model model) {

        // ── Base data set: exclude soft-deleted and draft records everywhere ──
        List<Livestock> allLivestock = livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .collect(Collectors.toList());

        long totalAll      = allLivestock.size();
        long countActive   = countByStatus(allLivestock, Livestock.STATUS_ACTIVE);
        long countSick     = countByStatus(allLivestock, Livestock.STATUS_SICK);
        long countPregnant = countByStatus(allLivestock, Livestock.STATUS_PREGNANT);
        long countSold     = countByStatus(allLivestock, Livestock.STATUS_SOLD);
        long countDead     = countByStatus(allLivestock, Livestock.STATUS_DEAD);

        List<Livestock> activeAnimals = allLivestock.stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()))
                .sorted(Comparator.comparing(Livestock::getTagNumber,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());

        BigDecimal totalActiveValue = sumValue(activeAnimals);
        BigDecimal averageActiveValue = activeAnimals.isEmpty()
                ? BigDecimal.ZERO
                : totalActiveValue.divide(BigDecimal.valueOf(activeAnimals.size()), 0, RoundingMode.HALF_UP);

        // ── Financial figures — same single source of truth as the dashboard ──
        LocalDate fromDate = null; // all-time
        LocalDate toDate = null;

        BigDecimal totalTreatmentSpend = financialCalculationService.calculatePreventiveTreatmentCosts(fromDate, toDate);
        BigDecimal totalSickSpend      = financialCalculationService.calculateCurativeTreatmentCosts(fromDate, toDate);
        BigDecimal totalCareSpend      = totalTreatmentSpend.add(totalSickSpend);
        BigDecimal totalSaleRevenue    = financialCalculationService.calculateSalesRevenue(fromDate, toDate);

        List<LivestockTreatment> treatments = treatmentService.getAll().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                .collect(Collectors.toList());
        long totalTreatmentsCount = treatments.size();

        List<LivestockSale> sales = saleService.getAll().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .collect(Collectors.toList());
        long totalSalesCount = sales.size();

        BigDecimal averageSalePrice = totalSalesCount == 0
                ? BigDecimal.ZERO
                : totalSaleRevenue.divide(BigDecimal.valueOf(totalSalesCount), 0, RoundingMode.HALF_UP);

        BigDecimal netPosition    = totalSaleRevenue.subtract(totalCareSpend);
        boolean isProfit          = netPosition.signum() >= 0;
        BigDecimal netPositionAbs = netPosition.abs();

        // What share of care spend is preventive vs. curative — useful signal
        // for whether the operation is managing herd health proactively.
        int preventiveSharePct = totalCareSpend.signum() == 0 ? 0
                : totalTreatmentSpend.multiply(BigDecimal.valueOf(100))
                .divide(totalCareSpend, 0, RoundingMode.HALF_UP).intValue();

        // ── Herd composition breakdowns (active animals only — this is what a
        //    reader cares about: what's actually on the farm right now) ──────
        Map<String, Long> categoryBreakdown = new LinkedHashMap<>();
        Map<String, Long> locationBreakdown = new LinkedHashMap<>();
        Map<String, Long> genderBreakdown = new LinkedHashMap<>();
        for (Livestock a : activeAnimals) {
            LivestockCategory cat = a.getLivestockCategory();
            if (cat != null && cat.getName() != null) {
                categoryBreakdown.merge(cat.getName(), 1L, Long::sum);
            }
            Location loc = a.getLocation();
            if (loc != null && loc.getName() != null) {
                locationBreakdown.merge(loc.getName(), 1L, Long::sum);
            }
            if (a.getGender() != null) {
                genderBreakdown.merge(a.getGender(), 1L, Long::sum);
            }
        }

        // Top 5 locations by headcount, rest bucketed as "Other" so the chart
        // stays readable even with many farm locations.
        Map<String, Long> topLocations = topNWithOther(locationBreakdown, 5);

        model.addAttribute("generatedAt", LocalDateTime.now());
        model.addAttribute("generatedBy", authentication != null ? authentication.getName() : "System");

        model.addAttribute("countActive", countActive);
        model.addAttribute("countSick", countSick);
        model.addAttribute("countPregnant", countPregnant);
        model.addAttribute("countSold", countSold);
        model.addAttribute("countDead", countDead);
        model.addAttribute("totalAll", totalAll);

        model.addAttribute("totalActiveValue", totalActiveValue);
        model.addAttribute("averageActiveValue", averageActiveValue);

        model.addAttribute("totalCareSpend", totalCareSpend);
        model.addAttribute("totalTreatmentSpend", totalTreatmentSpend);
        model.addAttribute("totalSickSpend", totalSickSpend);
        model.addAttribute("totalTreatmentsCount", totalTreatmentsCount);
        model.addAttribute("preventiveSharePct", preventiveSharePct);

        model.addAttribute("totalSaleRevenue", totalSaleRevenue);
        model.addAttribute("totalSalesCount", totalSalesCount);
        model.addAttribute("averageSalePrice", averageSalePrice);

        model.addAttribute("isProfit", isProfit);
        model.addAttribute("netPosition", netPosition);
        model.addAttribute("netPositionAbs", netPositionAbs);

        model.addAttribute("activeAnimals", activeAnimals);

        model.addAttribute("categoryBreakdown", categoryBreakdown);
        model.addAttribute("categoryBreakdownJson", toJson(categoryBreakdown));
        model.addAttribute("topLocationsJson", toJson(topLocations));
        model.addAttribute("genderBreakdownJson", toJson(genderBreakdown));
        model.addAttribute("financeChartJson",
                toJson(List.of(totalSaleRevenue, totalTreatmentSpend, totalSickSpend)));

        return "livestock-summary-report";
    }

    // ────────────────────────────────────────────────────────────────────────

    private long countByStatus(List<Livestock> list, String status) {
        return list.stream().filter(l -> status.equals(l.getStatus())).count();
    }

    private BigDecimal sumValue(List<Livestock> list) {
        return list.stream()
                .map(Livestock::getCurrentValue)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Long> topNWithOther(Map<String, Long> source, int n) {
        Map<String, Long> result = new LinkedHashMap<>();
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(source.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        long otherTotal = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (i < n) {
                result.put(sorted.get(i).getKey(), sorted.get(i).getValue());
            } else {
                otherTotal += sorted.get(i).getValue();
            }
        }
        if (otherTotal > 0) {
            result.put("Other", otherTotal);
        }
        return result;
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
