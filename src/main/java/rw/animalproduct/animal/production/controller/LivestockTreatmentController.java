package rw.animalproduct.animal.production.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.LivestockTreatment;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.MedicationRepository;
import rw.animalproduct.animal.production.services.LivestockTreatmentService;

import java.time.LocalDate;
import java.util.UUID;

@Controller
@RequestMapping("/livestock/treatments")
public class LivestockTreatmentController {

    // Cap page size server-side so nobody can request ?size=100000 and load
    // the whole table into memory / render a 20,000-row <table>.
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 15;

    private final LivestockTreatmentService treatmentService;
    private final LivestockRepository       livestockRepository;
    private final MedicationRepository      medicationRepository;

    public LivestockTreatmentController(
            LivestockTreatmentService treatmentService,
            LivestockRepository livestockRepository,
            MedicationRepository medicationRepository
    ) {
        this.treatmentService     = treatmentService;
        this.livestockRepository  = livestockRepository;
        this.medicationRepository = medicationRepository;
    }

    // ── LIST (paginated + filtered) ─────────────────────────────────────────────
    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @RequestParam(required = false) LivestockTreatment.TreatmentStatus status,
            @RequestParam(required = false) LivestockTreatment.TreatmentCategory type,
            @RequestParam(required = false) UUID livestockId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Boolean isPaid,
            @RequestParam(required = false) String search,
            Model model
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "treatmentDate"));

        LivestockTreatmentService.TreatmentFilter filter = new LivestockTreatmentService.TreatmentFilter();
        filter.status = status;
        filter.type = type;
        filter.livestockId = livestockId;
        filter.fromDate = fromDate;
        filter.toDate = toDate;
        filter.isPaid = isPaid;
        filter.search = search;

        Page<LivestockTreatment> treatmentPage = treatmentService.getPage(filter, pageable);

        model.addAttribute("treatments", treatmentPage.getContent());
        model.addAttribute("treatmentPage", treatmentPage);
        model.addAttribute("currentPage", treatmentPage.getNumber());
        model.addAttribute("totalPages", treatmentPage.getTotalPages());
        model.addAttribute("totalElements", treatmentPage.getTotalElements());
        model.addAttribute("pageSize", safeSize);

        // Echo filters back so the form stays populated after a search
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterType", type);
        model.addAttribute("filterLivestockId", livestockId);
        model.addAttribute("filterFromDate", fromDate);
        model.addAttribute("filterToDate", toDate);
        model.addAttribute("filterIsPaid", isPaid);
        model.addAttribute("filterSearch", search);

        model.addAttribute("stats", treatmentService.getStats());

        // Dropdown data + blank form-backing object for the "new treatment" form
        model.addAttribute("livestockList", livestockRepository.findAll());
        model.addAttribute("medicationList", medicationRepository.findAll());
        if (!model.containsAttribute("treatment")) {
            model.addAttribute("treatment", new LivestockTreatment());
        }

        return "livestock-treatments-list";
    }

    // ── CREATE ───────────────────────────────────────────────────────────────
    @PostMapping("/new")
    public String create(
            @ModelAttribute("treatment") LivestockTreatment treatment,
            RedirectAttributes redirectAttributes
    ) {
        try {
            treatmentService.addNew(treatment);
            redirectAttributes.addFlashAttribute("success", "Treatment record saved successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not save treatment record: " + e.getMessage());
        }
        return "redirect:/livestock/treatments";
    }

    // ── EDIT (form) ──────────────────────────────────────────────────────────
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable UUID id, Model model) {
        return treatmentService.getById(id)
                .map(t -> {
                    // Pre-populate the transient *_IdValue fields the edit form binds to
                    if (t.getLivestock() != null) t.setLivestockIdValue(t.getLivestock().getId().toString());
                    if (t.getMedication() != null) t.setMedicationIdValue(t.getMedication().getId().toString());
                    if (t.getVeterinarian() != null) t.setVeterinarianIdValue(t.getVeterinarian().getId().toString());

                    model.addAttribute("treatment", t);
                    model.addAttribute("livestockList", livestockRepository.findAll());
                    model.addAttribute("medicationList", medicationRepository.findAll());
                    return "livestock-treatment-edit";
                })
                .orElse("redirect:/livestock/treatments");
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────
    @PostMapping("/update/{id}")
    public String update(
            @PathVariable UUID id,
            @ModelAttribute("treatment") LivestockTreatment treatment,
            RedirectAttributes redirectAttributes
    ) {
        try {
            treatmentService.update(id, treatment);
            redirectAttributes.addFlashAttribute("success", "Treatment record updated successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not update treatment record: " + e.getMessage());
        }
        return "redirect:/livestock/treatments";
    }

    // ── DELETE (soft) ────────────────────────────────────────────────────────
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        treatmentService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Treatment record deleted.");
        return "redirect:/livestock/treatments";
    }
}