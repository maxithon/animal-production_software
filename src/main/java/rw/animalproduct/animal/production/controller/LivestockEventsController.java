package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSickRepository;
import rw.animalproduct.animal.production.repository.MedicationRepository;
import rw.animalproduct.animal.production.repository.VeterinarianRepository;
import rw.animalproduct.animal.production.services.LivestockSickService;
import rw.animalproduct.animal.production.services.LivestockTreatmentService;

import java.time.LocalDate;
import java.util.UUID;

@Controller
@RequestMapping("/livestock/events")
public class LivestockEventsController {

    private final LivestockSickService sickService;
    private final LivestockTreatmentService treatmentService;
    private final LivestockRepository livestockRepository;
    private final LivestockSickRepository sickRepository;
    private final MedicationRepository medicationRepository;
    private final VeterinarianRepository veterinarianRepository;

    @Autowired
    public LivestockEventsController(
            LivestockSickService sickService,
            LivestockTreatmentService treatmentService,
            LivestockRepository livestockRepository,
            LivestockSickRepository sickRepository,
            MedicationRepository medicationRepository,
            VeterinarianRepository veterinarianRepository) {
        this.sickService = sickService;
        this.treatmentService = treatmentService;
        this.livestockRepository = livestockRepository;
        this.sickRepository = sickRepository;
        this.medicationRepository = medicationRepository;
        this.veterinarianRepository = veterinarianRepository;
    }

    // ── LIST EVENTS ──────────────────────────────────────────────────────────────
    @GetMapping
    public String listEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) UUID livestockId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // For now, just show sick records and treatments combined
        // This is a simplified view - you may want to create a custom DTO or query
        Page<LivestockSick> sickPage = sickRepository.findAll(pageable);

        model.addAttribute("sickRecords", sickPage.getContent());
        model.addAttribute("sickPage", sickPage);
        model.addAttribute("currentPage", sickPage.getNumber());
        model.addAttribute("totalPages", sickPage.getTotalPages());
        model.addAttribute("totalElements", sickPage.getTotalElements());

        model.addAttribute("livestockList", livestockRepository.findAll());

        return "livestock-events-list";
    }

    // ── VIEW SICK DETAIL ──────────────────────────────────────────────────────────
    @GetMapping("/sick/{id}")
    public String viewSickDetail(@PathVariable UUID id, Model model) {
        return sickService.getById(id)
                .map(sick -> {
                    model.addAttribute("sickRecord", sick);
                    model.addAttribute("treatments", treatmentService.getByLivestock(sick.getLivestock().getId()));
                    return "livestock-sick-detail";
                })
                .orElse("redirect:/livestock/events");
    }

    // ── VIEW TREATMENT DETAIL ────────────────────────────────────────────────────
    @GetMapping("/treatment/{id}")
    public String viewTreatmentDetail(@PathVariable UUID id, Model model) {
        return treatmentService.getById(id)
                .map(treatment -> {
                    model.addAttribute("treatment", treatment);
                    return "livestock-treatment-detail";
                })
                .orElse("redirect:/livestock/events");
    }

    // ── MARK SICK ─────────────────────────────────────────────────────────────────
    @GetMapping("/sick/new")
    public String showSickForm(@RequestParam(required = false) UUID livestockId, Model model) {
        LivestockSick sick = new LivestockSick();
        if (livestockId != null) {
            livestockRepository.findById(livestockId).ifPresent(sick::setLivestock);
        }
        model.addAttribute("sickRecord", sick);
        model.addAttribute("livestockList", livestockRepository.findAll());
        return "livestock-sick-form";
    }

    @PostMapping("/sick/new")
    public String createSick(
            @Valid @ModelAttribute("sickRecord") LivestockSick sick,
            BindingResult result,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fix the errors in the form.");
            return "livestock-sick-form";
        }

        try {
            sickService.addNew(sick);
            redirectAttributes.addFlashAttribute("success", "Sick record created successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error creating sick record: " + e.getMessage());
            return "livestock-sick-form";
        }

        return "redirect:/livestock/events";
    }

    // ── ADD TREATMENT TO SICK RECORD ─────────────────────────────────────────────
    @GetMapping("/sick/{sickId}/treatment/new")
    public String showTreatmentFormForSick(
            @PathVariable UUID sickId,
            Model model) {

        return sickService.getById(sickId)
                .map(sick -> {
                    LivestockTreatment treatment = new LivestockTreatment();
                    treatment.setSickLivestock(sick);
                    treatment.setLivestock(sick.getLivestock());
                    treatment.setTreatmentDate(LocalDate.now());

                    model.addAttribute("treatment", treatment);
                    model.addAttribute("sickRecord", sick);
                    model.addAttribute("livestockList", livestockRepository.findAll());
                    model.addAttribute("medicationList", medicationRepository.findAll());
                    model.addAttribute("veterinarianList", veterinarianRepository.findAll());
                    return "livestock-treatment-form";
                })
                .orElse("redirect:/livestock/events");
    }

    // ── CREATE TREATMENT FROM SICK RECORD ────────────────────────────────────────
    @PostMapping("/sick/{sickId}/treatment/new")
    public String createTreatmentForSick(
            @PathVariable UUID sickId,
            @Valid @ModelAttribute("treatment") LivestockTreatment treatment,
            BindingResult result,
            RedirectAttributes redirectAttributes) {

        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "Please fix the errors in the form.");
            return "redirect:/livestock/events/sick/" + sickId + "/treatment/new";
        }

        try {
            // Ensure the sick record is set
            sickService.getById(sickId).ifPresent(sick -> {
                treatment.setSickLivestock(sick);
                treatment.setLivestock(sick.getLivestock());
            });

            treatmentService.addNew(treatment);
            redirectAttributes.addFlashAttribute("success", "Treatment added successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error adding treatment: " + e.getMessage());
            return "redirect:/livestock/events/sick/" + sickId + "/treatment/new";
        }

        return "redirect:/livestock/events/sick/" + sickId;
    }

    // ── RECOVER (Mark sick as recovered) ─────────────────────────────────────────
    @PostMapping("/sick/{id}/recover")
    public String markAsRecovered(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            sickService.markAsRecovered(id);
            redirectAttributes.addFlashAttribute("success", "Animal marked as recovered.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error marking as recovered: " + e.getMessage());
        }
        return "redirect:/livestock/events";
    }

    // ── DELETE SICK RECORD ───────────────────────────────────────────────────────
    @PostMapping("/sick/{id}/delete")
    public String deleteSickRecord(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            sickService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Sick record deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting record: " + e.getMessage());
        }
        return "redirect:/livestock/events";
    }

    // ── REMOVED: Duplicate updateTreatment method that conflicted with LivestockTreatmentController ──
    // The update functionality for treatments is now handled exclusively by
    // LivestockTreatmentController at /livestock/treatments/update/{id}
}