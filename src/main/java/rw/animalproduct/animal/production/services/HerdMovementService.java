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

@Service
@RequiredArgsConstructor
public class HerdMovementService {

    private final LivestockRepository livestockRepository;
    private final LivestockSaleService saleService;
    private final LivestockDeathService deathService;

    /**
     * FAO Tropical Livestock Unit (TLU) conversion factors per species code.
     * Standard reference values (FAO/Jahnke). Extend this map as you add
     * more livestock categories (e.g. cattle, poultry, sheep).
     */
    private static final Map<String, Double> TLU_FACTORS = new HashMap<>();
    static {
        TLU_FACTORS.put("GOA", 0.10); // Goats
        TLU_FACTORS.put("SHP", 0.10); // Sheep
        TLU_FACTORS.put("PIG", 0.20); // Pigs
        TLU_FACTORS.put("COW", 0.70); // Cattle
        TLU_FACTORS.put("CTL", 0.70); // Cattle (alt code)
        TLU_FACTORS.put("CHK", 0.01); // Chickens / poultry
        TLU_FACTORS.put("POU", 0.01); // Poultry (alt code)
    }
    private static final double DEFAULT_TLU_FACTOR = 0.10;

    /** Fallback breeding-age threshold (months) if a category has no minBreedingAgeMonths set. */
    private static final int DEFAULT_ADULT_THRESHOLD_MONTHS = 8;

    public HerdMovementReportDto generateReport(LocalDate from, LocalDate to) {
        return generateReport(from, to, null);
    }

    /**
     * @param categoryId if null, includes every livestock type. If provided,
     *                    the whole report is scoped to that single type.
     */
    public HerdMovementReportDto generateReport(LocalDate from, LocalDate to, UUID categoryId) {

        HerdMovementReportDto report = new HerdMovementReportDto();
        report.setFromDate(from);
        report.setToDate(to);

        // LivestockRepository.findAll() already excludes soft-deleted records
        List<Livestock> allLivestock = livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> categoryId == null || matchesCategory(l.getLivestockCategory(), categoryId))
                .collect(Collectors.toList());

        List<LivestockSale> allSales = saleService.getAll().stream()
                .filter(s -> s.getLivestock() != null)
                .filter(s -> categoryId == null || matchesCategory(s.getLivestock().getLivestockCategory(), categoryId))
                .collect(Collectors.toList());

        List<LivestockDeath> allDeaths = deathService.getAll().stream()
                .filter(d -> d.getLivestock() != null)
                .filter(d -> categoryId == null || matchesCategory(d.getLivestock().getLivestockCategory(), categoryId))
                .collect(Collectors.toList());

        Map<UUID, LivestockSale> saleByLivestockId = new HashMap<>();
        for (LivestockSale s : allSales) {
            saleByLivestockId.put(s.getLivestock().getId(), s);
        }
        Map<UUID, LivestockDeath> deathByLivestockId = new HashMap<>();
        for (LivestockDeath d : allDeaths) {
            deathByLivestockId.put(d.getLivestock().getId(), d);
        }

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

            // ── OPENING: existed before period start, not yet removed by then ──
            boolean existedBeforePeriod = entryDate.isBefore(from);
            boolean stillPresentAtStart = removalDate == null || !removalDate.isBefore(from);
            if (existedBeforePeriod && stillPresentAtStart) {
                report.setOpeningCount(report.getOpeningCount() + 1);
                report.setOpeningValue(report.getOpeningValue().add(currentValue));
                row.setOpeningCount(row.getOpeningCount() + 1);
                row.setOpeningValue(row.getOpeningValue().add(currentValue));
            }

