package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.CategoryMovementRow;
import rw.animalproduct.animal.production.dto.HerdMovementReportDto;
import rw.animalproduct.animal.production.dto.HerdStructureRow;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.entity.LivestockDeath;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds the Herd Movement / Reconciliation report.
 *
 * FAO (2018) methodology — Opening + In − Out = Closing, verified for
 * both count and value. Age-weighted TLU factors per category sex/age class.
 *
 * Deliberately does NOT call FinancialCalculationService — the movement
 * report is a physical-stock document, not a P&L.
 *
 * Recursion safety note:
 *   The public entry point is {@link #generateReport(LocalDate, LocalDate, List)}.
 *   It delegates to a private {@link #generateReportInternal(LocalDate, LocalDate, List, boolean)}
 *   which takes an "includePrevious" flag. The previous-period summary is
 *   built by calling generateReportInternal(..., false) — so the recursion
 *   terminates after exactly one level and cannot loop forever.
 */
@Service
@RequiredArgsConstructor
public class HerdMovementService {

    private final LivestockRepository livestockRepository;
    private final LivestockSaleService saleService;
    private final LivestockDeathService deathService;

    /** Age-weighted TLU factors — FAO/Jahnke reference values. */
    private static final Map<String, Double> ADULT_TLU_FACTORS = new HashMap<>();
    private static final Map<String, Double> YOUNG_TLU_FACTORS = new HashMap<>();
    static {
        ADULT_TLU_FACTORS.put("COW", 0.70); YOUNG_TLU_FACTORS.put("COW", 0.40);
        ADULT_TLU_FACTORS.put("CTL", 0.70); YOUNG_TLU_FACTORS.put("CTL", 0.40);
        ADULT_TLU_FACTORS.put("GOA", 0.10); YOUNG_TLU_FACTORS.put("GOA", 0.05);
        ADULT_TLU_FACTORS.put("SHP", 0.10); YOUNG_TLU_FACTORS.put("SHP", 0.05);
        ADULT_TLU_FACTORS.put("PIG", 0.20); YOUNG_TLU_FACTORS.put("PIG", 0.10);
        ADULT_TLU_FACTORS.put("CHK", 0.01); YOUNG_TLU_FACTORS.put("CHK", 0.005);
        ADULT_TLU_FACTORS.put("POU", 0.01); YOUNG_TLU_FACTORS.put("POU", 0.005);
    }
    private static final double DEFAULT_TLU_FACTOR = 0.10;
    private static final int DEFAULT_ADULT_THRESHOLD_MONTHS = 8;

    /* ================================================================
     *  PUBLIC ENTRY POINT
     * ================================================================ */

    /**
     * @param categoryIds null or empty = all categories; otherwise the
     *                    report is scoped to animals whose category is in
     *                    the set. Pass List.of(singleId) for a single
     *                    category.
     */
    public HerdMovementReportDto generateReport(LocalDate from,
                                                LocalDate to,
                                                List<UUID> categoryIds) {
        return generateReportInternal(from, to, categoryIds, /* includePrevious = */ true);
    }

    /* ================================================================
     *  INTERNAL ENGINE — recursion-safe
     * ================================================================ */
    private HerdMovementReportDto generateReportInternal(LocalDate from,
                                                         LocalDate to,
                                                         List<UUID> categoryIds,
                                                         boolean includePrevious) {

        Set<UUID> catFilter = (categoryIds == null || categoryIds.isEmpty())
                ? null : new HashSet<>(categoryIds);

        List<Livestock> allLivestock = livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> catFilter == null || matchesCategory(l.getLivestockCategory(), catFilter))
                .collect(Collectors.toList());

        List<LivestockSale> allSales = saleService.getAll().stream()
                .filter(s -> s.getLivestock() != null)
                .filter(s -> catFilter == null || matchesCategory(s.getLivestock().getLivestockCategory(), catFilter))
                .collect(Collectors.toList());

        List<LivestockDeath> allDeaths = deathService.getAll().stream()
                .filter(d -> d.getLivestock() != null)
                .filter(d -> catFilter == null || matchesCategory(d.getLivestock().getLivestockCategory(), catFilter))
                .collect(Collectors.toList());

        Map<UUID, LivestockSale> saleByLivestockId = new HashMap<>();
        for (LivestockSale s : allSales) saleByLivestockId.put(s.getLivestock().getId(), s);
        Map<UUID, LivestockDeath> deathByLivestockId = new HashMap<>();
        for (LivestockDeath d : allDeaths) deathByLivestockId.put(d.getLivestock().getId(), d);

        HerdMovementReportDto report = new HerdMovementReportDto();
        report.setFromDate(from);
        report.setToDate(to);

        Map<String, CategoryMovementRow> categoryMap = new LinkedHashMap<>();
        Map<String, LivestockCategory> categoryEntityByName = new HashMap<>();
        Map<String, HerdStructureRow> structureMap = new LinkedHashMap<>();

        for (Livestock l : allLivestock) {
            LocalDate entryDate = resolveEntryDate(l);

            LivestockSale sale = saleByLivestockId.get(l.getId());
            LivestockDeath death = deathByLivestockId.get(l.getId());

            LocalDate saleDate = sale != null ? sale.getSaleDate() : null;
            LocalDate deathDate = death != null ? death.getDeathDate() : null;
            LocalDate removalDate = saleDate != null ? saleDate : deathDate;

            String categoryName = l.getLivestockCategory() != null
                    ? l.getLivestockCategory().getName() : "Uncategorized";
            categoryEntityByName.putIfAbsent(categoryName, l.getLivestockCategory());

            CategoryMovementRow row = categoryMap.computeIfAbsent(categoryName, k -> {
                CategoryMovementRow r = new CategoryMovementRow();
                r.setCategoryName(k);
                return r;
            });

            BigDecimal currentValue = nz(l.getCurrentValue());

            // OPENING
            boolean existedBefore = entryDate.isBefore(from);
            boolean presentAtStart = removalDate == null || !removalDate.isBefore(from);
            if (existedBefore && presentAtStart) {
                report.setOpeningCount(report.getOpeningCount() + 1);
                report.setOpeningValue(report.getOpeningValue().add(currentValue));
                row.setOpeningCount(row.getOpeningCount() + 1);
                row.setOpeningValue(row.getOpeningValue().add(currentValue));
            }

            // ADDITIONS
            boolean enteredInPeriod = !entryDate.isBefore(from) && !entryDate.isAfter(to);
            if (enteredInPeriod) {
                String method = l.getAcquisitionMethod() != null ? l.getAcquisitionMethod() : "PURCHASE";
                switch (method) {
                    case "BIRTH":
                        report.setBornCount(report.getBornCount() + 1);
                        report.setBornValue(report.getBornValue().add(currentValue));
                        row.setBornCount(row.getBornCount() + 1);
                        row.setBornValue(row.getBornValue().add(currentValue));
                        break;
                    case "DONATION":
                        report.setDonatedCount(report.getDonatedCount() + 1);
                        report.setDonatedValue(report.getDonatedValue().add(currentValue));
                        row.setDonatedCount(row.getDonatedCount() + 1);
                        row.setDonatedValue(row.getDonatedValue().add(currentValue));
                        break;
                    case "TRANSFER":
                        report.setTransferredInCount(report.getTransferredInCount() + 1);
                        report.setTransferredInValue(report.getTransferredInValue().add(currentValue));
                        row.setTransferredInCount(row.getTransferredInCount() + 1);
                        row.setTransferredInValue(row.getTransferredInValue().add(currentValue));
                        break;
                    default: // PURCHASE
                        report.setPurchasedCount(report.getPurchasedCount() + 1);
                        report.setPurchasedValue(report.getPurchasedValue().add(currentValue));
                        row.setPurchasedCount(row.getPurchasedCount() + 1);
                        row.setPurchasedValue(row.getPurchasedValue().add(currentValue));
                        break;
                }
            }

            // SALES
            if (saleDate != null && !saleDate.isBefore(from) && !saleDate.isAfter(to)) {
                BigDecimal price = nz(sale.getSalePrice());
                report.setSoldCount(report.getSoldCount() + 1);
                report.setSoldRevenue(report.getSoldRevenue().add(price));
                row.setSoldCount(row.getSoldCount() + 1);
                row.setSoldRevenue(row.getSoldRevenue().add(price));
            }

            // DEATHS
            if (deathDate != null && !deathDate.isBefore(from) && !deathDate.isAfter(to)) {
                report.setDeadCount(report.getDeadCount() + 1);
                report.setDeadValueLoss(report.getDeadValueLoss().add(currentValue));
                row.setDeadCount(row.getDeadCount() + 1);
                row.setDeadValueLoss(row.getDeadValueLoss().add(currentValue));
            }

            // CLOSING + herd structure
            boolean closingPresent = !entryDate.isAfter(to)
                    && (removalDate == null || removalDate.isAfter(to));
            if (closingPresent) {
                report.setClosingCount(report.getClosingCount() + 1);
                report.setClosingValue(report.getClosingValue().add(currentValue));
                row.setClosingCount(row.getClosingCount() + 1);
                row.setClosingValue(row.getClosingValue().add(currentValue));

                String sexAge = classifySexAge(l, to);
                String key = categoryName + "|" + sexAge;
                HerdStructureRow sr = structureMap.computeIfAbsent(key, k -> {
                    HerdStructureRow r = new HerdStructureRow();
                    r.setCategoryName(categoryName);
                    r.setSexAgeClass(sexAge);
                    r.setSexAgeLabel(labelForSexAgeClass(sexAge));
                    return r;
                });
                sr.setCount(sr.getCount() + 1);
                sr.setValue(sr.getValue().add(currentValue));
            }
        }

        // Age-weighted TLU per category
        BigDecimal totalTLU = BigDecimal.ZERO;
        for (CategoryMovementRow row : categoryMap.values()) {
            LivestockCategory cat = categoryEntityByName.get(row.getCategoryName());
            String code = cat != null && cat.getCode() != null ? cat.getCode().toUpperCase() : null;
            double adultFactor = code != null
                    ? ADULT_TLU_FACTORS.getOrDefault(code, DEFAULT_TLU_FACTOR)
                    : DEFAULT_TLU_FACTOR;
            double youngFactor = code != null
                    ? YOUNG_TLU_FACTORS.getOrDefault(code, DEFAULT_TLU_FACTOR * 0.5)
                    : DEFAULT_TLU_FACTOR * 0.5;

            long adults = 0, young = 0;
            for (HerdStructureRow sr : structureMap.values()) {
                if (!sr.getCategoryName().equals(row.getCategoryName())) continue;
                if (sr.getSexAgeClass().startsWith("ADULT_")) adults += sr.getCount();
                else if (sr.getSexAgeClass().startsWith("YOUNG_")) young += sr.getCount();
            }
            if (adults + young == 0) adults = row.getClosingCount();

            double tlu = adults * adultFactor + young * youngFactor;
            row.setTluFactor(adultFactor);
            row.setClosingTLU(BigDecimal.valueOf(tlu).setScale(2, RoundingMode.HALF_UP));
            totalTLU = totalTLU.add(row.getClosingTLU());
        }
        report.setTotalClosingTLU(totalTLU);

        // Herd-structure percentages
        for (HerdStructureRow sr : structureMap.values()) {
            CategoryMovementRow catRow = categoryMap.get(sr.getCategoryName());
            int catClosing = catRow != null ? catRow.getClosingCount() : 0;
            double pct = catClosing > 0 ? (sr.getCount() * 100.0 / catClosing) : 0.0;
            sr.setPercentOfCategory(Math.round(pct * 10) / 10.0);
        }
        List<HerdStructureRow> structureRows = new ArrayList<>(structureMap.values());
        structureRows.sort(Comparator.comparing(HerdStructureRow::getCategoryName)
                .thenComparing(HerdStructureRow::getSexAgeClass));
        report.setHerdStructure(structureRows);

        List<CategoryMovementRow> rows = new ArrayList<>(categoryMap.values());
        rows.sort(Comparator.comparing(CategoryMovementRow::getCategoryName));
        report.setByCategory(rows);

        // Previous-period comparison — ONLY when called from the public entry point
        if (includePrevious) {
            report.setPrevious(buildPreviousPeriodSummary(from, to, catFilter));
        }

        // Data-quality flags
        report.setDataQualityFlags(buildDataQualityFlags(allLivestock, categoryMap));

        // Benchmark classification
        classifyBenchmarks(report);

        return report;
    }

    /* ================================================================
     *  PREVIOUS PERIOD — one level deep, no recursion
     * ================================================================ */
    private HerdMovementReportDto.PreviousPeriodSummary buildPreviousPeriodSummary(
            LocalDate from, LocalDate to, Set<UUID> catFilter) {

        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevFrom = from.minusDays(days);
        LocalDate prevTo = from.minusDays(1);

        List<UUID> prevIds = (catFilter == null) ? null : new ArrayList<>(catFilter);

        // KEY: call the INTERNAL method with includePrevious=false.
        HerdMovementReportDto prev = generateReportInternal(prevFrom, prevTo, prevIds, false);

        HerdMovementReportDto.PreviousPeriodSummary s = new HerdMovementReportDto.PreviousPeriodSummary();
        s.setFromDate(prevFrom);
        s.setToDate(prevTo);
        s.setOpeningCount(prev.getOpeningCount());
        s.setClosingCount(prev.getClosingCount());
        s.setBornCount(prev.getBornCount());
        s.setSoldCount(prev.getSoldCount());
        s.setDeadCount(prev.getDeadCount());
        s.setClosingValue(prev.getClosingValue());
        s.setSoldRevenue(prev.getSoldRevenue());
        s.setDeadValueLoss(prev.getDeadValueLoss());
        s.setMortalityRatePercent(prev.getMortalityRatePercent());
        s.setOfftakeRatePercent(prev.getOfftakeRatePercent());
        return s;
    }

    /* ================================================================
     *  DATA-QUALITY FLAGS
     * ================================================================ */
    private List<HerdMovementReportDto.DataQualityFlag> buildDataQualityFlags(
            List<Livestock> allLivestock, Map<String, CategoryMovementRow> categoryMap) {

        List<HerdMovementReportDto.DataQualityFlag> flags = new ArrayList<>();

        long missingDates = allLivestock.stream()
                .filter(l -> l.getDateReceived() == null
                        && l.getBirthDate() == null
                        && l.getCreatedAt() == null)
                .count();
        if (missingDates > 0) {
            HerdMovementReportDto.DataQualityFlag f = new HerdMovementReportDto.DataQualityFlag();
            f.setSeverity("WARN");
            f.setMessage(missingDates + " animal(s) have no usable entry date; defaulted to today.");
            f.setContext("Check livestock.date_received / birth_date");
            flags.add(f);
        }

        long missingValue = allLivestock.stream()
                .filter(l -> l.getCurrentValue() == null)
                .count();
        if (missingValue > 0) {
            HerdMovementReportDto.DataQualityFlag f = new HerdMovementReportDto.DataQualityFlag();
            f.setSeverity("WARN");
            f.setMessage(missingValue + " animal(s) have no recorded current value; counted as RWF 0.");
            f.setContext("Check livestock.current_value");
            flags.add(f);
        }

        for (CategoryMovementRow row : categoryMap.values()) {
            if (!row.isReconciled()) {
                HerdMovementReportDto.DataQualityFlag f = new HerdMovementReportDto.DataQualityFlag();
                f.setSeverity("ERROR");
                f.setMessage("Category '" + row.getCategoryName() + "' does not reconcile: expected "
                        + row.getExpectedClosingCount() + ", found " + row.getClosingCount() + ".");
                f.setContext("Gap = " + (row.getClosingCount() - row.getExpectedClosingCount()));
                flags.add(f);
            }
        }

        long usedFallback = allLivestock.stream()
                .filter(l -> l.getDateReceived() == null
                        && l.getBirthDate() == null
                        && l.getCreatedAt() != null)
                .count();
        if (usedFallback > 0) {
            HerdMovementReportDto.DataQualityFlag f = new HerdMovementReportDto.DataQualityFlag();
            f.setSeverity("WARN");
            f.setMessage(usedFallback + " animal(s) used record-creation date as a proxy entry date.");
            f.setContext("Consider filling in date_received");
            flags.add(f);
        }

        return flags;
    }

    /* ================================================================
     *  FAO BENCHMARK BANDS
     * ================================================================ */
    private void classifyBenchmarks(HerdMovementReportDto report) {
        double m = report.getMortalityRatePercent();
        double o = report.getOfftakeRatePercent();
        double g = report.getHerdGrowthRatePercent();

        report.setMortalityBenchmark(m <= 5 ? "good" : m <= 15 ? "warn" : "bad");
        report.setOfftakeBenchmark(o <= 20 ? "good" : o <= 40 ? "warn" : "bad");
        report.setGrowthBenchmark(g >= 0 ? "good" : g >= -10 ? "warn" : "bad");
    }

    /* ================================================================
     *  HELPERS
     * ================================================================ */
    private boolean matchesCategory(LivestockCategory category, Set<UUID> ids) {
        return category != null && ids.contains(category.getId());
    }

    private String classifySexAge(Livestock l, LocalDate referenceDate) {
        String gender = l.getGender();
        if (gender == null) return "UNKNOWN";
        String g = gender.toUpperCase();
        if (!g.equals("MALE") && !g.equals("FEMALE")) return "UNKNOWN";

        Integer minBreedingAgeMonths = (l.getLivestockCategory() != null)
                ? l.getLivestockCategory().getMinBreedingAgeMonths() : null;
        int threshold = minBreedingAgeMonths != null
                ? minBreedingAgeMonths : DEFAULT_ADULT_THRESHOLD_MONTHS;

        boolean isAdult;
        if (l.getBirthDate() != null) {
            long ageMonths = ChronoUnit.MONTHS.between(l.getBirthDate(), referenceDate);
            isAdult = ageMonths >= threshold;
        } else {
            isAdult = "PURCHASE".equals(l.getAcquisitionMethod());
        }
        return (isAdult ? "ADULT_" : "YOUNG_") + g;
    }

    private String labelForSexAgeClass(String code) {
        switch (code) {
            case "ADULT_FEMALE": return "Adult Female (Breeding Stock)";
            case "ADULT_MALE":   return "Adult Male (Breeding Stock)";
            case "YOUNG_FEMALE": return "Young Female (Replacement Stock)";
            case "YOUNG_MALE":   return "Young Male (Immature)";
            default:             return "Unknown / Unclassified";
        }
    }

    private LocalDate resolveEntryDate(Livestock l) {
        if (l.getDateReceived() != null) return l.getDateReceived();
        if (l.getBirthDate() != null) return l.getBirthDate();
        if (l.getCreatedAt() != null) return l.getCreatedAt().toLocalDate();
        return LocalDate.now();
    }

    private static BigDecimal nz(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}