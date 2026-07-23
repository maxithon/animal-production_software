package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.BusinessOverviewResponse;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the Business Overview payload for GET /livestock/business-overview-data.
 *
 * This does NOT recompute income/expense math itself — every headline figure
 * (sales revenue, active stock value, treatment costs, purchase costs, death
 * loss, total income/expenses) comes straight from your existing
 * FinancialCalculationService, so Business Overview can never disagree with
 * the Financial Summary page or the Dashboard. This service only adds the
 * things those two don't already provide: category-level performance,
 * operational alerts, and monthly trend series for charting.
 */
@Service
public class BusinessOverviewService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");

    private final FinancialCalculationService financialCalculationService;
    private final LivestockRepository livestockRepository;
    private final LivestockBirthService birthService;
    private final LivestockSaleService saleService;
    private final LivestockDeathService deathService;
    private final LivestockTreatmentService treatmentService;
    private final LivestockSickService sickService;

    public BusinessOverviewService(FinancialCalculationService financialCalculationService,
                                   LivestockRepository livestockRepository,
                                   LivestockBirthService birthService,
                                   LivestockSaleService saleService,
                                   LivestockDeathService deathService,
                                   LivestockTreatmentService treatmentService,
                                   LivestockSickService sickService) {
        this.financialCalculationService = financialCalculationService;
        this.livestockRepository = livestockRepository;
        this.birthService = birthService;
        this.saleService = saleService;
        this.deathService = deathService;
        this.treatmentService = treatmentService;
        this.sickService = sickService;
    }

    private <T> List<T> safe(List<T> list) { return list != null ? list : Collections.emptyList(); }

    public BusinessOverviewResponse build(int months) {
        if (months <= 0) months = 12;

        List<Livestock> allLivestock = safe(livestockRepository.findAll()).stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .collect(Collectors.toList());

        List<LivestockSale> sales = safe(saleService.getAll()).stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .collect(Collectors.toList());

        List<LivestockDeath> deaths = safe(deathService.getAll()).stream()
                .filter(d -> !Boolean.TRUE.equals(d.getIsDeleted()))
                .collect(Collectors.toList());

        List<LivestockTreatment> treatments = safe(treatmentService.getAll()).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                .collect(Collectors.toList());

        List<LivestockSick> sickRecords = safe(sickService.getAll()).stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .collect(Collectors.toList());

        List<LivestockBirth> births = safe(birthService.getAll());

        BusinessOverviewResponse resp = new BusinessOverviewResponse();

        // ── Headline financials — delegate entirely to the existing single
        //    source of truth, all-time (null, null) ─────────────────────────
        BigDecimal salesRevenue   = financialCalculationService.calculateSalesRevenue(null, null);
        BigDecimal activeStock    = financialCalculationService.calculateCurrentHerdValue();
        BigDecimal treatmentCost  = financialCalculationService.calculatePreventiveTreatmentCosts(null, null)
                .add(financialCalculationService.calculateCurativeTreatmentCosts(null, null));
        BigDecimal purchaseCosts  = financialCalculationService.calculatePurchaseCosts(null, null);
        BigDecimal deathLoss      = financialCalculationService.calculateDeathLoss(null, null);
        BigDecimal totalIncome    = financialCalculationService.calculateTotalIncome(null, null);
        BigDecimal totalExpenses  = financialCalculationService.calculateTotalExpenses(null, null);
        BigDecimal netPosition    = financialCalculationService.calculateNetProfit(null, null);

        resp.setSalesRevenue(salesRevenue);
        resp.setActiveStockValue(activeStock);
        resp.setTreatmentCosts(treatmentCost);
        resp.setPurchaseCosts(purchaseCosts);
        resp.setDeathLoss(deathLoss);
        resp.setTotalIncome(totalIncome);
        resp.setTotalExpenses(totalExpenses);
        resp.setNetPosition(netPosition);
        resp.setBusinessStatus(
                netPosition.compareTo(BigDecimal.ZERO) > 0 ? "gain"
                        : netPosition.compareTo(BigDecimal.ZERO) < 0 ? "loss"
                        : "breakeven"
        );

        // ── Herd counts ──────────────────────────────────────────────────
        long total  = allLivestock.size();
        long active = allLivestock.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count();
        long sold   = allLivestock.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count();
        long dead   = allLivestock.stream().filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus())).count();
        long sick   = allLivestock.stream().filter(l -> Livestock.STATUS_SICK.equals(l.getStatus())).count();
        long pregnant = allLivestock.stream().filter(l -> Livestock.STATUS_PREGNANT.equals(l.getStatus())).count();

        resp.setTotalAnimals(total);
        resp.setActiveCount(active);
        resp.setSoldCount(sold);
        resp.setSickCount(sick);

        // ── FAO-style indicators ─────────────────────────────────────────
        BusinessOverviewResponse.Indicators ind = new BusinessOverviewResponse.Indicators();
        double mortalityRate = (active + dead) > 0 ? (dead * 100.0) / (active + dead) : 0.0;
        long openingHerd = active + sold + dead; // herd as it stood before this period's exits
        double offtakeRate = openingHerd > 0 ? (sold * 100.0) / openingHerd : 0.0;
        double replacementRate = total > 0 ? (births.size() * 100.0) / total : 0.0;
        BigDecimal avgSalePrice = !sales.isEmpty()
                ? salesRevenue.divide(BigDecimal.valueOf(sales.size()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        ind.setMortalityRate(round1(mortalityRate));
        ind.setOfftakeRate(round1(offtakeRate));
        ind.setReplacementRate(round1(replacementRate));
        ind.setAvgSalePrice(avgSalePrice);
        resp.setIndicators(ind);

        // ── Operational alerts ───────────────────────────────────────────
        BusinessOverviewResponse.Alerts alerts = new BusinessOverviewResponse.Alerts();
        long unpaidCount = treatments.stream().filter(t -> t.getIsPaid() != null && !t.getIsPaid()).count();
        BigDecimal unpaidValue = treatments.stream()
                .filter(t -> t.getIsPaid() != null && !t.getIsPaid() && t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long ongoingCount = treatments.stream()
                .filter(t -> t.getTreatmentStatus() != null && "ONGOING".equalsIgnoreCase(t.getTreatmentStatus().name()))
                .count();
        long activeSick = sickRecords.stream()
                .filter(s -> s.getStatus() != null && s.getStatus() != LivestockSick.SickStatus.RECOVERED)
                .count();

        alerts.setUnpaidTreatments(unpaidCount);
        alerts.setUnpaidTreatmentValue(unpaidValue);
        alerts.setOngoingTreatments(ongoingCount);
        alerts.setActiveSickCases(activeSick);
        alerts.setPregnantCount(pregnant);
        resp.setAlerts(alerts);

        // ── Category performance ─────────────────────────────────────────
        resp.setCategoryPerformance(buildCategoryPerformance(allLivestock, sales, treatments, deaths));

        // ── Monthly trend series ─────────────────────────────────────────
        buildMonthlyTrends(resp, months, births, sales, deaths, treatments);

        return resp;
    }

    private Double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    private String categoryName(Livestock l) {
        return (l != null && l.getLivestockCategory() != null) ? l.getLivestockCategory().getName() : "Uncategorized";
    }

    private List<BusinessOverviewResponse.CategoryPerformance> buildCategoryPerformance(
            List<Livestock> allLivestock, List<LivestockSale> sales,
            List<LivestockTreatment> treatments, List<LivestockDeath> deaths) {

        Map<String, BusinessOverviewResponse.CategoryPerformance> byCategory = new LinkedHashMap<>();

        for (Livestock l : allLivestock) {
            String cat = categoryName(l);
            BusinessOverviewResponse.CategoryPerformance cp = byCategory.computeIfAbsent(cat, k -> {
                BusinessOverviewResponse.CategoryPerformance c = new BusinessOverviewResponse.CategoryPerformance();
                c.setName(k);
                c.setTotal(0L);
                c.setActive(0L);
                c.setRevenue(BigDecimal.ZERO);
                c.setCosts(BigDecimal.ZERO);
                c.setValue(BigDecimal.ZERO);
                return c;
            });
            cp.setTotal(cp.getTotal() + 1);
            if (Livestock.STATUS_ACTIVE.equals(l.getStatus()) || Livestock.STATUS_PREGNANT.equals(l.getStatus())) {
                cp.setActive(cp.getActive() + 1);
                if (l.getCurrentValue() != null) cp.setValue(cp.getValue().add(l.getCurrentValue()));
            }
        }

        for (LivestockSale s : sales) {
            if (s.getLivestock() == null || s.getSalePrice() == null) continue;
            String cat = categoryName(s.getLivestock());
            BusinessOverviewResponse.CategoryPerformance cp = byCategory.get(cat);
            if (cp != null) cp.setRevenue(cp.getRevenue().add(s.getSalePrice()));
        }

        for (LivestockTreatment t : treatments) {
            if (t.getLivestock() == null || t.getTreatmentCost() == null) continue;
            String cat = categoryName(t.getLivestock());
            BusinessOverviewResponse.CategoryPerformance cp = byCategory.get(cat);
            if (cp != null) cp.setCosts(cp.getCosts().add(t.getTreatmentCost()));
        }

        for (LivestockDeath d : deaths) {
            if (d.getLivestock() == null || d.getLivestock().getCurrentValue() == null) continue;
            String cat = categoryName(d.getLivestock());
            BusinessOverviewResponse.CategoryPerformance cp = byCategory.get(cat);
            if (cp != null) cp.setCosts(cp.getCosts().add(d.getLivestock().getCurrentValue()));
        }

        for (BusinessOverviewResponse.CategoryPerformance cp : byCategory.values()) {
            cp.setProfit(cp.getRevenue().subtract(cp.getCosts()));
        }

        return byCategory.values().stream()
                .sorted(Comparator.comparing(BusinessOverviewResponse.CategoryPerformance::getTotal).reversed())
                .collect(Collectors.toList());
    }

    private void buildMonthlyTrends(BusinessOverviewResponse resp, int months,
                                    List<LivestockBirth> births, List<LivestockSale> sales,
                                    List<LivestockDeath> deaths, List<LivestockTreatment> treatments) {

        YearMonth current = YearMonth.from(LocalDate.now());
        List<YearMonth> window = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) window.add(current.minusMonths(i));

        Map<YearMonth, Long> birthsByMonth = new LinkedHashMap<>();
        Map<YearMonth, Long> salesByMonth = new LinkedHashMap<>();
        Map<YearMonth, Long> deathsByMonth = new LinkedHashMap<>();
        Map<YearMonth, Long> treatmentsByMonth = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> revenueByMonth = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> costsByMonth = new LinkedHashMap<>();
        for (YearMonth ym : window) {
            birthsByMonth.put(ym, 0L);
            salesByMonth.put(ym, 0L);
            deathsByMonth.put(ym, 0L);
            treatmentsByMonth.put(ym, 0L);
            revenueByMonth.put(ym, BigDecimal.ZERO);
            costsByMonth.put(ym, BigDecimal.ZERO);
        }

        for (LivestockBirth b : safe(births)) {
            if (b.getBirthDate() == null) continue;
            YearMonth ym = YearMonth.from(b.getBirthDate());
            if (birthsByMonth.containsKey(ym)) birthsByMonth.merge(ym, 1L, Long::sum);
        }
        for (LivestockSale s : safe(sales)) {
            if (s.getSaleDate() == null) continue;
            YearMonth ym = YearMonth.from(s.getSaleDate());
            if (salesByMonth.containsKey(ym)) {
                salesByMonth.merge(ym, 1L, Long::sum);
                if (s.getSalePrice() != null) revenueByMonth.merge(ym, s.getSalePrice(), BigDecimal::add);
            }
        }
        for (LivestockDeath d : safe(deaths)) {
            if (d.getDeathDate() == null) continue;
            YearMonth ym = YearMonth.from(d.getDeathDate());
            if (deathsByMonth.containsKey(ym)) deathsByMonth.merge(ym, 1L, Long::sum);
        }
        for (LivestockTreatment t : safe(treatments)) {
            if (t.getTreatmentDate() == null) continue;
            YearMonth ym = YearMonth.from(t.getTreatmentDate());
            if (treatmentsByMonth.containsKey(ym)) {
                treatmentsByMonth.merge(ym, 1L, Long::sum);
                if (t.getTreatmentCost() != null) costsByMonth.merge(ym, t.getTreatmentCost(), BigDecimal::add);
            }
        }

        resp.setMonthLabels(window.stream().map(ym -> ym.format(MONTH_LABEL)).collect(Collectors.toList()));
        resp.setMonthlyBirths(new ArrayList<>(birthsByMonth.values()));
        resp.setMonthlySales(new ArrayList<>(salesByMonth.values()));
        resp.setMonthlyDeaths(new ArrayList<>(deathsByMonth.values()));
        resp.setMonthlyTreatments(new ArrayList<>(treatmentsByMonth.values()));
        resp.setMonthlyRevenue(new ArrayList<>(revenueByMonth.values()));
        resp.setMonthlyCosts(new ArrayList<>(costsByMonth.values()));
    }
}