            // ── ADDITIONS DURING PERIOD ──
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
                    default: // PURCHASE and anything else unmapped
                        report.setPurchasedCount(report.getPurchasedCount() + 1);
                        report.setPurchasedValue(report.getPurchasedValue().add(currentValue));
                        row.setPurchasedCount(row.getPurchasedCount() + 1);
                        row.setPurchasedValue(row.getPurchasedValue().add(currentValue));
                        break;
                }
            }

            // ── SALES DURING PERIOD ──
            if (saleDate != null && !saleDate.isBefore(from) && !saleDate.isAfter(to)) {
                BigDecimal price = nz(sale.getSalePrice());
                report.setSoldCount(report.getSoldCount() + 1);
                report.setSoldRevenue(report.getSoldRevenue().add(price));
                row.setSoldCount(row.getSoldCount() + 1);
                row.setSoldRevenue(row.getSoldRevenue().add(price));
            }

            // ── DEATHS DURING PERIOD ──
            if (deathDate != null && !deathDate.isBefore(from) && !deathDate.isAfter(to)) {
                report.setDeadCount(report.getDeadCount() + 1);
                report.setDeadValueLoss(report.getDeadValueLoss().add(currentValue));
                row.setDeadCount(row.getDeadCount() + 1);
                row.setDeadValueLoss(row.getDeadValueLoss().add(currentValue));
            }

            // ── CLOSING: present at end of period ──
            boolean closingPresent = !entryDate.isAfter(to) && (removalDate == null || removalDate.isAfter(to));
            if (closingPresent) {
                report.setClosingCount(report.getClosingCount() + 1);
                report.setClosingValue(report.getClosingValue().add(currentValue));
                row.setClosingCount(row.getClosingCount() + 1);
                row.setClosingValue(row.getClosingValue().add(currentValue));

                // ── FAO herd structure classification (closing stock only) ──
                String sexAgeClass = classifySexAge(l, to);
                String structureKey = categoryName + "|" + sexAgeClass;
                HerdStructureRow structRow = structureMap.computeIfAbsent(structureKey, k -> {
                    HerdStructureRow r = new HerdStructureRow();
                    r.setCategoryName(categoryName);
                    r.setSexAgeClass(sexAgeClass);
                    r.setSexAgeLabel(labelForSexAgeClass(sexAgeClass));
                    return r;
                });
                structRow.setCount(structRow.getCount() + 1);
                structRow.setValue(structRow.getValue().add(currentValue));
            }
        }

        // ── Finalize TLU per category + report total ──
        BigDecimal totalTLU = BigDecimal.ZERO;
        for (CategoryMovementRow row : categoryMap.values()) {
            LivestockCategory cat = categoryEntityByName.get(row.getCategoryName());
            double factor = tluFactorFor(cat);
            row.setTluFactor(factor);
            BigDecimal tlu = BigDecimal.valueOf(row.getClosingCount() * factor).setScale(2, RoundingMode.HALF_UP);
            row.setClosingTLU(tlu);
            totalTLU = totalTLU.add(tlu);
        }
        report.setTotalClosingTLU(totalTLU);

        // ── Finalize herd structure percentages ──
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

        return report;
    }

    private boolean matchesCategory(LivestockCategory category, UUID categoryId) {
        return category != null && categoryId.equals(category.getId());
    }

    private double tluFactorFor(LivestockCategory category) {
        if (category == null || category.getCode() == null) return DEFAULT_TLU_FACTOR;
        return TLU_FACTORS.getOrDefault(category.getCode().toUpperCase(), DEFAULT_TLU_FACTOR);
    }

    /**
     * FAO-style sex/age classification for herd structure reporting.
     * "Adult" = age at or past the category's minimum breeding age
     * (falls back to a fixed 8-month threshold if the category has none set).
     * When birthDate is missing, we reuse the same convention already used
     * in LivestockRepository.findEligibleMothers(): a purchased animal with
     * no recorded birth date is presumed mature; anything else is treated
     * as unclassifiable-by-age and grouped as "Young" to avoid overstating
     * breeding stock.
     */
    private String classifySexAge(Livestock l, LocalDate referenceDate) {
        String gender = l.getGender();
        if (gender == null) return "UNKNOWN";

        String genderUpper = gender.toUpperCase();
        if (!genderUpper.equals("MALE") && !genderUpper.equals("FEMALE")) {
            return "UNKNOWN";
        }

        Integer minBreedingAgeMonths = (l.getLivestockCategory() != null)
                ? l.getLivestockCategory().getMinBreedingAgeMonths() : null;
        int thresholdMonths = minBreedingAgeMonths != null ? minBreedingAgeMonths : DEFAULT_ADULT_THRESHOLD_MONTHS;

        boolean isAdult;
        if (l.getBirthDate() != null) {
            long ageMonths = ChronoUnit.MONTHS.between(l.getBirthDate(), referenceDate);
            isAdult = ageMonths >= thresholdMonths;
        } else {
            isAdult = "PURCHASE".equals(l.getAcquisitionMethod());
        }

        return (isAdult ? "ADULT_" : "YOUNG_") + genderUpper;
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

    /**
     * Best-effort "date this animal entered the herd". Falls back through
     * dateReceived -> birthDate -> createdAt so every animal always has a
     * usable entry date, even with the null-date data quality issues we've
     * seen on BIRTH-acquired and draft records.
     */
    private LocalDate resolveEntryDate(Livestock l) {
        if (l.getDateReceived() != null) return l.getDateReceived();
        if (l.getBirthDate() != null) return l.getBirthDate();
        if (l.getCreatedAt() != null) return l.getCreatedAt().toLocalDate();
        return LocalDate.now();
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}