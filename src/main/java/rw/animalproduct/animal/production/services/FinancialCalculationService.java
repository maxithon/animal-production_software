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
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Autowired
    private rw.animalproduct.animal.production.repository.OtherExpenseRepository otherExpenseRepository;

    // ========================================================================
    // DATE-RANGE HELPER (null-safe)
    // ========================================================================

    private boolean inRange(LocalDate date, LocalDate fromDate, LocalDate toDate) {
        if (date == null) return false;
        if (fromDate != null && date.isBefore(fromDate)) return false;
        if (toDate != null && date.isAfter(toDate)) return false;
        return true;
    }

    private List<Livestock> getActiveLivestock() {
        return livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .collect(Collectors.toList());
    }

    // ========================================================================
    // HERD VALUE CALCULATIONS
    // ========================================================================

    public BigDecimal calculateCurrentHerdValue() {
        return getActiveLivestock().stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()) ||
                        Livestock.STATUS_PREGNANT.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateSickHerdValue() {
        return getActiveLivestock().stream()
                .filter(l -> Livestock.STATUS_SICK.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateTotalHerdValue() {
        return getActiveLivestock().stream()
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateSoldHerdValue() {
        return getActiveLivestock().stream()
                .filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateDeadHerdValue() {
        return getActiveLivestock().stream()
                .filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateBornAnimalsValue(LocalDate fromDate, LocalDate toDate) {
        return livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> "BIRTH".equalsIgnoreCase(l.getAcquisitionMethod()))
                .filter(l -> inRange(l.getBirthDate(), fromDate, toDate))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateDonationValue(LocalDate fromDate, LocalDate toDate) {
        return livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> "DONATION".equalsIgnoreCase(l.getAcquisitionMethod()))
                .filter(l -> inRange(l.getDateReceived(), fromDate, toDate))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ========================================================================
    // HERD COUNT METRICS
    // ========================================================================

    public long countActiveHerd() {
        return getActiveLivestock().stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()) ||
                        Livestock.STATUS_PREGNANT.equals(l.getStatus()))
                .count();
    }

    public long countSickHerd() {
        return getActiveLivestock().stream()
                .filter(l -> Livestock.STATUS_SICK.equals(l.getStatus()))
                .count();
    }

    public long countSoldHerd() {
        return getActiveLivestock().stream()
                .filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus()))
                .count();
    }

    public long countDeadHerd() {
        return getActiveLivestock().stream()
                .filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus()))
                .count();
    }

    public long countTotalHerd() {
        return getActiveLivestock().size();
    }

    public BigDecimal calculateAverageHerdValue() {
        long count = countActiveHerd();
        if (count == 0) return BigDecimal.ZERO;
        return calculateCurrentHerdValue().divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateAverageSickValue() {
        long count = countSickHerd();
        if (count == 0) return BigDecimal.ZERO;
        return calculateSickHerdValue().divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    // ========================================================================
    // INCOME / REVENUE
    // ========================================================================

    /**
     * Revenue from confirmed livestock sales within the period.
     * NOTE: LivestockSale still uses isDeleted (unchanged).
     */
    public BigDecimal calculateSalesRevenue(LocalDate fromDate, LocalDate toDate) {
        return saleService.getAll().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .filter(s -> inRange(s.getSaleDate(), fromDate, toDate))
                .filter(s -> s.getSalePrice() != null)
                .map(LivestockSale::getSalePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateProductionSales(LocalDate fromDate, LocalDate toDate) {
        return BigDecimal.ZERO;
    }

    public BigDecimal calculateOtherIncome(LocalDate fromDate, LocalDate toDate) {
        return BigDecimal.ZERO;
    }

    public BigDecimal calculateTotalRevenue(LocalDate fromDate, LocalDate toDate) {
        BigDecimal livestockSales = calculateSalesRevenue(fromDate, toDate);
        BigDecimal productionSales = calculateProductionSales(fromDate, toDate);
        BigDecimal otherIncome = calculateOtherIncome(fromDate, toDate);
        return livestockSales.add(productionSales).add(otherIncome);
    }

    @Deprecated
    public BigDecimal calculateTotalIncome(LocalDate fromDate, LocalDate toDate) {
        return calculateTotalRevenue(fromDate, toDate);
    }

    // ========================================================================
    // EXPENSES
    // ========================================================================

    public BigDecimal calculatePurchaseCosts(LocalDate fromDate, LocalDate toDate) {
        return livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> "PURCHASE".equalsIgnoreCase(l.getAcquisitionMethod()))
                .filter(l -> inRange(l.getDateReceived(), fromDate, toDate))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** LivestockDeath no longer has isDeleted (managed via audit_log). */
    public BigDecimal calculateDeathLoss(LocalDate fromDate, LocalDate toDate) {
        return deathService.getAll().stream()
                .filter(d -> inRange(d.getDeathDate(), fromDate, toDate))
                .filter(d -> d.getLivestock() != null && d.getLivestock().getCurrentValue() != null)
                .map(d -> d.getLivestock().getCurrentValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * LivestockTreatment no longer has isDeleted (managed via audit_log).
     * The isDeleted guard has been removed — getAll() already returns the
     * authoritative set.
     */
    public BigDecimal calculatePreventiveTreatmentCosts(LocalDate fromDate, LocalDate toDate) {
        return treatmentService.getAll().stream()
                .filter(t -> inRange(t.getTreatmentDate(), fromDate, toDate))
                .filter(t -> t.getTreatmentCost() != null)
                .filter(t -> t.getTreatmentType() == LivestockTreatment.TreatmentCategory.PREVENTIVE)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** LivestockTreatment no longer has isDeleted (managed via audit_log). */
    public BigDecimal calculateCurativeTreatmentCosts(LocalDate fromDate, LocalDate toDate) {
        return treatmentService.getAll().stream()
                .filter(t -> inRange(t.getTreatmentDate(), fromDate, toDate))
                .filter(t -> t.getTreatmentCost() != null)
                .filter(t -> t.getTreatmentType() == LivestockTreatment.TreatmentCategory.CURATIVE)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<String, BigDecimal> calculateOtherExpensesByCategory(LocalDate fromDate, LocalDate toDate) {
        return otherExpenseRepository.findAll().stream()
                .filter(e -> !Boolean.TRUE.equals(e.getIsDeleted()))
                .filter(e -> inRange(e.getExpenseDate(), fromDate, toDate))
                .filter(e -> e.getAmount() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory() != null ? e.getCategory() : "OTHER",
                        LinkedHashMap::new,
                        Collectors.reducing(BigDecimal.ZERO,
                                rw.animalproduct.animal.production.entity.OtherExpense::getAmount,
                                BigDecimal::add)
                ));
    }

    public BigDecimal calculateOtherExpenses(LocalDate fromDate, LocalDate toDate) {
        return calculateOtherExpensesByCategory(fromDate, toDate).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal extractCategory(Map<String, BigDecimal> byCategory, String categoryKey) {
        for (Map.Entry<String, BigDecimal> entry : byCategory.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(categoryKey)) {
                return entry.getValue() != null ? entry.getValue() : BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    public Map<String, BigDecimal> calculateExpenseBreakdown(LocalDate fromDate, LocalDate toDate) {
        Map<String, BigDecimal> byCategory = calculateOtherExpensesByCategory(fromDate, toDate);

        BigDecimal feed = extractCategory(byCategory, "FEED");
        BigDecimal breeding = extractCategory(byCategory, "BREEDING");
        BigDecimal labor = extractCategory(byCategory, "LABOR");
        BigDecimal transport = extractCategory(byCategory, "TRANSPORT");

        BigDecimal explicitlyBucketed = feed.add(breeding).add(labor).add(transport);
        BigDecimal otherExpensesRemainder = calculateOtherExpenses(fromDate, toDate).subtract(explicitlyBucketed);
        if (otherExpensesRemainder.compareTo(BigDecimal.ZERO) < 0) {
            otherExpensesRemainder = BigDecimal.ZERO;
        }

        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        breakdown.put("Animal Purchases", calculatePurchaseCosts(fromDate, toDate));
        breakdown.put("Feed", feed);
        breakdown.put("Treatment", calculatePreventiveTreatmentCosts(fromDate, toDate));
        breakdown.put("Veterinary/Health", calculateCurativeTreatmentCosts(fromDate, toDate));
        breakdown.put("Breeding", breeding);
        breakdown.put("Labor", labor);
        breakdown.put("Transport", transport);
        breakdown.put("Other Expenses", otherExpensesRemainder);
        breakdown.put("Death/Mortality Losses", calculateDeathLoss(fromDate, toDate));
        return breakdown;
    }

    public BigDecimal calculateTotalExpenses(LocalDate fromDate, LocalDate toDate) {
        BigDecimal preventive = calculatePreventiveTreatmentCosts(fromDate, toDate);
        BigDecimal curative = calculateCurativeTreatmentCosts(fromDate, toDate);
        BigDecimal purchases = calculatePurchaseCosts(fromDate, toDate);
        BigDecimal deaths = calculateDeathLoss(fromDate, toDate);
        BigDecimal other = calculateOtherExpenses(fromDate, toDate);
        return preventive.add(curative).add(purchases).add(deaths).add(other);
    }

    // ========================================================================
    // LIVESTOCK ASSET POSITION
    // ========================================================================

    public Map<String, Object> calculateLivestockAssetPosition(LocalDate fromDate, LocalDate toDate) {
        BigDecimal closingValue = calculateCurrentHerdValue();
        BigDecimal purchases = calculatePurchaseCosts(fromDate, toDate);
        BigDecimal bornAnimalsValue = calculateBornAnimalsValue(fromDate, toDate);
        BigDecimal deathLosses = calculateDeathLoss(fromDate, toDate);
        BigDecimal salesValue = calculateSoldHerdValue();
        BigDecimal valueAdjustments = BigDecimal.ZERO;

        BigDecimal openingValue = closingValue
                .subtract(purchases)
                .subtract(bornAnimalsValue)
                .add(deathLosses)
                .add(salesValue)
                .subtract(valueAdjustments);
        if (openingValue.compareTo(BigDecimal.ZERO) < 0) {
            openingValue = BigDecimal.ZERO;
        }

        Map<String, Object> position = new LinkedHashMap<>();
        position.put("openingValue", openingValue);
        position.put("purchases", purchases);
        position.put("bornAnimalsValue", bornAnimalsValue);
        position.put("deathLosses", deathLosses);
        position.put("salesValue", salesValue);
        position.put("valueAdjustments", valueAdjustments);
        position.put("closingValue", closingValue);
        return position;
    }

    // ========================================================================
    // CASH FLOW
    // ========================================================================

    public Map<String, Object> calculateCashFlow(LocalDate fromDate, LocalDate toDate) {
        BigDecimal cashInflows = calculateTotalRevenue(fromDate, toDate);

        BigDecimal cashOutflows = calculatePurchaseCosts(fromDate, toDate)
                .add(calculatePreventiveTreatmentCosts(fromDate, toDate))
                .add(calculateCurativeTreatmentCosts(fromDate, toDate))
                .add(calculateOtherExpenses(fromDate, toDate));

        BigDecimal netCashFlow = cashInflows.subtract(cashOutflows);

        Map<String, Object> cashFlow = new LinkedHashMap<>();
        cashFlow.put("cashInflows", cashInflows);
        cashFlow.put("cashOutflows", cashOutflows);
        cashFlow.put("netCashFlow", netCashFlow);
        return cashFlow;
    }

    // ========================================================================
    // PROFIT / MARGIN
    // ========================================================================

    public BigDecimal calculateNetProfit(LocalDate fromDate, LocalDate toDate) {
        return calculateTotalRevenue(fromDate, toDate).subtract(calculateTotalExpenses(fromDate, toDate));
    }

    public BigDecimal calculateProfitMargin(LocalDate fromDate, LocalDate toDate) {
        BigDecimal totalRevenue = calculateTotalRevenue(fromDate, toDate);
        if (totalRevenue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal netProfit = calculateNetProfit(fromDate, toDate);
        return netProfit
                .divide(totalRevenue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    public String getProfitStatus(LocalDate fromDate, LocalDate toDate) {
        return calculateNetProfit(fromDate, toDate).compareTo(BigDecimal.ZERO) >= 0 ? "PROFIT" : "LOSS";
    }

    public Map<String, Object> calculateProfitabilityAnalysis(LocalDate fromDate, LocalDate toDate) {
        BigDecimal revenue = calculateTotalRevenue(fromDate, toDate);
        BigDecimal totalExpenses = calculateTotalExpenses(fromDate, toDate);

        BigDecimal directCosts = calculatePurchaseCosts(fromDate, toDate)
                .add(calculateDeathLoss(fromDate, toDate));
        BigDecimal grossProfit = revenue.subtract(directCosts);

        BigDecimal operatingExpenses = totalExpenses.subtract(directCosts);
        BigDecimal operatingProfit = grossProfit.subtract(operatingExpenses);

        BigDecimal netProfit = calculateNetProfit(fromDate, toDate);
        BigDecimal profitMargin = calculateProfitMargin(fromDate, toDate);

        BigDecimal costToRevenueRatio = revenue.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : totalExpenses.divide(revenue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("grossProfit", grossProfit);
        analysis.put("operatingProfit", operatingProfit);
        analysis.put("netProfit", netProfit);
        analysis.put("profitMargin", profitMargin);
        analysis.put("costToRevenueRatio", costToRevenueRatio);
        return analysis;
    }

    // ========================================================================
    // PERIOD COMPARISON
    // ========================================================================

    public LocalDate[] getPreviousPeriod(LocalDate fromDate, LocalDate toDate) {
        long periodLengthDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        LocalDate previousTo = fromDate.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(periodLengthDays - 1);
        return new LocalDate[] { previousFrom, previousTo };
    }

    public Map<String, Object> buildComparison(BigDecimal currentValue, BigDecimal previousValue) {
        BigDecimal current = currentValue != null ? currentValue : BigDecimal.ZERO;
        BigDecimal previous = previousValue != null ? previousValue : BigDecimal.ZERO;
        BigDecimal change = current.subtract(previous);

        BigDecimal changePercent;
        if (previous.compareTo(BigDecimal.ZERO) != 0) {
            changePercent = change.divide(previous.abs(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
        } else if (current.compareTo(BigDecimal.ZERO) != 0) {
            changePercent = BigDecimal.valueOf(100).setScale(1, RoundingMode.HALF_UP);
        } else {
            changePercent = BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }

        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("current", current);
        comparison.put("previous", previous);
        comparison.put("change", change);
        comparison.put("changePercent", changePercent);
        comparison.put("isIncrease", change.compareTo(BigDecimal.ZERO) >= 0);
        return comparison;
    }

    // ========================================================================
    // HERD METRICS (FAO Standards)
    // ========================================================================

    /** LivestockDeath no longer has isDeleted (managed via audit_log). */
    public double calculateMortalityRate(LocalDate fromDate, LocalDate toDate) {
        long deaths = deathService.getAll().stream()
                .filter(d -> inRange(d.getDeathDate(), fromDate, toDate))
                .count();

        long active = countActiveHerd();
        long total = active + deaths;

        if (total == 0) return 0.0;
        return (deaths * 100.0) / total;
    }

    public double calculateOfftakeRate(LocalDate fromDate, LocalDate toDate) {
        long sales = saleService.getAll().stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
                .filter(s -> inRange(s.getSaleDate(), fromDate, toDate))
                .count();

        long active = countActiveHerd();
        long sold = countSoldHerd();
        long dead = countDeadHerd();
        long openingHerd = active + sold + dead;

        if (openingHerd == 0) return 0.0;
        return (sales * 100.0) / openingHerd;
    }

    public double calculateReplacementRate(long totalBirths, long totalHerd) {
        if (totalHerd == 0) return 0.0;
        return (totalBirths * 100.0) / totalHerd;
    }

    // ========================================================================
    // BUNDLED SUMMARY
    // ========================================================================

    public Map<String, Object> generateFinancialSummary(LocalDate fromDate, LocalDate toDate) {
        BigDecimal salesRevenue = calculateSalesRevenue(fromDate, toDate);
        BigDecimal productionSales = calculateProductionSales(fromDate, toDate);
        BigDecimal otherIncome = calculateOtherIncome(fromDate, toDate);
        BigDecimal totalRevenue = calculateTotalRevenue(fromDate, toDate);
        BigDecimal currentLivestockValue = calculateCurrentHerdValue();
        BigDecimal bornAnimalsValue = calculateBornAnimalsValue(fromDate, toDate);

        BigDecimal preventiveTreatmentCosts = calculatePreventiveTreatmentCosts(fromDate, toDate);
        BigDecimal curativeTreatmentCosts = calculateCurativeTreatmentCosts(fromDate, toDate);
        BigDecimal purchaseCosts = calculatePurchaseCosts(fromDate, toDate);
        BigDecimal deathLoss = calculateDeathLoss(fromDate, toDate);
        BigDecimal otherExpenses = calculateOtherExpenses(fromDate, toDate);
        BigDecimal totalExpenses = calculateTotalExpenses(fromDate, toDate);
        Map<String, BigDecimal> expenseBreakdown = calculateExpenseBreakdown(fromDate, toDate);

        BigDecimal netProfit = calculateNetProfit(fromDate, toDate);
        BigDecimal profitMargin = calculateProfitMargin(fromDate, toDate);
        String profitStatus = getProfitStatus(fromDate, toDate);
        Map<String, Object> profitability = calculateProfitabilityAnalysis(fromDate, toDate);

        Map<String, Object> assetPosition = calculateLivestockAssetPosition(fromDate, toDate);
        Map<String, Object> cashFlow = calculateCashFlow(fromDate, toDate);

        long activeCount = countActiveHerd();
        long sickCount = countSickHerd();
        long soldCount = countSoldHerd();
        long deadCount = countDeadHerd();
        long totalCount = countTotalHerd();

        BigDecimal sickHerdValue = calculateSickHerdValue();
        BigDecimal soldHerdValue = calculateSoldHerdValue();
        BigDecimal deadHerdValue = calculateDeadHerdValue();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fromDate", fromDate);
        summary.put("toDate", toDate);

        summary.put("activeCount", activeCount);
        summary.put("sickCount", sickCount);
        summary.put("soldCount", soldCount);
        summary.put("deadCount", deadCount);
        summary.put("totalCount", totalCount);

        summary.put("currentHerdValue", currentLivestockValue);
        summary.put("sickHerdValue", sickHerdValue);
        summary.put("soldHerdValue", soldHerdValue);
        summary.put("deadHerdValue", deadHerdValue);
        summary.put("totalHerdValue", calculateTotalHerdValue());

        summary.put("salesRevenue", salesRevenue);
        summary.put("productionSales", productionSales);
        summary.put("otherIncome", otherIncome);
        summary.put("bornAnimalsValue", bornAnimalsValue);
        summary.put("totalRevenue", totalRevenue);
        summary.put("totalIncome", totalRevenue);

        summary.put("preventiveTreatmentCosts", preventiveTreatmentCosts);
        summary.put("curativeTreatmentCosts", curativeTreatmentCosts);
        summary.put("sickCareCosts", curativeTreatmentCosts);
        summary.put("treatmentCosts", preventiveTreatmentCosts);
        summary.put("purchaseCosts", purchaseCosts);
        summary.put("deathLoss", deathLoss);
        summary.put("otherExpenses", otherExpenses);
        summary.put("otherExpensesByCategory", calculateOtherExpensesByCategory(fromDate, toDate));
        summary.put("expenseBreakdown", expenseBreakdown);
        summary.put("totalExpenses", totalExpenses);

        summary.put("netProfit", netProfit);
        summary.put("profitMargin", profitMargin);
        summary.put("profitStatus", profitStatus);
        summary.put("profitability", profitability);

        summary.put("assetPosition", assetPosition);
        summary.put("cashFlow", cashFlow);

        summary.put("averageHerdValue", calculateAverageHerdValue());
        summary.put("averageSickValue", calculateAverageSickValue());

        return summary;
    }
}