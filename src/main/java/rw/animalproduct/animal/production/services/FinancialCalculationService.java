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

    /**
     * Get all active (non-deleted, non-draft) livestock records.
     */
    private List<Livestock> getActiveLivestock() {
        return livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .collect(Collectors.toList());
    }

    // ========================================================================
    // HERD VALUE CALCULATIONS  (Livestock Assets — NEVER revenue, see §Income)
    // ========================================================================

    /**
     * Present-day value of the live productive herd.
     *
     * IMPORTANT: This ONLY includes animals with status ACTIVE or PREGNANT.
     * - ACTIVE animals are productive and generating value
     * - PREGNANT animals represent future value (offspring)
     *
     * EXCLUDED:
     * - SICK animals (not productive, represent a liability/business risk)
     * - SOLD animals (no longer owned)
     * - DEAD animals (no longer alive)
     * - DRAFT records (not finalized)
     * - DELETED records (soft-deleted)
     *
     * This is ALWAYS a point-in-time snapshot ("as of today"), never date-range
     * filtered — the schema does not persist historical valuations.
     *
     * 🔴 This value must NEVER be added into revenue/income. It belongs under
     * "Livestock Assets" only. See {@link #calculateTotalRevenue(LocalDate, LocalDate)}.
     *
     * @return Total value of active and pregnant animals
     */
    public BigDecimal calculateCurrentHerdValue() {
        return getActiveLivestock().stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()) ||
                        Livestock.STATUS_PREGNANT.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Value of SICK animals in the herd.
     * These animals represent a business risk and potential loss.
     *
     * @return Total value of animals with SICK status
     */
    public BigDecimal calculateSickHerdValue() {
        return getActiveLivestock().stream()
                .filter(l -> Livestock.STATUS_SICK.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Total value of ALL livestock regardless of status.
     * This includes ACTIVE, PREGNANT, SICK, SOLD, and DEAD animals.
     * Useful for total asset valuation.
     *
     * @return Total value of all non-deleted, non-draft livestock
     */
    public BigDecimal calculateTotalHerdValue() {
        return getActiveLivestock().stream()
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Value of SOLD animals (all-time snapshot — status is not date-stamped in the
     * schema, so this cannot be filtered to a specific period). Used as a proxy for
     * the "Sales" line in the livestock asset roll-forward. See the limitation note
     * on {@link #calculateLivestockAssetPosition(LocalDate, LocalDate)}.
     *
     * @return Total value of animals with SOLD status
     */
    public BigDecimal calculateSoldHerdValue() {
        return getActiveLivestock().stream()
                .filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Value of DEAD animals.
     * These are historical records of animals that have died.
     *
     * @return Total value of animals with DEAD status
     */
    public BigDecimal calculateDeadHerdValue() {
        return getActiveLivestock().stream()
                .filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Value added to the herd through births in the period.
     * This is an asset-growth metric — it is NOT revenue (no cash changes hands).
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
     * Value of animals acquired through donation in the period.
     *
     * @return Total value of animals with acquisition_method = 'DONATION'
     */
    public BigDecimal calculateDonationValue(LocalDate fromDate, LocalDate toDate) {
        return livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
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

    /**
     * Calculate the average value per animal in the active herd.
     */
    public BigDecimal calculateAverageHerdValue() {
        long count = countActiveHerd();
        if (count == 0) return BigDecimal.ZERO;
        return calculateCurrentHerdValue().divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate the average value per animal in the sick herd.
     */
    public BigDecimal calculateAverageSickValue() {
        long count = countSickHerd();
        if (count == 0) return BigDecimal.ZERO;
        return calculateSickHerdValue().divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    // ========================================================================
    // 🔴 INCOME / REVENUE  (corrected — Livestock Assets are excluded)
    // ========================================================================

    /**
     * Revenue from confirmed livestock sales within the period.
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
     * Revenue from production sales (e.g. milk, eggs, wool, manure) in the period.
     *
     * EXTENSION POINT: no production-sales ledger exists in the schema yet.
     * Returns ZERO until one is added — wire a repository/service here rather than
     * approximating, so Total Revenue never silently overstates income.
     */
    public BigDecimal calculateProductionSales(LocalDate fromDate, LocalDate toDate) {
        return BigDecimal.ZERO;
    }

    /**
     * Other cash income not covered above (e.g. government subsidies, equipment
     * rental, insurance payouts).
     *
     * EXTENSION POINT: no "other income" ledger exists in the schema yet.
     * Returns ZERO until one is added.
     */
    public BigDecimal calculateOtherIncome(LocalDate fromDate, LocalDate toDate) {
        return BigDecimal.ZERO;
    }

    /**
     * 🔴 TOTAL REVENUE = Livestock Sales + Production Sales + Other Income.
     *
     * This is the corrected formula. Current Livestock Value is a point-in-time
     * ASSET valuation, not income earned in the period, and must never be added
     * here — doing so double-counts unrealized stock value as if it were cash
     * revenue. See {@link #calculateCurrentHerdValue()} and
     * {@link #calculateLivestockAssetPosition(LocalDate, LocalDate)}.
     *
     * Pass (null, null) for all-time revenue.
     */
    public BigDecimal calculateTotalRevenue(LocalDate fromDate, LocalDate toDate) {
        BigDecimal livestockSales = calculateSalesRevenue(fromDate, toDate);
        BigDecimal productionSales = calculateProductionSales(fromDate, toDate);
        BigDecimal otherIncome = calculateOtherIncome(fromDate, toDate);
        return livestockSales.add(productionSales).add(otherIncome);
    }

    /**
     * @deprecated Use {@link #calculateTotalRevenue(LocalDate, LocalDate)}.
     * Kept only so any existing caller (e.g. dashboard widgets) still compiles;
     * it now delegates to the corrected revenue formula instead of the old
     * "Sales + Current Livestock Value" calculation that double-counted assets
     * as income.
     */
    @Deprecated
    public BigDecimal calculateTotalIncome(LocalDate fromDate, LocalDate toDate) {
        return calculateTotalRevenue(fromDate, toDate);
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
     * This is a NON-CASH expense — it reduces the asset base, not the bank balance.
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
     * These are considered sick care / veterinary-health costs, separate from
     * preventive treatments.
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
     * Breakdown of other expenses by category as stored (OTHER, TRANSPORT,
     * EQUIPMENT, MAINTENANCE, UTILITIES, ... — plus FEED/LABOR/BREEDING once those
     * categories are introduced).
     *
     * IMPORTANT: deliberately does NOT delegate to
     * OtherExpenseRepository.sumByCategoryBetween(start, end) (or any of the
     * other :start/:end-based queries on that repository). Those all use a
     * plain JPQL "e.expenseDate BETWEEN :start AND :end", which is NOT
     * null-safe: if either bound is null, BETWEEN evaluates to NULL/false and
     * the query silently matches zero rows. Every other calculation in this
     * service (purchase costs, death loss, treatment costs, etc.) instead
     * pulls the full unfiltered list and applies the null-safe inRange()
     * helper in Java, so it keeps working when fromDate/toDate are null
     * (all-time) or match today's data. This follows the same pattern so
     * other-expenses can't silently return 0 the same way again.
     * Pass (null, null) for all-time.
     */
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

    /**
     * Miscellaneous costs logged in the other_expenses table
     * (e.g. TRANSPORT, EQUIPMENT, MAINTENANCE, UTILITIES, OTHER).
     * Pass (null, null) for all-time other expenses.
     */
    public BigDecimal calculateOtherExpenses(LocalDate fromDate, LocalDate toDate) {
        return calculateOtherExpensesByCategory(fromDate, toDate).values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Pulls one named category out of the other_expenses breakdown, case-insensitively.
     * Returns ZERO if that category has no records yet — this is what lets
     * {@link #calculateExpenseBreakdown(LocalDate, LocalDate)} show Feed / Labor /
     * Breeding / Transport rows today even though most farms haven't logged any
     * expenses under those categories yet, and pick them up automatically once they do.
     */
    private BigDecimal extractCategory(Map<String, BigDecimal> byCategory, String categoryKey) {
        for (Map.Entry<String, BigDecimal> entry : byCategory.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(categoryKey)) {
                return entry.getValue() != null ? entry.getValue() : BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * 🟢 Expense breakdown in the report's requested shape:
     * Animal Purchases, Feed, Treatment, Veterinary/Health, Breeding, Labor,
     * Transport, Other Expenses, Death/Mortality Losses.
     *
     * "Feed", "Breeding" and "Labor" read from the other_expenses category
     * breakdown so they populate automatically as soon as those categories are
     * used; anything not explicitly bucketed (EQUIPMENT, MAINTENANCE, UTILITIES,
     * OTHER, ...) falls into "Other Expenses" so the total always reconciles
     * with {@link #calculateTotalExpenses(LocalDate, LocalDate)}.
     *
     * Iteration order is preserved (LinkedHashMap) so the UI can render rows
     * in this exact order without re-sorting.
     */
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

    /**
     * TOTAL EXPENSES = Preventive Treatments + Curative Treatments + Purchase Costs
     * + Death Loss + Other Expenses
     * Pass (null, null) for all-time expenses.
     */
    public BigDecimal calculateTotalExpenses(LocalDate fromDate, LocalDate toDate) {
        BigDecimal preventive = calculatePreventiveTreatmentCosts(fromDate, toDate);
        BigDecimal curative = calculateCurativeTreatmentCosts(fromDate, toDate);
        BigDecimal purchases = calculatePurchaseCosts(fromDate, toDate);
        BigDecimal deaths = calculateDeathLoss(fromDate, toDate);
        BigDecimal other = calculateOtherExpenses(fromDate, toDate);
        return preventive.add(curative).add(purchases).add(deaths).add(other);
    }

    // ========================================================================
    // 🟢 LIVESTOCK ASSET POSITION (roll-forward)
    // ========================================================================

    /**
     * Livestock asset roll-forward for the period:
     * Opening Value + Purchases + Born Animals Value − Death Losses − Sales
     * + Value Adjustments = Closing Value.
     *
     * ⚠️ LIMITATION: the schema stores only the CURRENT value of each animal, not
     * a dated valuation history, so there is no way to read "herd value as of
     * 1 Jan" directly. Opening Value is therefore derived by rearranging the
     * roll-forward equation around today's Closing Value, and the Sales figure
     * uses the all-time SOLD-status value (not filtered to the period, since
     * sale status has no separate "period" concept in the schema either).
     * For an exact roll-forward, persist a dated valuation snapshot (e.g. a
     * `livestock_valuation_snapshots` table written on period close) and swap
     * this derivation for a real lookup.
     *
     * Pass (null, null) for all-time.
     */
    public Map<String, Object> calculateLivestockAssetPosition(LocalDate fromDate, LocalDate toDate) {
        BigDecimal closingValue = calculateCurrentHerdValue();
        BigDecimal purchases = calculatePurchaseCosts(fromDate, toDate);
        BigDecimal bornAnimalsValue = calculateBornAnimalsValue(fromDate, toDate);
        BigDecimal deathLosses = calculateDeathLoss(fromDate, toDate);
        BigDecimal salesValue = calculateSoldHerdValue();
        BigDecimal valueAdjustments = BigDecimal.ZERO; // no revaluation ledger yet

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
    // 🟢 CASH FLOW
    // ========================================================================

    /**
     * Cash flow for the period. Differs from Net Profit because it excludes
     * non-cash items:
     * - Death/Mortality Losses (write-off of an existing asset, no cash moves)
     * - Born Animals Value (asset appreciation, no cash moves)
     *
     * Cash Inflows = Total Revenue (cash actually received)
     * Cash Outflows = Animal Purchases + Treatment + Veterinary/Health
     *                 + Feed/Breeding/Labor/Transport/Other Expenses (all cash paid out)
     * Net Cash Flow = Cash Inflows − Cash Outflows
     *
     * Pass (null, null) for all-time.
     */
    public Map<String, Object> calculateCashFlow(LocalDate fromDate, LocalDate toDate) {
        BigDecimal cashInflows = calculateTotalRevenue(fromDate, toDate);

        BigDecimal cashOutflows = calculatePurchaseCosts(fromDate, toDate)
                .add(calculatePreventiveTreatmentCosts(fromDate, toDate))
                .add(calculateCurativeTreatmentCosts(fromDate, toDate))
                .add(calculateOtherExpenses(fromDate, toDate));
        // Death Loss is intentionally excluded — non-cash.

        BigDecimal netCashFlow = cashInflows.subtract(cashOutflows);

        Map<String, Object> cashFlow = new LinkedHashMap<>();
        cashFlow.put("cashInflows", cashInflows);
        cashFlow.put("cashOutflows", cashOutflows);
        cashFlow.put("netCashFlow", netCashFlow);
        return cashFlow;
    }

    // ========================================================================
    // 🔴 PROFIT / MARGIN  (corrected — uses Total Revenue, not Total Income)
    // ========================================================================

    /**
     * Pass (null, null) for all-time net profit.
     */
    public BigDecimal calculateNetProfit(LocalDate fromDate, LocalDate toDate) {
        return calculateTotalRevenue(fromDate, toDate).subtract(calculateTotalExpenses(fromDate, toDate));
    }

    /**
     * Net profit margin as a percentage of Total Revenue (not asset value).
     * Pass (null, null) for all-time margin.
     */
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

    /**
     * 🟢 Profitability Analysis section: Gross Profit, Operating Profit, Net
     * Profit/Loss, Profit Margin, Cost-to-Revenue Ratio.
     *
     * Classification used (documented so it's easy to adjust later):
     * - Direct/Cost-of-stock = Animal Purchases + Death/Mortality Losses
     *   (money/asset tied directly to acquiring or losing animals)
     * - Gross Profit = Total Revenue − Direct costs
     * - Operating Expenses = Total Expenses − Direct costs (treatment, vet,
     *   feed, breeding, labor, transport, other)
     * - Operating Profit = Gross Profit − Operating Expenses
     * - Net Profit = Operating Profit (no interest/tax layer modeled today)
     *
     * Pass (null, null) for all-time.
     */
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
    // 🟡 PERIOD COMPARISON
    // ========================================================================

    /**
     * Returns the immediately-preceding period of equal length to [fromDate, toDate],
     * as {previousFrom, previousTo}. E.g. for 01 Jun–30 Jun (30 days), returns
     * 02 May–31 May (also 30 days). Used for "Current Period vs Previous Period".
     */
    public LocalDate[] getPreviousPeriod(LocalDate fromDate, LocalDate toDate) {
        long periodLengthDays = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        LocalDate previousTo = fromDate.minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(periodLengthDays - 1);
        return new LocalDate[] { previousFrom, previousTo };
    }

    /**
     * Builds a {current, previous, change, changePercent, isIncrease} comparison
     * block for a single metric, for use in period-over-period KPI display.
     * If previousValue is zero and currentValue is non-zero, changePercent is
     * reported as 100% ("new" — cannot divide by zero meaningfully).
     */
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

    /**
     * Calculate the mortality rate as a percentage.
     * Mortality Rate = (Deaths / (Active + Dead)) × 100
     */
    public double calculateMortalityRate(LocalDate fromDate, LocalDate toDate) {
        long deaths = deathService.getAll().stream()
                .filter(d -> !Boolean.TRUE.equals(d.getIsDeleted()))
                .filter(d -> inRange(d.getDeathDate(), fromDate, toDate))
                .count();

        long active = countActiveHerd();
        long total = active + deaths;

        if (total == 0) return 0.0;
        return (deaths * 100.0) / total;
    }

    /**
     * Calculate the offtake rate as a percentage.
     * Offtake Rate = (Sales / Opening Herd) × 100
     */
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

    /**
     * Calculate the replacement rate as a percentage.
     * Replacement Rate = (Births / Total Herd) × 100
     */
    public double calculateReplacementRate(long totalBirths, long totalHerd) {
        if (totalHerd == 0) return 0.0;
        return (totalBirths * 100.0) / totalHerd;
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
     * 🔴 CORRECTED: totalIncome/netProfit/profitMargin now come from the fixed
     * Total Revenue calculation (Sales + Other Income), so Current Livestock
     * Value is no longer double-counted as income here or on the dashboard.
     *
     * Pass (null, null) to get all-time figures instead of a specific period.
     */
    public Map<String, Object> generateFinancialSummary(LocalDate fromDate, LocalDate toDate) {
        // REVENUE (corrected)
        BigDecimal salesRevenue = calculateSalesRevenue(fromDate, toDate);
        BigDecimal productionSales = calculateProductionSales(fromDate, toDate);
        BigDecimal otherIncome = calculateOtherIncome(fromDate, toDate);
        BigDecimal totalRevenue = calculateTotalRevenue(fromDate, toDate);
        BigDecimal currentLivestockValue = calculateCurrentHerdValue();
        BigDecimal bornAnimalsValue = calculateBornAnimalsValue(fromDate, toDate);

        // EXPENSES
        BigDecimal preventiveTreatmentCosts = calculatePreventiveTreatmentCosts(fromDate, toDate);
        BigDecimal curativeTreatmentCosts = calculateCurativeTreatmentCosts(fromDate, toDate);
        BigDecimal purchaseCosts = calculatePurchaseCosts(fromDate, toDate);
        BigDecimal deathLoss = calculateDeathLoss(fromDate, toDate);
        BigDecimal otherExpenses = calculateOtherExpenses(fromDate, toDate);
        BigDecimal totalExpenses = calculateTotalExpenses(fromDate, toDate);
        Map<String, BigDecimal> expenseBreakdown = calculateExpenseBreakdown(fromDate, toDate);

        // PROFIT (corrected)
        BigDecimal netProfit = calculateNetProfit(fromDate, toDate);
        BigDecimal profitMargin = calculateProfitMargin(fromDate, toDate);
        String profitStatus = getProfitStatus(fromDate, toDate);
        Map<String, Object> profitability = calculateProfitabilityAnalysis(fromDate, toDate);

        // ASSET POSITION & CASH FLOW
        Map<String, Object> assetPosition = calculateLivestockAssetPosition(fromDate, toDate);
        Map<String, Object> cashFlow = calculateCashFlow(fromDate, toDate);

        // HERD COUNTS
        long activeCount = countActiveHerd();
        long sickCount = countSickHerd();
        long soldCount = countSoldHerd();
        long deadCount = countDeadHerd();
        long totalCount = countTotalHerd();

        // HERD VALUES (for transparency)
        BigDecimal sickHerdValue = calculateSickHerdValue();
        BigDecimal soldHerdValue = calculateSoldHerdValue();
        BigDecimal deadHerdValue = calculateDeadHerdValue();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fromDate", fromDate);
        summary.put("toDate", toDate);

        // HERD COUNTS
        summary.put("activeCount", activeCount);
        summary.put("sickCount", sickCount);
        summary.put("soldCount", soldCount);
        summary.put("deadCount", deadCount);
        summary.put("totalCount", totalCount);

        // HERD VALUES
        summary.put("currentHerdValue", currentLivestockValue);
        summary.put("sickHerdValue", sickHerdValue);
        summary.put("soldHerdValue", soldHerdValue);
        summary.put("deadHerdValue", deadHerdValue);
        summary.put("totalHerdValue", calculateTotalHerdValue());

        // REVENUE SECTION (corrected key names + legacy aliases retained)
        summary.put("salesRevenue", salesRevenue);
        summary.put("productionSales", productionSales);
        summary.put("otherIncome", otherIncome);
        summary.put("bornAnimalsValue", bornAnimalsValue);
        summary.put("totalRevenue", totalRevenue);
        summary.put("totalIncome", totalRevenue); // legacy alias — now correct

        // EXPENSE SECTION
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

        // PROFIT SECTION
        summary.put("netProfit", netProfit);
        summary.put("profitMargin", profitMargin);
        summary.put("profitStatus", profitStatus);
        summary.put("profitability", profitability);

        // ASSET POSITION & CASH FLOW
        summary.put("assetPosition", assetPosition);
        summary.put("cashFlow", cashFlow);

        // AVERAGE VALUES
        summary.put("averageHerdValue", calculateAverageHerdValue());
        summary.put("averageSickValue", calculateAverageSickValue());

        return summary;
    }
}
