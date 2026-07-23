package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.LivestockSick;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.services.LivestockSickService;

import java.util.UUID;

/**
 * ✅ THIS FILE IS WHAT WAS MISSING (or broken) and causing:
 *   "No static resource livestock/sick" — a 404, not a compile error.
 *
 * That specific error means Spring received GET /livestock/sick, found no
 * @Controller method mapped to that exact path, and fell back to trying to
 * serve it as a static file (like /images/logo.png), which obviously doesn't
 * exist either. The fix is this controller existing, correctly annotated,
 * and correctly mapped — not a change to the entity, repository, service, or
 * HTML files, which is why fixing those didn't help.
 *
 * Every endpoint below is reconstructed directly from the th:action / href
 * values actually used in livestock-sick-list.html and livestock-sick-edit.html:
 *   GET  /livestock/sick               → list page
 *   POST /livestock/sick/new           → create
 *   GET  /livestock/sick/edit/{id}     → edit form
 *   POST /livestock/sick/update/{id}   → update
 *   POST /livestock/sick/delete/{id}   → soft-delete
 *
 * If your original controller had additional endpoints (e.g. a history page,
 * a report page, quick-status-update buttons) that aren't referenced by
 * these two templates, they won't be here — paste the original file (or
 * whatever additional templates reference other /livestock/sick/* paths)
 * and I'll fold them back in.
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

    // ── LIST ─────────────────────────────────────────────────────────
    @GetMapping
    public String list(Model model) {
        model.addAttribute("sickRecords", sickService.getAllActive());
        model.addAttribute("sickRecord", new LivestockSick());
        // Kept for backward compatibility in case any other fragment still
        // references it; the list/edit forms no longer need it since they
        // use the /livestock/search live-search widget instead.
        model.addAttribute("livestockList", livestockRepository.findAll());
        return "livestock-sick-list";
    }

    // ── CREATE ───────────────────────────────────────────────────────
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

    // ── EDIT FORM ────────────────────────────────────────────────────
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

    // ── UPDATE ───────────────────────────────────────────────────────
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

    // ── DELETE (soft-delete) ─────────────────────────────────────────
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
}