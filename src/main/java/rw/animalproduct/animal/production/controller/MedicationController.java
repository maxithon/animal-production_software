package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Medication;
import rw.animalproduct.animal.production.repository.MedicationRepository;
import rw.animalproduct.animal.production.services.MedicationService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/medications")
public class MedicationController {

    private static final Logger log = LoggerFactory.getLogger(MedicationController.class);

    private final MedicationService    medicationService;
    private final MedicationRepository medicationRepository;

    public MedicationController(MedicationService medicationService,
                                MedicationRepository medicationRepository) {
        this.medicationService    = medicationService;
        this.medicationRepository = medicationRepository;
    }

    // ── List ──────────────────────────────────────────────────────────────────
    @GetMapping
    public String list(Model model) {
        populateListModel(model);
        return "medications-list";
    }

    // ── Create ────────────────────────────────────────────────────────────────
    @PostMapping("/new")
    public String save(@Valid @ModelAttribute("medication") Medication medication,
                       BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            populateListModel(model);
            model.addAttribute("openAddForm", true);
            return "medications-list";
        }
        try {
            medicationService.save(medication);
            ra.addFlashAttribute("success",
                    "Medication \"" + medication.getName() + "\" added successfully.");
        } catch (Exception e) {
            log.error("Failed to save medication", e);
            ra.addFlashAttribute("error", "Could not save: " + e.getMessage());
        }
        return "redirect:/medications";
    }

    // ── Edit ──────────────────────────────────────────────────────────────────
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable UUID id, Model model, RedirectAttributes ra) {
        Optional<Medication> opt = medicationService.getById(id);
        if (opt.isEmpty()) {
            ra.addFlashAttribute("error", "Medication not found.");
            return "redirect:/medications";
        }
        model.addAttribute("medication",  opt.get());
        model.addAttribute("categories",  Medication.MedicationCategory.values());
        model.addAttribute("dosageUnits", Medication.DosageUnit.values());
        return "medication-edit";
    }

    // ── Update ────────────────────────────────────────────────────────────────
    @PostMapping("/update/{id}")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("medication") Medication medication,
                         BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("categories",  Medication.MedicationCategory.values());
            model.addAttribute("dosageUnits", Medication.DosageUnit.values());
            return "medication-edit";
        }
        try {
            medicationService.update(id, medication);
            ra.addFlashAttribute("success", "Medication updated successfully.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update medication {}", id, e);
            ra.addFlashAttribute("error", "Could not update: " + e.getMessage());
        }
        return "redirect:/medications";
    }

    // ── Delete ────────────────────────────────────────────────────────────────
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            medicationService.delete(id);
            ra.addFlashAttribute("success",
                    "Medication permanently deleted. Snapshot kept in audit log.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", "Medication not found.");
        } catch (Exception e) {
            log.error("Failed to delete medication {}", id, e);
            ra.addFlashAttribute("error", "Could not delete: " + e.getMessage());
        }
        return "redirect:/medications";
    }

    // ── Deactivate ────────────────────────────────────────────────────────────
    @PostMapping("/deactivate/{id}")
    public String deactivate(@PathVariable UUID id,
                             @RequestParam(required = false) String reason,
                             RedirectAttributes ra) {
        try {
            medicationService.deactivate(id, reason);
            ra.addFlashAttribute("success", "Medication deactivated.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to deactivate medication {}", id, e);
            ra.addFlashAttribute("error", "Could not deactivate: " + e.getMessage());
        }
        return "redirect:/medications";
    }

    // ── Activate ──────────────────────────────────────────────────────────────
    @PostMapping("/activate/{id}")
    public String activate(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            medicationService.reactivate(id);
            ra.addFlashAttribute("success", "Medication activated.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to activate medication {}", id, e);
            ra.addFlashAttribute("error", "Could not activate: " + e.getMessage());
        }
        return "redirect:/medications";
    }

    // ── AJAX: details for auto-fill ───────────────────────────────────────────
    @GetMapping("/api/{id}")
    @ResponseBody
    public Map<String, Object> getMedicationDetails(@PathVariable UUID id) {
        return medicationService.getById(id).map(med -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id",                med.getId());
            data.put("name",              med.getName());
            data.put("genericName",       med.getGenericName() != null ? med.getGenericName() : "");
            data.put("category",          med.getCategory() != null ? med.getCategory().name() : "");
            data.put("defaultDosage",     med.getDefaultDosage() != null ? med.getDefaultDosage() : "");
            data.put("defaultDosageUnit", med.getDefaultDosageUnit() != null ? med.getDefaultDosageUnit().name() : "");
            return data;
        }).orElse(Map.of());
    }

    // ── AJAX: active medications for dropdowns ────────────────────────────────
    @GetMapping("/api/active")
    @ResponseBody
    public List<Map<String, Object>> getActiveMedications() {
        return medicationService.getActive().stream().map(med -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id",                med.getId());
            m.put("name",              med.getName());
            m.put("genericName",       med.getGenericName());
            m.put("category",          med.getCategory() != null ? med.getCategory().name() : null);
            m.put("defaultDosage",     med.getDefaultDosage());
            m.put("defaultDosageUnit", med.getDefaultDosageUnit() != null
                    ? med.getDefaultDosageUnit().name() : null);
            return m;
        }).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void populateListModel(Model model) {
        List<Medication> meds = medicationService.getAll();

        // ONE bulk query instead of N per-row counts
        Map<UUID, Long> usageCounts = new HashMap<>();
        for (Object[] row : medicationRepository.countAllActiveTreatmentsByMedication()) {
            usageCounts.put((UUID) row[0], ((Number) row[1]).longValue());
        }

        model.addAttribute("medications", meds);
        model.addAttribute("usageCounts", usageCounts);
        model.addAttribute("medication",  new Medication());
        model.addAttribute("categories",  Medication.MedicationCategory.values());
        model.addAttribute("dosageUnits", Medication.DosageUnit.values());
    }
}