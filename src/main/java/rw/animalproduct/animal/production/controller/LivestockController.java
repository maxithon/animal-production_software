package rw.animalproduct.animal.production.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;
import rw.animalproduct.animal.production.services.AuditLogService;
import rw.animalproduct.animal.production.services.LivestockBirthService;
import rw.animalproduct.animal.production.services.LivestockService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livestock")
public class LivestockController {

    @Autowired
    private LivestockService livestockService;

    @Autowired
    private LivestockBirthService birthService;

    @Autowired
    private LivestockRepository livestockRepository;

    @Autowired
    private LivestockCategoryRepository categoryRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private AuditLogService auditLogService;

    // ─────────────────────────────────────────────────────────────────────────
    // LIST PAGE
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/list")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Model model) {
        Page<Livestock> livestockPage = livestockService.getPaged(page, size);
        List<Livestock> livestockList = livestockPage.getContent();

        // Build maps for additional data
        Map<UUID, LivestockBirth> birthMap = new HashMap<>();
        Map<UUID, Object> treatmentMap = new HashMap<>();
        Map<UUID, Object> abortionMap = new HashMap<>();
        Map<UUID, Object> saleMap = new HashMap<>();
        Map<UUID, Boolean> breedingCapableMap = new HashMap<>();

        LocalDate today = LocalDate.now();

        for (Livestock animal : livestockList) {
            // Check if born on farm
            birthService.getByLivestockId(animal.getId()).stream()
                    .findFirst()
                    .ifPresent(birth -> birthMap.put(animal.getId(), birth));

            // Calculate breeding capability
            if (animal.getBirthDate() != null && animal.getLivestockCategory() != null
                    && animal.getLivestockCategory().getMinBreedingAgeMonths() != null) {
                long ageMonths = ChronoUnit.MONTHS.between(animal.getBirthDate(), today);
                boolean capable = ageMonths >= animal.getLivestockCategory().getMinBreedingAgeMonths();
                breedingCapableMap.put(animal.getId(), capable);
            } else {
                breedingCapableMap.put(animal.getId(), null);
            }
        }

        // Stats
        long totalItems      = livestockPage.getTotalElements();
        long totalActive     = livestockRepository.countByStatus(Livestock.STATUS_ACTIVE);
        long totalSold       = livestockRepository.countByStatus(Livestock.STATUS_SOLD);
        long totalSick       = livestockRepository.countByStatus(Livestock.STATUS_SICK);
        long totalDead       = livestockRepository.countByStatus(Livestock.STATUS_DEAD);
        long totalBornOnFarm = livestockList.stream()
                .filter(a -> Livestock.ACQ_BIRTH.equals(a.getAcquisitionMethod()))
                .count();
        long totalTreatments = 0;  // Implement as needed
        long totalAbortions  = 0;  // Implement as needed

        // ── Deleted count for the "Deleted (n)" button ──────────────────────
        long totalDeleted = livestockService.getAllSoftDeleted().size();

        model.addAttribute("livestockList", livestockList);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", livestockPage.getTotalPages());
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalActive", totalActive);
        model.addAttribute("totalSold", totalSold);
        model.addAttribute("totalSick", totalSick);
        model.addAttribute("totalDead", totalDead);
        model.addAttribute("totalBornOnFarm", totalBornOnFarm);
        model.addAttribute("totalTreatments", totalTreatments);
        model.addAttribute("totalAbortions", totalAbortions);
        model.addAttribute("totalDeleted", totalDeleted);
        model.addAttribute("birthMap", birthMap);
        model.addAttribute("treatmentMap", treatmentMap);
        model.addAttribute("abortionMap", abortionMap);
        model.addAttribute("saleMap", saleMap);
        model.addAttribute("breedingCapableMap", breedingCapableMap);

