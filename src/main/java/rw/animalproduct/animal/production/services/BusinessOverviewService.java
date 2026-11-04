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
 * Builds the merged Business Overview report.
 *
 * All financials come from FinancialCalculationService — the same single
 * source of truth the admin dashboard uses. Numbers can never disagree
 * across the two pages.
 */
@Service
public class BusinessOverviewService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final int MIN_MONTHS = 3;
    private static final int MAX_MONTHS = 24;

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

    private static <T> List<T> safe(List<T> l) { return l != null ? l : Collections.emptyList(); }
    private static BigDecimal bd(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private static String catName(Livestock ls) {
        if (ls == null || ls.getLivestockCategory() == null) return "Uncategorized";
        String n = ls.getLivestockCategory().getName();
        return n != null ? n : "Uncategorized";
    }

    private static Double round1(double v) { return Math.round(v * 10.0) / 10.0; }

    /* ============================================================
     *  MAIN
     * ============================================================ */
    public BusinessOverviewResponse build(int months) {
        if (months < MIN_MONTHS) months = MIN_MONTHS;
        if (months > MAX_MONTHS) months = MAX_MONTHS;

        List<Livestock> allLivestock = safe(livestockRepository.findAll()).stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .collect(Collectors.toList());

        // LivestockSale still uses is_deleted — keep the filter
        List<LivestockSale> sales = safe(saleService.getAll()).stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .collect(Collectors.toList());

        // LivestockDeath no longer has is_deleted (managed via audit_log)
        List<LivestockDeath> deaths = safe(deathService.getAll()).stream()
                .collect(Collectors.toList());

        // LivestockTreatment no longer has is_deleted (managed via audit_log) — no filter needed
        List<LivestockTreatment> treatments = safe(treatmentService.getAll()).stream()
                .collect(Collectors.toList());

        // LivestockSick no longer has is_deleted either
        List<LivestockSick> sickRecords = safe(sickService.getAll()).stream()
                .collect(Collectors.toList());

        List<LivestockBirth> births = safe(birthService.getAll());

        BusinessOverviewResponse resp = new BusinessOverviewResponse();
        resp.setMonths(months);
        resp.setPeriodLabel("Last " + months + " months");

        // ── Headline financials ──
        BigDecimal salesRevenue  = bd(financialCalculationService.calculateSalesRevenue(null, null));
        BigDecimal activeStock   = bd(financialCalculationService.calculateCurrentHerdValue());
        BigDecimal preventive    = bd(financialCalculationService.calculatePreventiveTreatmentCosts(null, null));
        BigDecimal curative      = bd(financialCalculationService.calculateCurativeTreatmentCosts(null, null));
        BigDecimal treatmentCost = preventive.add(curative);
        BigDecimal purchaseCosts = bd(financialCalculationService.calculatePurchaseCosts(null, null));
        BigDecimal deathLoss     = bd(financialCalculationService.calculateDeathLoss(null, null));
        BigDecimal totalIncome   = bd(financialCalculationService.calculateTotalIncome(null, null));
        BigDecimal totalExpenses = bd(financialCalculationService.calculateTotalExpenses(null, null));
        BigDecimal netPosition   = bd(financialCalculationService.calculateNetProfit(null, null));
        BigDecimal bornValue     = bd(financialCalculationService.calculateBornAnimalsValue(null, null));

        resp.setSalesRevenue(salesRevenue);
        resp.setActiveStockValue(activeStock);
        resp.setTreatmentCosts(treatmentCost);
        resp.setPreventiveCosts(preventive);
        resp.setCurativeCosts(curative);
        resp.setPurchaseCosts(purchaseCosts);
        resp.setDeathLoss(deathLoss);
        resp.setTotalIncome(totalIncome);
        resp.setTotalExpenses(totalExpenses);
        resp.setNetPosition(netPosition);
        resp.setBornAnimalsValue(bornValue);
        resp.setBusinessStatus(
                netPosition.compareTo(BigDecimal.ZERO) > 0 ? "gain"
                        : netPosition.compareTo(BigDecimal.ZERO) < 0 ? "loss"
                        : "breakeven");

        // ── Herd counts ──
        long total    = allLivestock.size();
        long active   = allLivestock.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count();
        long sold     = allLivestock.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count();
        long dead     = allLivestock.stream().filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus())).count();
        long trans    = allLivestock.stream().filter(l -> Livestock.STATUS_TRANSFERRED.equals(l.getStatus())).count();
        long sick     = allLivestock.stream().filter(l -> Livestock.HEALTH_SICK.equals(l.getHealthStatus())).count();
        long preg     = allLivestock.stream().filter(l -> Boolean.TRUE.equals(l.getIsPregnant())).count();
        long healthy  = allLivestock.stream().filter(l -> Livestock.HEALTH_HEALTHY.equals(l.getHealthStatus())).count();
        long underTx  = allLivestock.stream().filter(l -> Livestock.HEALTH_UNDER_TREATMENT.equals(l.getHealthStatus())).count();
        long critHlth = allLivestock.stream().filter(l -> Livestock.HEALTH_CRITICAL.equals(l.getHealthStatus())).count();
        long readyBreed = allLivestock.stream().filter(l -> Livestock.REPRO_READY_TO_BREED.equals(l.getReproductiveStatus())).count();
        long bred     = allLivestock.stream().filter(l -> Livestock.REPRO_BRED.equals(l.getReproductiveStatus())).count();
        long open     = allLivestock.stream().filter(l -> Livestock.REPRO_OPEN.equals(l.getReproductiveStatus())).count();
        long nursing  = allLivestock.stream().filter(l -> Livestock.REPRO_NURSING.equals(l.getReproductiveStatus())).count();
        long na       = allLivestock.stream().filter(l -> Livestock.REPRO_NOT_APPLICABLE.equals(l.getReproductiveStatus())).count();

        resp.setTotalAnimals(total);
        resp.setActiveCount(active);
        resp.setSoldCount(sold);
        resp.setDeadCount(dead);
        resp.setTransferredCount(trans);
        resp.setSickCount(sick);
        resp.setPregnantCount(preg);
        resp.setHealthyCount(healthy);
        resp.setUnderTreatmentCount(underTx);
        resp.setCriticalHealthCount(critHlth);
        resp.setReadyToBreedCount(readyBreed);
        resp.setBredCount(bred);
        resp.setOpenCount(open);
        resp.setNursingCount(nursing);
        resp.setNotApplicableCount(na);

        // ── Active value ──
        List<Livestock> activeAnimals = allLivestock.stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()))
                .collect(Collectors.toList());
        BigDecimal totalActiveValue = activeAnimals.stream()
                .map(Livestock::getCurrentValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgActiveValue = activeAnimals.isEmpty() ? BigDecimal.ZERO
                : totalActiveValue.divide(BigDecimal.valueOf(activeAnimals.size()), 0, RoundingMode.HALF_UP);
        resp.setAverageActiveValue(avgActiveValue);

        // ── FAO indicators ──
        BusinessOverviewResponse.Indicators ind = new BusinessOverviewResponse.Indicators();
        double mortalityRate = (active + dead) > 0 ? (dead * 100.0) / (active + dead) : 0.0;
        long openingHerd = active + sold + dead;
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

        // ── Counts ──
        long treatmentCount = treatments.size();
        long salesCount = sales.size();
        resp.setTotalTreatmentsCount(treatmentCount);
        resp.setTotalSalesCount(salesCount);
        int preventiveShare = treatmentCost.signum() == 0 ? 0
                : preventive.multiply(BigDecimal.valueOf(100))
                .divide(treatmentCost, 0, RoundingMode.HALF_UP).intValue();
        resp.setPreventiveSharePct(preventiveShare);

        // ── Alerts ──
        BusinessOverviewResponse.Alerts alerts = new BusinessOverviewResponse.Alerts();
        long unpaidCount = treatments.stream().filter(t -> t.getIsPaid() != null && !t.getIsPaid()).count();
        BigDecimal unpaidValue = treatments.stream()
                .filter(t -> t.getIsPaid() != null && !t.getIsPaid() && t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long ongoingCount = treatments.stream()
                .filter(t -> t.getTreatmentStatus() != null
                        && "ONGOING".equalsIgnoreCase(t.getTreatmentStatus().name()))
                .count();
        long activeSick = sickRecords.stream()
                .filter(s -> s.getStatus() != null && s.getStatus() != LivestockSick.SickStatus.RECOVERED)
                .count();

        alerts.setUnpaidTreatments(unpaidCount);
        alerts.setUnpaidTreatmentValue(unpaidValue);
        alerts.setOngoingTreatments(ongoingCount);
        alerts.setActiveSickCases(activeSick);
        alerts.setPregnantCount(preg);
        resp.setAlerts(alerts);

        // ── Category performance ──
        resp.setCategoryPerformance(buildCategoryPerformance(allLivestock, sales, treatments, deaths));

        // ── Category breakdowns ──
        resp.setStatusTotalsByCategory(buildStatusTotalsByCategory(allLivestock));
        resp.setStatusByCategory(buildStatusByCategory(allLivestock));
        resp.setCategoryIndicators(buildCategoryIndicators(allLivestock, sales, births));

        // ── Active animals (Animals tab) ──
        resp.setActiveAnimals(buildActiveAnimals(activeAnimals));

        // ── Breakdowns for Herd tab ──
        resp.setCategoryBreakdown(buildSimpleBreakdown(activeAnimals, BusinessOverviewService::catName));
        resp.setLocationBreakdown(buildSimpleBreakdown(activeAnimals,
                l -> l.getLocation() != null && l.getLocation().getName() != null
                        ? l.getLocation().getName() : null));
        resp.setGenderBreakdown(buildSimpleBreakdown(activeAnimals, Livestock::getGender));
        resp.setHealthBreakdown(buildSimpleBreakdown(activeAnimals, Livestock::healthStatusLabel));
        resp.setReproBreakdown(buildSimpleBreakdown(activeAnimals, Livestock::reproductiveStatusLabel));

        // ── Monthly trends ──
        buildMonthlyTrends(resp, months, births, sales, deaths, treatments);

        return resp;
    }

    /* ============================================================ */
    private List<BusinessOverviewResponse.ActiveAnimal> buildActiveAnimals(List<Livestock> activeAnimals) {
        return activeAnimals.stream()
                .sorted(Comparator.comparing(Livestock::getTagNumber,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(l -> {
                    var aa = new BusinessOverviewResponse.ActiveAnimal();
                    aa.setTagNumber(l.getTagNumber());
                    aa.setCategoryName(catName(l));
                    aa.setGender(l.getGender());
                    if (l.getBeneficiary() != null) {
                        aa.setBeneficiaryName(l.getBeneficiary().getFirstName() + " "
                                + l.getBeneficiary().getLastName());
                    }
                    if (l.getLocation() != null) aa.setLocationName(l.getLocation().getName());
                    aa.setDateReceived(l.getDateReceived());
                    aa.setCurrentValue(l.getCurrentValue());
                    return aa;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Long> buildSimpleBreakdown(List<Livestock> animals,
                                                   java.util.function.Function<Livestock, String> keyFn) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Livestock l : animals) {
            String k = keyFn.apply(l);
            if (k == null || k.isBlank()) continue;
            out.merge(k, 1L, Long::sum);
        }
        return sortByValueDesc(out);
    }

    private List<BusinessOverviewResponse.CategoryPerformance> buildCategoryPerformance(
            List<Livestock> allLivestock, List<LivestockSale> sales,
            List<LivestockTreatment> treatments, List<LivestockDeath> deaths) {

        Map<String, BusinessOverviewResponse.CategoryPerformance> byCategory = new LinkedHashMap<>();

        for (Livestock l : allLivestock) {
            String cat = catName(l);
            var cp = byCategory.computeIfAbsent(cat, k -> {
                var c = new BusinessOverviewResponse.CategoryPerformance();
                c.setName(k);
                c.setTotal(0L); c.setActive(0L);
                c.setRevenue(BigDecimal.ZERO);
                c.setCosts(BigDecimal.ZERO);
                c.setValue(BigDecimal.ZERO);
                return c;
            });
            cp.setTotal(cp.getTotal() + 1);
            if (Livestock.STATUS_ACTIVE.equals(l.getStatus())) {
                cp.setActive(cp.getActive() + 1);
                if (l.getCurrentValue() != null) cp.setValue(cp.getValue().add(l.getCurrentValue()));
            }
        }

        for (LivestockSale s : sales) {
            if (s.getLivestock() == null || s.getSalePrice() == null) continue;
            var cp = byCategory.get(catName(s.getLivestock()));
            if (cp != null) cp.setRevenue(cp.getRevenue().add(s.getSalePrice()));
        }
        for (LivestockTreatment t : treatments) {
            if (t.getLivestock() == null || t.getTreatmentCost() == null) continue;
            var cp = byCategory.get(catName(t.getLivestock()));
            if (cp != null) cp.setCosts(cp.getCosts().add(t.getTreatmentCost()));
        }
        for (LivestockDeath d : deaths) {
            if (d.getLivestock() == null || d.getLivestock().getCurrentValue() == null) continue;
            var cp = byCategory.get(catName(d.getLivestock()));
            if (cp != null) cp.setCosts(cp.getCosts().add(d.getLivestock().getCurrentValue()));
        }
        for (var cp : byCategory.values()) {
            cp.setProfit(cp.getRevenue().subtract(cp.getCosts()));
        }
        return byCategory.values().stream()
                .sorted(Comparator.comparingLong(
                        BusinessOverviewResponse.CategoryPerformance::getActive).reversed())
                .collect(Collectors.toList());
    }

    private Map<String, Long> buildStatusTotalsByCategory(List<Livestock> allLivestock) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Livestock l : allLivestock) {
            String cat = catName(l);
            boolean counts = Livestock.STATUS_ACTIVE.equals(l.getStatus())
                    || Livestock.HEALTH_SICK.equals(l.getHealthStatus());
            if (counts) out.merge(cat, 1L, Long::sum);
        }
        return sortByValueDesc(out);
    }

    private Map<String, Map<String, Long>> buildStatusByCategory(List<Livestock> allLivestock) {
        Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        for (String status : List.of("ACTIVE", "SICK", "PREGNANT", "SOLD", "DEAD")) {
            out.put(status, new LinkedHashMap<>());
        }
        for (Livestock l : allLivestock) {
            String cat = catName(l);
            if (Livestock.STATUS_ACTIVE.equals(l.getStatus())) out.get("ACTIVE").merge(cat, 1L, Long::sum);
            if (Livestock.HEALTH_SICK.equals(l.getHealthStatus())) out.get("SICK").merge(cat, 1L, Long::sum);
            if (Boolean.TRUE.equals(l.getIsPregnant())) out.get("PREGNANT").merge(cat, 1L, Long::sum);
            if (Livestock.STATUS_SOLD.equals(l.getStatus())) out.get("SOLD").merge(cat, 1L, Long::sum);
            if (Livestock.STATUS_DEAD.equals(l.getStatus())) out.get("DEAD").merge(cat, 1L, Long::sum);
        }
        out.replaceAll((k, v) -> sortByValueDesc(v));
        return out;
    }

    private List<BusinessOverviewResponse.CategoryIndicators> buildCategoryIndicators(
            List<Livestock> allLivestock, List<LivestockSale> sales, List<LivestockBirth> births) {

        Map<String, BusinessOverviewResponse.CategoryIndicators> byCat = new LinkedHashMap<>();
        for (Livestock l : allLivestock) {
            String cat = catName(l);
            var ci = byCat.computeIfAbsent(cat, k -> {
                var c = new BusinessOverviewResponse.CategoryIndicators();
                c.setName(k);
                c.setTotal(0L); c.setActive(0L); c.setSold(0L); c.setDead(0L);
                return c;
            });
            ci.setTotal(ci.getTotal() + 1);
            if (Livestock.STATUS_ACTIVE.equals(l.getStatus())) ci.setActive(ci.getActive() + 1);
            if (Livestock.STATUS_SOLD.equals(l.getStatus()))   ci.setSold(ci.getSold() + 1);
            if (Livestock.STATUS_DEAD.equals(l.getStatus()))   ci.setDead(ci.getDead() + 1);
        }

        Map<String, BigDecimal> revenueByCat = new HashMap<>();
        Map<String, Long> salesCountByCat = new HashMap<>();
        for (LivestockSale s : sales) {
            if (s.getLivestock() == null || s.getSalePrice() == null) continue;
            String cat = catName(s.getLivestock());
            revenueByCat.merge(cat, s.getSalePrice(), BigDecimal::add);
            salesCountByCat.merge(cat, 1L, Long::sum);
        }

        Map<String, Long> birthsByCat = new HashMap<>();
        for (LivestockBirth b : births) {
            Livestock mother = null;
            try { mother = (Livestock) b.getClass().getMethod("getLivestock").invoke(b); } catch (Exception ignored) {}
            if (mother == null) {
                try { mother = (Livestock) b.getClass().getMethod("getMother").invoke(b); } catch (Exception ignored) {}
            }
            if (mother == null) continue;
            birthsByCat.merge(catName(mother), 1L, Long::sum);
        }

        for (var ci : byCat.values()) {
            long a = ci.getActive(), s = ci.getSold(), d = ci.getDead(), t = ci.getTotal();
            double mortality = (a + d) > 0 ? (d * 100.0) / (a + d) : 0.0;
            long opening = a + s + d;
            double offtake = opening > 0 ? (s * 100.0) / opening : 0.0;
            long b = birthsByCat.getOrDefault(ci.getName(), 0L);
            double replacement = t > 0 ? (b * 100.0) / t : 0.0;
            ci.setMortalityRate(round1(mortality));
            ci.setOfftakeRate(round1(offtake));
            ci.setReplacementRate(round1(replacement));

            BigDecimal rev = revenueByCat.getOrDefault(ci.getName(), BigDecimal.ZERO);
            long cnt = salesCountByCat.getOrDefault(ci.getName(), 0L);
            ci.setAvgSalePrice(cnt > 0
                    ? rev.divide(BigDecimal.valueOf(cnt), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
        }
        return byCat.values().stream()
                .sorted(Comparator.comparingLong(BusinessOverviewResponse.CategoryIndicators::getActive).reversed())
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

    private Map<String, Long> sortByValueDesc(Map<String, Long> in) {
        return in.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }
}