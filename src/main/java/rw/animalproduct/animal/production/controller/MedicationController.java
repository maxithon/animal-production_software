package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Medication;
import rw.animalproduct.animal.production.services.MedicationService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/medications")
public class MedicationController {

    private final MedicationService medicationService;

    public MedicationController(MedicationService medicationService) {

        this.medicationService = medicationService;
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @GetMapping
    public String list(Model model) {
        model.addAttribute("medications", medicationService.getAll());
        model.addAttribute("medication", new Medication());
        model.addAttribute("categories", Medication.MedicationCategory.values());
        model.addAttribute("dosageUnits", Medication.DosageUnit.values());
        return "medications-list";
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping("/new")
    public String save(@Valid @ModelAttribute("medication") Medication medication,
                       BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("medications", medicationService.getAll());
            model.addAttribute("categories", Medication.MedicationCategory.values());
            model.addAttribute("dosageUnits", Medication.DosageUnit.values());
            return "medications-list";
        }
        medicationService.save(medication);
        ra.addFlashAttribute("success", "Medication \"" + medication.getName() + "\" saved successfully!");
        return "redirect:/medications";
    }

    // ── Edit form ─────────────────────────────────────────────────────────────

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable UUID id, Model model) {
        Optional<Medication> opt = medicationService.getById(id);
        if (opt.isEmpty()) return "redirect:/medications";
        model.addAttribute("medication", opt.get());
        model.addAttribute("categories", Medication.MedicationCategory.values());
        model.addAttribute("dosageUnits", Medication.DosageUnit.values());
        return "medication-edit";
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PostMapping("/update/{id}")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("medication") Medication medication,
                         BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("categories", Medication.MedicationCategory.values());
            model.addAttribute("dosageUnits", Medication.DosageUnit.values());
            return "medication-edit";
        }
        medicationService.update(id, medication);
        ra.addFlashAttribute("success", "Medication updated successfully!");
        return "redirect:/medications";
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            medicationService.delete(id);
            ra.addFlashAttribute("success", "Medication deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: medication may be in use by treatment records.");
        }
        return "redirect:/medications";
    }

    // ── AJAX: medication details for auto-fill ────────────────────────────────
    // Called by the treatment form when user selects a medication from the dropdown.
    // Returns { defaultDosage, defaultDosageUnit } so the form can pre-fill fields.

    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMedicationDetails(@PathVariable UUID id) {
        return medicationService.getById(id).map(med -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id",               med.getId());
            data.put("name",             med.getName());
            data.put("genericName",      med.getGenericName());
            data.put("category",         med.getCategory() != null ? med.getCategory().name() : null);
            data.put("defaultDosage",    med.getDefaultDosage());
            data.put("defaultDosageUnit",med.getDefaultDosageUnit() != null ? med.getDefaultDosageUnit().name() : null);
            return ResponseEntity.ok(data);
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── AJAX: all active medications list (for dynamic dropdowns) ─────────────

    @GetMapping("/api/active")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getActiveMedications() {
        List<Map<String, Object>> list = medicationService.getActive().stream().map(med -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",          med.getId());
            m.put("name",        med.getName());
            m.put("genericName", med.getGenericName());
            m.put("category",    med.getCategory() != null ? med.getCategory().name() : null);
            m.put("defaultDosage",    med.getDefaultDosage());
            m.put("defaultDosageUnit",med.getDefaultDosageUnit() != null ? med.getDefaultDosageUnit().name() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(list);
    }
}
