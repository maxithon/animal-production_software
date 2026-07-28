package rw.animalproduct.animal.production.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;
import rw.animalproduct.animal.production.services.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    private final RepresentativeService representativesAbororaService;
    private final BeneficiaryService beneficiariesAmatungoService;
    private final UsersService usersService;
    private final LivestockRepository livestockRepository;
    private final LivestockBirthService birthService;
    private final LivestockTreatmentService treatmentService;
    private final LivestockSickService sickService;
    private final LivestockAbortionService abortionService;
    private final LivestockSaleService saleService;
    private final LivestockDeathService deathService;
    private final LivestockSickHistoryRepository sickHistoryRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final BuyerRepository buyerRepository;
    private final LivestockDeathRepository livestockDeathRepository;

    // ── SINGLE SOURCE OF TRUTH for all financial math. The Dashboard no longer
    //    re-implements sales/treatment/purchase/death-loss calculations itself;
    //    it delegates to this service with (null, null) dates to get all-time
    //    totals, which is exactly how the Financial Summary page's math works
    //    when no date filter is applied. This guarantees the two pages can
    //    never disagree again. ──────────────────────────────────────────────
    private final FinancialCalculationService financialCalculationService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int DAYS_FOR_ALERTS = 7;
    private static final DecimalFormat RWF_FORMAT = new DecimalFormat("#,###.00");

    public DashboardController(RepresentativeService representativesAbororaService,
                               BeneficiaryService beneficiariesAmatungoService,
                               UsersService usersService,
                               LivestockRepository livestockRepository,
                               LivestockBirthService birthService,
                               LivestockTreatmentService treatmentService,
                               LivestockSickService sickService,
                               LivestockAbortionService abortionService,
                               LivestockSaleService saleService,
                               LivestockDeathService deathService,
                               LivestockSickHistoryRepository sickHistoryRepository,
                               VeterinarianRepository veterinarianRepository,
                               BuyerRepository buyerRepository,
                               LivestockDeathRepository livestockDeathRepository,
                               FinancialCalculationService financialCalculationService) {
        this.representativesAbororaService = representativesAbororaService;
        this.beneficiariesAmatungoService = beneficiariesAmatungoService;
        this.usersService = usersService;
        this.livestockRepository = livestockRepository;
        this.birthService = birthService;
        this.treatmentService = treatmentService;
        this.sickService = sickService;
        this.abortionService = abortionService;
        this.saleService = saleService;
        this.deathService = deathService;
        this.sickHistoryRepository = sickHistoryRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.buyerRepository = buyerRepository;
        this.livestockDeathRepository = livestockDeathRepository;
        this.financialCalculationService = financialCalculationService;
    }

    private static final ObjectMapper MAPPER;
    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private List<Livestock> scopedLivestock(Authentication authentication) {
        if (isAdmin(authentication)) {
            return safeList(livestockRepository.findAll());
        }
        Users current = usersService.getUserByEmail(authentication.getName()).orElse(null);
        if (current == null || current.getBeneficiaryId() == null) {
            return Collections.emptyList();
        }
        return safeList(livestockRepository.findByBeneficiaryId(current.getBeneficiaryId()));
    }

    private String toJson(Object obj) {
        try { return MAPPER.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : null;
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private BigDecimal safeBigDecimal(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String catName(Livestock ls) {
        if (ls == null) return null;
        LivestockCategory cat = ls.getLivestockCategory();
        return cat != null ? cat.getName() : null;
    }

    private String formatRWF(BigDecimal amount) {
        if (amount == null) return "0 RWF";
        return RWF_FORMAT.format(amount) + " RWF";
    }

    // ========================================================================
    // FINANCIAL SUMMARY - DELEGATES TO FinancialCalculationService
    // This guarantees the Dashboard always agrees with the Financial Summary
    // page's all-time figures, because both read from the same formulas.
    // ========================================================================

    /**
     * All-time financial summary for the dashboard.
     * Delegates every figure to FinancialCalculationService with an unbounded
     * (null, null) date range so nothing is duplicated or can drift out of
     * sync with the Financial Summary page.
     */
    private FinancialSummary calculateFinancialSummary() {
        FinancialSummary fs = new FinancialSummary();

        LocalDate fromDate = null; // unbounded -> all-time
        LocalDate toDate = null;   // unbounded -> all-time

        fs.setSalesRevenue(financialCalculationService.calculateSalesRevenue(fromDate, toDate));
        fs.setCurrentHerdValue(financialCalculationService.calculateCurrentHerdValue());
        fs.setBornAnimalsValue(financialCalculationService.calculateBornAnimalsValue(fromDate, toDate));
        fs.setPurchaseCosts(financialCalculationService.calculatePurchaseCosts(fromDate, toDate));
        fs.setPreventiveTreatmentCosts(financialCalculationService.calculatePreventiveTreatmentCosts(fromDate, toDate));
        fs.setCurativeTreatmentCosts(financialCalculationService.calculateCurativeTreatmentCosts(fromDate, toDate));
        fs.setDeathLoss(financialCalculationService.calculateDeathLoss(fromDate, toDate));
        fs.setTotalIncome(financialCalculationService.calculateTotalIncome(fromDate, toDate));
        fs.setTotalExpenses(financialCalculationService.calculateTotalExpenses(fromDate, toDate));

        BigDecimal netProfit = financialCalculationService.calculateNetProfit(fromDate, toDate);
        fs.setNetProfit(netProfit);
        fs.setProfit(netProfit.compareTo(BigDecimal.ZERO) >= 0);

        return fs;
    }

    // ── Statistics (admin-wide, unfiltered) ────────────────────────────────────
    private DashboardStatistics calculateStatistics() {
        DashboardStatistics stats = new DashboardStatistics();
        List<LivestockBirth> births           = safeList(birthService.getAll());
        List<LivestockSick> sickRecords       = safeList(sickService.getAll());
        List<LivestockSale> sales             = safeList(saleService.getAll());
        List<LivestockDeath> deaths           = safeList(deathService.getAll());
        List<LivestockTreatment> treatments   = safeList(treatmentService.getAll());
        List<LivestockAbortion> abortions     = safeList(abortionService.getAll());
        List<Livestock> livestock             = safeList(livestockRepository.findAll());
        List<Users> users                     = safeList(usersService.getAllUsers());

        // ── Livestock Counts ────────────────────────────────────────────────
        stats.setTotalLivestock((long) livestock.size());
        stats.setActiveLivestock(livestock.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count());
        stats.setPregnantLivestock(livestock.stream().filter(l -> Livestock.STATUS_PREGNANT.equals(l.getStatus())).count());
        stats.setSickLivestock(livestock.stream().filter(l -> Livestock.STATUS_SICK.equals(l.getStatus())).count());
        stats.setDeadLivestock(livestock.stream().filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus())).count());
        stats.setSoldLivestock(livestock.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count());

        // ── Use the single-source-of-truth financial summary ──────────────────
        FinancialSummary fs = calculateFinancialSummary();

        // ── CURRENT LIVESTOCK VALUE ──────────────────────────────────────────
        stats.setTotalCurrentValue(fs.getCurrentHerdValue());

        long animalsWithValue = livestock.stream()
                .filter(l -> !Livestock.STATUS_SOLD.equals(l.getStatus()) && !Livestock.STATUS_DEAD.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() != null).count();
        BigDecimal avgValue = animalsWithValue > 0 ? fs.getCurrentHerdValue().divide(BigDecimal.valueOf(animalsWithValue), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        stats.setAverageCurrentValue(avgValue);

        // ── BORN ANIMALS VALUE ──────────────────────────────────────────────
        stats.setBornAnimalsValue(fs.getBornAnimalsValue());

        // ── PURCHASE COSTS ────────────────────────────────────────────────────
        stats.setPurchaseCosts(fs.getPurchaseCosts());

        // ── Value by category ──────────────────────────────────────────────
        Map<String, BigDecimal> valueByCategory = new LinkedHashMap<>();
        for (Livestock l : livestock) {
            if (l.getCurrentValue() != null && !Livestock.STATUS_DEAD.equals(l.getStatus()) && !Livestock.STATUS_SOLD.equals(l.getStatus())) {
                String cat = catName(l);
                if (cat != null) {
                    valueByCategory.merge(cat, l.getCurrentValue(), BigDecimal::add);
                }
            }
        }
        stats.setValueByCategory(valueByCategory);

        // ── Births ──────────────────────────────────────────────────────────
        stats.setTotalBirths((long) births.size());
        stats.setTotalMothers(births.stream()
                .map(b -> b.getLivestock() != null ? b.getLivestock().getId() : null)
                .filter(Objects::nonNull).distinct().count());
        stats.setTotalChildren(births.stream()
                .mapToLong(b -> b.getChildren() != null ? b.getChildren().size() : 0).sum());
        double avgOffspring = births.isEmpty() ? 0 :
                births.stream().mapToInt(b -> b.getOffspringCount() != null ? b.getOffspringCount() : 0)
                        .average().orElse(0);
        stats.setAverageOffspringPerBirth(Math.round(avgOffspring));

        // ── Sick ────────────────────────────────────────────────────────────
        stats.setTotalSick((long) sickRecords.size());
        stats.setCurrentlySick(sickRecords.stream()
                .filter(s -> s.getStatus() != null && !s.getStatus().name().equals("RECOVERED")).count());
        stats.setCriticalCount(sickRecords.stream()
                .filter(s -> s.getStatus() != null && s.getStatus().name().equals("CRITICAL")).count());
        stats.setRecoveringCount(sickRecords.stream()
                .filter(s -> s.getStatus() != null && s.getStatus().name().equals("RECOVERING")).count());
        stats.setRecoveredCount(sickRecords.stream()
                .filter(s -> s.getStatus() != null && s.getStatus().name().equals("RECOVERED")).count());

        // ── Treatments (using the single-source-of-truth calculations) ──────
        stats.setTotalTreatments((long) treatments.size());
        stats.setTotalTreatmentCost(fs.getPreventiveTreatmentCosts());
        stats.setCurativeTreatmentCost(fs.getCurativeTreatmentCosts());
        stats.setPreventiveTreatmentCost(fs.getPreventiveTreatmentCosts());

        // Total sick treatment cost = curative treatments
        stats.setTotalSickTreatmentCost(fs.getCurativeTreatmentCosts());

        // ── Treatment costs by category ──────────────────────────────────────
        Map<String, BigDecimal> treatmentCostByCategory = new LinkedHashMap<>();
        for (LivestockTreatment t : treatments) {
            if (t.getTreatmentCost() != null && t.getLivestock() != null) {
                String cat = catName(t.getLivestock());
                if (cat != null) {
                    treatmentCostByCategory.merge(cat, t.getTreatmentCost(), BigDecimal::add);
                }
            }
        }
        stats.setTreatmentCostByCategory(treatmentCostByCategory);

        stats.setUnpaidTreatmentCount(treatments.stream()
                .filter(t -> t.getIsPaid() != null && !t.getIsPaid()).count());
        stats.setOngoingTreatmentCount(treatments.stream()
                .filter(t -> t.getTreatmentStatus() != null
                        && t.getTreatmentStatus().name().equalsIgnoreCase("ONGOING")).count());

        // ── Sales ────────────────────────────────────────────────────────────
        stats.setTotalSales((long) sales.size());
        stats.setTotalSaleRevenue(fs.getSalesRevenue());

        // ── LOSS FROM DEATHS ────────────────────────────────────────────────
        stats.setTotalDeadValue(fs.getDeathLoss());

        long deadWithValue = livestock.stream()
                .filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus()) && l.getCurrentValue() != null).count();
        BigDecimal avgDeadValue = deadWithValue > 0 ? fs.getDeathLoss().divide(BigDecimal.valueOf(deadWithValue), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        stats.setAverageDeadValue(avgDeadValue);

        // Deaths by cause with value
        Map<String, Long> deathsByCause = new LinkedHashMap<>();
        Map<String, BigDecimal> valueLostByCause = new LinkedHashMap<>();
        for (LivestockDeath d : deaths) {
            if (d.getCauseOfDeath() != null && !d.getCauseOfDeath().isBlank()) {
                deathsByCause.merge(d.getCauseOfDeath(), 1L, Long::sum);
                if (d.getLivestock() != null && d.getLivestock().getCurrentValue() != null) {
                    valueLostByCause.merge(d.getCauseOfDeath(), d.getLivestock().getCurrentValue(), BigDecimal::add);
                }
            }
        }
        stats.setDeathsByCause(deathsByCause);
        stats.setValueLostByCause(valueLostByCause);

        // ── Abortions ────────────────────────────────────────────────────────
        stats.setTotalAbortions((long) abortions.size());

        // ── Users ─────────────────────────────────────────────────────────────
        stats.setTotalUsers((long) users.size());
        stats.setActiveUsers(users.stream().filter(Users::isActive).count());
        stats.setInactiveUsers(users.stream().filter(u -> !u.isActive()).count());

        stats.setTotalBeneficiaries((long) safeList(beneficiariesAmatungoService.getAll()).size());
        stats.setTotalRepresentatives((long) safeList(representativesAbororaService.getAll()).size());

        // ── FINANCIAL SUMMARY (single source of truth) ──────────────────────
        stats.setTotalIncome(fs.getTotalIncome());
        stats.setTotalExpenses(fs.getTotalExpenses());
        stats.setNetProfit(fs.getNetProfit());
        stats.setProfit(fs.isProfit());

        // Net position (revenue - total treatment costs) - kept for backward compatibility
        BigDecimal netPosition = safeBigDecimal(fs.getSalesRevenue())
                .subtract(safeBigDecimal(fs.getPreventiveTreatmentCosts()));
        stats.setNetPosition(netPosition);

        // ── FAO Standard Indicators ──────────────────────────────────────────
        long totalHerd = livestock.stream()
                .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus()))
                .count();
        double mortalityRate = totalHerd > 0 ? ((double) deaths.size() / (totalHerd + deaths.size())) * 100 : 0;
        stats.setMortalityRate(Math.round(mortalityRate * 10) / 10.0);

        long totalMonths = births.stream()
                .filter(b -> b.getBirthDate() != null)
                .count();
        double calvingInterval = births.size() > 0 ? (double) totalMonths / births.size() * 12 : 0;
        stats.setCalvingInterval(Math.round(calvingInterval * 10) / 10.0);

        long openingHerd = livestock.size();
        double offtakeRate = openingHerd > 0 ? ((double) sales.size() / openingHerd) * 100 : 0;
        stats.setOfftakeRate(Math.round(offtakeRate * 10) / 10.0);

        double replacementRate = livestock.size() > 0 ? ((double) births.size() / livestock.size()) * 100 : 0;
        stats.setReplacementRate(Math.round(replacementRate * 10) / 10.0);

        double productivity = sales.size() > 0 ? fs.getSalesRevenue().doubleValue() / sales.size() : 0;
        stats.setProductivity(Math.round(productivity));

        return stats;
    }

    private MonthlyTrends calculateMonthlyTrends(int year) {
        MonthlyTrends trends = new MonthlyTrends();
        int[] births = new int[12], sales = new int[12], deaths = new int[12], treatments = new int[12];
        double[] revenue = new double[12], costs = new double[12], deathValue = new double[12];

        for (LivestockBirth b : safeList(birthService.getAll()))
            if (b.getBirthDate() != null && b.getBirthDate().getYear() == year)
                births[b.getBirthDate().getMonthValue() - 1]++;

        for (LivestockSale s : safeList(saleService.getAll()))
            if (s.getSaleDate() != null && s.getSaleDate().getYear() == year) {
                int idx = s.getSaleDate().getMonthValue() - 1;
                sales[idx]++;
                if (s.getSalePrice() != null) revenue[idx] += s.getSalePrice().doubleValue();
            }

        for (LivestockDeath d : safeList(deathService.getAll()))
            if (d.getDeathDate() != null && d.getDeathDate().getYear() == year) {
                int idx = d.getDeathDate().getMonthValue() - 1;
                deaths[idx]++;
                if (d.getLivestock() != null && d.getLivestock().getCurrentValue() != null) {
                    deathValue[idx] += d.getLivestock().getCurrentValue().doubleValue();
                }
            }

        for (LivestockTreatment t : safeList(treatmentService.getAll()))
            if (t.getTreatmentDate() != null && t.getTreatmentDate().getYear() == year) {
                int idx = t.getTreatmentDate().getMonthValue() - 1;
                treatments[idx]++;
                if (t.getTreatmentCost() != null) costs[idx] += t.getTreatmentCost().doubleValue();
            }

        trends.setMonthlyBirths(births);
        trends.setMonthlySales(sales);
        trends.setMonthlyDeaths(deaths);
        trends.setMonthlyTreatments(treatments);
        trends.setMonthlyRevenue(revenue);
        trends.setMonthlyCosts(costs);
        trends.setMonthlyDeathValue(deathValue);
        return trends;
    }

    private List<Map<String, String>> generateAlerts() {
        List<Map<String, String>> alertList = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate weekFromNow = today.plusDays(DAYS_FOR_ALERTS);

        for (LivestockTreatment t : safeList(treatmentService.getAll())) {
            if (t.getNextTreatmentDate() != null
                    && !t.getNextTreatmentDate().isBefore(today)
                    && t.getNextTreatmentDate().isBefore(weekFromNow)) {
                Map<String, String> alert = new LinkedHashMap<>();
                alert.put("type", "TREATMENT");
                alert.put("severity", "WARNING");
                alert.put("message", "Treatment due for "
                        + (t.getLivestock() != null ? t.getLivestock().getTagNumber() : "Unknown")
                        + " on " + formatDate(t.getNextTreatmentDate()));
                alertList.add(alert);
            }
        }
        for (Livestock l : safeList(livestockRepository.findAll())) {
            if (Livestock.STATUS_PREGNANT.equals(l.getStatus())
                    && l.getExpectedDueDate() != null
                    && !l.getExpectedDueDate().isBefore(today)
                    && l.getExpectedDueDate().isBefore(weekFromNow)) {
                Map<String, String> alert = new LinkedHashMap<>();
                alert.put("type", "BIRTH");
                alert.put("severity", "INFO");
                alert.put("message", "Expected birth for " + l.getTagNumber()
                        + " on " + formatDate(l.getExpectedDueDate()));
                alertList.add(alert);
            }
        }
        for (LivestockSick s : safeList(sickService.getAll())) {
            if (s.getStatus() != LivestockSick.SickStatus.RECOVERED
                    && s.getReportedDate() != null
                    && s.getReportedDate().isBefore(today.minusDays(3))) {
                Map<String, String> alert = new LinkedHashMap<>();
                alert.put("type", "SICK");
                alert.put("severity", "CRITICAL");
                alert.put("message", "Animal "
                        + (s.getLivestock() != null ? s.getLivestock().getTagNumber() : "Unknown")
                        + " sick since " + formatDate(s.getReportedDate()));
                alertList.add(alert);
            }
        }
        for (LivestockTreatment t : safeList(treatmentService.getAll())) {
            if (t.getNextTreatmentDate() != null
                    && t.getNextTreatmentDate().isBefore(today)
                    && t.getTreatmentStatus() != null
                    && !t.getTreatmentStatus().name().equalsIgnoreCase("COMPLETED")) {
                Map<String, String> alert = new LinkedHashMap<>();
                alert.put("type", "OVERDUE_TREATMENT");
                alert.put("severity", "CRITICAL");
                alert.put("message", "Treatment OVERDUE for "
                        + (t.getLivestock() != null ? t.getLivestock().getTagNumber() : "Unknown"));
                alertList.add(alert);
            }
        }
        return alertList;
    }

    // ── ADMIN DASHBOARD ────────────────────────────────────────────────────────
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/dashboard")
    public String adminDashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        LocalDate today = LocalDate.now();
        int year = today.getYear();

        model.addAttribute("username", email);
        model.addAttribute("userInitial", email.substring(0, 1).toUpperCase());
        model.addAttribute("isSuperAdmin", true);

        // ── Calculate Statistics (uses the single-source-of-truth financial calculations) ──
        DashboardStatistics stats = calculateStatistics();

        // ── Get the financial summary separately ─────────────────────────────
        FinancialSummary fs = calculateFinancialSummary();

        // ── Livestock totals ─────────────────────────────────────────────────
        model.addAttribute("totalLivestock",      stats.getTotalLivestock());
        model.addAttribute("totalMothers",         stats.getTotalMothers());
        model.addAttribute("totalBirths",          stats.getTotalBirths());
        model.addAttribute("totalChildren",        stats.getTotalChildren());
        model.addAttribute("avgOffspringPerBirth", stats.getAverageOffspringPerBirth());
        model.addAttribute("activeCount",          stats.getActiveLivestock());
        model.addAttribute("soldCount",            stats.getSoldLivestock());
        model.addAttribute("deadCount",            stats.getDeadLivestock());
        model.addAttribute("sickCount",            stats.getSickLivestock());
        model.addAttribute("pregnantCount",        stats.getPregnantLivestock());

        // ── Current Value Metrics ───────────────────────────────────────────
        model.addAttribute("totalCurrentValue",    fs.getCurrentHerdValue());
        model.addAttribute("averageCurrentValue",  stats.getAverageCurrentValue());
        model.addAttribute("valueByCategory",      toJson(stats.getValueByCategory()));
        model.addAttribute("totalDeadValue",       fs.getDeathLoss());
        model.addAttribute("averageDeadValue",     stats.getAverageDeadValue());
        model.addAttribute("deathsByCause",        toJson(stats.getDeathsByCause()));
        model.addAttribute("valueLostByCause",     toJson(stats.getValueLostByCause()));

        // ── Born Animals Value ──────────────────────────────────────────────
        model.addAttribute("bornAnimalsValue",     fs.getBornAnimalsValue());

        // ── Purchase Costs ──────────────────────────────────────────────────
        model.addAttribute("purchaseCosts",        fs.getPurchaseCosts());

        // ── FINANCIAL SUMMARY ────────────────────────────────────────────────
        model.addAttribute("totalIncome",          fs.getTotalIncome());
        model.addAttribute("totalExpenses",        fs.getTotalExpenses());
        model.addAttribute("totalSickTreatmentCost", fs.getCurativeTreatmentCosts());
        model.addAttribute("totalTreatmentCost",   fs.getPreventiveTreatmentCosts());
        model.addAttribute("totalSaleRevenue",     fs.getSalesRevenue());
        model.addAttribute("salesRevenue",         fs.getSalesRevenue());
        model.addAttribute("currentHerdValue",     fs.getCurrentHerdValue());
        model.addAttribute("preventiveTreatmentCosts", fs.getPreventiveTreatmentCosts());
        model.addAttribute("curativeTreatmentCosts", fs.getCurativeTreatmentCosts());
        model.addAttribute("deathLoss",            fs.getDeathLoss());
        model.addAttribute("netProfit",            fs.getNetProfit());
        model.addAttribute("isProfit",             fs.isProfit());

        // ── Formatted values for display with RWF ──────────────────────────
        model.addAttribute("totalIncomeFormatted", formatRWF(fs.getTotalIncome()));
        model.addAttribute("totalExpensesFormatted", formatRWF(fs.getTotalExpenses()));
        model.addAttribute("netProfitFormatted", formatRWF(fs.getNetProfit()));
        model.addAttribute("totalCurrentValueFormatted", formatRWF(fs.getCurrentHerdValue()));
        model.addAttribute("bornAnimalsValueFormatted", formatRWF(fs.getBornAnimalsValue()));
        model.addAttribute("purchaseCostsFormatted", formatRWF(fs.getPurchaseCosts()));
        model.addAttribute("totalDeadValueFormatted", formatRWF(fs.getDeathLoss()));
        model.addAttribute("totalSaleRevenueFormatted", formatRWF(fs.getSalesRevenue()));
        model.addAttribute("totalTreatmentCostFormatted", formatRWF(fs.getPreventiveTreatmentCosts()));
        model.addAttribute("totalSickTreatmentCostFormatted", formatRWF(fs.getCurativeTreatmentCosts()));
        model.addAttribute("salesRevenueFormatted", formatRWF(fs.getSalesRevenue()));
        model.addAttribute("currentHerdValueFormatted", formatRWF(fs.getCurrentHerdValue()));
        model.addAttribute("preventiveTreatmentCostsFormatted", formatRWF(fs.getPreventiveTreatmentCosts()));
        model.addAttribute("curativeTreatmentCostsFormatted", formatRWF(fs.getCurativeTreatmentCosts()));
        model.addAttribute("deathLossFormatted", formatRWF(fs.getDeathLoss()));

        List<Livestock> allLivestock = safeList(livestockRepository.findAll());
        Map<String, Long> categoryBreakdown = new LinkedHashMap<>();
        for (Livestock ls : allLivestock) {
            String cat = catName(ls);
            if (cat != null) categoryBreakdown.merge(cat, 1L, Long::sum);
        }
        model.addAttribute("categoryBreakdown", toJson(categoryBreakdown));

        // ── Users / People ───────────────────────────────────────────────────
        model.addAttribute("totalUsers",           stats.getTotalUsers());
        model.addAttribute("activeUsers",          stats.getActiveUsers());
        model.addAttribute("inactiveUsers",        stats.getInactiveUsers());
        model.addAttribute("totalRepresentatives", stats.getTotalRepresentatives());
        model.addAttribute("totalBeneficiaries",   stats.getTotalBeneficiaries());

        long vetCount   = safeList(veterinarianRepository.findByIsDeletedFalseAndIsActiveTrue()).size();
        long buyerCount = buyerRepository.countActiveBuyers();
        model.addAttribute("vetCount",   vetCount);
        model.addAttribute("buyerCount", buyerCount);

        // ── Births ───────────────────────────────────────────────────────────
        List<LivestockBirth> allBirths = safeList(birthService.getAll());
        model.addAttribute("recentBirths", toJson(buildBirthMaps(allBirths)));

        Map<String, Long> birthBreakdown = new LinkedHashMap<>();
        for (LivestockBirth b : allBirths) {
            String cat = b.getLivestock() != null ? catName(b.getLivestock()) : null;
            if (cat != null) birthBreakdown.merge(cat, 1L, Long::sum);
        }
        model.addAttribute("birthBreakdown", toJson(birthBreakdown));

        // ── Treatments (with detailed cost breakdown) ───────────────────────
        List<LivestockTreatment> treatmentList = safeList(treatmentService.getAll());
        model.addAttribute("treatmentList",        treatmentList);
        model.addAttribute("totalTreatments",      stats.getTotalTreatments());
        model.addAttribute("curativeTreatmentCost", fs.getCurativeTreatmentCosts());
        model.addAttribute("preventiveTreatmentCost", fs.getPreventiveTreatmentCosts());
        model.addAttribute("treatmentCostByCategory", toJson(stats.getTreatmentCostByCategory()));
        model.addAttribute("unpaidTreatmentCount", stats.getUnpaidTreatmentCount());
        model.addAttribute("ongoingTreatmentCount",stats.getOngoingTreatmentCount());

        long treatmentsThisMonth = treatmentList.stream()
                .filter(t -> t.getTreatmentDate() != null
                        && t.getTreatmentDate().getMonthValue() == today.getMonthValue()
                        && t.getTreatmentDate().getYear() == year).count();
        model.addAttribute("treatmentsThisMonth", treatmentsThisMonth);

        String mostTreatedTag = treatmentList.stream()
                .filter(t -> t.getLivestock() != null && t.getLivestock().getTagNumber() != null)
                .collect(Collectors.groupingBy(t -> t.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostTreatedAnimalTag", mostTreatedTag);

        Map<String, Long> treatBreakdown = new LinkedHashMap<>();
        for (LivestockTreatment t : treatmentList) {
            String cat = t.getLivestock() != null ? catName(t.getLivestock()) : null;
            if (cat != null) treatBreakdown.merge(cat, 1L, Long::sum);
        }
        model.addAttribute("treatBreakdown", toJson(treatBreakdown));

        // ── Sick Records ─────────────────────────────────────────────────────
        List<LivestockSick> sickList = safeList(sickService.getAll());
        model.addAttribute("sickRecords",     sickList);
        model.addAttribute("sickList",        toJson(buildSickMaps(sickList)));
        model.addAttribute("totalSick",       stats.getTotalSick());
        model.addAttribute("currentlySick",   stats.getCurrentlySick());
        model.addAttribute("criticalCount",   stats.getCriticalCount());
        model.addAttribute("recoveringCount", stats.getRecoveringCount());
        model.addAttribute("recoveredCount",  stats.getRecoveredCount());

        long sickThisMonth = sickList.stream()
                .filter(s -> s.getReportedDate() != null
                        && s.getReportedDate().getMonthValue() == today.getMonthValue()
                        && s.getReportedDate().getYear() == year).count();
        model.addAttribute("sickThisMonth", sickThisMonth);

        String mostSickTag = sickList.stream()
                .filter(s -> s.getLivestock() != null && s.getLivestock().getTagNumber() != null)
                .collect(Collectors.groupingBy(s -> s.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostSickAnimalTag", mostSickTag);

        long lifeThreatCount = sickList.stream()
                .filter(s -> s.getSeverityLevel() != null
                        && s.getSeverityLevel().name().equals("LIFE_THREATENING")).count();
        model.addAttribute("lifeThreatCount", lifeThreatCount);

        Map<String, Long> sickBreakdown = new LinkedHashMap<>();
        for (LivestockSick s : sickList) {
            String cat = s.getLivestock() != null ? catName(s.getLivestock()) : null;
            if (cat != null) sickBreakdown.merge(cat, 1L, Long::sum);
        }
        model.addAttribute("sickBreakdown", toJson(sickBreakdown));

        // ── Sick History ─────────────────────────────────────────────────────
        long yearSickCount      = sickHistoryRepository.countSickByYear(year);
        long yearCriticalCount  = sickHistoryRepository.countCriticalByYear(year);
        long yearRecoveredCount = sickHistoryRepository.countRecoveredByYear(year);
        model.addAttribute("yearSickCount",      yearSickCount);
        model.addAttribute("yearCriticalCount",  yearCriticalCount);
        model.addAttribute("yearRecoveredCount", yearRecoveredCount);

        long recoveryRate = yearSickCount > 0
                ? Math.round((yearRecoveredCount * 100.0) / yearSickCount) : 100L;
        model.addAttribute("recoveryRate", recoveryRate);

        LocalDateTime historyFrom = LocalDateTime.now().minusDays(30);
        LocalDateTime historyTo   = LocalDateTime.now();
        List<LivestockSickHistory> recentHistory = safeList(
                sickHistoryRepository.findByDateRange(historyFrom, historyTo));
        model.addAttribute("totalHistoryEvents30Days", recentHistory.size());
        model.addAttribute("recentSickHistory", recentHistory.stream().limit(20).collect(Collectors.toList()));

        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        model.addAttribute("criticalThisMonthCount",
                safeList(sickHistoryRepository.findCriticalCasesByDateRange(monthStart, historyTo)).size());
        model.addAttribute("recoveredThisMonthCount",
                safeList(sickHistoryRepository.findRecoveredCasesByDateRange(monthStart, historyTo)).size());

        // ── Available livestock for forms ────────────────────────────────────
        model.addAttribute("livestockList", allLivestock.stream()
                .filter(ls -> !Livestock.STATUS_DEAD.equals(ls.getStatus()) && !"SOLD".equals(ls.getStatus()))
                .collect(Collectors.toList()));
        model.addAttribute("sickRecord", new LivestockSick());

        // ── Abortions ────────────────────────────────────────────────────────
        List<LivestockAbortion> abortionList = safeList(abortionService.getAll());
        model.addAttribute("abortionList",   abortionList);
        model.addAttribute("totalAbortions", stats.getTotalAbortions());

        long totalAnimalsAffected = abortionList.stream()
                .filter(a -> a.getLivestock() != null)
                .map(a -> a.getLivestock().getId()).distinct().count();
        model.addAttribute("totalAnimalsAffected", totalAnimalsAffected);

        String mostAffectedTag = abortionList.stream()
                .filter(a -> a.getLivestock() != null && a.getLivestock().getTagNumber() != null)
                .collect(Collectors.groupingBy(a -> a.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostAffectedAnimalTag", mostAffectedTag);

        long abortionsThisMonth = abortionList.stream()
                .filter(a -> a.getAbortionDate() != null
                        && a.getAbortionDate().getMonthValue() == today.getMonthValue()
                        && a.getAbortionDate().getYear() == year).count();
        model.addAttribute("abortionsThisMonth", abortionsThisMonth);

        Map<String, Long> abortBreakdown = new LinkedHashMap<>();
        for (LivestockAbortion a : abortionList) {
            String cat = a.getLivestock() != null ? catName(a.getLivestock()) : null;
            if (cat != null) abortBreakdown.merge(cat, 1L, Long::sum);
        }
        model.addAttribute("abortBreakdown", toJson(abortBreakdown));

        // ── Sales ────────────────────────────────────────────────────────────
        List<LivestockSale> salesList = safeList(saleService.getAll());
        model.addAttribute("salesList",        toJson(buildSaleMaps(salesList)));
        model.addAttribute("totalSales",       stats.getTotalSales());

        String mostSoldTag = salesList.stream()
                .filter(s -> s.getLivestock() != null && s.getLivestock().getTagNumber() != null)
                .collect(Collectors.groupingBy(s -> s.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostSoldAnimalTag", mostSoldTag);

        long salesThisMonth = salesList.stream()
                .filter(s -> s.getSaleDate() != null
                        && s.getSaleDate().getMonthValue() == today.getMonthValue()
                        && s.getSaleDate().getYear() == year).count();
        model.addAttribute("salesThisMonth", salesThisMonth);

        Map<String, Long> salesBreakdown = new LinkedHashMap<>();
        for (LivestockSale s : salesList) {
            String cat = s.getLivestock() != null ? catName(s.getLivestock()) : null;
            if (cat != null) salesBreakdown.merge(cat, 1L, Long::sum);
        }
        model.addAttribute("salesBreakdown", toJson(salesBreakdown));

        // ── Deaths with detailed info ───────────────────────────────────────
        List<LivestockDeath> deathsList = safeList(deathService.getAll());
        model.addAttribute("deathsList",   toJson(buildDeathMaps(deathsList)));
        model.addAttribute("totalDeaths",  stats.getTotalDeaths());
        model.addAttribute("deathsByCause", toJson(stats.getDeathsByCause()));
        model.addAttribute("valueLostByCause", toJson(stats.getValueLostByCause()));

        long deathsThisMonth = deathsList.stream()
                .filter(d -> d.getDeathDate() != null
                        && d.getDeathDate().getMonthValue() == today.getMonthValue()
                        && d.getDeathDate().getYear() == year).count();
        model.addAttribute("deathsThisMonth", deathsThisMonth);

        String mostCommonCause = deathsList.stream()
                .filter(d -> d.getCauseOfDeath() != null && !d.getCauseOfDeath().isBlank())
                .collect(Collectors.groupingBy(LivestockDeath::getCauseOfDeath, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("—");
        model.addAttribute("mostCommonCause", mostCommonCause);

        long distinctCausesCount = deathsList.stream()
                .filter(d -> d.getCauseOfDeath() != null && !d.getCauseOfDeath().isBlank())
                .map(LivestockDeath::getCauseOfDeath).distinct().count();
        model.addAttribute("distinctCausesCount", distinctCausesCount);

        Map<String, Long> deathBreakdown = new LinkedHashMap<>();
        for (LivestockDeath d : deathsList) {
            String cat = d.getLivestock() != null ? catName(d.getLivestock()) : null;
            if (cat != null) deathBreakdown.merge(cat, 1L, Long::sum);
        }
        model.addAttribute("deathBreakdown", toJson(deathBreakdown));

        // ── Financial KPIs ──────────────────────────────────────────────────
        model.addAttribute("netProfit", fs.getNetProfit());
        model.addAttribute("isProfit", fs.isProfit());
        model.addAttribute("netPosition", stats.getNetPosition());

        // ── FAO Standard Indicators ─────────────────────────────────────────
        model.addAttribute("mortalityRate", stats.getMortalityRate());
        model.addAttribute("calvingInterval", stats.getCalvingInterval());
        model.addAttribute("offtakeRate", stats.getOfftakeRate());
        model.addAttribute("replacementRate", stats.getReplacementRate());
        model.addAttribute("productivity", stats.getProductivity());

        // ── Monthly Trends ───────────────────────────────────────────────────
        MonthlyTrends trends = calculateMonthlyTrends(year);
        model.addAttribute("monthlyBirths",     toJson(trends.getMonthlyBirths()));
        model.addAttribute("monthlySales",      toJson(trends.getMonthlySales()));
        model.addAttribute("monthlyDeaths",     toJson(trends.getMonthlyDeaths()));
        model.addAttribute("monthlyTreatments", toJson(trends.getMonthlyTreatments()));
        model.addAttribute("monthlyRevenue",    toJson(trends.getMonthlyRevenue()));
        model.addAttribute("monthlyCosts",      toJson(trends.getMonthlyCosts()));
        model.addAttribute("monthlyDeathValue", toJson(trends.getMonthlyDeathValue()));

        // ── Alerts ───────────────────────────────────────────────────────────
        List<Map<String, String>> alerts = generateAlerts();
        model.addAttribute("alerts",        alerts);
        model.addAttribute("totalAlerts",   alerts.size());
        model.addAttribute("criticalAlerts",alerts.stream()
                .filter(a -> "CRITICAL".equals(a.get("severity"))).count());

        return "admin-dashboard";
    }

    // ── USER DASHBOARD ─────────────────────────────────────────────────────────
    @GetMapping("/user/dashboard")
    public String userDashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        model.addAttribute("username", email);
        model.addAttribute("userInitial", email.substring(0, 1).toUpperCase());
        model.addAttribute("isSuperAdmin", false);

        Users currentUser = usersService.getUserByEmail(email).orElse(null);
        if (currentUser != null && currentUser.getBeneficiaryId() != null) {
            UUID beneficiaryId = currentUser.getBeneficiaryId();
            List<Livestock> userLivestock = livestockRepository.findByBeneficiaryId(beneficiaryId);

            model.addAttribute("totalLivestock", (long) userLivestock.size());
            model.addAttribute("userLivestockCount", userLivestock.size());
            model.addAttribute("userActiveLivestock", userLivestock.stream()
                    .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count());

            BigDecimal userTotalValue = userLivestock.stream()
                    .filter(l -> l.getCurrentValue() != null)
                    .map(Livestock::getCurrentValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("userTotalValue", userTotalValue);

            LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
            List<LivestockBirth> userBirths = new ArrayList<>();
            for (Livestock ls : userLivestock) userBirths.addAll(birthService.getBirthsByMother(ls.getId()));
            model.addAttribute("totalBirths", (long) userBirths.size());
            model.addAttribute("userRecentBirths", userBirths.stream()
                    .filter(b -> b.getBirthDate() != null && !b.getBirthDate().isBefore(thirtyDaysAgo)).count());

            List<LivestockSale> userSales = new ArrayList<>();
            for (Livestock ls : userLivestock) userSales.addAll(saleService.getByLivestock(ls.getId()));
            model.addAttribute("totalSales", (long) userSales.size());
            model.addAttribute("userRecentSales", userSales.size());
            model.addAttribute("userTotalRevenue", userSales.stream()
                    .map(LivestockSale::getSalePrice).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            List<LivestockSick> userSick = new ArrayList<>();
            for (Livestock ls : userLivestock) userSick.addAll(sickService.getByLivestock(ls.getId()));
            model.addAttribute("totalSick", (long) userSick.size());
            model.addAttribute("userSickAnimals", userSick.size());

            List<LivestockTreatment> userTreatments = new ArrayList<>();
            for (Livestock ls : userLivestock) {
                userTreatments.addAll(treatmentService.getByLivestock(ls.getId()));
            }
            BigDecimal userTreatmentCost = userTreatments.stream()
                    .filter(t -> t.getTreatmentCost() != null)
                    .map(LivestockTreatment::getTreatmentCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("userTreatmentCost", userTreatmentCost);
            model.addAttribute("totalTreatments", (long) userTreatments.size());

            List<LivestockDeath> userDeaths = new ArrayList<>();
            for (Livestock ls : userLivestock) {
                userDeaths.addAll(deathService.getByLivestock(ls.getId()));
            }
            model.addAttribute("totalDeaths", (long) userDeaths.size());

            BigDecimal userDeathValue = userDeaths.stream()
                    .filter(d -> d.getLivestock() != null && d.getLivestock().getCurrentValue() != null)
                    .map(d -> d.getLivestock().getCurrentValue())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("userDeathValue", userDeathValue);

            model.addAttribute("totalAbortions", 0L);
            model.addAttribute("totalRepresentatives", 0L);
            model.addAttribute("totalBeneficiaries", 0L);
        } else {
            model.addAttribute("totalLivestock", 0L);
            model.addAttribute("userLivestockCount", 0);
            model.addAttribute("userActiveLivestock", 0);
            model.addAttribute("userTotalValue", BigDecimal.ZERO);
            model.addAttribute("totalBirths", 0L);
            model.addAttribute("userRecentBirths", 0);
            model.addAttribute("totalSales", 0L);
            model.addAttribute("userRecentSales", 0);
            model.addAttribute("userTotalRevenue", BigDecimal.ZERO);
            model.addAttribute("totalSick", 0L);
            model.addAttribute("userSickAnimals", 0);
            model.addAttribute("totalTreatments", 0L);
            model.addAttribute("userTreatmentCost", BigDecimal.ZERO);
            model.addAttribute("totalAbortions", 0L);
            model.addAttribute("totalDeaths", 0L);
            model.addAttribute("userDeathValue", BigDecimal.ZERO);
            model.addAttribute("totalRepresentatives", 0L);
            model.addAttribute("totalBeneficiaries", 0L);
            model.addAttribute("noBeneficiary", true);
        }
        return "user-dashboard";
    }

    // ── Build map helpers for JSON serialisation ──────────────────────────────
    private List<Map<String, Object>> buildBirthMaps(List<LivestockBirth> births) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LivestockBirth b : safeList(births)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("birthDate", formatDate(b.getBirthDate()));
            m.put("offspringCount", b.getOffspringCount());
            m.put("offspringGender", b.getOffspringGender());
            Map<String, Object> ls = new LinkedHashMap<>();
            if (b.getLivestock() != null) {
                ls.put("tagNumber", b.getLivestock().getTagNumber());
                ls.put("id", b.getLivestock().getId());
                ls.put("currentValue", b.getLivestock().getCurrentValue());
                if (b.getLivestock().getLivestockCategory() != null) {
                    Map<String, Object> cat = new LinkedHashMap<>();
                    cat.put("name", b.getLivestock().getLivestockCategory().getName());
                    ls.put("livestockCategory", cat);
                }
            }
            m.put("livestock", ls);
            m.put("children", b.getChildren() != null ? b.getChildren().size() : 0);
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildSickMaps(List<LivestockSick> records) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LivestockSick s : safeList(records)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reportedDate", formatDate(s.getReportedDate()));
            m.put("symptoms", s.getSymptoms());
            m.put("diagnosis", s.getDiagnosis());
            m.put("vetName", s.getVetName());
            m.put("status", s.getStatus() != null ? s.getStatus().name() : null);
            m.put("severityLevel", s.getSeverityLevel() != null ? s.getSeverityLevel().name() : null);
            Map<String, Object> ls = new LinkedHashMap<>();
            if (s.getLivestock() != null) {
                ls.put("tagNumber", s.getLivestock().getTagNumber());
                ls.put("gender", s.getLivestock().getGender());
                ls.put("currentValue", s.getLivestock().getCurrentValue());
                if (s.getLivestock().getLivestockCategory() != null) {
                    Map<String, Object> cat = new LinkedHashMap<>();
                    cat.put("name", s.getLivestock().getLivestockCategory().getName());
                    ls.put("livestockCategory", cat);
                }
            }
            m.put("livestock", ls);
            if (s.getVeterinarian() != null) {
                Map<String, Object> vet = new LinkedHashMap<>();
                vet.put("fullName", s.getVeterinarian().getFullName());
                m.put("veterinarian", vet);
            }
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildSaleMaps(List<LivestockSale> sales) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LivestockSale s : safeList(sales)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("saleDate", formatDate(s.getSaleDate()));
            m.put("salePrice", s.getSalePrice() != null ? s.getSalePrice().doubleValue() : null);
            m.put("saleReason", s.getSaleReason());
            Map<String, Object> ls = new LinkedHashMap<>();
            if (s.getLivestock() != null) {
                ls.put("tagNumber", s.getLivestock().getTagNumber());
                ls.put("currentValue", s.getLivestock().getCurrentValue());
                if (s.getLivestock().getLivestockCategory() != null) {
                    Map<String, Object> cat = new LinkedHashMap<>();
                    cat.put("name", s.getLivestock().getLivestockCategory().getName());
                    ls.put("livestockCategory", cat);
                }
            }
            m.put("livestock", ls);
            Map<String, Object> buyer = new LinkedHashMap<>();
            if (s.getBuyer() != null) {
                buyer.put("name", s.getBuyer().getBuyerName());
                buyer.put("phone", s.getBuyer().getPhone());
            }
            m.put("buyer", buyer);
            m.put("buyerName", s.getBuyer() != null ? s.getBuyer().getBuyerName() : "Unknown Buyer");
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildDeathMaps(List<LivestockDeath> deaths) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LivestockDeath d : safeList(deaths)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deathDate", formatDate(d.getDeathDate()));
            m.put("causeOfDeath", d.getCauseOfDeath());
            Map<String, Object> ls = new LinkedHashMap<>();
            if (d.getLivestock() != null) {
                ls.put("tagNumber", d.getLivestock().getTagNumber());
                ls.put("gender", d.getLivestock().getGender());
                ls.put("currentValue", d.getLivestock().getCurrentValue());
                if (d.getLivestock().getLivestockCategory() != null) {
                    Map<String, Object> cat = new LinkedHashMap<>();
                    cat.put("name", d.getLivestock().getLivestockCategory().getName());
                    ls.put("livestockCategory", cat);
                }
            }
            m.put("livestock", ls);
            list.add(m);
        }
        return list;
    }

    @GetMapping("/access-denied")
    public String accessDenied() { return "access-denied"; }

    // ========================================================================
    // INNER CLASSES
    // ========================================================================

    /**
     * Financial Summary DTO used by the Dashboard view. Values are populated
     * exclusively from FinancialCalculationService (see calculateFinancialSummary()
     * above) so there is only ONE implementation of the financial math in the
     * whole application.
     */
    public static class FinancialSummary {
        private BigDecimal salesRevenue = BigDecimal.ZERO;
        private BigDecimal currentHerdValue = BigDecimal.ZERO;
        private BigDecimal bornAnimalsValue = BigDecimal.ZERO;
        private BigDecimal purchaseCosts = BigDecimal.ZERO;
        private BigDecimal preventiveTreatmentCosts = BigDecimal.ZERO;
        private BigDecimal curativeTreatmentCosts = BigDecimal.ZERO;
        private BigDecimal deathLoss = BigDecimal.ZERO;
        private BigDecimal totalIncome = BigDecimal.ZERO;
        private BigDecimal totalExpenses = BigDecimal.ZERO;
        private BigDecimal netProfit = BigDecimal.ZERO;
        private boolean isProfit;

        // Getters and Setters
        public BigDecimal getSalesRevenue() { return salesRevenue; }
        public void setSalesRevenue(BigDecimal v) { this.salesRevenue = v != null ? v : BigDecimal.ZERO; }

        public BigDecimal getCurrentHerdValue() { return currentHerdValue; }
        public void setCurrentHerdValue(BigDecimal v) { this.currentHerdValue = v != null ? v : BigDecimal.ZERO; }

        public BigDecimal getBornAnimalsValue() { return bornAnimalsValue; }
        public void setBornAnimalsValue(BigDecimal v) { this.bornAnimalsValue = v != null ? v : BigDecimal.ZERO; }

        public BigDecimal getPurchaseCosts() { return purchaseCosts; }
        public void setPurchaseCosts(BigDecimal v) { this.purchaseCosts = v != null ? v : BigDecimal.ZERO; }

        public BigDecimal getPreventiveTreatmentCosts() { return preventiveTreatmentCosts; }
        public void setPreventiveTreatmentCosts(BigDecimal v) { this.preventiveTreatmentCosts = v != null ? v : BigDecimal.ZERO; }

        public BigDecimal getCurativeTreatmentCosts() { return curativeTreatmentCosts; }
        public void setCurativeTreatmentCosts(BigDecimal v) { this.curativeTreatmentCosts = v != null ? v : BigDecimal.ZERO; }

        public BigDecimal getDeathLoss() { return deathLoss; }
        public void setDeathLoss(BigDecimal v) { this.deathLoss = v != null ? v : BigDecimal.ZERO; }

        public BigDecimal getTotalIncome() { return totalIncome; }
        public void setTotalIncome(BigDecimal v) { this.totalIncome = v != null ? v : BigDecimal.ZERO; }

        public BigDecimal getTotalExpenses() { return totalExpenses; }
        public void setTotalExpenses(BigDecimal v) { this.totalExpenses = v != null ? v : BigDecimal.ZERO; }

        public BigDecimal getNetProfit() { return netProfit; }
        public void setNetProfit(BigDecimal v) { this.netProfit = v != null ? v : BigDecimal.ZERO; }

        public boolean isProfit() { return isProfit; }
        public void setProfit(boolean v) { isProfit = v; }
    }

    public static class DashboardStatistics {
        // Livestock counts
        private Long totalLivestock, activeLivestock, pregnantLivestock, sickLivestock, deadLivestock, soldLivestock;
        private Long totalBirths, totalMothers, totalChildren;
        private double averageOffspringPerBirth;
        private Long totalSick, currentlySick, criticalCount, recoveringCount, recoveredCount;
        private Long totalTreatments, unpaidTreatmentCount, ongoingTreatmentCount;
        private BigDecimal totalTreatmentCost;
        private Long totalSales;
        private BigDecimal totalSaleRevenue;
        private Long totalDeaths, totalAbortions;
        private Long totalUsers, activeUsers, inactiveUsers;
        private Long totalBeneficiaries, totalRepresentatives;

        // Current Value metrics
        private BigDecimal totalCurrentValue;
        private BigDecimal averageCurrentValue;
        private Map<String, BigDecimal> valueByCategory;
        private BigDecimal totalDeadValue;
        private BigDecimal averageDeadValue;
        private Map<String, Long> deathsByCause;
        private Map<String, BigDecimal> valueLostByCause;

        // Treatment cost breakdown
        private BigDecimal curativeTreatmentCost;
        private BigDecimal preventiveTreatmentCost;
        private Map<String, BigDecimal> treatmentCostByCategory;

        // Sick care costs
        private BigDecimal totalSickTreatmentCost;

        // Born animals value
        private BigDecimal bornAnimalsValue;

        // Purchase costs
        private BigDecimal purchaseCosts;

        // Financial KPIs
        private BigDecimal totalIncome;
        private BigDecimal totalExpenses;
        private BigDecimal netProfit;
        private BigDecimal netPosition;
        private boolean isProfit;

        // FAO Standard Indicators
        private double mortalityRate;
        private double calvingInterval;
        private double offtakeRate;
        private double replacementRate;
        private double productivity;

        // Getters and Setters
        public Long getTotalLivestock() { return totalLivestock; }
        public void setTotalLivestock(Long v) { this.totalLivestock = v; }
        public Long getActiveLivestock() { return activeLivestock; }
        public void setActiveLivestock(Long v) { this.activeLivestock = v; }
        public Long getPregnantLivestock() { return pregnantLivestock; }
        public void setPregnantLivestock(Long v) { this.pregnantLivestock = v; }
        public Long getSickLivestock() { return sickLivestock; }
        public void setSickLivestock(Long v) { this.sickLivestock = v; }
        public Long getDeadLivestock() { return deadLivestock; }
        public void setDeadLivestock(Long v) { this.deadLivestock = v; }
        public Long getSoldLivestock() { return soldLivestock; }
        public void setSoldLivestock(Long v) { this.soldLivestock = v; }
        public Long getTotalBirths() { return totalBirths; }
        public void setTotalBirths(Long v) { this.totalBirths = v; }
        public Long getTotalMothers() { return totalMothers; }
        public void setTotalMothers(Long v) { this.totalMothers = v; }
        public Long getTotalChildren() { return totalChildren; }
        public void setTotalChildren(Long v) { this.totalChildren = v; }
        public double getAverageOffspringPerBirth() { return averageOffspringPerBirth; }
        public void setAverageOffspringPerBirth(double v) { this.averageOffspringPerBirth = v; }
        public Long getTotalSick() { return totalSick; }
        public void setTotalSick(Long v) { this.totalSick = v; }
        public Long getCurrentlySick() { return currentlySick; }
        public void setCurrentlySick(Long v) { this.currentlySick = v; }
        public Long getCriticalCount() { return criticalCount; }
        public void setCriticalCount(Long v) { this.criticalCount = v; }
        public Long getRecoveringCount() { return recoveringCount; }
        public void setRecoveringCount(Long v) { this.recoveringCount = v; }
        public Long getRecoveredCount() { return recoveredCount; }
        public void setRecoveredCount(Long v) { this.recoveredCount = v; }
        public Long getTotalTreatments() { return totalTreatments; }
        public void setTotalTreatments(Long v) { this.totalTreatments = v; }
        public BigDecimal getTotalTreatmentCost() { return totalTreatmentCost; }
        public void setTotalTreatmentCost(BigDecimal v) { this.totalTreatmentCost = v; }
        public Long getUnpaidTreatmentCount() { return unpaidTreatmentCount; }
        public void setUnpaidTreatmentCount(Long v) { this.unpaidTreatmentCount = v; }
        public Long getOngoingTreatmentCount() { return ongoingTreatmentCount; }
        public void setOngoingTreatmentCount(Long v) { this.ongoingTreatmentCount = v; }
        public Long getTotalSales() { return totalSales; }
        public void setTotalSales(Long v) { this.totalSales = v; }
        public BigDecimal getTotalSaleRevenue() { return totalSaleRevenue; }
        public void setTotalSaleRevenue(BigDecimal v) { this.totalSaleRevenue = v; }
        public Long getTotalDeaths() { return totalDeaths; }
        public void setTotalDeaths(Long v) { this.totalDeaths = v; }
        public Long getTotalAbortions() { return totalAbortions; }
        public void setTotalAbortions(Long v) { this.totalAbortions = v; }
        public Long getTotalUsers() { return totalUsers; }
        public void setTotalUsers(Long v) { this.totalUsers = v; }
        public Long getActiveUsers() { return activeUsers; }
        public void setActiveUsers(Long v) { this.activeUsers = v; }
        public Long getInactiveUsers() { return inactiveUsers; }
        public void setInactiveUsers(Long v) { this.inactiveUsers = v; }
        public Long getTotalBeneficiaries() { return totalBeneficiaries; }
        public void setTotalBeneficiaries(Long v) { this.totalBeneficiaries = v; }
        public Long getTotalRepresentatives() { return totalRepresentatives; }
        public void setTotalRepresentatives(Long v) { this.totalRepresentatives = v; }
        public BigDecimal getTotalCurrentValue() { return totalCurrentValue; }
        public void setTotalCurrentValue(BigDecimal v) { this.totalCurrentValue = v; }
        public BigDecimal getAverageCurrentValue() { return averageCurrentValue; }
        public void setAverageCurrentValue(BigDecimal v) { this.averageCurrentValue = v; }
        public Map<String, BigDecimal> getValueByCategory() { return valueByCategory; }
        public void setValueByCategory(Map<String, BigDecimal> v) { this.valueByCategory = v; }
        public BigDecimal getTotalDeadValue() { return totalDeadValue; }
        public void setTotalDeadValue(BigDecimal v) { this.totalDeadValue = v; }
        public BigDecimal getAverageDeadValue() { return averageDeadValue; }
        public void setAverageDeadValue(BigDecimal v) { this.averageDeadValue = v; }
        public Map<String, Long> getDeathsByCause() { return deathsByCause; }
        public void setDeathsByCause(Map<String, Long> v) { this.deathsByCause = v; }
        public Map<String, BigDecimal> getValueLostByCause() { return valueLostByCause; }
        public void setValueLostByCause(Map<String, BigDecimal> v) { this.valueLostByCause = v; }
        public BigDecimal getCurativeTreatmentCost() { return curativeTreatmentCost; }
        public void setCurativeTreatmentCost(BigDecimal v) { this.curativeTreatmentCost = v; }
        public BigDecimal getPreventiveTreatmentCost() { return preventiveTreatmentCost; }
        public void setPreventiveTreatmentCost(BigDecimal v) { this.preventiveTreatmentCost = v; }
        public Map<String, BigDecimal> getTreatmentCostByCategory() { return treatmentCostByCategory; }
        public void setTreatmentCostByCategory(Map<String, BigDecimal> v) { this.treatmentCostByCategory = v; }
        public BigDecimal getTotalSickTreatmentCost() { return totalSickTreatmentCost; }
        public void setTotalSickTreatmentCost(BigDecimal v) { this.totalSickTreatmentCost = v; }
        public BigDecimal getBornAnimalsValue() { return bornAnimalsValue; }
        public void setBornAnimalsValue(BigDecimal v) { this.bornAnimalsValue = v; }
        public BigDecimal getPurchaseCosts() { return purchaseCosts; }
        public void setPurchaseCosts(BigDecimal v) { this.purchaseCosts = v; }
        public BigDecimal getTotalIncome() { return totalIncome; }
        public void setTotalIncome(BigDecimal v) { this.totalIncome = v; }
        public BigDecimal getTotalExpenses() { return totalExpenses; }
        public void setTotalExpenses(BigDecimal v) { this.totalExpenses = v; }
        public BigDecimal getNetProfit() { return netProfit; }
        public void setNetProfit(BigDecimal v) { this.netProfit = v; }
        public BigDecimal getNetPosition() { return netPosition; }
        public void setNetPosition(BigDecimal v) { this.netPosition = v; }
        public boolean isProfit() { return isProfit; }
        public void setProfit(boolean v) { isProfit = v; }
        public double getMortalityRate() { return mortalityRate; }
        public void setMortalityRate(double v) { this.mortalityRate = v; }
        public double getCalvingInterval() { return calvingInterval; }
        public void setCalvingInterval(double v) { this.calvingInterval = v; }
        public double getOfftakeRate() { return offtakeRate; }
        public void setOfftakeRate(double v) { this.offtakeRate = v; }
        public double getReplacementRate() { return replacementRate; }
        public void setReplacementRate(double v) { this.replacementRate = v; }
        public double getProductivity() { return productivity; }
        public void setProductivity(double v) { this.productivity = v; }
    }

    public static class MonthlyTrends {
        private int[] monthlyBirths = new int[12];
        private int[] monthlySales = new int[12];
        private int[] monthlyDeaths = new int[12];
        private int[] monthlyTreatments = new int[12];
        private double[] monthlyRevenue = new double[12];
        private double[] monthlyCosts = new double[12];
        private double[] monthlyDeathValue = new double[12];

        public int[] getMonthlyBirths() { return monthlyBirths; }
        public void setMonthlyBirths(int[] v) { this.monthlyBirths = v; }
        public int[] getMonthlySales() { return monthlySales; }
        public void setMonthlySales(int[] v) { this.monthlySales = v; }
        public int[] getMonthlyDeaths() { return monthlyDeaths; }
        public void setMonthlyDeaths(int[] v) { this.monthlyDeaths = v; }
        public int[] getMonthlyTreatments() { return monthlyTreatments; }
        public void setMonthlyTreatments(int[] v) { this.monthlyTreatments = v; }
        public double[] getMonthlyRevenue() { return monthlyRevenue; }
        public void setMonthlyRevenue(double[] v) { this.monthlyRevenue = v; }
        public double[] getMonthlyCosts() { return monthlyCosts; }
        public void setMonthlyCosts(double[] v) { this.monthlyCosts = v; }
        public double[] getMonthlyDeathValue() { return monthlyDeathValue; }
        public void setMonthlyDeathValue(double[] v) { this.monthlyDeathValue = v; }
    }
}