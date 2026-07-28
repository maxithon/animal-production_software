package rw.animalproduct.animal.production.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.services.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class LivestockBusinessOverviewController {

    private final LivestockRepository livestockRepository;
    private final LivestockBirthService birthService;
    private final LivestockSaleService saleService;
    private final LivestockDeathService deathService;
    private final LivestockTreatmentService treatmentService;
    private final LivestockSickService sickService;
    private final FinancialCalculationService financialCalculationService;

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM");

    public LivestockBusinessOverviewController(LivestockRepository livestockRepository,
                                               LivestockBirthService birthService,
                                               LivestockSaleService saleService,
                                               LivestockDeathService deathService,
                                               LivestockTreatmentService treatmentService,
                                               LivestockSickService sickService,
                                               FinancialCalculationService financialCalculationService) {
        this.livestockRepository = livestockRepository;
        this.birthService = birthService;
        this.saleService = saleService;
        this.deathService = deathService;
        this.treatmentService = treatmentService;
        this.sickService = sickService;
        this.financialCalculationService = financialCalculationService;
    }

    private <T> List<T> safe(List<T> l) { return l != null ? l : Collections.emptyList(); }
    private BigDecimal bd(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    private String catName(Livestock ls) {
        if (ls == null || ls.getLivestockCategory() == null) return "Uncategorized";
        String n = ls.getLivestockCategory().getName();
        return n != null ? n : "Uncategorized";
    }

    @GetMapping(value = "/livestock/business-overview-data", produces = "application/json")
    @ResponseBody
    public Map<String, Object> businessOverviewData(
            @RequestParam(name = "months", defaultValue = "12") int months) {

        if (months < 1) months = 1;
        if (months > 36) months = 36;

        List<Livestock> allLivestock = safe(livestockRepository.findAll());
        List<LivestockBirth> births = safe(birthService.getAll());
        List<LivestockSale> sales = safe(saleService.getAll());
        List<LivestockDeath> deaths = safe(deathService.getAll());
        List<LivestockTreatment> treatments = safe(treatmentService.getAll());
        List<LivestockSick> sickRecords = safe(sickService.getAll());

        // ── Headline financials (single source of truth) ───────────────────
        BigDecimal salesRevenue = bd(financialCalculationService.calculateSalesRevenue(null, null));
        BigDecimal activeStockValue = bd(financialCalculationService.calculateCurrentHerdValue());
        BigDecimal bornAnimalsValue = bd(financialCalculationService.calculateBornAnimalsValue(null, null));
        BigDecimal purchaseCosts = bd(financialCalculationService.calculatePurchaseCosts(null, null));
        BigDecimal preventiveCosts = bd(financialCalculationService.calculatePreventiveTreatmentCosts(null, null));
        BigDecimal curativeCosts = bd(financialCalculationService.calculateCurativeTreatmentCosts(null, null));
        BigDecimal treatmentCosts = preventiveCosts.add(curativeCosts);
        BigDecimal deathLoss = bd(financialCalculationService.calculateDeathLoss(null, null));
        BigDecimal totalIncome = bd(financialCalculationService.calculateTotalIncome(null, null));
        BigDecimal totalExpenses = bd(financialCalculationService.calculateTotalExpenses(null, null));
        BigDecimal netPosition = bd(financialCalculationService.calculateNetProfit(null, null));

        String businessStatus;
        if (netPosition.compareTo(BigDecimal.ZERO) > 0) businessStatus = "gain";
        else if (netPosition.compareTo(BigDecimal.ZERO) < 0) businessStatus = "loss";
        else businessStatus = "breakeven";

        // ── Herd counts ──────────────────────────────────────────────────
        long totalAnimals = allLivestock.size();
        long activeCount = allLivestock.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count();
        long soldCount = allLivestock.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count();
        long sickCount = allLivestock.stream().filter(l -> Livestock.STATUS_SICK.equals(l.getStatus())).count();
        long deadCount = allLivestock.stream().filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus())).count();
        long pregnantCount = allLivestock.stream().filter(l -> Livestock.STATUS_PREGNANT.equals(l.getStatus())).count();

        // ── Performance indicators ──────────────────────────────────────
        double mortalityRate = (totalAnimals + deaths.size()) > 0
                ? Math.round(((double) deaths.size() / (totalAnimals + deaths.size())) * 1000) / 10.0 : 0;
        long openingHerd = activeCount + soldCount + deadCount;
        double offtakeRate = openingHerd > 0 ? Math.round(((double) sales.size() / openingHerd) * 1000) / 10.0 : 0;
        double replacementRate = totalAnimals > 0 ? Math.round(((double) births.size() / totalAnimals) * 1000) / 10.0 : 0;
        double avgSalePrice = !sales.isEmpty() ? salesRevenue.doubleValue() / sales.size() : 0;

        Map<String, Object> indicators = new LinkedHashMap<>();
        indicators.put("mortalityRate", mortalityRate);
        indicators.put("offtakeRate", offtakeRate);
        indicators.put("replacementRate", replacementRate);
        indicators.put("avgSalePrice", Math.round(avgSalePrice));

        // ── Alerts ───────────────────────────────────────────────────────
        long unpaidTreatments = treatments.stream().filter(t -> t.getIsPaid() != null && !t.getIsPaid()).count();
        BigDecimal unpaidTreatmentValue = treatments.stream()
                .filter(t -> t.getIsPaid() != null && !t.getIsPaid() && t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long ongoingTreatments = treatments.stream()
                .filter(t -> t.getTreatmentStatus() != null && t.getTreatmentStatus().name().equalsIgnoreCase("ONGOING"))
                .count();
        long activeSickCases = sickRecords.stream()
                .filter(s -> s.getStatus() != null && !s.getStatus().name().equals("RECOVERED"))
                .count();

        Map<String, Object> alerts = new LinkedHashMap<>();
        alerts.put("unpaidTreatments", unpaidTreatments);
        alerts.put("unpaidTreatmentValue", unpaidTreatmentValue);
        alerts.put("ongoingTreatments", ongoingTreatments);
        alerts.put("activeSickCases", activeSickCases);
        alerts.put("pregnantCount", pregnantCount);

        // ── Rolling monthly trend series (last N months, oldest -> newest) ─
        LocalDate today = LocalDate.now();
        List<YearMonth> window = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) window.add(YearMonth.from(today).minusMonths(i));

        Map<YearMonth, BigDecimal> revenueByMonth = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> costsByMonth = new LinkedHashMap<>();
        Map<YearMonth, Integer> birthsByMonth = new LinkedHashMap<>();
        Map<YearMonth, Integer> salesByMonth = new LinkedHashMap<>();
        Map<YearMonth, Integer> deathsByMonth = new LinkedHashMap<>();
        Map<YearMonth, Integer> treatmentsByMonth = new LinkedHashMap<>();
        for (YearMonth ym : window) {
            revenueByMonth.put(ym, BigDecimal.ZERO);
            costsByMonth.put(ym, BigDecimal.ZERO);
            birthsByMonth.put(ym, 0);
            salesByMonth.put(ym, 0);
            deathsByMonth.put(ym, 0);
            treatmentsByMonth.put(ym, 0);
        }

        for (LivestockSale s : sales) {
            if (s.getSaleDate() == null) continue;
            YearMonth ym = YearMonth.from(s.getSaleDate());
            if (!salesByMonth.containsKey(ym)) continue;
            salesByMonth.merge(ym, 1, Integer::sum);
            if (s.getSalePrice() != null) revenueByMonth.merge(ym, s.getSalePrice(), BigDecimal::add);
        }
        for (LivestockTreatment t : treatments) {
            if (t.getTreatmentDate() == null) continue;
            YearMonth ym = YearMonth.from(t.getTreatmentDate());
            if (!treatmentsByMonth.containsKey(ym)) continue;
            treatmentsByMonth.merge(ym, 1, Integer::sum);
            if (t.getTreatmentCost() != null) costsByMonth.merge(ym, t.getTreatmentCost(), BigDecimal::add);
        }
        for (LivestockBirth b : births) {
            if (b.getBirthDate() == null) continue;
            YearMonth ym = YearMonth.from(b.getBirthDate());
            if (birthsByMonth.containsKey(ym)) birthsByMonth.merge(ym, 1, Integer::sum);
        }
        for (LivestockDeath d : deaths) {
            if (d.getDeathDate() == null) continue;
            YearMonth ym = YearMonth.from(d.getDeathDate());
            if (deathsByMonth.containsKey(ym)) deathsByMonth.merge(ym, 1, Integer::sum);
        }

        List<String> monthLabels = new ArrayList<>();
        List<BigDecimal> monthlyRevenue = new ArrayList<>();
        List<BigDecimal> monthlyCosts = new ArrayList<>();
        List<Integer> monthlyBirths = new ArrayList<>();
        List<Integer> monthlySales = new ArrayList<>();
        List<Integer> monthlyDeaths = new ArrayList<>();
        List<Integer> monthlyTreatments = new ArrayList<>();
        for (YearMonth ym : window) {
            monthLabels.add(ym.format(MONTH_LABEL));
            monthlyRevenue.add(revenueByMonth.get(ym));
            monthlyCosts.add(costsByMonth.get(ym));
            monthlyBirths.add(birthsByMonth.get(ym));
            monthlySales.add(salesByMonth.get(ym));
            monthlyDeaths.add(deathsByMonth.get(ym));
            monthlyTreatments.add(treatmentsByMonth.get(ym));
        }

        // ── Category performance ────────────────────────────────────────
        Map<String, List<Livestock>> byCategory = allLivestock.stream()
                .collect(Collectors.groupingBy(this::catName, LinkedHashMap::new, Collectors.toList()));

        Map<String, BigDecimal> revenueByCategory = new HashMap<>();
        for (LivestockSale s : sales) {
            if (s.getLivestock() == null || s.getSalePrice() == null) continue;
            revenueByCategory.merge(catName(s.getLivestock()), s.getSalePrice(), BigDecimal::add);
        }
        Map<String, BigDecimal> costsByCategoryMap = new HashMap<>();
        for (LivestockTreatment t : treatments) {
            if (t.getLivestock() == null || t.getTreatmentCost() == null) continue;
            costsByCategoryMap.merge(catName(t.getLivestock()), t.getTreatmentCost(), BigDecimal::add);
        }

        List<Map<String, Object>> categoryPerformance = new ArrayList<>();
        for (Map.Entry<String, List<Livestock>> entry : byCategory.entrySet()) {
            String cat = entry.getKey();
            List<Livestock> group = entry.getValue();
            long total = group.size();
            long active = group.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count();
            BigDecimal revenue = revenueByCategory.getOrDefault(cat, BigDecimal.ZERO);
            BigDecimal costs = costsByCategoryMap.getOrDefault(cat, BigDecimal.ZERO);
            BigDecimal profit = revenue.subtract(costs);
            BigDecimal value = group.stream()
                    .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus()) && !Livestock.STATUS_SOLD.equals(l.getStatus()))
                    .map(Livestock::getCurrentValue)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", cat);
            row.put("total", total);
            row.put("active", active);
            row.put("revenue", revenue);
            row.put("costs", costs);
            row.put("profit", profit);
            row.put("value", value);
            categoryPerformance.add(row);
        }
        categoryPerformance.sort((a, b) -> Long.compare((Long) b.get("total"), (Long) a.get("total")));

        // ── Assemble response ───────────────────────────────────────────
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("businessStatus", businessStatus);
        response.put("netPosition", netPosition);
        response.put("totalAnimals", totalAnimals);
        response.put("activeCount", activeCount);
        response.put("soldCount", soldCount);
        response.put("sickCount", sickCount);

        response.put("totalIncome", totalIncome);
        response.put("salesRevenue", salesRevenue);
        response.put("activeStockValue", activeStockValue);
        response.put("bornAnimalsValue", bornAnimalsValue);

        response.put("totalExpenses", totalExpenses);
        response.put("treatmentCosts", treatmentCosts);
        response.put("purchaseCosts", purchaseCosts);
        response.put("deathLoss", deathLoss);

        response.put("indicators", indicators);
        response.put("alerts", alerts);

        response.put("monthLabels", monthLabels);
        response.put("monthlyRevenue", monthlyRevenue);
        response.put("monthlyCosts", monthlyCosts);
        response.put("monthlyBirths", monthlyBirths);
        response.put("monthlySales", monthlySales);
        response.put("monthlyDeaths", monthlyDeaths);
        response.put("monthlyTreatments", monthlyTreatments);

        response.put("categoryPerformance", categoryPerformance);

        return response;
    }
}