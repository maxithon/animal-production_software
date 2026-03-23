package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSickRepository;
import rw.animalproduct.animal.production.services.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livestock")
public class LivestockEventsController {

    private final LivestockRepository       livestockRepository;
    private final LivestockAbortionService  abortionService;
    private final LivestockDeathService     deathService;
    private final LivestockSaleService      saleService;
    private final LivestockTreatmentService treatmentService;
    private final LivestockSickService      sickService;
    private final LivestockSickRepository   sickRepository;

    public LivestockEventsController(LivestockRepository livestockRepository,
                                     LivestockAbortionService abortionService,
                                     LivestockDeathService deathService,
                                     LivestockSaleService saleService,
                                     LivestockTreatmentService treatmentService,
                                     LivestockSickService sickService,
                                     LivestockSickRepository sickRepository) {
        this.livestockRepository = livestockRepository;
        this.abortionService     = abortionService;
        this.deathService        = deathService;
        this.saleService         = saleService;
        this.treatmentService    = treatmentService;
        this.sickService         = sickService;
        this.sickRepository      = sickRepository;
    }

    // ── Model helpers ─────────────────────────────────────────────────

    private void addLivestockToModel(Model model) {
        List<Livestock> available = livestockRepository.findAll().stream()
                .filter(ls -> !Livestock.STATUS_DEAD.equals(ls.getStatus())
                        && !"SOLD".equals(ls.getStatus()))
                .collect(Collectors.toList());
        model.addAttribute("livestockList", available);
    }

    private void addAllLivestockToModel(Model model) {
        model.addAttribute("livestockList", livestockRepository.findAll());
    }

    // =========================================================================
    // ABORTIONS
    // =========================================================================

    @GetMapping("/abortions")
    public String listAbortions(Model model) {
        model.addAttribute("abortions", abortionService.getAll());
        model.addAttribute("abortion", new LivestockAbortion());
        addLivestockToModel(model);
        return "livestock-abortions-list";
    }

