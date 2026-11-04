package rw.animalproduct.animal.production.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import rw.animalproduct.animal.production.services.FinancialCalculationService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class LivestockFinancialController {

    private final FinancialCalculationService financialCalculationService;

    public LivestockFinancialController(FinancialCalculationService financialCalculationService) {
        this.financialCalculationService = financialCalculationService;
    }

    @GetMapping("/livestock/financial-summary")
    public String financialSummary(
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to,
            @RequestParam(name = "period", required = false) String period,
            Model model) {

        LocalDate today = LocalDate.now();
        boolean customRange = (from != null || to != null);
        String selectedPeriod;
        LocalDate fromDate;
        LocalDate toDate;

        if (customRange) {
            // 🟡 Explicit dates always win, and are never silently pushed to Dec 31.
            fromDate = (from != null) ? from : today.withDayOfYear(1);
            toDate = (to != null) ? to : today;
            selectedPeriod = "custom";
        } else {
            selectedPeriod = (period != null && !period.isBlank()) ? period : "ytd";
            switch (selectedPeriod) {
                case "full_year":
                    // Only shows Jan 1–Dec 31 when the user EXPLICITLY asks for the full year.
                    fromDate = today.withDayOfYear(1);
                    toDate = LocalDate.of(today.getYear(), 12, 31);
                    break;
                case "this_month":
                    fromDate = today.withDayOfMonth(1);
                    toDate = today;
                    break;
                case "last_month":
                    LocalDate lastMonth = today.minusMonths(1);
                    fromDate = lastMonth.withDayOfMonth(1);
                    toDate = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());
                    break;
                case "ytd":
                default:
                    // 🔴 KEY FIX: Year-to-Date ends TODAY, not Dec 31 — a farm running this
                    // report in August must not see "01 Jan 2026 – 31 Dec 2026".
                    fromDate = today.withDayOfYear(1);
                    toDate = today;
                    selectedPeriod = "ytd";
                    break;
            }
        }

        LocalDate[] previousPeriod = financialCalculationService.getPreviousPeriod(fromDate, toDate);
        LocalDate prevFromDate = previousPeriod[0];
        LocalDate prevToDate = previousPeriod[1];

        Map<String, Object> current = financialCalculationService.generateFinancialSummary(fromDate, toDate);
        Map<String, Object> previous = financialCalculationService.generateFinancialSummary(prevFromDate, prevToDate);

        // ── Period ───────────────────────────────────────────────────────────
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("prevFromDate", prevFromDate);
        model.addAttribute("prevToDate", prevToDate);
        model.addAttribute("selectedPeriod", selectedPeriod);

        // ── Financial KPIs ──────────────────────────────────────────────────
        BigDecimal totalRevenue = (BigDecimal) current.get("totalRevenue");
        BigDecimal totalExpenses = (BigDecimal) current.get("totalExpenses");
        BigDecimal netProfit = (BigDecimal) current.get("netProfit");
        BigDecimal profitMargin = (BigDecimal) current.get("profitMargin");
        String profitStatus = (String) current.get("profitStatus");
        BigDecimal currentLivestockValue = (BigDecimal) current.get("currentHerdValue");

        @SuppressWarnings("unchecked")
        Map<String, Object> cashFlow = (Map<String, Object>) current.get("cashFlow");
        BigDecimal netCashFlow = (BigDecimal) cashFlow.get("netCashFlow");

        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalExpenses", totalExpenses);
        model.addAttribute("netProfit", netProfit);
        model.addAttribute("profitMargin", profitMargin);
        model.addAttribute("profitStatus", profitStatus);
        model.addAttribute("currentLivestockValue", currentLivestockValue);
        model.addAttribute("cashFlow", cashFlow);
        model.addAttribute("netCashFlow", netCashFlow);

        // ── Income / Revenue section ────────────────────────────────────────
        model.addAttribute("salesRevenue", current.get("salesRevenue"));
        model.addAttribute("productionSales", current.get("productionSales"));
        model.addAttribute("otherIncome", current.get("otherIncome"));

        // ── Expenses section ────────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> expenseBreakdown = (Map<String, BigDecimal>) current.get("expenseBreakdown");
        model.addAttribute("expenseBreakdown", expenseBreakdown);

        // Highest expense line, for the "Financial Insights" narrative
        String highestExpenseLabel = null;
        BigDecimal highestExpenseValue = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : expenseBreakdown.entrySet()) {
            BigDecimal value = entry.getValue() != null ? entry.getValue() : BigDecimal.ZERO;
            if (value.compareTo(highestExpenseValue) > 0) {
                highestExpenseValue = value;
                highestExpenseLabel = entry.getKey();
            }
        }
        BigDecimal highestExpensePercent = BigDecimal.ZERO;
        if (totalExpenses != null && totalExpenses.compareTo(BigDecimal.ZERO) > 0 && highestExpenseLabel != null) {
            highestExpensePercent = highestExpenseValue
                    .divide(totalExpenses, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, java.math.RoundingMode.HALF_UP);
        }
        model.addAttribute("highestExpenseLabel", highestExpenseLabel);
        model.addAttribute("highestExpenseValue", highestExpenseValue);
        model.addAttribute("highestExpensePercent", highestExpensePercent);

        BigDecimal deathLoss = (BigDecimal) current.get("deathLoss");
        BigDecimal mortalityPercentOfExpenses = BigDecimal.ZERO;
        if (totalExpenses != null && totalExpenses.compareTo(BigDecimal.ZERO) > 0 && deathLoss != null) {
            mortalityPercentOfExpenses = deathLoss
                    .divide(totalExpenses, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, java.math.RoundingMode.HALF_UP);
        }
        model.addAttribute("deathLoss", deathLoss);
        model.addAttribute("mortalityPercentOfExpenses", mortalityPercentOfExpenses);

        // ── Livestock Asset Position ────────────────────────────────────────
        model.addAttribute("assetPosition", current.get("assetPosition"));

        // ── Profitability Analysis ──────────────────────────────────────────
        model.addAttribute("profitability", current.get("profitability"));

        // ── 🟡 Period comparison (current vs previous, % change) ───────────
        Map<String, Object> comparisons = new LinkedHashMap<>();
        comparisons.put("revenue", financialCalculationService.buildComparison(
                totalRevenue, (BigDecimal) previous.get("totalRevenue")));
        comparisons.put("expenses", financialCalculationService.buildComparison(
                totalExpenses, (BigDecimal) previous.get("totalExpenses")));
        comparisons.put("netProfit", financialCalculationService.buildComparison(
                netProfit, (BigDecimal) previous.get("netProfit")));
        comparisons.put("livestockValue", financialCalculationService.buildComparison(
                currentLivestockValue, (BigDecimal) previous.get("currentHerdValue")));
        @SuppressWarnings("unchecked")
        Map<String, Object> previousCashFlow = (Map<String, Object>) previous.get("cashFlow");
        comparisons.put("cashFlow", financialCalculationService.buildComparison(
                netCashFlow, (BigDecimal) previousCashFlow.get("netCashFlow")));
        model.addAttribute("comparisons", comparisons);

        return "financial-summary";
    }
}
