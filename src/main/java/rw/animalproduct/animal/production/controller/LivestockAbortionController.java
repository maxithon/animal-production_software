package rw.animalproduct.animal.production.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.LivestockAbortion;
import rw.animalproduct.animal.production.repository.LivestockAbortionRepository;
import rw.animalproduct.animal.production.services.LivestockAbortionService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livestock")
public class LivestockAbortionController {

    private final LivestockAbortionService abortionService;
    private final LivestockAbortionRepository abortionRepository;

    @Autowired
    public LivestockAbortionController(LivestockAbortionService abortionService,
                                       LivestockAbortionRepository abortionRepository) {
        this.abortionService = abortionService;
        this.abortionRepository = abortionRepository;
    }

    @GetMapping("/abortions")
    public String listAbortions(Model model) {
        List<LivestockAbortion> abortions =
                abortionRepository.findByIsDeletedFalseOrderByAbortionDateDesc();
        model.addAttribute("abortions", abortions);
        return "livestock-abortions-list";
    }

    @GetMapping("/abortions/api/search")
    @ResponseBody
    public List<Map<String, Object>> searchPregnantAnimals(
            @RequestParam(value = "q", defaultValue = "") String q,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return abortionService.searchPregnantForPicker(q, limit);
    }

    @GetMapping("/abortions/api/lookup/{animalId}")
    @ResponseBody
    public Map<String, Object> lookupAnimal(@PathVariable UUID animalId) {
        return abortionService.lookupAnimal(animalId);
    }

    @PostMapping("/abortions/new")
    public String createAbortion(
            @RequestParam("livestockId") String livestockIdStr,
            @RequestParam("abortionDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate abortionDate,
            @RequestParam(value = "pregnancyNumber", required = false) Integer pregnancyNumber,
            @RequestParam(value = "expectedBirthDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedBirthDate,
            @RequestParam(value = "abortionReason", required = false) String abortionReason,
            RedirectAttributes redirectAttributes) {
        try {
            LivestockAbortion abortion = new LivestockAbortion();
            abortion.setLivestockIdValue(livestockIdStr);
            abortion.setAbortionDate(abortionDate);
            abortion.setPregnancyNumber(pregnancyNumber);
            abortion.setAbortionReason(abortionReason);
            abortion.setExpectedBirthDate(expectedBirthDate);
            abortion.setIsDeleted(false);

            LivestockAbortion saved = abortionService.addNew(abortion);

            redirectAttributes.addFlashAttribute("success",
                    "Abortion recorded successfully for animal: " +
                            (saved.getLivestock() != null
                                    ? saved.getLivestock().getTagNumber()
                                    : livestockIdStr));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to record abortion: " + e.getMessage());
        }
        return "redirect:/livestock/abortions";
    }

    @GetMapping("/abortions/edit/{id}")
    public String editAbortionForm(@PathVariable UUID id, Model model) {
        LivestockAbortion abortion = abortionService.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid abortion ID: " + id));
        model.addAttribute("abortion", abortion);
        return "livestock-abortions-edit";
    }

    @PostMapping("/abortions/edit/{id}")
    public String updateAbortion(
            @PathVariable UUID id,
            @RequestParam("livestockId") String livestockIdStr,
            @RequestParam("abortionDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate abortionDate,
            @RequestParam(value = "pregnancyNumber", required = false) Integer pregnancyNumber,
            @RequestParam(value = "expectedBirthDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expectedBirthDate,
            @RequestParam(value = "abortionReason", required = false) String abortionReason,
            @RequestParam(value = "stageOfPregnancy", required = false) String stageOfPregnancy,
            RedirectAttributes redirectAttributes) {
        try {
            LivestockAbortion existing = abortionService.getById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid abortion ID: " + id));
            existing.setLivestockIdValue(livestockIdStr);
            existing.setAbortionDate(abortionDate);
            existing.setPregnancyNumber(pregnancyNumber);
            existing.setAbortionReason(abortionReason);
            existing.setStageOfPregnancy(stageOfPregnancy);
            existing.setExpectedBirthDate(expectedBirthDate);
            abortionService.update(id, existing);
            redirectAttributes.addFlashAttribute("success", "Abortion record updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update abortion: " + e.getMessage());
        }
        return "redirect:/livestock/abortions";
    }

    @PostMapping("/abortions/delete/{id}")
    public String deleteAbortion(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            LivestockAbortion abortion = abortionService.getById(id).orElse(null);
            String tag = (abortion != null && abortion.getLivestock() != null)
                    ? abortion.getLivestock().getTagNumber() : "Unknown";

            abortionService.delete(id);

            redirectAttributes.addFlashAttribute("success",
                    "Abortion record for " + tag + " deleted. The animal has been restored to the Pregnancy Tracking list.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete abortion: " + e.getMessage());
        }
        return "redirect:/livestock/abortions";
    }

    @GetMapping("/abortion-report")
    public String abortionReport(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        final LocalDate fromDate = (from != null) ? from : LocalDate.now().minusMonths(12);
        final LocalDate toDate   = (to   != null) ? to   : LocalDate.now().plusMonths(12);

        List<LivestockAbortion> allAbortions =
                abortionRepository.findByAbortionDateBetween(fromDate, toDate);

        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("abortions", allAbortions);
        model.addAttribute("totalAbortions", allAbortions.size());
        model.addAttribute("distinctAnimals", allAbortions.stream()
                .filter(a -> a.getLivestock() != null).map(a -> a.getLivestock().getId()).distinct().count());
        model.addAttribute("earlyCount", allAbortions.stream()
                .filter(a -> "Early".equalsIgnoreCase(a.getStageOfPregnancy())).count());
        model.addAttribute("midCount", allAbortions.stream()
                .filter(a -> "Mid".equalsIgnoreCase(a.getStageOfPregnancy())).count());
        model.addAttribute("lateCount", allAbortions.stream()
                .filter(a -> "Late".equalsIgnoreCase(a.getStageOfPregnancy())).count());
        model.addAttribute("postTermCount", allAbortions.stream()
                .filter(a -> "Post-term".equalsIgnoreCase(a.getStageOfPregnancy())).count());
        model.addAttribute("reasonBreakdown", allAbortions.stream()
                .filter(a -> a.getAbortionReason() != null && !a.getAbortionReason().isBlank())
                .collect(Collectors.groupingBy(
                        a -> a.getAbortionReason().toLowerCase(),
                        Collectors.counting())));
        model.addAttribute("categoryBreakdown", allAbortions.stream()
                .filter(a -> a.getLivestock() != null && a.getLivestock().getLivestockCategory() != null)
                .collect(Collectors.groupingBy(
                        a -> a.getLivestock().getLivestockCategory().getName(),
                        Collectors.counting())));
        model.addAttribute("thisMonthCount", allAbortions.stream()
                .filter(a -> a.getAbortionDate() != null
                        && a.getAbortionDate().getMonthValue() == LocalDate.now().getMonthValue()
                        && a.getAbortionDate().getYear() == LocalDate.now().getYear())
                .count());

        return "livestock-abortion-report";
    }
}