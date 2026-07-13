package rw.animalproduct.animal.production.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.entity.LivestockTreatment;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Financial calculations for the livestock operation.
 *
 * SINGLE SOURCE OF TRUTH
 * -----------------------
 * This is the ONLY place financial totals are calculated. The Dashboard
 * (DashboardController) and the Financial Summary page both call INTO this
 * service instead of re-implementing the math, so the two pages can never
 * silently drift apart again.
 *
 * DATE RANGE SEMANTICS
 * -----------------------
 * fromDate / toDate are now OPTIONAL (nullable):
 *   - Pass real dates  -> figures are restricted to that period (Financial Summary page use case).
 *   - Pass null, null  -> figures are ALL-TIME / unfiltered (Dashboard use case).
 *   - Pass one null     -> that side of the range is unbounded (e.g. "everything up to today").
 *
 * KEY RULES:
 * 1. Treatment costs are separated: PREVENTIVE treatments are "Treatment Costs",
 *    CURATIVE treatments are "Sick Care Costs" (and NOT counted in treatment costs).
 * 2. Current Herd Value ONLY counts ACTIVE and PREGNANT animals (not SOLD, not DEAD).
 *    It is always a point-in-time snapshot and is never date-filtered.
 * 3. Born Animals Value is a separate metric showing value of BIRTH animals.
 * 4. Purchase Costs only count animals with acquisition_method = 'PURCHASE'.
 * 5. Death Loss only counts deaths within the date range (based on LivestockDeath records).
 * 6. Total Income = Sales Revenue + Current Herd Value (Born Animals Value is shown
 *    separately but NOT added to total income because it's already in Current Herd
 *    Value if the animal is alive).
 * 7. Total Expenses = Preventive Treatments + Curative Treatments + Purchase Costs + Death Loss.
 */
@Service
public class FinancialCalculationService {

    @Autowired
    private LivestockSaleService saleService;

    @Autowired
    private LivestockDeathService deathService;

    @Autowired
    private LivestockTreatmentService treatmentService;

    @Autowired
    private LivestockRepository livestockRepository;

    // ========================================================================
    // DATE-RANGE HELPER (null-safe)
    // ========================================================================

    /**
     * Returns true if `date` falls within [fromDate, toDate], where either
     * bound may be null to mean "unbounded" on that side. A null `date` never
     * matches.
     */
    private boolean inRange(LocalDate date, LocalDate fromDate, LocalDate toDate) {
        if (date == null) return false;
        if (fromDate != null && date.isBefore(fromDate)) return false;
        if (toDate != null && date.isAfter(toDate)) return false;
        return true;
    }

    // ========================================================================
    // INCOME
    // ========================================================================

