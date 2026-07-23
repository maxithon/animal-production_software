package rw.animalproduct.animal.production.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockAbortion;
import rw.animalproduct.animal.production.repository.LivestockAbortionRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.services.LivestockAbortionService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/livestock")
public class LivestockAbortionController {

    private final LivestockAbortionService abortionService;
    private final LivestockRepository livestockRepository;
    private final LivestockAbortionRepository abortionRepository;

    @Autowired
    public LivestockAbortionController(LivestockAbortionService abortionService,
                                       LivestockRepository livestockRepository,
                                       LivestockAbortionRepository abortionRepository) {
        this.abortionService = abortionService;
        this.livestockRepository = livestockRepository;
        this.abortionRepository = abortionRepository;
    }

    /**
     * GET /livestock/abortions - List all abortion records
     */
    @GetMapping("/abortions")
    public String listAbortions(Model model) {
        // Get all non-deleted abortions
        List<LivestockAbortion> abortions = abortionRepository.findByIsDeletedFalseOrderByAbortionDateDesc();

        // Get all non-deleted livestock for the dropdown - using findAll() which already excludes deleted
        List<Livestock> livestockList = livestockRepository.findAll();

        model.addAttribute("abortions", abortions);
        model.addAttribute("livestockList", livestockList);

        return "livestock-abortions-list";
    }

    /**
     * POST /livestock/abortions/new - Create a new abortion record
     */
    @PostMapping("/abortions/new")
    public String createAbortion(
            @RequestParam("livestockId") String livestockIdStr,
            @RequestParam("abortionDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate abortionDate,
            @RequestParam(value = "pregnancyNumber", required = false) Integer pregnancyNumber,
            @RequestParam(value = "expectedBirthDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedBirthDate,
            RedirectAttributes redirectAttributes) {

        try {
            LivestockAbortion abortion = new LivestockAbortion();
            abortion.setLivestockIdValue(livestockIdStr);
            abortion.setAbortionDate(abortionDate);
            abortion.setPregnancyNumber(pregnancyNumber);
            abortion.setExpectedBirthDate(expectedBirthDate);
            abortion.setIsDeleted(false);

            // Auto-set stage of pregnancy based on expected birth date
            if (expectedBirthDate != null) {
                LocalDate now = LocalDate.now();
                long daysDifference = java.time.temporal.ChronoUnit.DAYS.between(now, expectedBirthDate);
                if (daysDifference < 0) {
                    abortion.setStageOfPregnancy("Post-term");
                } else if (daysDifference <= 30) {
                    abortion.setStageOfPregnancy("Late");
                } else if (daysDifference <= 60) {
                    abortion.setStageOfPregnancy("Mid");
                } else {
                    abortion.setStageOfPregnancy("Early");
                }
            }

            LivestockAbortion saved = abortionService.addNew(abortion);
            redirectAttributes.addFlashAttribute("success",
                    "Abortion recorded successfully for animal: " +
                            (saved.getLivestock() != null ? saved.getLivestock().getTagNumber() : livestockIdStr));

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to record abortion: " + e.getMessage());
        }

        return "redirect:/livestock/abortions";
    }

    /**
     * GET /livestock/abortions/edit/{id} - Show edit form
     */
    @GetMapping("/abortions/edit/{id}")
    public String editAbortionForm(@PathVariable UUID id, Model model) {
        LivestockAbortion abortion = abortionService.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid abortion ID: " + id));

        List<Livestock> livestockList = livestockRepository.findAll();

        model.addAttribute("abortion", abortion);
        model.addAttribute("livestockList", livestockList);

        return "livestock-abortions-edit";
    }

    /**
     * POST /livestock/abortions/edit/{id} - Update an abortion record
     */
    @PostMapping("/abortions/edit/{id}")
    public String updateAbortion(
            @PathVariable UUID id,
            @RequestParam("livestockId") String livestockIdStr,
            @RequestParam("abortionDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate abortionDate,
            @RequestParam(value = "pregnancyNumber", required = false) Integer pregnancyNumber,
            @RequestParam(value = "expectedBirthDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedBirthDate,
            @RequestParam(value = "abortionReason", required = false) String abortionReason,
            @RequestParam(value = "stageOfPregnancy", required = false) String stageOfPregnancy,
            RedirectAttributes redirectAttributes) {

        try {
            LivestockAbortion existing = abortionService.getById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid abortion ID: " + id));

            existing.setLivestockIdValue(livestockIdStr);
            existing.setAbortionDate(abortionDate);
            existing.setPregnancyNumber(pregnancyNumber);
            existing.setExpectedBirthDate(expectedBirthDate);
            existing.setAbortionReason(abortionReason);
            existing.setStageOfPregnancy(stageOfPregnancy);

            LivestockAbortion updated = abortionService.update(id, existing);
            redirectAttributes.addFlashAttribute("success",
                    "Abortion record updated successfully");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to update abortion: " + e.getMessage());
        }

        return "redirect:/livestock/abortions";
    }

    /**
     * POST /livestock/abortions/delete/{id} - Soft delete an abortion record
     */
    @PostMapping("/abortions/delete/{id}")
    public String deleteAbortion(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            abortionService.delete(id);
            redirectAttributes.addFlashAttribute("success",
                    "Abortion record deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to delete abortion: " + e.getMessage());
        }
        return "redirect:/livestock/abortions";
    }
}