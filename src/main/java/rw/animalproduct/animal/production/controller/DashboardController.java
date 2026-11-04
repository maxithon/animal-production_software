package rw.animalproduct.animal.production.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;
import rw.animalproduct.animal.production.services.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

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
    private final LivestockBreedingService breedingService;
    private final FinancialCalculationService financialCalculationService;
    private final LivestockBreedingRepository breedingRepository;
    private final PregnancyAlertService pregnancyAlertService;

    private final LivestockValuationRepository valuationRepository;
    private final LivestockSalePaymentRepository salePaymentRepository;
    private final LivestockTreatmentPaymentRepository treatmentPaymentRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM");
    private static final int DAYS_FOR_ALERTS = 7;
    private static final int DAYS_FOR_CALENDAR = 14;
    private static final DecimalFormat RWF_FORMAT = new DecimalFormat("#,###.00");

    private static final ObjectMapper MAPPER;
    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MAPPER.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

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
                               LivestockBreedingService breedingService,
                               FinancialCalculationService financialCalculationService,
                               LivestockValuationRepository valuationRepository,
                               LivestockSalePaymentRepository salePaymentRepository,
                               LivestockTreatmentPaymentRepository treatmentPaymentRepository,
                               LivestockBreedingRepository breedingRepository,
                               PregnancyAlertService pregnancyAlertService) {
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
        this.breedingService = breedingService;
        this.financialCalculationService = financialCalculationService;
        this.valuationRepository = valuationRepository;
        this.salePaymentRepository = salePaymentRepository;
        this.treatmentPaymentRepository = treatmentPaymentRepository;
        this.breedingRepository = breedingRepository;
        this.pregnancyAlertService = pregnancyAlertService;
    }

    // ════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════
    private String toJson(Object obj) {
        if (obj == null) return "{}";
        try { return MAPPER.writeValueAsString(obj); }
        catch (Exception e) {
            log.debug("toJson failed: {}", e.getMessage());
            return "{}";
        }
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : null;
    }

    private <T> List<T> safeList(List<T> list) {
        return list != null ? list : Collections.emptyList();
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

    private BigDecimal parseMoney(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Double) {
            double d = (Double) v;
            if (Double.isNaN(d) || Double.isInfinite(d)) return BigDecimal.ZERO;
            return BigDecimal.valueOf(d);
        }
        if (v instanceof Float) {
            float f = (Float) v;
            if (Float.isNaN(f) || Float.isInfinite(f)) return BigDecimal.ZERO;
            return BigDecimal.valueOf((double) f);
        }
        if (v instanceof Integer) return BigDecimal.valueOf((Integer) v);
        if (v instanceof Long)    return BigDecimal.valueOf((Long) v);
        if (v instanceof Number)  return BigDecimal.valueOf(((Number) v).doubleValue());
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || s.equalsIgnoreCase("null")) return BigDecimal.ZERO;
        s = s.replace(",", "").replace(" ", "").replace("RWF", "").replace("Frw", "").replace("frw", "").trim();
        try { return new BigDecimal(s); }
        catch (Exception e) {
            log.debug("parseMoney: unable to parse '{}' — returning ZERO", v);
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    // ════════════════════════════════════════════════════════════════════
    // SNAPSHOT
    // ════════════════════════════════════════════════════════════════════
    private static class DataSnapshot {
        final List<LivestockBirth>     births;
        final List<LivestockSick>      sickList;
        final List<LivestockSale>      sales;
        final List<LivestockDeath>     deaths;
        final List<LivestockTreatment> treatments;
        final List<LivestockAbortion>  abortions;
        final List<Livestock>          livestock;
        final List<Users>              users;
        final List<LivestockBreeding>  breedings;
        final List<Beneficiary>        beneficiaries;
        final List<Representative>     representatives;

        DataSnapshot(List<LivestockBirth> births, List<LivestockSick> sickList,
                     List<LivestockSale> sales, List<LivestockDeath> deaths,
                     List<LivestockTreatment> treatments, List<LivestockAbortion> abortions,
                     List<Livestock> livestock, List<Users> users,
                     List<LivestockBreeding> breedings,
                     List<Beneficiary> beneficiaries,
                     List<Representative> representatives) {
            this.births = births; this.sickList = sickList; this.sales = sales;
            this.deaths = deaths; this.treatments = treatments; this.abortions = abortions;
            this.livestock = livestock; this.users = users; this.breedings = breedings;
            this.beneficiaries = beneficiaries; this.representatives = representatives;
        }
    }

    private DataSnapshot loadSnapshot() {
        return new DataSnapshot(
                safeList(birthService.getAll()),
                safeList(sickService.getAll()),
                safeList(saleService.getAll()),
                safeList(deathService.getAll()),
                safeList(treatmentService.getAll()),
                safeList(abortionService.getAll()),
                safeList(livestockRepository.findAll()),
                safeList(usersService.getAllUsers()),
                safeList(breedingService.getAll()),
                safeList(beneficiariesAmatungoService.getAll()),
                safeList(representativesAbororaService.getAll())
        );
    }

    // ════════════════════════════════════════════════════════════════════
    // DIAGNOSTICS
    // ════════════════════════════════════════════════════════════════════
    private Map<String, Object> buildDiagnostics(DataSnapshot snap, FinancialSummary fs) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("livestockTotal", snap.livestock.size());
        long live = snap.livestock.stream()
                .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus()))
                .filter(l -> !Livestock.STATUS_SOLD.equals(l.getStatus()))
                .count();
        d.put("livestockLive", live);
        long withValue = snap.livestock.stream().filter(l -> l.getCurrentValue() != null).count();
        d.put("livestockWithCurrentValue", withValue);
        long withParsable = snap.livestock.stream()
                .map(l -> parseMoney(l.getCurrentValue()))
                .filter(v -> v.compareTo(BigDecimal.ZERO) > 0)
                .count();
        d.put("livestockWithParsableValue", withParsable);
        List<String> samples = snap.livestock.stream()
                .limit(8)
                .map(l -> l.getTagNumber() + "=" + l.getCurrentValue() + " (" + l.getStatus() + ")")
                .collect(Collectors.toList());
        d.put("rawSamples", samples);
        Map<String, Long> statusCounts = snap.livestock.stream()
                .collect(Collectors.groupingBy(l -> l.getStatus() == null ? "NULL" : l.getStatus(), Collectors.counting()));
        d.put("statusCounts", statusCounts);
        d.put("computedCurrentHerdValue", fs.getCurrentHerdValue());
        d.put("computedSalesRevenue", fs.getSalesRevenue());
        d.put("computedCashCollected", fs.getCashCollected());
        d.put("computedCashSpent", fs.getCashSpent());
        d.put("computedNetCashFlow", fs.getNetCashFlow());
        d.put("computedTotalHealthCosts", fs.getTotalHealthCosts());
        d.put("computedReceivables", fs.getReceivables());
        d.put("computedPurchaseCosts", fs.getPurchaseCosts());
        d.put("computedDeathLoss", fs.getDeathLoss());
        d.put("computedBornAnimalsValue", fs.getBornAnimalsValue());
        return d;
    }

    // ════════════════════════════════════════════════════════════════════
    // FINANCIAL ENGINE
    // ════════════════════════════════════════════════════════════════════
    private FinancialSummary calculateFinancialSummaryCorrect(DataSnapshot snap) {
        FinancialSummary fs = new FinancialSummary();

        Map<UUID, BigDecimal> latestValuation = new HashMap<>();
        try {
            List<LivestockValuation> allLatest = valuationRepository.findAllLatestValuations();
            if (allLatest != null) {
                for (LivestockValuation v : allLatest) {
                    if (v == null || v.getLivestock() == null) continue;
                    Double est = v.getEstimatedValue();
                    if (est == null) continue;
                    latestValuation.put(v.getLivestock().getId(), BigDecimal.valueOf(est));
                }
            }
            log.debug("[FIN] Loaded {} latest valuations", latestValuation.size());
        } catch (Exception e) {
            log.warn("[FIN] findAllLatestValuations failed: {}", e.getMessage());
        }

        Map<UUID, BigDecimal> initialValuation = new HashMap<>();
        try {
            List<LivestockValuation> allInitial = valuationRepository.findAllInitialValuations();
            if (allInitial != null) {
                for (LivestockValuation v : allInitial) {
                    if (v == null || v.getLivestock() == null || v.getEstimatedValue() == null) continue;
                    initialValuation.put(v.getLivestock().getId(), BigDecimal.valueOf(v.getEstimatedValue()));
                }
            }
            log.debug("[FIN] Loaded {} initial valuations", initialValuation.size());
        } catch (Exception e) {
            log.warn("[FIN] findAllInitialValuations unavailable — falling back to per-animal lookups");
        }

        // ---------- SALES ----------
        BigDecimal salesRevenue  = BigDecimal.ZERO;
        BigDecimal cashCollected = BigDecimal.ZERO;
        BigDecimal receivables   = BigDecimal.ZERO;
        int confirmedSaleCount   = 0;

        for (LivestockSale s : snap.sales) {
            if (s == null || Boolean.TRUE.equals(s.getIsDeleted())) continue;  // LivestockSale still has isDeleted
            String status = s.getSaleStatus() != null ? s.getSaleStatus().toString().toUpperCase() : "CONFIRMED";
            if (status.equals("CANCELLED") || status.equals("REFUNDED")) continue;
            BigDecimal price = parseMoney(s.getSalePrice());
            BigDecimal paid  = parseMoney(s.getAmountPaid());
            if (paid.compareTo(BigDecimal.ZERO) == 0) {
                try {
                    BigDecimal fromTable = salePaymentRepository.sumAmountBySaleId(s.getId());
                    if (fromTable != null && fromTable.compareTo(BigDecimal.ZERO) > 0) paid = fromTable;
                } catch (Exception ignore) {}
            }
            salesRevenue = salesRevenue.add(price);
            cashCollected = cashCollected.add(paid);
            if (paid.compareTo(price) < 0) receivables = receivables.add(price.subtract(paid));
            confirmedSaleCount++;
        }
        fs.setSalesRevenue(salesRevenue);
        fs.setCashCollected(cashCollected);
        fs.setReceivables(receivables);
        fs.setConfirmedSaleCount(confirmedSaleCount);

        // ---------- PURCHASE COSTS ----------
        BigDecimal purchaseCosts = BigDecimal.ZERO;
        int purchaseCount = 0;
        for (Livestock l : snap.livestock) {
            if (!"PURCHASE".equalsIgnoreCase(l.getAcquisitionMethod())) continue;
            BigDecimal initial = initialValuation.get(l.getId());
            if (initial == null || initial.compareTo(BigDecimal.ZERO) == 0) {
                try {
                    List<LivestockValuation> initials = valuationRepository.findInitialValuations(l.getId());
                    if (initials != null && !initials.isEmpty()
                            && initials.get(0).getEstimatedValue() != null) {
                        initial = BigDecimal.valueOf(initials.get(0).getEstimatedValue());
                    }
                } catch (Exception ignore) {}
            }
            if (initial == null || initial.compareTo(BigDecimal.ZERO) == 0)
                initial = parseMoney(l.getCurrentValue());
            purchaseCosts = purchaseCosts.add(initial);
            purchaseCount++;
        }
        fs.setPurchaseCosts(purchaseCosts);

        // ---------- TREATMENTS ----------
        BigDecimal preventive = BigDecimal.ZERO;
        BigDecimal curative   = BigDecimal.ZERO;
        BigDecimal treatmentPaid   = BigDecimal.ZERO;
        BigDecimal treatmentUnpaid = BigDecimal.ZERO;

        Map<UUID, BigDecimal> treatmentPaidMap = new HashMap<>();
        try {
            List<LivestockTreatmentPayment> allPays = treatmentPaymentRepository.findAll();
            if (allPays != null) {
                for (LivestockTreatmentPayment p : allPays) {
                    if (p == null || p.getTreatment() == null) continue;
                    try { if (Boolean.TRUE.equals(p.getIsDeleted())) continue; } catch (Exception ignore) {}
                    treatmentPaidMap.merge(p.getTreatment().getId(), nz(p.getAmount()), BigDecimal::add);
                }
            }
        } catch (Exception ignore) {}

        for (LivestockTreatment t : snap.treatments) {
            // LivestockTreatment no longer has is_deleted (managed via audit_log)
            if (t == null) continue;
            BigDecimal cost = parseMoney(t.getTreatmentCost());
            String type = t.getTreatmentType() != null ? t.getTreatmentType().name() : "CURATIVE";
            if (type.equalsIgnoreCase("PREVENTIVE")) preventive = preventive.add(cost);
            else curative = curative.add(cost);
            BigDecimal paid = treatmentPaidMap.get(t.getId());
            if (paid == null) paid = parseMoney(t.getAmountPaid());
            treatmentPaid = treatmentPaid.add(paid);
            if (paid.compareTo(cost) < 0) treatmentUnpaid = treatmentUnpaid.add(cost.subtract(paid));
        }
        fs.setPreventiveTreatmentCosts(preventive);
        fs.setCurativeTreatmentCosts(curative);
        fs.setTotalHealthCosts(preventive.add(curative));
        fs.setTreatmentPaid(treatmentPaid);
        fs.setTreatmentUnpaid(treatmentUnpaid);

        BigDecimal otherExpenses = BigDecimal.ZERO;
        try { otherExpenses = nz(financialCalculationService.calculateOtherExpenses(null, null)); }
        catch (Exception ignore) {}
        fs.setOtherExpenses(otherExpenses);

        BigDecimal cashSpent = purchaseCosts.add(treatmentPaid).add(otherExpenses);
        BigDecimal netCashFlow = cashCollected.subtract(cashSpent);
        fs.setCashSpent(cashSpent);
        fs.setNetCashFlow(netCashFlow);
        fs.setCashPositive(netCashFlow.compareTo(BigDecimal.ZERO) >= 0);

        if (salesRevenue.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal gm = salesRevenue.subtract(treatmentPaid).subtract(otherExpenses);
            fs.setGrossMargin(gm);
            fs.setGrossMarginPct(gm.multiply(BigDecimal.valueOf(100))
                    .divide(salesRevenue, 2, RoundingMode.HALF_UP));
        }

        // ---------- CURRENT HERD VALUE ----------
        BigDecimal currentHerdValue = BigDecimal.ZERO;
        long liveAnimalCount = 0;
        for (Livestock l : snap.livestock) {
            if (Livestock.STATUS_DEAD.equals(l.getStatus())
                    || Livestock.STATUS_SOLD.equals(l.getStatus())) continue;
            BigDecimal v = latestValuation.get(l.getId());
            if (v == null) v = parseMoney(l.getCurrentValue());
            currentHerdValue = currentHerdValue.add(v);
            liveAnimalCount++;
        }
        fs.setCurrentHerdValue(currentHerdValue);
        fs.setLiveAnimalCount(liveAnimalCount);
        fs.setAverageHerdValue(liveAnimalCount > 0
                ? currentHerdValue.divide(BigDecimal.valueOf(liveAnimalCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        // ---------- NEWBORN VALUE ----------
        BigDecimal newbornValue = BigDecimal.ZERO;
        int newbornCount = 0;
        for (Livestock l : snap.livestock) {
            if (!"BIRTH".equalsIgnoreCase(l.getAcquisitionMethod())) continue;
            if (Livestock.STATUS_DEAD.equals(l.getStatus())
                    || Livestock.STATUS_SOLD.equals(l.getStatus())) continue;
            BigDecimal v = latestValuation.get(l.getId());
            if (v == null) v = parseMoney(l.getCurrentValue());
            newbornValue = newbornValue.add(v);
            newbornCount++;
        }
        fs.setBornAnimalsValue(newbornValue);
        fs.setNewbornCount(newbornCount);

        // ---------- DEATH LOSS ----------
        BigDecimal deathLoss = BigDecimal.ZERO;
        for (LivestockDeath d : snap.deaths) {
            Livestock l = d.getLivestock();
            if (l == null) continue;
            BigDecimal v = latestValuation.get(l.getId());
            if (v == null) v = parseMoney(l.getCurrentValue());
            deathLoss = deathLoss.add(v);
        }
        fs.setDeathLoss(deathLoss);

        fs.setUnpaidTreatments(treatmentUnpaid);
        fs.setUnpaidSales(receivables);
        long unpaidTreatmentCount = snap.treatments.stream()
                .filter(t -> t.getIsPaid() != null && !t.getIsPaid()).count();
        fs.setUnpaidTreatmentCount(unpaidTreatmentCount);

        fs.setTotalIncome(salesRevenue);
        fs.setTotalExpenses(cashSpent);
        fs.setNetProfit(netCashFlow);
        fs.setProfit(netCashFlow.compareTo(BigDecimal.ZERO) >= 0);

        return fs;
    }

    private DashboardStatistics calculateStatistics(DataSnapshot snap) {
        DashboardStatistics stats = new DashboardStatistics();
        FinancialSummary fs = calculateFinancialSummaryCorrect(snap);
        List<Livestock> livestock = snap.livestock;

        stats.setTotalLivestock((long) livestock.size());
        stats.setActiveLivestock(livestock.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count());
        stats.setPregnantLivestock(livestock.stream().filter(l -> Livestock.REPRO_PREGNANT.equals(l.getReproductiveStatus())).count());
        stats.setSickLivestock(livestock.stream().filter(l -> Livestock.HEALTH_SICK.equals(l.getHealthStatus())).count());
        stats.setDeadLivestock(livestock.stream().filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus())).count());
        stats.setSoldLivestock(livestock.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count());
        stats.setMaleLivestock(livestock.stream()
                .filter(l -> "MALE".equalsIgnoreCase(l.getGender()))
                .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus()) && !Livestock.STATUS_SOLD.equals(l.getStatus())).count());
        stats.setFemaleLivestock(livestock.stream()
                .filter(l -> "FEMALE".equalsIgnoreCase(l.getGender()))
                .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus()) && !Livestock.STATUS_SOLD.equals(l.getStatus())).count());

        stats.setTotalCurrentValue(fs.getCurrentHerdValue());
        stats.setAverageCurrentValue(fs.getAverageHerdValue());
        stats.setBornAnimalsValue(fs.getBornAnimalsValue());
        stats.setPurchaseCosts(fs.getPurchaseCosts());

        Map<String, BigDecimal> valueByCategory = new LinkedHashMap<>();
        for (Livestock l : livestock) {
            if (Livestock.STATUS_DEAD.equals(l.getStatus()) || Livestock.STATUS_SOLD.equals(l.getStatus())) continue;
            String cat = catName(l);
            if (cat != null) valueByCategory.merge(cat, parseMoney(l.getCurrentValue()), BigDecimal::add);
        }
        stats.setValueByCategory(valueByCategory);

        stats.setTotalBirths((long) snap.births.size());
        stats.setTotalMothers(snap.births.stream()
                .map(b -> b.getLivestock() != null ? b.getLivestock().getId() : null)
                .filter(Objects::nonNull).distinct().count());
        stats.setTotalChildren(snap.births.stream()
                .mapToLong(b -> b.getChildren() != null ? b.getChildren().size() : 0).sum());
        double avgOffspring = snap.births.isEmpty() ? 0
                : snap.births.stream().mapToInt(b -> b.getOffspringCount() != null ? b.getOffspringCount() : 0)
                .average().orElse(0);
        stats.setAverageOffspringPerBirth(Math.round(avgOffspring));

        stats.setTotalSick((long) snap.sickList.size());
        stats.setCurrentlySick(snap.sickList.stream().filter(s -> s.getStatus() != null && !s.getStatus().name().equals("RECOVERED")).count());
        stats.setCriticalCount(snap.sickList.stream().filter(s -> s.getStatus() != null && s.getStatus().name().equals("CRITICAL")).count());
        stats.setRecoveringCount(snap.sickList.stream().filter(s -> s.getStatus() != null && s.getStatus().name().equals("RECOVERING")).count());
        stats.setRecoveredCount(snap.sickList.stream().filter(s -> s.getStatus() != null && s.getStatus().name().equals("RECOVERED")).count());

        stats.setTotalTreatments((long) snap.treatments.size());
        stats.setPreventiveTreatmentCost(fs.getPreventiveTreatmentCosts());
        stats.setCurativeTreatmentCost(fs.getCurativeTreatmentCosts());
        stats.setTotalSickTreatmentCost(fs.getCurativeTreatmentCosts());
        stats.setTotalTreatmentCost(fs.getTotalHealthCosts());

        Map<String, BigDecimal> treatmentCostByCategory = new LinkedHashMap<>();
        for (LivestockTreatment t : snap.treatments) {
            if (t.getTreatmentCost() != null && t.getLivestock() != null) {
                String cat = catName(t.getLivestock());
                if (cat != null) treatmentCostByCategory.merge(cat, parseMoney(t.getTreatmentCost()), BigDecimal::add);
            }
        }
        stats.setTreatmentCostByCategory(treatmentCostByCategory);

        stats.setUnpaidTreatmentCount(fs.getUnpaidTreatmentCount());
        stats.setOngoingTreatmentCount(snap.treatments.stream()
                .filter(t -> t.getTreatmentStatus() != null && t.getTreatmentStatus().name().equalsIgnoreCase("ONGOING")).count());
        stats.setOutstandingPayments(fs.getUnpaidTreatments());

        stats.setTotalSales((long) fs.getConfirmedSaleCount());
        stats.setTotalSaleRevenue(fs.getSalesRevenue());
        stats.setTotalDeaths((long) snap.deaths.size());
        stats.setTotalDeadValue(fs.getDeathLoss());

        long deadWithValue = livestock.stream()
                .filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus()) && l.getCurrentValue() != null).count();
        stats.setAverageDeadValue(deadWithValue > 0
                ? fs.getDeathLoss().divide(BigDecimal.valueOf(deadWithValue), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        Map<String, Long> deathsByCause = new LinkedHashMap<>();
        Map<String, BigDecimal> valueLostByCause = new LinkedHashMap<>();
        for (LivestockDeath d : snap.deaths) {
            if (d.getCauseOfDeath() != null && !d.getCauseOfDeath().isBlank()) {
                deathsByCause.merge(d.getCauseOfDeath(), 1L, Long::sum);
                if (d.getLivestock() != null && d.getLivestock().getCurrentValue() != null)
                    valueLostByCause.merge(d.getCauseOfDeath(), parseMoney(d.getLivestock().getCurrentValue()), BigDecimal::add);
            }
        }
        stats.setDeathsByCause(deathsByCause);
        stats.setValueLostByCause(valueLostByCause);

        stats.setTotalAbortions((long) snap.abortions.size());
        stats.setTotalUsers((long) snap.users.size());
        stats.setActiveUsers(snap.users.stream().filter(Users::isActive).count());
        stats.setInactiveUsers(snap.users.stream().filter(u -> !u.isActive()).count());
        stats.setTotalBeneficiaries((long) snap.beneficiaries.size());
        stats.setTotalRepresentatives((long) snap.representatives.size());

        stats.setTotalIncome(fs.getTotalIncome());
        stats.setTotalExpenses(fs.getTotalExpenses());
        stats.setOtherExpenses(fs.getOtherExpenses());
        stats.setNetProfit(fs.getNetProfit());
        stats.setProfit(fs.isProfit());

        long liveHerd = livestock.stream().filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus())).count();
        long totalEverPresent = liveHerd + snap.deaths.size();
        stats.setMortalityRate(totalEverPresent > 0
                ? Math.round((snap.deaths.size() * 1000.0) / totalEverPresent) / 10.0 : 0.0);
        stats.setCalvingInterval(calculateCalvingInterval(snap.births));
        stats.setOfftakeRate(liveHerd > 0 ? Math.round(snap.sales.size() * 1000.0 / liveHerd) / 10.0 : 0.0);
        stats.setReplacementRate(liveHerd > 0 ? Math.round(snap.births.size() * 1000.0 / liveHerd) / 10.0 : 0.0);
        stats.setProductivity(snap.sales.isEmpty() ? 0
                : Math.round(fs.getSalesRevenue().doubleValue() / snap.sales.size()));
        return stats;
    }

    private double calculateCalvingInterval(List<LivestockBirth> births) {
        Map<UUID, List<LocalDate>> byMother = new HashMap<>();
        for (LivestockBirth b : births) {
            if (b.getLivestock() == null || b.getBirthDate() == null) continue;
            byMother.computeIfAbsent(b.getLivestock().getId(), k -> new ArrayList<>()).add(b.getBirthDate());
        }
        long totalDays = 0; int intervalCount = 0;
        for (List<LocalDate> dates : byMother.values()) {
            if (dates.size() < 2) continue;
            Collections.sort(dates);
            for (int i = 1; i < dates.size(); i++) {
                totalDays += ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
                intervalCount++;
            }
        }
        return intervalCount == 0 ? 0.0
                : Math.round((totalDays / (double) intervalCount / 30.4375) * 10) / 10.0;
    }

    private MonthlyTrends calculateMonthlyTrends(int year, DataSnapshot snap) {
        MonthlyTrends trends = new MonthlyTrends();
        int[] births = new int[12], sales = new int[12], deaths = new int[12], treatments = new int[12];
        int[] sick = new int[12], abortions = new int[12], pregnancies = new int[12];
        double[] revenue = new double[12], costs = new double[12], deathValue = new double[12];

        for (LivestockBirth b : snap.births)
            if (b.getBirthDate() != null && b.getBirthDate().getYear() == year)
                births[b.getBirthDate().getMonthValue() - 1]++;
        for (LivestockSale s : snap.sales)
            if (s.getSaleDate() != null && s.getSaleDate().getYear() == year && !Boolean.TRUE.equals(s.getIsDeleted())) {
                int idx = s.getSaleDate().getMonthValue() - 1;
                sales[idx]++;
                if (s.getSalePrice() != null) revenue[idx] += parseMoney(s.getSalePrice()).doubleValue();
            }
        for (LivestockDeath d : snap.deaths)
            if (d.getDeathDate() != null && d.getDeathDate().getYear() == year) {
                int idx = d.getDeathDate().getMonthValue() - 1;
                deaths[idx]++;
                if (d.getLivestock() != null && d.getLivestock().getCurrentValue() != null)
                    deathValue[idx] += parseMoney(d.getLivestock().getCurrentValue()).doubleValue();
            }
        for (LivestockTreatment t : snap.treatments)
            if (t.getTreatmentDate() != null && t.getTreatmentDate().getYear() == year) {
                int idx = t.getTreatmentDate().getMonthValue() - 1;
                treatments[idx]++;
                if (t.getTreatmentCost() != null) costs[idx] += parseMoney(t.getTreatmentCost()).doubleValue();
            }
        for (LivestockSick s : snap.sickList)
            if (s.getReportedDate() != null && s.getReportedDate().getYear() == year)
                sick[s.getReportedDate().getMonthValue() - 1]++;
        for (LivestockAbortion a : snap.abortions)
            if (a.getAbortionDate() != null && a.getAbortionDate().getYear() == year)
                abortions[a.getAbortionDate().getMonthValue() - 1]++;
        for (LivestockBreeding b : snap.breedings) {
            if (b.getPregnancyStatus() == null) continue;
            String ps = b.getPregnancyStatus().toString();
            if (ps.contains("CONFIRMED") || ps.contains("PREGNANT")) {
                LocalDate checkDate = b.getExpectedPregnancyCheckDate();
                if (checkDate != null && checkDate.getYear() == year)
                    pregnancies[checkDate.getMonthValue() - 1]++;
            }
        }
        trends.setMonthlyBirths(births); trends.setMonthlySales(sales);
        trends.setMonthlyDeaths(deaths); trends.setMonthlyTreatments(treatments);
        trends.setMonthlySick(sick); trends.setMonthlyAbortions(abortions);
        trends.setMonthlyPregnancies(pregnancies); trends.setMonthlyRevenue(revenue);
        trends.setMonthlyCosts(costs); trends.setMonthlyDeathValue(deathValue);
        return trends;
    }

    private List<Map<String, String>> generateAlerts(DataSnapshot snap) {
        List<Map<String, String>> alertList = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate weekFromNow = today.plusDays(DAYS_FOR_ALERTS);

        for (LivestockTreatment t : snap.treatments) {
            if (t.getNextTreatmentDate() != null
                    && !t.getNextTreatmentDate().isBefore(today)
                    && t.getNextTreatmentDate().isBefore(weekFromNow)) {
                String tag = t.getLivestock() != null ? t.getLivestock().getTagNumber() : "Unknown";
                alertList.add(alert("TREATMENT", "WARNING",
                        "💊 Treatment due for " + tag + " on " + formatDate(t.getNextTreatmentDate()),
                        "/livestock/treatments", "View treatments"));
            }
        }

        for (LivestockSick s : snap.sickList) {
            if (s.getStatus() != LivestockSick.SickStatus.RECOVERED
                    && s.getReportedDate() != null
                    && s.getReportedDate().isBefore(today.minusDays(3))) {
                String tag = s.getLivestock() != null ? s.getLivestock().getTagNumber() : "Unknown";
                long days = ChronoUnit.DAYS.between(s.getReportedDate(), today);
                alertList.add(alert("SICK", "CRITICAL",
                        "🩹 " + tag + " has been sick for " + days + " days (since "
                                + formatDate(s.getReportedDate()) + ")",
                        "/livestock/sick", "View sick list"));
            }
        }

        for (LivestockTreatment t : snap.treatments) {
            if (t.getNextTreatmentDate() != null
                    && t.getNextTreatmentDate().isBefore(today)
                    && t.getTreatmentStatus() != null
                    && !t.getTreatmentStatus().name().equalsIgnoreCase("COMPLETED")) {
                String tag = t.getLivestock() != null ? t.getLivestock().getTagNumber() : "Unknown";
                long days = ChronoUnit.DAYS.between(t.getNextTreatmentDate(), today);
                alertList.add(alert("OVERDUE_TREATMENT", "CRITICAL",
                        "⏰ Treatment OVERDUE for " + tag + " by " + days + " days",
                        "/livestock/treatments", "View treatments"));
            }
        }

        long readyCount = 0;
        for (Livestock l : snap.livestock) {
            if (readyCount >= 5) break;
            if (Livestock.STATUS_ACTIVE.equals(l.getStatus())
                    && "FEMALE".equalsIgnoreCase(l.getGender())
                    && "READY_TO_BREED".equalsIgnoreCase(l.getReproductiveStatus())) {
                alertList.add(alert("READY_TO_BREED", "INFO",
                        "❤️ Ready to breed: " + l.getTagNumber(),
                        "/livestock/lifecycle/ready-to-breed", "View"));
                readyCount++;
            }
        }

        BigDecimal outstanding = BigDecimal.ZERO;
        long unpaidCount = 0;
        for (LivestockTreatment t : snap.treatments) {
            if (t.getIsPaid() != null && !t.getIsPaid() && t.getTreatmentCost() != null) {
                outstanding = outstanding.add(parseMoney(t.getTreatmentCost()));
                unpaidCount++;
            }
        }
        if (unpaidCount > 0) {
            alertList.add(alert("OUTSTANDING_PAYMENT", unpaidCount > 5 ? "WARNING" : "INFO",
                    "💰 " + unpaidCount + " unpaid treatment(s) — " + formatRWF(outstanding) + " outstanding",
                    "/livestock/treatments", "View treatments"));
        }

        return alertList;
    }

    private List<Map<String, String>> buildAllAlerts(DataSnapshot snap) {
        List<Map<String, String>> combined = new ArrayList<>();
        combined.addAll(generateAlerts(snap));
        try {
            combined.addAll(pregnancyAlertService.generatePregnancyAlerts());
        } catch (Exception e) {
            log.warn("[alerts] pregnancy alerts failed: {}", e.getMessage());
        }
        combined.sort(Comparator.comparingInt(a -> {
            String sev = a.get("severity");
            if ("CRITICAL".equals(sev)) return 0;
            if ("WARNING".equals(sev))  return 1;
            return 2;
        }));
        return combined;
    }

    private Map<String, String> alert(String type, String severity, String message,
                                      String actionUrl, String actionLabel) {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("type",        type);
        a.put("severity",    severity);
        a.put("message",     message);
        a.put("actionUrl",   actionUrl);
        a.put("actionLabel", actionLabel);
        return a;
    }

    private List<Map<String, Object>> buildUpcomingEvents(DataSnapshot snap) {
        List<Map<String, Object>> events = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(30);

        for (LivestockBreeding b : snap.breedings) {
            if (b.getExpectedDueDate() == null) continue;
            if (b.getExpectedDueDate().isBefore(today) || b.getExpectedDueDate().isAfter(horizon)) continue;
            Livestock mother = b.getLivestock();
            String tag = mother != null ? mother.getTagNumber() : "Unknown";
            String cat = (mother != null && mother.getLivestockCategory() != null)
                    ? mother.getLivestockCategory().getName() : "";
            events.add(event(b.getExpectedDueDate(), "BIRTH", "🐣",
                    "Birth expected — " + tag, cat));
        }

        for (Livestock l : snap.livestock) {
            if (l.getExpectedDueDate() == null) continue;
            if (l.getExpectedDueDate().isBefore(today) || l.getExpectedDueDate().isAfter(horizon)) continue;
            boolean alreadyAdded = events.stream().anyMatch(e ->
                    "BIRTH".equals(e.get("type")) && ((String) e.get("title")).contains(l.getTagNumber()));
            if (alreadyAdded) continue;
            String cat = l.getLivestockCategory() != null ? l.getLivestockCategory().getName() : "";
            events.add(event(l.getExpectedDueDate(), "BIRTH", "🐣",
                    "Birth expected — " + l.getTagNumber(), cat));
        }

        for (LivestockBirth b : snap.births) {
            if (b.getWeaningDate() == null) continue;
            if (b.getWeaningDate().isBefore(today) || b.getWeaningDate().isAfter(horizon)) continue;
            Livestock mother = b.getLivestock();
            String motherTag = mother != null ? mother.getTagNumber() : "Unknown";
            events.add(event(b.getWeaningDate(), "WEANING", "🍼",
                    "Weaning due — offspring of " + motherTag,
                    (b.getOffspringCount() != null ? b.getOffspringCount() + " offspring" : "Offspring")));
        }

        for (LivestockBreeding b : snap.breedings) {
            if (b.getExpectedPregnancyCheckDate() == null) continue;
            if (b.getExpectedPregnancyCheckDate().isBefore(today)
                    || b.getExpectedPregnancyCheckDate().isAfter(horizon)) continue;
            String tag = b.getLivestock() != null ? b.getLivestock().getTagNumber() : "Unknown";
            events.add(event(b.getExpectedPregnancyCheckDate(), "PREGNANCY_CHECK", "🩺",
                    "Pregnancy check — " + tag, "Confirm pregnancy status"));
        }

        for (LivestockTreatment t : snap.treatments) {
            if (t.getNextTreatmentDate() == null) continue;
            if (t.getNextTreatmentDate().isBefore(today) || t.getNextTreatmentDate().isAfter(horizon)) continue;
            String tag = t.getLivestock() != null ? t.getLivestock().getTagNumber() : "Unknown";
            events.add(event(t.getNextTreatmentDate(), "TREATMENT", "💊",
                    "Treatment due — " + tag,
                    t.getDiagnosisName() != null ? t.getDiagnosisName() : "Follow-up"));
        }

        long readyCount = 0;
        for (Livestock l : snap.livestock) {
            if (readyCount >= 5) break;
            if (!Livestock.STATUS_ACTIVE.equals(l.getStatus())) continue;
            if (!"FEMALE".equalsIgnoreCase(l.getGender())) continue;
            if (!"READY_TO_BREED".equalsIgnoreCase(l.getReproductiveStatus())) continue;
            events.add(event(today, "BREEDING_READY", "❤️",
                    "Ready to breed — " + l.getTagNumber(),
                    l.getLivestockCategory() != null ? l.getLivestockCategory().getName() : ""));
            readyCount++;
        }

        events.sort(Comparator
                .comparing((Map<String, Object> e) -> (String) e.get("date"))
                .thenComparing(e -> priorityRank((String) e.get("type"))));
        return events;
    }

    private int priorityRank(String type) {
        if ("BIRTH".equals(type))           return 0;
        if ("TREATMENT".equals(type))       return 1;
        if ("PREGNANCY_CHECK".equals(type)) return 2;
        if ("WEANING".equals(type))         return 3;
        if ("BREEDING_READY".equals(type))  return 4;
        return 5;
    }

    private Map<String, Object> event(LocalDate date, String type, String icon, String title, String detail) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("date", date.format(DATE_FORMATTER));
        e.put("dateShort", date.format(SHORT_DATE_FORMATTER));
        e.put("daysAway", ChronoUnit.DAYS.between(LocalDate.now(), date));
        e.put("type", type); e.put("icon", icon); e.put("title", title); e.put("detail", detail);
        return e;
    }

    private Map<String, Object> buildReproductionFunnel(DataSnapshot snap) {
        Map<String, Object> funnel = new LinkedHashMap<>();
        long totalBreedings = snap.breedings.size();
        long confirmedPregnant = snap.breedings.stream()
                .filter(b -> b.getPregnancyStatus() != null
                        && (b.getPregnancyStatus().toString().contains("CONFIRMED") || b.getPregnancyStatus().toString().contains("PREGNANT"))).count();
        long failedBreedings = snap.breedings.stream()
                .filter(b -> b.getPregnancyStatus() != null && b.getPregnancyStatus().toString().contains("FAILED")).count();
        long birthsRecorded = snap.births.size();
        long liveOffspring = snap.births.stream().mapToLong(b -> b.getNumberBornAlive() != null ? b.getNumberBornAlive() : 0).sum();
        long stillborn = snap.births.stream().mapToLong(b -> b.getNumberStillborn() != null ? b.getNumberStillborn() : 0).sum();
        funnel.put("totalBreedings", totalBreedings);
        funnel.put("confirmedPregnant", confirmedPregnant);
        funnel.put("failedBreedings", failedBreedings);
        funnel.put("birthsRecorded", birthsRecorded);
        funnel.put("liveOffspring", liveOffspring);
        funnel.put("stillborn", stillborn);
        funnel.put("conceptionRate", totalBreedings > 0 ? Math.round(confirmedPregnant * 1000.0 / totalBreedings) / 10.0 : 0.0);
        funnel.put("calvingSuccessRate", confirmedPregnant > 0 ? Math.round(birthsRecorded * 1000.0 / confirmedPregnant) / 10.0 : 0.0);
        long totalBorn = liveOffspring + stillborn;
        funnel.put("liveBirthRate", totalBorn > 0 ? Math.round(liveOffspring * 1000.0 / totalBorn) / 10.0 : 100.0);
        return funnel;
    }

    private Map<String, Long> buildHerdByLocation(DataSnapshot snap) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Livestock l : snap.livestock) {
            if (Livestock.STATUS_DEAD.equals(l.getStatus()) || Livestock.STATUS_SOLD.equals(l.getStatus())) continue;
            String loc = (l.getLocation() != null && l.getLocation().getName() != null) ? l.getLocation().getName() : "Unspecified";
            counts.merge(loc, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, Map<String, Long>> buildAgePyramid(DataSnapshot snap) {
        Map<String, Map<String, Long>> pyramid = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (Livestock l : snap.livestock) {
            if (Livestock.STATUS_DEAD.equals(l.getStatus()) || Livestock.STATUS_SOLD.equals(l.getStatus())) continue;
            String cat = catName(l);
            if (cat == null) cat = "Uncategorized";
            long ageMonths = l.getBirthDate() != null ? ChronoUnit.MONTHS.between(l.getBirthDate(), today) : 999;
            String bucket = ageMonths < 12 ? "Young (<12mo)" : ageMonths < 36 ? "Adult (1–3y)" : "Mature (3y+)";
            pyramid.computeIfAbsent(cat, k -> new LinkedHashMap<>()).merge(bucket, 1L, Long::sum);
        }
        return pyramid;
    }

    private Map<String, Object> buildBeneficiarySummary(DataSnapshot snap) {
        Map<String, Object> s = new LinkedHashMap<>();
        long totalBeneficiaries = snap.beneficiaries.size();
        long activeBeneficiaries = snap.beneficiaries.stream().filter(Beneficiary::isActive).count();
        long withContract = snap.beneficiaries.stream()
                .filter(b -> b.getContractAgreement() != null && !b.getContractAgreement().isBlank()).count();
        long animalsUnder = snap.livestock.stream()
                .filter(l -> l.getBeneficiary() != null)
                .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus()) && !Livestock.STATUS_SOLD.equals(l.getStatus())).count();
        BigDecimal valueUnder = snap.livestock.stream()
                .filter(l -> l.getBeneficiary() != null)
                .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus()) && !Livestock.STATUS_SOLD.equals(l.getStatus()))
                .map(l -> parseMoney(l.getCurrentValue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        s.put("totalBeneficiaries", totalBeneficiaries);
        s.put("activeBeneficiaries", activeBeneficiaries);
        s.put("withContract", withContract);
        s.put("animalsUnderBeneficiaries", animalsUnder);
        s.put("valueUnderBeneficiaries", valueUnder);
        s.put("avgAnimalsPerBeneficiary", totalBeneficiaries > 0
                ? Math.round(animalsUnder * 10.0 / totalBeneficiaries) / 10.0 : 0.0);
        return s;
    }

    private Map<String, Long> buildDataQualityFlags(DataSnapshot snap) {
        Map<String, Long> flags = new LinkedHashMap<>();
        flags.put("missing_value", snap.livestock.stream()
                .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() == null).count());
        flags.put("missing_date_received", snap.livestock.stream()
                .filter(l -> l.getDateReceived() == null && l.getBirthDate() == null).count());
        flags.put("missing_gender", snap.livestock.stream().filter(l -> l.getGender() == null).count());
        flags.put("missing_category", snap.livestock.stream().filter(l -> l.getLivestockCategory() == null).count());
        return flags;
    }

    private Map<String, Object> buildComparison(LocalDate from, LocalDate to, DataSnapshot snap) {
        Map<String, Object> m = new LinkedHashMap<>();
        LocalDate[] prev = financialCalculationService.getPreviousPeriod(from, to);
        m.put("revenue", financialCalculationService.buildComparison(
                financialCalculationService.calculateTotalRevenue(from, to),
                financialCalculationService.calculateTotalRevenue(prev[0], prev[1])));
        m.put("expenses", financialCalculationService.buildComparison(
                financialCalculationService.calculateTotalExpenses(from, to),
                financialCalculationService.calculateTotalExpenses(prev[0], prev[1])));
        m.put("netProfit", financialCalculationService.buildComparison(
                financialCalculationService.calculateNetProfit(from, to),
                financialCalculationService.calculateNetProfit(prev[0], prev[1])));
        long curSales = snap.sales.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .filter(s -> s.getSaleDate() != null && !s.getSaleDate().isBefore(from) && !s.getSaleDate().isAfter(to)).count();
        long prevSales = snap.sales.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .filter(s -> s.getSaleDate() != null && !s.getSaleDate().isBefore(prev[0]) && !s.getSaleDate().isAfter(prev[1])).count();
        m.put("salesCount", financialCalculationService.buildComparison(
                BigDecimal.valueOf(curSales), BigDecimal.valueOf(prevSales)));
        return m;
    }

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
            m.put("salePrice", s.getSalePrice() != null ? parseMoney(s.getSalePrice()).doubleValue() : null);
            m.put("amountPaid", s.getAmountPaid() != null ? parseMoney(s.getAmountPaid()).doubleValue() : null);
            m.put("paymentStatus", s.getPaymentStatus() != null ? s.getPaymentStatus().toString() : null);
            m.put("saleStatus", s.getSaleStatus() != null ? s.getSaleStatus().toString() : "CONFIRMED");
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

    private List<Map<String, Object>> buildAbortionMaps(List<LivestockAbortion> abortions) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LivestockAbortion a : safeList(abortions)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("abortionDate", formatDate(a.getAbortionDate()));
            m.put("expectedBirthDate", formatDate(a.getExpectedBirthDate()));
            m.put("pregnancyNumber", a.getPregnancyNumber());
            Map<String, Object> ls = new LinkedHashMap<>();
            if (a.getLivestock() != null) {
                ls.put("tagNumber", a.getLivestock().getTagNumber());
                ls.put("currentValue", a.getLivestock().getCurrentValue());
                if (a.getLivestock().getLivestockCategory() != null) {
                    Map<String, Object> cat = new LinkedHashMap<>();
                    cat.put("name", a.getLivestock().getLivestockCategory().getName());
                    ls.put("livestockCategory", cat);
                }
            }
            m.put("livestock", ls);
            list.add(m);
        }
        return list;
    }

    private List<Map<String, Object>> buildTreatmentMaps(List<LivestockTreatment> treatments) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LivestockTreatment t : safeList(treatments)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("treatmentDate", formatDate(t.getTreatmentDate()));
            m.put("treatmentType", t.getTreatmentType() != null ? t.getTreatmentType().name() : null);
            m.put("treatmentStatus", t.getTreatmentStatus() != null ? t.getTreatmentStatus().name() : null);
            m.put("treatmentCost", t.getTreatmentCost() != null ? parseMoney(t.getTreatmentCost()).doubleValue() : null);
            m.put("isPaid", t.getIsPaid());
            m.put("diagnosisName", t.getDiagnosisName());
            Map<String, Object> med = new LinkedHashMap<>();
            if (t.getMedication() != null) {
                med.put("name", t.getMedication().getName());
                med.put("genericName", t.getMedication().getGenericName());
            }
            m.put("medication", med);
            Map<String, Object> ls = new LinkedHashMap<>();
            if (t.getLivestock() != null) {
                ls.put("tagNumber", t.getLivestock().getTagNumber());
                ls.put("currentValue", t.getLivestock().getCurrentValue());
                if (t.getLivestock().getLivestockCategory() != null) {
                    Map<String, Object> cat = new LinkedHashMap<>();
                    cat.put("name", t.getLivestock().getLivestockCategory().getName());
                    ls.put("livestockCategory", cat);
                }
            }
            m.put("livestock", ls);
            Map<String, Object> vet = new LinkedHashMap<>();
            if (t.getVeterinarian() != null) vet.put("fullName", t.getVeterinarian().getFullName());
            m.put("veterinarian", vet);
            list.add(m);
        }
        return list;
    }

    @GetMapping("/access-denied")
    public String accessDenied() { return "access-denied"; }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/dashboard/diagnostics")
    @ResponseBody
    public Map<String, Object> diagnostics() {
        DataSnapshot snap = loadSnapshot();
        FinancialSummary fs = calculateFinancialSummaryCorrect(snap);
        return buildDiagnostics(snap, fs);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/dashboard/stats")
    @ResponseBody
    public Map<String, Object> liveDashboardStats() {
        DataSnapshot snap = loadSnapshot();
        DashboardStatistics s = calculateStatistics(snap);
        FinancialSummary fs = calculateFinancialSummaryCorrect(snap);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalLivestock", s.getTotalLivestock());
        out.put("activeCount", s.getActiveLivestock());
        out.put("soldCount", s.getSoldLivestock());
        out.put("deadCount", s.getDeadLivestock());
        out.put("sickCount", s.getSickLivestock());
        out.put("pregnantCount", s.getPregnantLivestock());
        out.put("maleCount", s.getMaleLivestock());
        out.put("femaleCount", s.getFemaleLivestock());
        out.put("totalBirths", s.getTotalBirths());
        out.put("totalSales", s.getTotalSales());
        out.put("totalTreatments", s.getTotalTreatments());
        out.put("totalSick", s.getTotalSick());
        out.put("totalAbortions", s.getTotalAbortions());
        out.put("totalDeaths", s.getTotalDeaths());
        out.put("totalUsers", s.getTotalUsers());
        out.put("activeUsers", s.getActiveUsers());
        out.put("inactiveUsers", s.getInactiveUsers());
        out.put("totalRepresentatives", s.getTotalRepresentatives());
        out.put("totalBeneficiaries", s.getTotalBeneficiaries());
        out.put("totalIncome", fs.getTotalIncome());
        out.put("totalExpenses", fs.getTotalExpenses());
        out.put("netProfit", fs.getNetProfit());
        out.put("isProfit", fs.isProfit());
        out.put("totalSaleRevenue", fs.getSalesRevenue());
        out.put("cashCollected", fs.getCashCollected());
        out.put("receivables", fs.getReceivables());
        out.put("totalHealthCosts", fs.getTotalHealthCosts());
        out.put("outstandingPayments", s.getOutstandingPayments());
        out.put("netCashFlow", fs.getNetCashFlow());
        out.put("mortalityRate", s.getMortalityRate());
        out.put("calvingInterval", s.getCalvingInterval());
        out.put("offtakeRate", s.getOfftakeRate());
        out.put("replacementRate", s.getReplacementRate());
        out.put("productivity", s.getProductivity());

        List<Map<String, String>> alerts = buildAllAlerts(snap);
        out.put("alerts", alerts);
        out.put("criticalAlerts", alerts.stream()
                .filter(a -> "CRITICAL".equals(a.get("severity")))
                .collect(Collectors.toList()));
        out.put("totalAlerts", alerts.size());
        out.put("criticalAlertsCount", alerts.stream()
                .filter(a -> "CRITICAL".equals(a.get("severity"))).count());
        out.put("refreshedAt", LocalDateTime.now().toString());
        return out;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/dashboard")
    public String adminDashboard(Authentication authentication, Model model) {
        long requestStart = System.currentTimeMillis();
        String email = authentication.getName();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        int year = today.getYear();

        model.addAttribute("username", email);
        model.addAttribute("userInitial", email.substring(0, 1).toUpperCase());
        model.addAttribute("isSuperAdmin", true);

        long t0 = System.currentTimeMillis();
        DataSnapshot snap = loadSnapshot();
        long t1 = System.currentTimeMillis();
        log.info("[dashboard] snapshot loaded in {} ms (livestock={}, births={}, sales={})",
                (t1 - t0), snap.livestock.size(), snap.births.size(), snap.sales.size());

        DashboardStatistics stats = calculateStatistics(snap);
        long t2 = System.currentTimeMillis();
        log.info("[dashboard] statistics computed in {} ms", (t2 - t1));

        FinancialSummary fs = calculateFinancialSummaryCorrect(snap);
        long t3 = System.currentTimeMillis();
        log.info("[dashboard] financial summary in {} ms", (t3 - t2));

        // ===================== Financial attributes =====================
        model.addAttribute("salesRevenue",           fs.getSalesRevenue());
        model.addAttribute("salesRevenueFormatted",  formatRWF(fs.getSalesRevenue()));
        model.addAttribute("cashCollected",          fs.getCashCollected());
        model.addAttribute("cashCollectedFormatted", formatRWF(fs.getCashCollected()));
        model.addAttribute("receivables",            fs.getReceivables());
        model.addAttribute("receivablesFormatted",   formatRWF(fs.getReceivables()));
        model.addAttribute("cashSpent",              fs.getCashSpent());
        model.addAttribute("cashSpentFormatted",     formatRWF(fs.getCashSpent()));
        model.addAttribute("netCashFlow",            fs.getNetCashFlow());
        model.addAttribute("netCashFlowFormatted",   formatRWF(fs.getNetCashFlow()));
        model.addAttribute("cashPositive",           fs.isCashPositive());
        model.addAttribute("grossMargin",            fs.getGrossMargin());
        model.addAttribute("grossMarginPct",         fs.getGrossMarginPct());
        model.addAttribute("confirmedSaleCount",     fs.getConfirmedSaleCount());

        model.addAttribute("currentHerdValue",              fs.getCurrentHerdValue());
        model.addAttribute("currentHerdValueFormatted",     formatRWF(fs.getCurrentHerdValue()));
        model.addAttribute("averageCurrentValue",           fs.getAverageHerdValue());
        model.addAttribute("averageCurrentValueFormatted",  formatRWF(fs.getAverageHerdValue()));
        model.addAttribute("liveAnimalCount",               fs.getLiveAnimalCount());
        model.addAttribute("bornAnimalsValue",              fs.getBornAnimalsValue());
        model.addAttribute("bornAnimalsValueFormatted",     formatRWF(fs.getBornAnimalsValue()));
        model.addAttribute("newbornCount",                  fs.getNewbornCount());
        model.addAttribute("deathLoss",                     fs.getDeathLoss());
        model.addAttribute("deathLossFormatted",            formatRWF(fs.getDeathLoss()));

        model.addAttribute("unpaidTreatments",            fs.getUnpaidTreatments());
        model.addAttribute("unpaidTreatmentsFormatted",   formatRWF(fs.getUnpaidTreatments()));
        model.addAttribute("unpaidSales",                 fs.getUnpaidSales());
        model.addAttribute("unpaidSalesFormatted",        formatRWF(fs.getUnpaidSales()));
        model.addAttribute("unpaidTreatmentCount",        fs.getUnpaidTreatmentCount());
        model.addAttribute("outstandingPayments",         fs.getUnpaidTreatments());
        model.addAttribute("outstandingPaymentsFormatted",formatRWF(fs.getUnpaidTreatments()));

        model.addAttribute("preventiveTreatmentCosts",            fs.getPreventiveTreatmentCosts());
        model.addAttribute("preventiveTreatmentCostsFormatted",   formatRWF(fs.getPreventiveTreatmentCosts()));
        model.addAttribute("curativeTreatmentCosts",              fs.getCurativeTreatmentCosts());
        model.addAttribute("curativeTreatmentCostsFormatted",     formatRWF(fs.getCurativeTreatmentCosts()));
        model.addAttribute("totalHealthCosts",                    fs.getTotalHealthCosts());
        model.addAttribute("totalHealthCostsFormatted",           formatRWF(fs.getTotalHealthCosts()));
        model.addAttribute("treatmentPaid",                       fs.getTreatmentPaid());
        model.addAttribute("treatmentPaidFormatted",              formatRWF(fs.getTreatmentPaid()));
        model.addAttribute("treatmentUnpaid",                     fs.getTreatmentUnpaid());
        model.addAttribute("treatmentUnpaidFormatted",            formatRWF(fs.getTreatmentUnpaid()));
        model.addAttribute("purchaseCosts",                       fs.getPurchaseCosts());
        model.addAttribute("purchaseCostsFormatted",              formatRWF(fs.getPurchaseCosts()));
        model.addAttribute("otherExpenses",                       fs.getOtherExpenses());
        model.addAttribute("otherExpensesFormatted",              formatRWF(fs.getOtherExpenses()));

        model.addAttribute("totalIncome",              fs.getTotalIncome());
        model.addAttribute("totalIncomeFormatted",     formatRWF(fs.getTotalIncome()));
        model.addAttribute("totalExpenses",            fs.getTotalExpenses());
        model.addAttribute("totalExpensesFormatted",   formatRWF(fs.getTotalExpenses()));
        model.addAttribute("netProfit",                fs.getNetProfit());
        model.addAttribute("netProfitFormatted",       formatRWF(fs.getNetProfit()));
        model.addAttribute("isProfit",                 fs.isProfit());
        model.addAttribute("totalCurrentValue",        fs.getCurrentHerdValue());
        model.addAttribute("totalCurrentValueFormatted", formatRWF(fs.getCurrentHerdValue()));
        model.addAttribute("totalSaleRevenue",         fs.getSalesRevenue());
        model.addAttribute("totalSaleRevenueFormatted",formatRWF(fs.getSalesRevenue()));
        model.addAttribute("totalDeadValue",           fs.getDeathLoss());
        model.addAttribute("totalDeadValueFormatted",  formatRWF(fs.getDeathLoss()));
        model.addAttribute("totalTreatmentCost",       fs.getTotalHealthCosts());
        model.addAttribute("totalTreatmentCostFormatted", formatRWF(fs.getTotalHealthCosts()));
        model.addAttribute("totalSickTreatmentCost",   fs.getCurativeTreatmentCosts());
        model.addAttribute("totalSickTreatmentCostFormatted", formatRWF(fs.getCurativeTreatmentCosts()));

        // ===================== Statistic attributes =====================
        model.addAttribute("totalLivestock",      stats.getTotalLivestock());
        model.addAttribute("totalMothers",        stats.getTotalMothers());
        model.addAttribute("totalBirths",         stats.getTotalBirths());
        model.addAttribute("totalChildren",       stats.getTotalChildren());
        model.addAttribute("avgOffspringPerBirth",stats.getAverageOffspringPerBirth());
        model.addAttribute("activeCount",         stats.getActiveLivestock());
        model.addAttribute("soldCount",           stats.getSoldLivestock());
        model.addAttribute("deadCount",           stats.getDeadLivestock());
        model.addAttribute("sickCount",           stats.getSickLivestock());
        model.addAttribute("pregnantCount",       stats.getPregnantLivestock());
        model.addAttribute("maleCount",           stats.getMaleLivestock());
        model.addAttribute("femaleCount",         stats.getFemaleLivestock());
        model.addAttribute("totalSales",          stats.getTotalSales());
        model.addAttribute("totalDeaths",         stats.getTotalDeaths());
        model.addAttribute("totalAbortions",      stats.getTotalAbortions());
        model.addAttribute("totalTreatments",     stats.getTotalTreatments());
        model.addAttribute("totalSick",           stats.getTotalSick());
        model.addAttribute("averageDeadValue",    stats.getAverageDeadValue());
        model.addAttribute("deathsByCause",       toJson(stats.getDeathsByCause()));
        model.addAttribute("valueLostByCause",    toJson(stats.getValueLostByCause()));
        model.addAttribute("valueByCategory",     toJson(stats.getValueByCategory()));

        Map<String, BigDecimal> otherExpensesByCategory =
                financialCalculationService.calculateOtherExpensesByCategory(null, null);
        model.addAttribute("otherExpensesByCategory", toJson(otherExpensesByCategory));

        Map<String, Long> categoryBreakdown = new LinkedHashMap<>();
        for (Livestock ls : snap.livestock) {
            String cat = catName(ls);
            if (cat != null) categoryBreakdown.merge(cat, 1L, Long::sum);
        }
        model.addAttribute("categoryBreakdown", toJson(categoryBreakdown));

        model.addAttribute("totalUsers",           stats.getTotalUsers());
        model.addAttribute("activeUsers",          stats.getActiveUsers());
        model.addAttribute("inactiveUsers",        stats.getInactiveUsers());
        model.addAttribute("totalRepresentatives", stats.getTotalRepresentatives());
        model.addAttribute("totalBeneficiaries",   stats.getTotalBeneficiaries());

        long vetCount   = safeList(veterinarianRepository.findByIsDeletedFalseAndIsActiveTrue()).size();
        long buyerCount = buyerRepository.countActiveBuyers();
        model.addAttribute("vetCount",   vetCount);
        model.addAttribute("buyerCount", buyerCount);

        List<LivestockBirth> allBirths = snap.births;
        model.addAttribute("recentBirths", toJson(buildBirthMaps(allBirths)));

        Map<String, Long> birthBreakdown = new LinkedHashMap<>();
        for (LivestockBirth b : allBirths) {
            String cat = b.getLivestock() != null ? catName(b.getLivestock()) : null;
            if (cat != null) birthBreakdown.merge(cat, 1L, Long::sum);
        }
        model.addAttribute("birthBreakdown", toJson(birthBreakdown));

        List<LivestockTreatment> treatmentList = snap.treatments;
        model.addAttribute("treatmentList",        buildTreatmentMaps(treatmentList));
        model.addAttribute("treatmentCostByCategory", toJson(stats.getTreatmentCostByCategory()));
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

        List<LivestockSick> sickList = snap.sickList;
        model.addAttribute("sickRecords",     sickList);
        model.addAttribute("sickList",        toJson(buildSickMaps(sickList)));
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
                .filter(s -> s.getSeverityLevel() != null && s.getSeverityLevel().name().equals("LIFE_THREATENING")).count();
        model.addAttribute("lifeThreatCount", lifeThreatCount);

        Map<String, Long> sickBreakdown = new LinkedHashMap<>();
        for (LivestockSick s : sickList) {
            String cat = s.getLivestock() != null ? catName(s.getLivestock()) : null;
            if (cat != null) sickBreakdown.merge(cat, 1L, Long::sum);
        }
        model.addAttribute("sickBreakdown", toJson(sickBreakdown));

        long yearSickCount      = sickHistoryRepository.countSickByYear(year);
        long yearCriticalCount  = sickHistoryRepository.countCriticalByYear(year);
        long yearRecoveredCount = sickHistoryRepository.countRecoveredByYear(year);
        model.addAttribute("yearSickCount",      yearSickCount);
        model.addAttribute("yearCriticalCount",  yearCriticalCount);
        model.addAttribute("yearRecoveredCount", yearRecoveredCount);
        model.addAttribute("recoveryRate", yearSickCount > 0
                ? Math.round((yearRecoveredCount * 100.0) / yearSickCount) : 100L);

        LocalDateTime historyFrom = LocalDateTime.now().minusDays(30);
        LocalDateTime historyTo   = LocalDateTime.now();
        List<LivestockSickHistory> recentHistory = safeList(sickHistoryRepository.findByDateRange(historyFrom, historyTo));
        model.addAttribute("totalHistoryEvents30Days", recentHistory.size());
        model.addAttribute("recentSickHistory", recentHistory.stream().limit(20).collect(Collectors.toList()));

        LocalDateTime monthStartDT = today.withDayOfMonth(1).atStartOfDay();
        model.addAttribute("criticalThisMonthCount",
                safeList(sickHistoryRepository.findCriticalCasesByDateRange(monthStartDT, historyTo)).size());
        model.addAttribute("recoveredThisMonthCount",
                safeList(sickHistoryRepository.findRecoveredCasesByDateRange(monthStartDT, historyTo)).size());

        model.addAttribute("livestockList", snap.livestock.stream()
                .filter(ls -> !Livestock.STATUS_DEAD.equals(ls.getStatus()) && !"SOLD".equals(ls.getStatus()))
                .collect(Collectors.toList()));
        model.addAttribute("sickRecord", new LivestockSick());

        List<LivestockAbortion> abortionList = snap.abortions;
        model.addAttribute("abortionList",   toJson(buildAbortionMaps(abortionList)));
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

        List<LivestockSale> salesList = snap.sales;
        model.addAttribute("salesList",  toJson(buildSaleMaps(salesList)));

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

        List<LivestockDeath> deathsList = snap.deaths;
        model.addAttribute("deathsList",      toJson(buildDeathMaps(deathsList)));

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

        model.addAttribute("mortalityRate",   stats.getMortalityRate());
        model.addAttribute("calvingInterval", stats.getCalvingInterval());
        model.addAttribute("offtakeRate",     stats.getOfftakeRate());
        model.addAttribute("replacementRate", stats.getReplacementRate());
        model.addAttribute("productivity",    stats.getProductivity());

        MonthlyTrends trends = calculateMonthlyTrends(year, snap);
        model.addAttribute("monthlyBirths",       toJson(trends.getMonthlyBirths()));
        model.addAttribute("monthlySales",        toJson(trends.getMonthlySales()));
        model.addAttribute("monthlyDeaths",       toJson(trends.getMonthlyDeaths()));
        model.addAttribute("monthlyTreatments",   toJson(trends.getMonthlyTreatments()));
        model.addAttribute("monthlySick",         toJson(trends.getMonthlySick()));
        model.addAttribute("monthlyAbortions",    toJson(trends.getMonthlyAbortions()));
        model.addAttribute("monthlyPregnancies",  toJson(trends.getMonthlyPregnancies()));
        model.addAttribute("monthlyRevenue",      toJson(trends.getMonthlyRevenue()));
        model.addAttribute("monthlyCosts",        toJson(trends.getMonthlyCosts()));
        model.addAttribute("monthlyDeathValue",   toJson(trends.getMonthlyDeathValue()));

        List<Map<String, String>> alerts = buildAllAlerts(snap);
        model.addAttribute("alerts",         alerts);
        model.addAttribute("alertsJson",     toJson(alerts));
        model.addAttribute("totalAlerts",    alerts.size());

        List<Map<String, String>> criticalAlerts = alerts.stream()
                .filter(a -> "CRITICAL".equals(a.get("severity")))
                .collect(Collectors.toList());
        model.addAttribute("criticalAlertsList", criticalAlerts);
        model.addAttribute("criticalAlertsCount", criticalAlerts.size());
        model.addAttribute("criticalAlerts", criticalAlerts.size());

        model.addAttribute("overduePregnancies", alerts.stream()
                .filter(a -> "OVERDUE_PREGNANCY".equals(a.get("type"))
                        || "PREGNANCY_DUE".equals(a.get("type"))).count());

        List<Map<String, Object>> upcomingEvents = buildUpcomingEvents(snap);
        model.addAttribute("upcomingEvents",     upcomingEvents);
        model.addAttribute("upcomingEventsJson", toJson(upcomingEvents));
        model.addAttribute("upcomingEventsCount",upcomingEvents.size());

        Map<String, Object> funnel = buildReproductionFunnel(snap);
        model.addAttribute("reproductionFunnel",     funnel);
        model.addAttribute("reproductionFunnelJson", toJson(funnel));

        Map<String, Long> herdByLocation = buildHerdByLocation(snap);
        model.addAttribute("herdByLocation",     herdByLocation);
        model.addAttribute("herdByLocationJson", toJson(herdByLocation));

        Map<String, Map<String, Long>> agePyramid = buildAgePyramid(snap);
        model.addAttribute("agePyramid",     agePyramid);
        model.addAttribute("agePyramidJson", toJson(agePyramid));

        Map<String, Object> beneficiarySummary = buildBeneficiarySummary(snap);
        model.addAttribute("beneficiarySummary",     beneficiarySummary);
        model.addAttribute("beneficiarySummaryJson", toJson(beneficiarySummary));

        Map<String, Long> dataQuality = buildDataQualityFlags(snap);
        model.addAttribute("dataQuality",     dataQuality);
        model.addAttribute("dataQualityJson", toJson(dataQuality));

        Map<String, Object> comparison = buildComparison(monthStart, today, snap);
        model.addAttribute("comparison",     comparison);
        model.addAttribute("comparisonJson", toJson(comparison));

        long total = System.currentTimeMillis() - requestStart;
        log.info("[dashboard] adminDashboard total={} ms for user={}", total, email);

        return "admin-dashboard";
    }

    @GetMapping("/user/dashboard")
    public String userDashboard(Authentication authentication, Model model) {
        long start = System.currentTimeMillis();
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
                    .map(l -> parseMoney(l.getCurrentValue()))
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
            BigDecimal userTotalRevenue = userSales.stream()
                    .map(s -> parseMoney(s.getSalePrice()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("totalSales", (long) userSales.size());
            model.addAttribute("userRecentSales", userSales.size());
            model.addAttribute("userTotalRevenue", userTotalRevenue);

            List<LivestockSick> userSick = new ArrayList<>();
            for (Livestock ls : userLivestock) userSick.addAll(sickService.getByLivestock(ls.getId()));
            model.addAttribute("totalSick", (long) userSick.size());
            model.addAttribute("userSickAnimals", userSick.size());

            List<LivestockTreatment> userTreatments = new ArrayList<>();
            for (Livestock ls : userLivestock) userTreatments.addAll(treatmentService.getByLivestock(ls.getId()));
            BigDecimal userTreatmentCost = userTreatments.stream()
                    .map(t -> parseMoney(t.getTreatmentCost()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("userTreatmentCost", userTreatmentCost);
            model.addAttribute("totalTreatments", (long) userTreatments.size());

            List<LivestockDeath> userDeaths = new ArrayList<>();
            for (Livestock ls : userLivestock) userDeaths.addAll(deathService.getByLivestock(ls.getId()));
            model.addAttribute("totalDeaths", (long) userDeaths.size());
            BigDecimal userDeathValue = userDeaths.stream()
                    .filter(d -> d.getLivestock() != null)
                    .map(d -> parseMoney(d.getLivestock().getCurrentValue()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("userDeathValue", userDeathValue);

            model.addAttribute("totalAbortions", 0L);
            model.addAttribute("totalRepresentatives", 0L);
            model.addAttribute("totalBeneficiaries", 0L);

            BigDecimal avgAnimalValue = !userLivestock.isEmpty()
                    ? userTotalValue.divide(BigDecimal.valueOf(userLivestock.size()), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            model.addAttribute("avgAnimalValue", avgAnimalValue);

            BigDecimal newbornValue = userLivestock.stream()
                    .filter(l -> "BIRTH".equalsIgnoreCase(l.getAcquisitionMethod()))
                    .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus())
                            && !Livestock.STATUS_SOLD.equals(l.getStatus()))
                    .map(l -> parseMoney(l.getCurrentValue()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            model.addAttribute("newbornValue", newbornValue);

            BigDecimal netFarmPosition = userTotalRevenue
                    .subtract(userTreatmentCost)
                    .subtract(userDeathValue);
            model.addAttribute("netFarmPosition", netFarmPosition);
            model.addAttribute("netFarmPositive", netFarmPosition.compareTo(BigDecimal.ZERO) >= 0);

            Map<String, Long> userCategoryBreakdown = new LinkedHashMap<>();
            for (Livestock l : userLivestock) {
                if (Livestock.STATUS_DEAD.equals(l.getStatus())
                        || Livestock.STATUS_SOLD.equals(l.getStatus())) continue;
                String cat = catName(l);
                if (cat != null) userCategoryBreakdown.merge(cat, 1L, Long::sum);
            }
            model.addAttribute("categoryBreakdown", toJson(userCategoryBreakdown));

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

            model.addAttribute("avgAnimalValue", BigDecimal.ZERO);
            model.addAttribute("newbornValue", BigDecimal.ZERO);
            model.addAttribute("netFarmPosition", BigDecimal.ZERO);
            model.addAttribute("netFarmPositive", true);
            model.addAttribute("categoryBreakdown", "{}");
        }
        log.info("[dashboard] userDashboard total={} ms for user={}",
                System.currentTimeMillis() - start, email);
        return "user-dashboard";
    }

    // Inner classes (unchanged)
    public static class FinancialSummary {
        private BigDecimal salesRevenue = BigDecimal.ZERO;
        private BigDecimal cashCollected = BigDecimal.ZERO;
        private BigDecimal receivables = BigDecimal.ZERO;
        private BigDecimal cashSpent = BigDecimal.ZERO;
        private BigDecimal netCashFlow = BigDecimal.ZERO;
        private BigDecimal grossMargin = BigDecimal.ZERO;
        private BigDecimal grossMarginPct = BigDecimal.ZERO;
        private int confirmedSaleCount = 0;
        private boolean cashPositive = true;

        private BigDecimal currentHerdValue = BigDecimal.ZERO;
        private BigDecimal averageHerdValue = BigDecimal.ZERO;
        private BigDecimal bornAnimalsValue = BigDecimal.ZERO;
        private BigDecimal deathLoss = BigDecimal.ZERO;
        private long liveAnimalCount = 0;
        private int newbornCount = 0;

        private BigDecimal unpaidTreatments = BigDecimal.ZERO;
        private BigDecimal unpaidSales = BigDecimal.ZERO;
        private long unpaidTreatmentCount = 0;

        private BigDecimal purchaseCosts = BigDecimal.ZERO;
        private BigDecimal preventiveTreatmentCosts = BigDecimal.ZERO;
        private BigDecimal curativeTreatmentCosts = BigDecimal.ZERO;
        private BigDecimal totalHealthCosts = BigDecimal.ZERO;
        private BigDecimal treatmentPaid = BigDecimal.ZERO;
        private BigDecimal treatmentUnpaid = BigDecimal.ZERO;
        private BigDecimal otherExpenses = BigDecimal.ZERO;

        private BigDecimal totalIncome = BigDecimal.ZERO;
        private BigDecimal totalExpenses = BigDecimal.ZERO;
        private BigDecimal netProfit = BigDecimal.ZERO;
        private boolean isProfit = true;

        public BigDecimal getSalesRevenue() { return salesRevenue; }
        public void setSalesRevenue(BigDecimal v) { this.salesRevenue = nz(v); }
        public BigDecimal getCashCollected() { return cashCollected; }
        public void setCashCollected(BigDecimal v) { this.cashCollected = nz(v); }
        public BigDecimal getReceivables() { return receivables; }
        public void setReceivables(BigDecimal v) { this.receivables = nz(v); }
        public BigDecimal getCashSpent() { return cashSpent; }
        public void setCashSpent(BigDecimal v) { this.cashSpent = nz(v); }
        public BigDecimal getNetCashFlow() { return netCashFlow; }
        public void setNetCashFlow(BigDecimal v) { this.netCashFlow = nz(v); }
        public BigDecimal getGrossMargin() { return grossMargin; }
        public void setGrossMargin(BigDecimal v) { this.grossMargin = nz(v); }
        public BigDecimal getGrossMarginPct() { return grossMarginPct; }
        public void setGrossMarginPct(BigDecimal v) { this.grossMarginPct = nz(v); }
        public int getConfirmedSaleCount() { return confirmedSaleCount; }
        public void setConfirmedSaleCount(int v) { this.confirmedSaleCount = v; }
        public boolean isCashPositive() { return cashPositive; }
        public void setCashPositive(boolean v) { this.cashPositive = v; }

        public BigDecimal getCurrentHerdValue() { return currentHerdValue; }
        public void setCurrentHerdValue(BigDecimal v) { this.currentHerdValue = nz(v); }
        public BigDecimal getAverageHerdValue() { return averageHerdValue; }
        public void setAverageHerdValue(BigDecimal v) { this.averageHerdValue = nz(v); }
        public BigDecimal getBornAnimalsValue() { return bornAnimalsValue; }
        public void setBornAnimalsValue(BigDecimal v) { this.bornAnimalsValue = nz(v); }
        public BigDecimal getDeathLoss() { return deathLoss; }
        public void setDeathLoss(BigDecimal v) { this.deathLoss = nz(v); }
        public long getLiveAnimalCount() { return liveAnimalCount; }
        public void setLiveAnimalCount(long v) { this.liveAnimalCount = v; }
        public int getNewbornCount() { return newbornCount; }
        public void setNewbornCount(int v) { this.newbornCount = v; }

        public BigDecimal getUnpaidTreatments() { return unpaidTreatments; }
        public void setUnpaidTreatments(BigDecimal v) { this.unpaidTreatments = nz(v); }
        public BigDecimal getUnpaidSales() { return unpaidSales; }
        public void setUnpaidSales(BigDecimal v) { this.unpaidSales = nz(v); }
        public long getUnpaidTreatmentCount() { return unpaidTreatmentCount; }
        public void setUnpaidTreatmentCount(long v) { this.unpaidTreatmentCount = v; }

        public BigDecimal getPurchaseCosts() { return purchaseCosts; }
        public void setPurchaseCosts(BigDecimal v) { this.purchaseCosts = nz(v); }
        public BigDecimal getPreventiveTreatmentCosts() { return preventiveTreatmentCosts; }
        public void setPreventiveTreatmentCosts(BigDecimal v) { this.preventiveTreatmentCosts = nz(v); }
        public BigDecimal getCurativeTreatmentCosts() { return curativeTreatmentCosts; }
        public void setCurativeTreatmentCosts(BigDecimal v) { this.curativeTreatmentCosts = nz(v); }
        public BigDecimal getTotalHealthCosts() { return totalHealthCosts; }
        public void setTotalHealthCosts(BigDecimal v) { this.totalHealthCosts = nz(v); }
        public BigDecimal getTreatmentPaid() { return treatmentPaid; }
        public void setTreatmentPaid(BigDecimal v) { this.treatmentPaid = nz(v); }
        public BigDecimal getTreatmentUnpaid() { return treatmentUnpaid; }
        public void setTreatmentUnpaid(BigDecimal v) { this.treatmentUnpaid = nz(v); }
        public BigDecimal getOtherExpenses() { return otherExpenses; }
        public void setOtherExpenses(BigDecimal v) { this.otherExpenses = nz(v); }

        public BigDecimal getTotalIncome() { return totalIncome; }
        public void setTotalIncome(BigDecimal v) { this.totalIncome = nz(v); }
        public BigDecimal getTotalExpenses() { return totalExpenses; }
        public void setTotalExpenses(BigDecimal v) { this.totalExpenses = nz(v); }
        public BigDecimal getNetProfit() { return netProfit; }
        public void setNetProfit(BigDecimal v) { this.netProfit = nz(v); }
        public boolean isProfit() { return isProfit; }
        public void setProfit(boolean v) { isProfit = v; }

        private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
    }

    public static class DashboardStatistics {
        private Long totalLivestock, activeLivestock, pregnantLivestock, sickLivestock, deadLivestock, soldLivestock;
        private Long maleLivestock, femaleLivestock;
        private Long totalBirths, totalMothers, totalChildren;
        private double averageOffspringPerBirth;
        private Long totalSick, currentlySick, criticalCount, recoveringCount, recoveredCount;
        private Long totalTreatments, unpaidTreatmentCount, ongoingTreatmentCount;
        private BigDecimal totalTreatmentCost;
        private BigDecimal outstandingPayments = BigDecimal.ZERO;
        private Long totalSales;
        private BigDecimal totalSaleRevenue;
        private Long totalDeaths, totalAbortions;
        private Long totalUsers, activeUsers, inactiveUsers;
        private Long totalBeneficiaries, totalRepresentatives;
        private BigDecimal totalCurrentValue;
        private BigDecimal averageCurrentValue;
        private Map<String, BigDecimal> valueByCategory;
        private BigDecimal totalDeadValue;
        private BigDecimal averageDeadValue;
        private Map<String, Long> deathsByCause;
        private Map<String, BigDecimal> valueLostByCause;
        private BigDecimal curativeTreatmentCost;
        private BigDecimal preventiveTreatmentCost;
        private Map<String, BigDecimal> treatmentCostByCategory;
        private BigDecimal totalSickTreatmentCost;
        private BigDecimal bornAnimalsValue;
        private BigDecimal purchaseCosts;
        private BigDecimal totalIncome, totalExpenses, otherExpenses, netProfit, netPosition;
        private boolean isProfit;
        private double mortalityRate, calvingInterval, offtakeRate, replacementRate, productivity;

        public Long getTotalLivestock() { return totalLivestock; } public void setTotalLivestock(Long v) { this.totalLivestock = v; }
        public Long getActiveLivestock() { return activeLivestock; } public void setActiveLivestock(Long v) { this.activeLivestock = v; }
        public Long getPregnantLivestock() { return pregnantLivestock; } public void setPregnantLivestock(Long v) { this.pregnantLivestock = v; }
        public Long getSickLivestock() { return sickLivestock; } public void setSickLivestock(Long v) { this.sickLivestock = v; }
        public Long getDeadLivestock() { return deadLivestock; } public void setDeadLivestock(Long v) { this.deadLivestock = v; }
        public Long getSoldLivestock() { return soldLivestock; } public void setSoldLivestock(Long v) { this.soldLivestock = v; }
        public Long getMaleLivestock() { return maleLivestock; } public void setMaleLivestock(Long v) { this.maleLivestock = v; }
        public Long getFemaleLivestock() { return femaleLivestock; } public void setFemaleLivestock(Long v) { this.femaleLivestock = v; }
        public Long getTotalBirths() { return totalBirths; } public void setTotalBirths(Long v) { this.totalBirths = v; }
        public Long getTotalMothers() { return totalMothers; } public void setTotalMothers(Long v) { this.totalMothers = v; }
        public Long getTotalChildren() { return totalChildren; } public void setTotalChildren(Long v) { this.totalChildren = v; }
        public double getAverageOffspringPerBirth() { return averageOffspringPerBirth; } public void setAverageOffspringPerBirth(double v) { this.averageOffspringPerBirth = v; }
        public Long getTotalSick() { return totalSick; } public void setTotalSick(Long v) { this.totalSick = v; }
        public Long getCurrentlySick() { return currentlySick; } public void setCurrentlySick(Long v) { this.currentlySick = v; }
        public Long getCriticalCount() { return criticalCount; } public void setCriticalCount(Long v) { this.criticalCount = v; }
        public Long getRecoveringCount() { return recoveringCount; } public void setRecoveringCount(Long v) { this.recoveringCount = v; }
        public Long getRecoveredCount() { return recoveredCount; } public void setRecoveredCount(Long v) { this.recoveredCount = v; }
        public Long getTotalTreatments() { return totalTreatments; } public void setTotalTreatments(Long v) { this.totalTreatments = v; }
        public BigDecimal getTotalTreatmentCost() { return totalTreatmentCost; } public void setTotalTreatmentCost(BigDecimal v) { this.totalTreatmentCost = v; }
        public BigDecimal getOutstandingPayments() { return outstandingPayments; } public void setOutstandingPayments(BigDecimal v) { this.outstandingPayments = v; }
        public Long getUnpaidTreatmentCount() { return unpaidTreatmentCount; } public void setUnpaidTreatmentCount(Long v) { this.unpaidTreatmentCount = v; }
        public Long getOngoingTreatmentCount() { return ongoingTreatmentCount; } public void setOngoingTreatmentCount(Long v) { this.ongoingTreatmentCount = v; }
        public Long getTotalSales() { return totalSales; } public void setTotalSales(Long v) { this.totalSales = v; }
        public BigDecimal getTotalSaleRevenue() { return totalSaleRevenue; } public void setTotalSaleRevenue(BigDecimal v) { this.totalSaleRevenue = v; }
        public Long getTotalDeaths() { return totalDeaths; } public void setTotalDeaths(Long v) { this.totalDeaths = v; }
        public Long getTotalAbortions() { return totalAbortions; } public void setTotalAbortions(Long v) { this.totalAbortions = v; }
        public Long getTotalUsers() { return totalUsers; } public void setTotalUsers(Long v) { this.totalUsers = v; }
        public Long getActiveUsers() { return activeUsers; } public void setActiveUsers(Long v) { this.activeUsers = v; }
        public Long getInactiveUsers() { return inactiveUsers; } public void setInactiveUsers(Long v) { this.inactiveUsers = v; }
        public Long getTotalBeneficiaries() { return totalBeneficiaries; } public void setTotalBeneficiaries(Long v) { this.totalBeneficiaries = v; }
        public Long getTotalRepresentatives() { return totalRepresentatives; } public void setTotalRepresentatives(Long v) { this.totalRepresentatives = v; }
        public BigDecimal getTotalCurrentValue() { return totalCurrentValue; } public void setTotalCurrentValue(BigDecimal v) { this.totalCurrentValue = v; }
        public BigDecimal getAverageCurrentValue() { return averageCurrentValue; } public void setAverageCurrentValue(BigDecimal v) { this.averageCurrentValue = v; }
        public Map<String, BigDecimal> getValueByCategory() { return valueByCategory; } public void setValueByCategory(Map<String, BigDecimal> v) { this.valueByCategory = v; }
        public BigDecimal getTotalDeadValue() { return totalDeadValue; } public void setTotalDeadValue(BigDecimal v) { this.totalDeadValue = v; }
        public BigDecimal getAverageDeadValue() { return averageDeadValue; } public void setAverageDeadValue(BigDecimal v) { this.averageDeadValue = v; }
        public Map<String, Long> getDeathsByCause() { return deathsByCause; } public void setDeathsByCause(Map<String, Long> v) { this.deathsByCause = v; }
        public Map<String, BigDecimal> getValueLostByCause() { return valueLostByCause; } public void setValueLostByCause(Map<String, BigDecimal> v) { this.valueLostByCause = v; }
        public BigDecimal getCurativeTreatmentCost() { return curativeTreatmentCost; } public void setCurativeTreatmentCost(BigDecimal v) { this.curativeTreatmentCost = v; }
        public BigDecimal getPreventiveTreatmentCost() { return preventiveTreatmentCost; } public void setPreventiveTreatmentCost(BigDecimal v) { this.preventiveTreatmentCost = v; }
        public Map<String, BigDecimal> getTreatmentCostByCategory() { return treatmentCostByCategory; } public void setTreatmentCostByCategory(Map<String, BigDecimal> v) { this.treatmentCostByCategory = v; }
        public BigDecimal getTotalSickTreatmentCost() { return totalSickTreatmentCost; } public void setTotalSickTreatmentCost(BigDecimal v) { this.totalSickTreatmentCost = v; }
        public BigDecimal getBornAnimalsValue() { return bornAnimalsValue; } public void setBornAnimalsValue(BigDecimal v) { this.bornAnimalsValue = v; }
        public BigDecimal getPurchaseCosts() { return purchaseCosts; } public void setPurchaseCosts(BigDecimal v) { this.purchaseCosts = v; }
        public BigDecimal getTotalIncome() { return totalIncome; } public void setTotalIncome(BigDecimal v) { this.totalIncome = v; }
        public BigDecimal getTotalExpenses() { return totalExpenses; } public void setTotalExpenses(BigDecimal v) { this.totalExpenses = v; }
        public BigDecimal getOtherExpenses() { return otherExpenses; } public void setOtherExpenses(BigDecimal v) { this.otherExpenses = v; }
        public BigDecimal getNetProfit() { return netProfit; } public void setNetProfit(BigDecimal v) { this.netProfit = v; }
        public BigDecimal getNetPosition() { return netPosition; } public void setNetPosition(BigDecimal v) { this.netPosition = v; }
        public boolean isProfit() { return isProfit; } public void setProfit(boolean v) { isProfit = v; }
        public double getMortalityRate() { return mortalityRate; } public void setMortalityRate(double v) { this.mortalityRate = v; }
        public double getCalvingInterval() { return calvingInterval; } public void setCalvingInterval(double v) { this.calvingInterval = v; }
        public double getOfftakeRate() { return offtakeRate; } public void setOfftakeRate(double v) { this.offtakeRate = v; }
        public double getReplacementRate() { return replacementRate; } public void setReplacementRate(double v) { this.replacementRate = v; }
        public double getProductivity() { return productivity; } public void setProductivity(double v) { this.productivity = v; }
    }

    public static class MonthlyTrends {
        private int[] monthlyBirths = new int[12], monthlySales = new int[12], monthlyDeaths = new int[12],
                monthlyTreatments = new int[12], monthlySick = new int[12], monthlyAbortions = new int[12],
                monthlyPregnancies = new int[12];
        private double[] monthlyRevenue = new double[12], monthlyCosts = new double[12],
                monthlyDeathValue = new double[12];
        public int[] getMonthlyBirths() { return monthlyBirths; } public void setMonthlyBirths(int[] v) { this.monthlyBirths = v; }
        public int[] getMonthlySales() { return monthlySales; } public void setMonthlySales(int[] v) { this.monthlySales = v; }
        public int[] getMonthlyDeaths() { return monthlyDeaths; } public void setMonthlyDeaths(int[] v) { this.monthlyDeaths = v; }
        public int[] getMonthlyTreatments() { return monthlyTreatments; } public void setMonthlyTreatments(int[] v) { this.monthlyTreatments = v; }
        public int[] getMonthlySick() { return monthlySick; } public void setMonthlySick(int[] v) { this.monthlySick = v; }
        public int[] getMonthlyAbortions() { return monthlyAbortions; } public void setMonthlyAbortions(int[] v) { this.monthlyAbortions = v; }
        public int[] getMonthlyPregnancies() { return monthlyPregnancies; } public void setMonthlyPregnancies(int[] v) { this.monthlyPregnancies = v; }
        public double[] getMonthlyRevenue() { return monthlyRevenue; } public void setMonthlyRevenue(double[] v) { this.monthlyRevenue = v; }
        public double[] getMonthlyCosts() { return monthlyCosts; } public void setMonthlyCosts(double[] v) { this.monthlyCosts = v; }
        public double[] getMonthlyDeathValue() { return monthlyDeathValue; } public void setMonthlyDeathValue(double[] v) { this.monthlyDeathValue = v; }
    }
}