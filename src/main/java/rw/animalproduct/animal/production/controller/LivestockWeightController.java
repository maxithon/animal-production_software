package rw.animalproduct.animal.production.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockWeightHistory;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.services.LivestockWeightService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/livestock/{livestockId}/weight")
public class LivestockWeightController {

    private static final Logger log = LoggerFactory.getLogger(LivestockWeightController.class);

    private final LivestockWeightService weightService;
    private final LivestockRepository livestockRepository;

    public LivestockWeightController(LivestockWeightService weightService,
                                     LivestockRepository livestockRepository) {
        this.weightService = weightService;
        this.livestockRepository = livestockRepository;
    }

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @GetMapping
    public String history(@PathVariable UUID livestockId, Model model) {
        Livestock animal = livestockRepository.findById(livestockId).orElse(null);
        if (animal == null) return "redirect:/livestock/list?error=notfound";

        List<LivestockWeightHistory> history = weightService.history(livestockId);

        model.addAttribute("animal", animal);
        model.addAttribute("history", history);
        model.addAttribute("latest", weightService.latest(livestockId).orElse(null));
        model.addAttribute("adg", weightService.averageDailyGain(livestockId));
        model.addAttribute("pctChange", weightService.percentChange(livestockId));
        model.addAttribute("totalWeighings", history.size());
        return "livestock-weight-history";
    }

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @GetMapping("/add")
    public String showAddForm(@PathVariable UUID livestockId, Model model) {
        Livestock animal = livestockRepository.findById(livestockId).orElse(null);
        if (animal == null) return "redirect:/livestock/list?error=notfound";

        model.addAttribute("animal", animal);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("record", new LivestockWeightHistory());
        model.addAttribute("lastWeighing", weightService.latest(livestockId).orElse(null));
        return "livestock-weight-add";
    }

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @PostMapping("/add")
    public String saveWeight(@PathVariable UUID livestockId,
                             @RequestParam("weighedOn") String weighedOn,
                             @RequestParam("weightKg") BigDecimal weightKg,
                             @RequestParam(value = "weightMethod", required = false) String weightMethod,
                             @RequestParam(value = "headCount", required = false) Integer headCount,
                             @RequestParam(value = "bodyCondition", required = false) String bodyCondition,
                             @RequestParam(value = "notes", required = false) String notes,
                             Authentication auth,
                             RedirectAttributes redirectAttributes) {
        try {
            String recordedBy = auth != null ? auth.getName() : "system";
            weightService.recordWeight(livestockId, LocalDate.parse(weighedOn), weightKg,
                    weightMethod, headCount, bodyCondition, recordedBy, notes);
            redirectAttributes.addFlashAttribute("success", "Weight recorded successfully.");
        } catch (Exception e) {
            log.warn("[weight] save failed for {}: {}", livestockId, e.getMessage());
            redirectAttributes.addFlashAttribute("error",
                    "Could not save weight: " + e.getMessage());
        }
        return "redirect:/livestock/" + livestockId + "/weight";
    }

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @PostMapping("/delete/{weightId}")
    public String deleteWeight(@PathVariable UUID livestockId,
                               @PathVariable UUID weightId,
                               RedirectAttributes redirectAttributes) {
        try {
            weightService.softDelete(weightId);
            redirectAttributes.addFlashAttribute("success", "Weighing deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Could not delete: " + e.getMessage());
        }
        return "redirect:/livestock/" + livestockId + "/weight";
    }
}