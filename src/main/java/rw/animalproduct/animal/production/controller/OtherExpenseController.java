package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.dto.OtherExpenseSummary;
import rw.animalproduct.animal.production.entity.OtherExpense;
import rw.animalproduct.animal.production.services.AuditLogService;
import rw.animalproduct.animal.production.services.OtherExpenseService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/other-expenses")
public class OtherExpenseController {

    private final OtherExpenseService otherExpenseService;
    private final AuditLogService auditLogService;

    public OtherExpenseController(OtherExpenseService otherExpenseService, AuditLogService auditLogService) {
        this.otherExpenseService = otherExpenseService;
        this.auditLogService = auditLogService;
    }

    // ── LIST ───────────────────────────────────────────────────────────
    @GetMapping
    public String listExpenses(Model model) {
        model.addAttribute("expenses", otherExpenseService.getAllSummaries());
        model.addAttribute("expense", new OtherExpense());
        model.addAttribute("totalThisMonth", otherExpenseService.totalThisMonth());
        model.addAttribute("totalThisYear", otherExpenseService.totalThisYear());
        model.addAttribute("totalAllTime", otherExpenseService.totalAllTime());
        model.addAttribute("recordCount", otherExpenseService.countAll());
        model.addAttribute("categoryTotals", otherExpenseService.totalsByCategory());
        return "other-expenses-list";
    }

    // ── CREATE ─────────────────────────────────────────────────────────
    // NOTE: the "send email on record" behavior now lives inside
    // OtherExpenseService.addNew() itself (mirrors LivestockService.create()),
    // so this method is back to its original shape — no email-specific code here.
    @PostMapping("/new")
    public String saveExpense(@Valid @ModelAttribute("expense") OtherExpense expense,
                              BindingResult result,
                              Model model,
                              RedirectAttributes ra) {
        if (result.hasErrors()) {
            return reRenderListWithErrors(model);
        }
        try {
            expense.setCreatedByUsername(getCurrentUsername());
            OtherExpense saved = otherExpenseService.addNew(expense);

            if (saved == null || saved.getId() == null) {
                throw new IllegalStateException("Save did not return a persisted record.");
            }

            auditLogService.log(
                    "other_expense",
                    saved.getId(),
                    "CREATE",
                    getCurrentUsername(),
                    null,
                    saved,
                    "Recorded expense: " + saved.getDescription() + " (" + saved.getAmount() + ")"
            );

            ra.addFlashAttribute("success", "Expense \"" + saved.getDescription() + "\" saved successfully!");
            return "redirect:/other-expenses";
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not save expense: " + e.getMessage());
            return "redirect:/other-expenses";
        }
    }

    @GetMapping("/edit/{id}")
    public String editExpenseForm(@PathVariable UUID id, Model model) {
        Optional<OtherExpense> opt = otherExpenseService.getById(id);
        if (opt.isEmpty()) return "redirect:/other-expenses";
        model.addAttribute("expense", opt.get());
        return "other-expense-edit";
    }

    // ── UPDATE ─────────────────────────────────────────────────────────
    @PostMapping("/update/{id}")
    public String updateExpense(@PathVariable UUID id,
                                @Valid @ModelAttribute("expense") OtherExpense expense,
                                BindingResult result,
                                Model model,
                                RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "other-expense-edit";
        }
        try {
            String beforeSnapshot = otherExpenseService.getById(id)
                    .map(auditLogService::snapshot)
                    .orElse(null);

            OtherExpense updated = otherExpenseService.update(id, expense);

            auditLogService.log(
                    "other_expense",
                    id,
                    "UPDATE",
                    getCurrentUsername(),
                    beforeSnapshot,
                    updated,
                    "Updated expense: " + updated.getDescription()
            );

            ra.addFlashAttribute("success", "Expense updated successfully!");
            return "redirect:/other-expenses";
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not update expense: " + e.getMessage());
            model.addAttribute("expense", expense);
            return "other-expense-edit";
        }
    }

    // ── DELETE (soft-delete, keeps history for reporting/audit) ──────────
    @PostMapping("/delete/{id}")
    public String deleteExpense(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            Optional<OtherExpense> opt = otherExpenseService.getById(id);
            if (opt.isPresent()) {
                OtherExpense expense = opt.get();
                auditLogService.log(
                        "other_expense",
                        id,
                        "SOFT_DELETE",
                        getCurrentUsername(),
                        expense,
                        null,
                        "Deleted expense: " + expense.getDescription() + " (" + expense.getAmount() + ")"
                );
            }
            otherExpenseService.delete(id);
            ra.addFlashAttribute("success", "Expense deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/other-expenses";
    }

    @GetMapping("/search")
    @ResponseBody
    public List<OtherExpenseSummary> searchExpenses(@RequestParam String query) {
        return otherExpenseService.search(query);
    }

    // ── REPORT ─────────────────────────────────────────────────────────
    @GetMapping("/report")
    public String otherExpensesReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category,
            Model model) {

        // Default window: year-to-date, so the report is never blank on first visit
        LocalDate effectiveStart = (startDate != null) ? startDate : LocalDate.now().withDayOfYear(1);
        LocalDate effectiveEnd = (endDate != null) ? endDate : LocalDate.now();
        String effectiveCategory = (category != null && category.isBlank()) ? null : category;

        model.addAttribute("expenses", otherExpenseService.getReportData(effectiveStart, effectiveEnd, effectiveCategory));
        model.addAttribute("totalAmount", otherExpenseService.getReportTotal(effectiveStart, effectiveEnd, effectiveCategory));
        model.addAttribute("categoryTotals", otherExpenseService.getReportCategoryBreakdown(effectiveStart, effectiveEnd));
        model.addAttribute("monthlyTotals", otherExpenseService.getReportMonthlyTrend(effectiveStart, effectiveEnd));
        model.addAttribute("allCategories", otherExpenseService.getDistinctCategories());

        model.addAttribute("startDate", effectiveStart);
        model.addAttribute("endDate", effectiveEnd);
        model.addAttribute("selectedCategory", effectiveCategory);
        model.addAttribute("generatedBy", getCurrentUsername());
        model.addAttribute("generatedAt", java.time.LocalDateTime.now());

        return "other-expenses-report";
    }


    // ── Helpers ────────────────────────────────────────────────────────

    private String reRenderListWithErrors(Model model) {
        model.addAttribute("expenses", otherExpenseService.getAllSummaries());
        model.addAttribute("totalThisMonth", otherExpenseService.totalThisMonth());
        model.addAttribute("totalThisYear", otherExpenseService.totalThisYear());
        model.addAttribute("totalAllTime", otherExpenseService.totalAllTime());
        model.addAttribute("recordCount", otherExpenseService.countAll());
        model.addAttribute("categoryTotals", otherExpenseService.totalsByCategory());
        model.addAttribute("openAddModal", true);
        return "other-expenses-list";
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "system";
    }

}
