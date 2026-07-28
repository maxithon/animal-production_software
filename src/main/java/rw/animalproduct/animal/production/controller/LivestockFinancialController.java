package rw.animalproduct.animal.production.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import rw.animalproduct.animal.production.services.FinancialCalculationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

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
            Model model) {

        // ── Default period: start of current year -> today, if not supplied ──
        LocalDate today = LocalDate.now();
        LocalDate fromDate = (from != null) ? from : today.withDayOfYear(1);
        LocalDate toDate   = (to != null) ? to : today;

        // ── Income ───────────────────────────────────────────────────────────
        BigDecimal salesRevenue          = financialCalculationService.calculateSalesRevenue(fromDate, toDate);
        BigDecimal currentLivestockValue = financialCalculationService.calculateCurrentHerdValue();
        BigDecimal bornAnimalsValue      = financialCalculationService.calculateBornAnimalsValue(fromDate, toDate);
        BigDecimal totalIncome           = financialCalculationService.calculateTotalIncome(fromDate, toDate);

        // ── Expenses ─────────────────────────────────────────────────────────
        BigDecimal treatmentCosts  = financialCalculationService.calculatePreventiveTreatmentCosts(fromDate, toDate);
        BigDecimal sickCareCosts   = financialCalculationService.calculateCurativeTreatmentCosts(fromDate, toDate);
        BigDecimal purchaseCosts   = financialCalculationService.calculatePurchaseCosts(fromDate, toDate);
        BigDecimal deathLoss       = financialCalculationService.calculateDeathLoss(fromDate, toDate);
        BigDecimal totalExpenses   = financialCalculationService.calculateTotalExpenses(fromDate, toDate);

        // ── Net profit / margin ─────────────────────────────────────────────
        BigDecimal netProfit = financialCalculationService.calculateNetProfit(fromDate, toDate);
        String profitStatus = netProfit.compareTo(BigDecimal.ZERO) >= 0 ? "PROFIT" : "LOSS";

        BigDecimal profitMargin = BigDecimal.ZERO;
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            profitMargin = netProfit
                    .divide(totalIncome, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        // ── Model attributes (match financial-summary.html exactly) ────────
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);

        model.addAttribute("salesRevenue", salesRevenue);
        model.addAttribute("currentLivestockValue", currentLivestockValue);
        model.addAttribute("bornAnimalsValue", bornAnimalsValue);
        model.addAttribute("totalIncome", totalIncome);

        model.addAttribute("treatmentCosts", treatmentCosts);
        model.addAttribute("sickCareCosts", sickCareCosts);
        model.addAttribute("purchaseCosts", purchaseCosts);
        model.addAttribute("deathLoss", deathLoss);
        model.addAttribute("totalExpenses", totalExpenses);

        model.addAttribute("netProfit", netProfit);
        model.addAttribute("profitStatus", profitStatus);
        model.addAttribute("profitMargin", profitMargin);

        return "financial-summary";
    }
}