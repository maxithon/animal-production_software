package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.LivestockSick;
import rw.animalproduct.animal.production.entity.LivestockSickHistory;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.services.LivestockSickService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Controller for Livestock Sick records.
 *
 * Handles the sick list, create, edit, update, delete, mark-as-recovered,
 * and the period-based report page.
 *
 * @author HABANABAKIZE & NDAYIZEYE
 * @version 1.0.0
 */
@Controller
@RequestMapping("/livestock/sick")
public class LivestockSickController {

    private final LivestockSickService sickService;
    private final LivestockRepository livestockRepository;

    public LivestockSickController(LivestockSickService sickService,
                                   LivestockRepository livestockRepository) {
        this.sickService = sickService;
        this.livestockRepository = livestockRepository;
    }

    // ═════════════════════════════════════════════════════════════════
    // LIST
    // ═════════════════════════════════════════════════════════════════
    @GetMapping
    public String list(Model model) {
        model.addAttribute("sickRecords", sickService.getAllActive());
        model.addAttribute("sickRecord", new LivestockSick());
        model.addAttribute("livestockList", livestockRepository.findAll());
        return "livestock-sick-list";
    }

    // ═════════════════════════════════════════════════════════════════
    // CREATE
    // ═════════════════════════════════════════════════════════════════
    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("sickRecord") LivestockSick sickRecord,
                         BindingResult result,
                         RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fix the errors in the form.");
            return "redirect:/livestock/sick";
        }
        try {
            sickService.addNew(sickRecord);
            redirectAttributes.addFlashAttribute("success", "Sick record added successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error adding sick record: " + e.getMessage());
        }
        return "redirect:/livestock/sick";
    }

    // ═════════════════════════════════════════════════════════════════
    // EDIT FORM
    // ═════════════════════════════════════════════════════════════════
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        return sickService.getById(id)
                .map(sick -> {
                    model.addAttribute("sickRecord", sick);
                    model.addAttribute("livestockList", livestockRepository.findAll());
                    return "livestock-sick-edit";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Sick record not found.");
                    return "redirect:/livestock/sick";
                });
    }

    // ═════════════════════════════════════════════════════════════════
    // UPDATE
    // ═════════════════════════════════════════════════════════════════
    @PostMapping("/update/{id}")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("sickRecord") LivestockSick sickRecord,
                         BindingResult result,
                         RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fix the errors in the form.");
            return "redirect:/livestock/sick/edit/" + id;
        }
        try {
            sickService.update(id, sickRecord);
            redirectAttributes.addFlashAttribute("success", "Sick record updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating sick record: " + e.getMessage());
        }
        return "redirect:/livestock/sick";
    }

    // ═════════════════════════════════════════════════════════════════
    // DELETE (soft-delete)
    // ═════════════════════════════════════════════════════════════════
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            sickService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Sick record deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting sick record: " + e.getMessage());
        }
        return "redirect:/livestock/sick";
    }

    // ═════════════════════════════════════════════════════════════════
    // MARK AS RECOVERED
    // ═════════════════════════════════════════════════════════════════
    @PostMapping("/recover/{id}")
    public String markAsRecovered(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            sickService.markAsRecovered(id);
            redirectAttributes.addFlashAttribute("success", "Animal marked as recovered.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error marking as recovered: " + e.getMessage());
        }
        return "redirect:/livestock/sick";
    }

    // ═════════════════════════════════════════════════════════════════
    // REPORT
    // ═════════════════════════════════════════════════════════════════
    @GetMapping("/report")
    public String report(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        // Resolve defaults into final variables so the lambdas below
        // can capture them (lambdas require effectively-final variables).
        final LocalDate fromDate = (from != null) ? from : LocalDate.now().withDayOfMonth(1);
        final LocalDate toDate   = (to   != null) ? to   : LocalDate.now();

        // Convert to LocalDateTime for the history repository
        LocalDateTime fromDT = fromDate.atStartOfDay();
        LocalDateTime toDT   = toDate.plusDays(1).atStartOfDay(); // inclusive end-of-day

        // ── All history events in range (status changes) ─────────────
        List<LivestockSickHistory> allHistory = sickService.getHistoryInRange(fromDT, toDT);

        // ── Filtered subsets for KPI strip ───────────────────────────
        List<LivestockSickHistory> sickCases      = sickService.getSickCasesInRange(fromDT, toDT);
        List<LivestockSickHistory> criticalCases  = sickService.getCriticalCasesInRange(fromDT, toDT);
        List<LivestockSickHistory> recoveredCases = sickService.getRecoveredCasesInRange(fromDT, toDT);

        // "Recovering" — derive from allHistory
        List<LivestockSickHistory> recoveringCases = allHistory.stream()
                .filter(h -> h.getStatus() != null
                        && h.getStatus().name().equals("RECOVERING"))
                .toList();

        // ── Full episodes (used by the per-animal journey cards) ─────
        // Filter by reportedDate within [fromDate, toDate].
        List<LivestockSick> sickRecords = sickService.getAllActive().stream()
                .filter(s -> s.getReportedDate() != null
                        && !s.getReportedDate().isBefore(fromDate)
                        && !s.getReportedDate().isAfter(toDate))
                .toList();

        // ── Year totals for the bar chart ────────────────────────────
        int currentYear = LocalDate.now().getYear();
        long yearSick      = sickService.countSickByYear(currentYear);
        long yearCritical  = sickService.countCriticalByYear(currentYear);
        long yearRecovered = sickService.countRecoveredByYear(currentYear);

        // ── Push everything to the view ──────────────────────────────
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("allHistory", allHistory);
        model.addAttribute("sickCases", sickCases);
        model.addAttribute("criticalCases", criticalCases);
        model.addAttribute("recoveringCases", recoveringCases);
        model.addAttribute("recoveredCases", recoveredCases);
        model.addAttribute("sickRecords", sickRecords);
        model.addAttribute("yearSick", yearSick);
        model.addAttribute("yearCritical", yearCritical);
        model.addAttribute("yearRecovered", yearRecovered);

        return "livestock-sick-report";
    }
}