        return "livestock-list";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SOFT DELETE
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/delete/{id}")
    public String softDelete(@PathVariable UUID id,
                             @RequestParam(required = false) String redirect,
                             RedirectAttributes redirectAttributes) {
        try {
            Optional<Livestock> livestockOpt = livestockService.getByIdIncludingDeleted(id);
            if (livestockOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Animal not found");
            } else {
                String tagNumber = livestockOpt.get().getTagNumber();
                livestockService.softDelete(id);
                redirectAttributes.addFlashAttribute("success",
                        "Animal " + tagNumber + " has been deleted. It can be restored by an administrator if needed.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting animal: " + e.getMessage());
        }
        return "redirect:/livestock/list";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HARD DELETE (Permanent — Admin only)
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/hard-delete/{id}")
    public String hardDelete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            Optional<Livestock> livestockOpt = livestockService.getByIdIncludingDeleted(id);
            if (livestockOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Animal not found");
            } else {
                String tagNumber = livestockOpt.get().getTagNumber();
                livestockService.hardDelete(id);
                redirectAttributes.addFlashAttribute("success",
                        "Animal " + tagNumber + " has been permanently removed from the database.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error permanently deleting animal: " + e.getMessage());
        }
        return "redirect:/livestock/deleted";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESTORE (Recover soft-deleted animal)
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/restore/{id}")
    public String restore(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            livestockService.restore(id);
            redirectAttributes.addFlashAttribute("success", "Animal successfully restored.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error restoring animal: " + e.getMessage());
        }
        return "redirect:/livestock/deleted";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BULK OPERATIONS
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/bulk-delete")
    public String bulkDelete(@RequestParam List<UUID> ids, RedirectAttributes redirectAttributes) {
        try {
            livestockService.bulkSoftDelete(ids);
            redirectAttributes.addFlashAttribute("success",
                    ids.size() + " animal(s) have been deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting animals: " + e.getMessage());
        }
        return "redirect:/livestock/list";
    }

    @PostMapping("/bulk-restore")
    public String bulkRestore(@RequestParam List<UUID> ids, RedirectAttributes redirectAttributes) {
        try {
            livestockService.bulkRestore(ids);
            redirectAttributes.addFlashAttribute("success",
                    ids.size() + " animal(s) have been restored.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error restoring animals: " + e.getMessage());
        }
        return "redirect:/livestock/deleted";
    }

    @PostMapping("/bulk-hard-delete")
    public String bulkHardDelete(@RequestParam List<UUID> ids, RedirectAttributes redirectAttributes) {
        try {
            ids.forEach(id -> livestockService.hardDelete(id));
            redirectAttributes.addFlashAttribute("success",
                    ids.size() + " animal(s) permanently deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error permanently deleting animals: " + e.getMessage());
        }
        return "redirect:/livestock/deleted";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SOFT DELETED ANIMALS LIST (Admin page)
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/deleted")
    public String listDeleted(Model model) {
        List<Livestock> deletedAnimals = livestockService.getAllSoftDeleted();
        model.addAttribute("deletedAnimals", deletedAnimals);
        model.addAttribute("count", deletedAnimals.size());
        return "livestock-deleted-list";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VIEW, EDIT, REGISTER PAGES
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/view/{id}")
    public String view(@PathVariable UUID id, Model model) {
        Optional<Livestock> livestockOpt = livestockService.getById(id);
        if (livestockOpt.isEmpty()) {
            return "redirect:/livestock/list?error=notfound";
        }
        model.addAttribute("livestock", livestockOpt.get());
        return "livestock-view";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable UUID id, Model model) {
        Optional<Livestock> livestockOpt = livestockService.getById(id);
        if (livestockOpt.isEmpty()) {
            return "redirect:/livestock/list?error=notfound";
        }
        model.addAttribute("livestock", livestockOpt.get());
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("beneficiaries", beneficiaryRepository.findAll());
        model.addAttribute("locations", locationRepository.findAll());
        return "livestock-edit";
    }

    @PostMapping("/edit/{id}")
    public String update(@PathVariable UUID id, @ModelAttribute Livestock livestock,
                         RedirectAttributes redirectAttributes) {
        try {
            livestockService.update(id, livestock);
            redirectAttributes.addFlashAttribute("success", "Animal updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating animal: " + e.getMessage());
        }
        return "redirect:/livestock/view/" + id;
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("livestock", new Livestock());
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("beneficiaries", beneficiaryRepository.findAll());
        model.addAttribute("locations", locationRepository.findAll());
        return "livestock-register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute Livestock livestock,
                           RedirectAttributes redirectAttributes) {
        try {
            Livestock saved = livestockService.addNew(livestock);
            redirectAttributes.addFlashAttribute("success",
                    "Animal registered successfully with tag: " + saved.getTagNumber());
            return "redirect:/livestock/view/" + saved.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error registering animal: " + e.getMessage());
            return "redirect:/livestock/register";
        }
    }
}