    @PostMapping("/abortions/new")
    public String saveAbortion(@Valid @ModelAttribute("abortion") LivestockAbortion abortion,
                               BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("abortions", abortionService.getAll());
            addLivestockToModel(model);
            return "livestock-abortions-list";
        }
        abortionService.addNew(abortion);
        ra.addFlashAttribute("success", "Abortion record saved successfully!");
        return "redirect:/livestock/abortions";
    }

    @GetMapping("/abortions/edit/{id}")
    public String editAbortionForm(@PathVariable UUID id, Model model) {
        Optional<LivestockAbortion> opt = abortionService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/abortions";
        LivestockAbortion a = opt.get();
        if (a.getLivestock() != null) a.setLivestockIdValue(a.getLivestock().getId().toString());
        model.addAttribute("abortion", a);
        addAllLivestockToModel(model);
        return "livestock-abortion-edit";
    }

    @PostMapping("/abortions/update/{id}")
    public String updateAbortion(@PathVariable UUID id,
                                 @Valid @ModelAttribute("abortion") LivestockAbortion abortion,
                                 BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            addAllLivestockToModel(model);
            return "livestock-abortion-edit";
        }
        abortionService.update(id, abortion);
        ra.addFlashAttribute("success", "Abortion record updated successfully!");
        return "redirect:/livestock/abortions";
    }

    @PostMapping("/abortions/delete/{id}")
    public String deleteAbortion(@PathVariable UUID id, RedirectAttributes ra) {
        abortionService.delete(id);
        ra.addFlashAttribute("success", "Abortion record deleted.");
        return "redirect:/livestock/abortions";
    }

    // =========================================================================
    // DEATHS
    // =========================================================================

    @GetMapping("/deaths")
    public String listDeaths(Model model) {
        model.addAttribute("deaths", deathService.getAll());
        model.addAttribute("death", new LivestockDeath());
        addLivestockToModel(model);
        return "livestock-deaths-list";
    }

    @PostMapping("/deaths/new")
    public String saveDeath(@Valid @ModelAttribute("death") LivestockDeath death,
                            BindingResult result, Model model, RedirectAttributes ra) {
        if (death.getLivestockIdValue() == null || death.getLivestockIdValue().trim().isEmpty()) {
            result.rejectValue("livestockIdValue", "error.death", "Animal is required");
        }
        if (result.hasErrors()) {
            model.addAttribute("deaths", deathService.getAll());
            model.addAttribute("error", "Please fix the errors below.");
            addLivestockToModel(model);
            return "livestock-deaths-list";
        }
        try {
            deathService.addNew(death);
            ra.addFlashAttribute("success",
                    "Death recorded successfully! Animal status has been updated to DEAD.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/deaths";
    }

    @GetMapping("/deaths/edit/{id}")
    public String editDeathForm(@PathVariable UUID id, Model model) {
        Optional<LivestockDeath> opt = deathService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/deaths";
        LivestockDeath d = opt.get();
        if (d.getLivestock() != null) d.setLivestockIdValue(d.getLivestock().getId().toString());
        model.addAttribute("death", d);
        addAllLivestockToModel(model);
        return "livestock-death-edit";
    }

    @PostMapping("/deaths/update/{id}")
    public String updateDeath(@PathVariable UUID id,
                              @Valid @ModelAttribute("death") LivestockDeath death,
                              BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            addAllLivestockToModel(model);
            return "livestock-death-edit";
        }
        try {
            deathService.update(id, death);
            ra.addFlashAttribute("success", "Death record updated successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/deaths";
    }

    @PostMapping("/deaths/delete/{id}")
    public String deleteDeath(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            deathService.delete(id);
            ra.addFlashAttribute("success", "Death record deleted. Animal status restored to ACTIVE.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/livestock/deaths";
    }

    // =========================================================================
    // SALES
    // =========================================================================

    @GetMapping("/sales")
    public String listSales(Model model) {
        model.addAttribute("sales", saleService.getAll());
        model.addAttribute("sale", new LivestockSale());
        addLivestockToModel(model);
        return "livestock-sales-list";
    }

    @PostMapping("/sales/new")
    public String saveSale(@Valid @ModelAttribute("sale") LivestockSale sale,
                           BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("sales", saleService.getAll());
            addLivestockToModel(model);
            return "livestock-sales-list";
        }
        try {
            saleService.addNew(sale);
            ra.addFlashAttribute("success", "Sale record saved successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/sales";
    }

    @GetMapping("/sales/edit/{id}")
    public String editSaleForm(@PathVariable UUID id, Model model) {
        Optional<LivestockSale> opt = saleService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/sales";
        LivestockSale s = opt.get();
        if (s.getLivestock() != null) s.setLivestockIdValue(s.getLivestock().getId().toString());
        model.addAttribute("sale", s);
        addAllLivestockToModel(model);
        return "livestock-sale-edit";
    }

    @PostMapping("/sales/update/{id}")
    public String updateSale(@PathVariable UUID id,
                             @Valid @ModelAttribute("sale") LivestockSale sale,
                             BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            addAllLivestockToModel(model);
            return "livestock-sale-edit";
        }
        try {
            saleService.update(id, sale);
            ra.addFlashAttribute("success", "Sale record updated successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/sales";
    }

    @PostMapping("/sales/delete/{id}")
    public String deleteSale(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            saleService.delete(id);
            ra.addFlashAttribute("success", "Sale record deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/livestock/sales";
    }

    // =========================================================================
    // TREATMENTS
    // =========================================================================

    @GetMapping("/treatments")
    public String listTreatments(Model model) {
        model.addAttribute("treatments", treatmentService.getAll());
        model.addAttribute("treatment", new LivestockTreatment());
        addLivestockToModel(model);
        return "livestock-treatments-list";
    }

    @PostMapping("/treatments/new")
    public String saveTreatment(@Valid @ModelAttribute("treatment") LivestockTreatment treatment,
                                BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("treatments", treatmentService.getAll());
            addLivestockToModel(model);
            return "livestock-treatments-list";
        }
        try {
            treatmentService.addNew(treatment);
            ra.addFlashAttribute("success", "Treatment record saved successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/treatments";
    }

    @GetMapping("/treatments/edit/{id}")
    public String editTreatmentForm(@PathVariable UUID id, Model model) {
        Optional<LivestockTreatment> opt = treatmentService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/treatments";
        LivestockTreatment t = opt.get();
        if (t.getLivestock() != null) t.setLivestockIdValue(t.getLivestock().getId().toString());
        model.addAttribute("treatment", t);
        addAllLivestockToModel(model);
        return "livestock-treatment-edit";
    }

    @PostMapping("/treatments/update/{id}")
    public String updateTreatment(@PathVariable UUID id,
                                  @Valid @ModelAttribute("treatment") LivestockTreatment treatment,
                                  BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            addAllLivestockToModel(model);
            return "livestock-treatment-edit";
        }
        try {
            treatmentService.update(id, treatment);
            ra.addFlashAttribute("success", "Treatment record updated successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/treatments";
    }

    @PostMapping("/treatments/delete/{id}")
    public String deleteTreatment(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            treatmentService.delete(id);
            ra.addFlashAttribute("success", "Treatment record deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/livestock/treatments";
    }

    // =========================================================================
    // SICK LIVESTOCK
    // =========================================================================

    @GetMapping("/sick")
    public String listSick(Model model) {
        model.addAttribute("sickRecords", sickService.getAll());
        model.addAttribute("sickRecord", new LivestockSick());
        addLivestockToModel(model);
        return "livestock-sick-list";
    }

    @PostMapping("/sick/new")
    public String saveSick(@Valid @ModelAttribute("sickRecord") LivestockSick sickRecord,
                           BindingResult result, Model model, RedirectAttributes ra) {
        if (sickRecord.getLivestockIdValue() == null
                || sickRecord.getLivestockIdValue().trim().isEmpty()) {
            result.rejectValue("livestockIdValue", "error.sick", "Animal is required");
        }
        if (result.hasErrors()) {
            model.addAttribute("sickRecords", sickService.getAll());
            model.addAttribute("error", "Please fix the errors below.");
            addLivestockToModel(model);
            return "livestock-sick-list";
        }
        try {
            sickService.addNew(sickRecord);
            ra.addFlashAttribute("success",
                    "Sick record saved! Animal status has been updated to SICK.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/sick";
    }

    @GetMapping("/sick/edit/{id}")
    public String editSickForm(@PathVariable UUID id, Model model) {
        Optional<LivestockSick> opt = sickService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/sick";
        LivestockSick s = opt.get();
        if (s.getLivestock() != null) s.setLivestockIdValue(s.getLivestock().getId().toString());
        model.addAttribute("sickRecord", s);
        model.addAttribute("history", sickService.getHistory(id));
        addAllLivestockToModel(model);
        return "livestock-sick-edit";
    }

    @PostMapping("/sick/update/{id}")
    public String updateSick(@PathVariable UUID id,
                             @Valid @ModelAttribute("sickRecord") LivestockSick sickRecord,
                             BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("history", sickService.getHistory(id));
            addAllLivestockToModel(model);
            return "livestock-sick-edit";
        }
        try {
            sickService.update(id, sickRecord);
            ra.addFlashAttribute("success", "Sick record updated successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/sick";
    }

    @PostMapping("/sick/delete/{id}")
    public String deleteSick(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            sickService.delete(id);
            ra.addFlashAttribute("success",
                    "Sick record deleted. Animal status restored to ACTIVE.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/livestock/sick";
    }

    @GetMapping("/sick/quick-status/{id}")
    public String quickStatus(@PathVariable UUID id,
                              @RequestParam("status") String statusStr,
                              @RequestParam(value = "notes", required = false) String notes,
                              RedirectAttributes ra) {
        try {
            LivestockSick.SickStatus newStatus =
                    LivestockSick.SickStatus.valueOf(statusStr.toUpperCase());
            sickService.quickStatusUpdate(id, newStatus, notes);
            ra.addFlashAttribute("success",
                    "Status updated to " + newStatus.name() + " and recorded in history.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", "Invalid status: " + statusStr);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error updating status: " + e.getMessage());
        }
        return "redirect:/livestock/sick";
    }

    @GetMapping("/sick/history/{id}")
    public String viewHistory(@PathVariable UUID id, Model model) {
        Optional<LivestockSick> opt = sickService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/sick";
        model.addAttribute("sickRecord", opt.get());
        model.addAttribute("history", sickService.getHistory(id));
        return "livestock-sick-history";
    }

    // =========================================================================
    // SICK REPORT  —  GET /livestock/sick/report
    // =========================================================================

    @GetMapping("/sick/report")
    public String sickReport(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to",   required = false) String toStr,
            Model model) {

        // ── Parse / default dates ──────────────────────────────────────
        LocalDate fromDate = (fromStr != null && !fromStr.isBlank())
                ? LocalDate.parse(fromStr)
                : LocalDate.now().withDayOfMonth(1);

        LocalDate toDate = (toStr != null && !toStr.isBlank())
                ? LocalDate.parse(toStr)
                : LocalDate.now();

        LocalDateTime fromDt = fromDate.atStartOfDay();
        LocalDateTime toDt   = toDate.atTime(23, 59, 59);

        int year = fromDate.getYear();

        // ── All history events in the selected period ──────────────────
        List<LivestockSickHistory> allHistory =
                sickService.getHistoryInRange(fromDt, toDt);

        // ── Split by status ────────────────────────────────────────────
        List<LivestockSickHistory> sickCases =
                sickService.getSickCasesInRange(fromDt, toDt);

        List<LivestockSickHistory> criticalCases =
                sickService.getCriticalCasesInRange(fromDt, toDt);

        List<LivestockSickHistory> recoveredCases =
                sickService.getRecoveredCasesInRange(fromDt, toDt);

        // ── Recovering: derived from allHistory ────────────────────────
        List<LivestockSickHistory> recoveringCases = allHistory.stream()
                .filter(h -> h.getStatus() == LivestockSick.SickStatus.RECOVERING)
                .collect(Collectors.toList());

        // ── Sick episodes with full history (journey cards section) ────
        List<LivestockSick> sickRecords =
                sickRepository.findByReportedDateBetweenWithHistory(fromDate, toDate);

        // ── Year totals ────────────────────────────────────────────────
        long yearSick      = sickService.countSickByYear(year);
        long yearCritical  = sickService.countCriticalByYear(year);
        long yearRecovered = sickService.countRecoveredByYear(year);

        // ── Push all to model ──────────────────────────────────────────
        model.addAttribute("fromDate",        fromDate);
        model.addAttribute("toDate",          toDate);
        model.addAttribute("allHistory",      allHistory);
        model.addAttribute("sickCases",       sickCases);
        model.addAttribute("criticalCases",   criticalCases);
        model.addAttribute("recoveringCases", recoveringCases);
        model.addAttribute("recoveredCases",  recoveredCases);
        model.addAttribute("sickRecords",     sickRecords);
        model.addAttribute("yearSick",        yearSick);
        model.addAttribute("yearCritical",    yearCritical);
        model.addAttribute("yearRecovered",   yearRecovered);

        return "livestock-sick-report";
    }
}
