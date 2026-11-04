package rw.animalproduct.animal.production.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockAbortion;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.LivestockAbortionRepository;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
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
    private final LivestockRepository livestockRepository;
    private final LivestockAbortionRepository abortionRepository;
    private final LivestockBreedingRepository breedingRepository;

    @Autowired
    public LivestockAbortionController(LivestockAbortionService abortionService,
                                       LivestockRepository livestockRepository,
                                       LivestockAbortionRepository abortionRepository,
                                       LivestockBreedingRepository breedingRepository) {
        this.abortionService = abortionService;
        this.livestockRepository = livestockRepository;
        this.abortionRepository = abortionRepository;
        this.breedingRepository = breedingRepository;
    }

    // ═════════════════════════════════════════════════════════════════
    // LIST
    // ═════════════════════════════════════════════════════════════════
    @GetMapping("/abortions")
    public String listAbortions(Model model) {
        List<LivestockAbortion> abortions =
                abortionRepository.findByIsDeletedFalseOrderByAbortionDateDesc();
        List<Livestock> livestockList = livestockRepository.findAll();

        model.addAttribute("abortions", abortions);
        model.addAttribute("livestockList", livestockList);
        return "livestock-abortions-list";
    }

    // ═════════════════════════════════════════════════════════════════
    // CREATE — auto-fills expectedBirthDate from active breeding
    // ═════════════════════════════════════════════════════════════════
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
            abortion.setIsDeleted(false);

            // ★ Auto-fill expectedBirthDate from active breeding if not provided
            if (expectedBirthDate == null) {
                expectedBirthDate = lookupExpectedDueDate(livestockIdStr);
            }
            abortion.setExpectedBirthDate(expectedBirthDate);

            // Auto-compute stage of pregnancy
            if (expectedBirthDate != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expectedBirthDate);
                if (days < 0)        abortion.setStageOfPregnancy("Post-term");
                else if (days <= 30) abortion.setStageOfPregnancy("Late");
                else if (days <= 60) abortion.setStageOfPregnancy("Mid");
                else                 abortion.setStageOfPregnancy("Early");
            }

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

    /**
     * Looks up the animal's CONFIRMED pregnant breeding record
     * and returns its expected due date.
     */
    private LocalDate lookupExpectedDueDate(String livestockIdStr) {
        if (livestockIdStr == null || livestockIdStr.isBlank()) return null;
        try {
            UUID animalId = UUID.fromString(livestockIdStr);
            List<LivestockBreeding> breedings =
                    breedingRepository.findConfirmedPregnanciesByLivestockId(animalId);
            return breedings.stream()
                    .filter(b -> b.getExpectedDueDate() != null)
                    .map(LivestockBreeding::getExpectedDueDate)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // EDIT FORM
    // ═════════════════════════════════════════════════════════════════
    @GetMapping("/abortions/edit/{id}")
    public String editAbortionForm(@PathVariable UUID id, Model model) {
        LivestockAbortion abortion = abortionService.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid abortion ID: " + id));

        List<Livestock> livestockList = livestockRepository.findAll();

        model.addAttribute("abortion", abortion);
        model.addAttribute("livestockList", livestockList);
        return "livestock-abortions-edit";
    }

    // ═════════════════════════════════════════════════════════════════
    // UPDATE
    // ═════════════════════════════════════════════════════════════════
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

            if (expectedBirthDate == null) {
                expectedBirthDate = lookupExpectedDueDate(livestockIdStr);
            }
            existing.setExpectedBirthDate(expectedBirthDate);

            abortionService.update(id, existing);
            redirectAttributes.addFlashAttribute("success", "Abortion record updated successfully");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update abortion: " + e.getMessage());
        }

        return "redirect:/livestock/abortions";
    }

    // ═════════════════════════════════════════════════════════════════
    // DELETE
    // ═════════════════════════════════════════════════════════════════
    @PostMapping("/abortions/delete/{id}")
    public String deleteAbortion(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            abortionService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Abortion record deleted successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete abortion: " + e.getMessage());
        }
        return "redirect:/livestock/abortions";
    }

    // ═════════════════════════════════════════════════════════════════
    // REPORT — wide default window + full data
    // ═════════════════════════════════════════════════════════════════
    @GetMapping("/abortion-report")
    public String abortionReport(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        // Wide default window — covers ±12 months so future-dated records appear
        final LocalDate fromDate = (from != null) ? from : LocalDate.now().minusMonths(12);
        final LocalDate toDate   = (to   != null) ? to   : LocalDate.now().plusMonths(12);

        List<LivestockAbortion> allAbortions =
                abortionRepository.findByAbortionDateBetween(fromDate, toDate);

        long earlyCount    = allAbortions.stream()
                .filter(a -> "Early".equalsIgnoreCase(a.getStageOfPregnancy())).count();
        long midCount      = allAbortions.stream()
                .filter(a -> "Mid".equalsIgnoreCase(a.getStageOfPregnancy())).count();
        long lateCount     = allAbortions.stream()
                .filter(a -> "Late".equalsIgnoreCase(a.getStageOfPregnancy())).count();
        long postTermCount = allAbortions.stream()
                .filter(a -> "Post-term".equalsIgnoreCase(a.getStageOfPregnancy())).count();

        Map<String, Long> reasonBreakdown = allAbortions.stream()
                .filter(a -> a.getAbortionReason() != null && !a.getAbortionReason().isBlank())
                .collect(Collectors.groupingBy(
                        a -> normaliseReason(a.getAbortionReason()),
                        Collectors.counting()));

        Map<String, Long> categoryBreakdown = allAbortions.stream()
                .filter(a -> a.getLivestock() != null
                        && a.getLivestock().getLivestockCategory() != null)
                .collect(Collectors.groupingBy(
                        a -> a.getLivestock().getLivestockCategory().getName(),
                        Collectors.counting()));

        long distinctAnimals = allAbortions.stream()
                .filter(a -> a.getLivestock() != null)
                .map(a -> a.getLivestock().getId())
                .distinct()
                .count();

        long thisMonthCount = allAbortions.stream()
                .filter(a -> a.getAbortionDate() != null
                        && a.getAbortionDate().getMonthValue() == LocalDate.now().getMonthValue()
                        && a.getAbortionDate().getYear() == LocalDate.now().getYear())
                .count();

        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("abortions", allAbortions);
        model.addAttribute("totalAbortions", allAbortions.size());
        model.addAttribute("distinctAnimals", distinctAnimals);
        model.addAttribute("earlyCount", earlyCount);
        model.addAttribute("midCount", midCount);
        model.addAttribute("lateCount", lateCount);
        model.addAttribute("postTermCount", postTermCount);
        model.addAttribute("reasonBreakdown", reasonBreakdown);
        model.addAttribute("categoryBreakdown", categoryBreakdown);
        model.addAttribute("thisMonthCount", thisMonthCount);

        return "livestock-abortion-report";
    }

    private String normaliseReason(String raw) {
        if (raw == null) return "Other";
        String r = raw.toLowerCase();
        if (r.contains("disease") || r.contains("infection") || r.contains("brucell"))  return "Disease / Infection";
        if (r.contains("nutrition") || r.contains("malnutrition") || r.contains("feed")) return "Nutrition";
        if (r.contains("stress") || r.contains("heat") || r.contains("trauma"))          return "Stress / Trauma";
        if (r.contains("hormonal") || r.contains("genetic"))                              return "Hormonal / Genetic";
        if (r.contains("poison") || r.contains("toxin") || r.contains("chemical"))        return "Toxins";
        if (r.contains("unknown") || r.contains("not specified"))                         return "Unknown";
        return "Other";
    }
}