    /**
     * Revenue from confirmed sales within the period.
     * Pass (null, null) for all-time revenue.
     */
    public BigDecimal calculateSalesRevenue(LocalDate fromDate, LocalDate toDate) {
        return saleService.getAll().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .filter(s -> inRange(s.getSaleDate(), fromDate, toDate))
                .filter(s -> s.getSalePrice() != null)
                .map(LivestockSale::getSalePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Value added to the herd through births in the period.
     * This is a separate metric showing the value of animals born during the period.
     * NOTE: These animals are also included in currentHerdValue if they are still alive.
     * Pass (null, null) for all-time.
     */
    public BigDecimal calculateBornAnimalsValue(LocalDate fromDate, LocalDate toDate) {
        return livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> "BIRTH".equalsIgnoreCase(l.getAcquisitionMethod()))
                .filter(l -> inRange(l.getBirthDate(), fromDate, toDate))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Present-day value of the live herd.
     * Only includes ACTIVE and PREGNANT animals (alive and on the farm).
     * Excludes SOLD, DEAD, and draft records.
     * This is ALWAYS a point-in-time snapshot, never date-range filtered,
     * regardless of what the Dashboard or Financial Summary page requests.
     */
    public BigDecimal calculateCurrentHerdValue() {
        return livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()) ||
                        Livestock.STATUS_PREGNANT.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * TOTAL INCOME = Sales Revenue + Current Herd Value
     * Note: Born Animals Value is shown separately for transparency but NOT added
     * to total income because it's already included in Current Herd Value if the
     * born animal is still alive.
     * Pass (null, null) for all-time income.
     */
    public BigDecimal calculateTotalIncome(LocalDate fromDate, LocalDate toDate) {
        BigDecimal sales = calculateSalesRevenue(fromDate, toDate);
        BigDecimal herdValue = calculateCurrentHerdValue();
        return sales.add(herdValue);
    }

    // ========================================================================
    // EXPENSES
    // ========================================================================

    /**
     * Cost of animals actually purchased in the period (PURCHASE method).
     * Pass (null, null) for all-time purchase costs.
     */
    public BigDecimal calculatePurchaseCosts(LocalDate fromDate, LocalDate toDate) {
        return livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> "PURCHASE".equalsIgnoreCase(l.getAcquisitionMethod()))
                .filter(l -> inRange(l.getDateReceived(), fromDate, toDate))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Loss recognized from animals that died in the period.
     * Uses the livestock's current_value at the time of death.
     * Pass (null, null) for all-time death loss.
     */
    public BigDecimal calculateDeathLoss(LocalDate fromDate, LocalDate toDate) {
        return deathService.getAll().stream()
                .filter(d -> !Boolean.TRUE.equals(d.getIsDeleted()))
                .filter(d -> inRange(d.getDeathDate(), fromDate, toDate))
                .filter(d -> d.getLivestock() != null && d.getLivestock().getCurrentValue() != null)
                .map(d -> d.getLivestock().getCurrentValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * PREVENTIVE treatment costs (vaccinations, routine care, etc.)
     * These are considered general operating expenses.
     * Pass (null, null) for all-time preventive costs.
     */
    public BigDecimal calculatePreventiveTreatmentCosts(LocalDate fromDate, LocalDate toDate) {
        return treatmentService.getAll().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                .filter(t -> inRange(t.getTreatmentDate(), fromDate, toDate))
                .filter(t -> t.getTreatmentCost() != null)
                .filter(t -> t.getTreatmentType() == LivestockTreatment.TreatmentCategory.PREVENTIVE)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * CURATIVE treatment costs (treating sick animals).
     * These are considered sick care costs, separate from preventive treatments.
     * Pass (null, null) for all-time curative costs.
     */
    public BigDecimal calculateCurativeTreatmentCosts(LocalDate fromDate, LocalDate toDate) {
        return treatmentService.getAll().stream()
                .filter(t -> !Boolean.TRUE.equals(t.getIsDeleted()))
                .filter(t -> inRange(t.getTreatmentDate(), fromDate, toDate))
                .filter(t -> t.getTreatmentCost() != null)
                .filter(t -> t.getTreatmentType() == LivestockTreatment.TreatmentCategory.CURATIVE)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * TOTAL EXPENSES = Preventive Treatments + Curative Treatments + Purchase Costs + Death Loss
     * Pass (null, null) for all-time expenses.
     */
    public BigDecimal calculateTotalExpenses(LocalDate fromDate, LocalDate toDate) {
        BigDecimal preventive = calculatePreventiveTreatmentCosts(fromDate, toDate);
        BigDecimal curative = calculateCurativeTreatmentCosts(fromDate, toDate);
        BigDecimal purchases = calculatePurchaseCosts(fromDate, toDate);
        BigDecimal deaths = calculateDeathLoss(fromDate, toDate);
        return preventive.add(curative).add(purchases).add(deaths);
    }

    // ========================================================================
    // PROFIT / MARGIN
    // ========================================================================

    /**
     * Pass (null, null) for all-time net profit.
     */
    public BigDecimal calculateNetProfit(LocalDate fromDate, LocalDate toDate) {
        return calculateTotalIncome(fromDate, toDate).subtract(calculateTotalExpenses(fromDate, toDate));
    }

    /**
     * Net profit margin as a percentage of total income.
     * Pass (null, null) for all-time margin.
     */
    public BigDecimal calculateProfitMargin(LocalDate fromDate, LocalDate toDate) {
        BigDecimal totalIncome = calculateTotalIncome(fromDate, toDate);
        if (totalIncome.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal netProfit = calculateNetProfit(fromDate, toDate);
        return netProfit
                .divide(totalIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    public String getProfitStatus(LocalDate fromDate, LocalDate toDate) {
        return calculateNetProfit(fromDate, toDate).compareTo(BigDecimal.ZERO) >= 0 ? "PROFIT" : "LOSS";
    }

    // ========================================================================
    // BUNDLED SUMMARY
    // ========================================================================

    /**
     * Computes every figure the financial-summary.html template needs in one pass.
     *
     * IMPORTANT: This is the SINGLE SOURCE OF TRUTH for the financial summary.
     * The dashboard uses the SAME methods (with fromDate=null, toDate=null for
     * all-time totals) so the two pages can never disagree.
     *
     * Pass (null, null) to get all-time figures instead of a specific period.
     */
    public Map<String, Object> generateFinancialSummary(LocalDate fromDate, LocalDate toDate) {
        // INCOME
        BigDecimal salesRevenue = calculateSalesRevenue(fromDate, toDate);
        BigDecimal currentLivestockValue = calculateCurrentHerdValue();
        BigDecimal bornAnimalsValue = calculateBornAnimalsValue(fromDate, toDate);
        // Total Income = Sales Revenue + Current Herd Value (Born Animals Value is already in Current Herd Value)
        BigDecimal totalIncome = salesRevenue.add(currentLivestockValue);

        // EXPENSES
        BigDecimal preventiveTreatmentCosts = calculatePreventiveTreatmentCosts(fromDate, toDate);
        BigDecimal curativeTreatmentCosts = calculateCurativeTreatmentCosts(fromDate, toDate);
        BigDecimal purchaseCosts = calculatePurchaseCosts(fromDate, toDate);
        BigDecimal deathLoss = calculateDeathLoss(fromDate, toDate);
        BigDecimal totalExpenses = preventiveTreatmentCosts.add(curativeTreatmentCosts)
                .add(purchaseCosts).add(deathLoss);

        // PROFIT
        BigDecimal netProfit = totalIncome.subtract(totalExpenses);
        BigDecimal profitMargin = totalIncome.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : netProfit.divide(totalIncome, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
        String profitStatus = netProfit.compareTo(BigDecimal.ZERO) >= 0 ? "PROFIT" : "LOSS";

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fromDate", fromDate);
        summary.put("toDate", toDate);

        // INCOME SECTION
        summary.put("salesRevenue", salesRevenue);
        summary.put("currentLivestockValue", currentLivestockValue);
        summary.put("bornAnimalsValue", bornAnimalsValue);
        summary.put("totalIncome", totalIncome);

        // EXPENSE SECTION
        summary.put("preventiveTreatmentCosts", preventiveTreatmentCosts);
        summary.put("curativeTreatmentCosts", curativeTreatmentCosts);
        summary.put("sickCareCosts", curativeTreatmentCosts); // Alias for backward compatibility
        summary.put("treatmentCosts", preventiveTreatmentCosts); // Alias for backward compatibility
        summary.put("purchaseCosts", purchaseCosts);
        summary.put("deathLoss", deathLoss);
        summary.put("totalExpenses", totalExpenses);

        // PROFIT SECTION
        summary.put("netProfit", netProfit);
        summary.put("profitMargin", profitMargin);
        summary.put("profitStatus", profitStatus);

        return summary;
    }
}