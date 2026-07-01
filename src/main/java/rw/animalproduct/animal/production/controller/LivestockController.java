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

        Map<UUID, LivestockBirth> birthMap = new HashMap<>();
        Map<UUID, Object> treatmentMap = new HashMap<>();
        Map<UUID, Object> abortionMap = new HashMap<>();
        Map<UUID, Object> saleMap = new HashMap<>();
        Map<UUID, Boolean> breedingCapableMap = new HashMap<>();

        LocalDate today = LocalDate.now();

        for (Livestock animal : livestockList) {
            birthService.getByLivestockId(animal.getId()).stream()
                    .findFirst()
                    .ifPresent(birth -> birthMap.put(animal.getId(), birth));

            if (animal.getBirthDate() != null && animal.getLivestockCategory() != null
                    && animal.getLivestockCategory().getMinBreedingAgeMonths() != null) {
                long ageMonths = ChronoUnit.MONTHS.between(animal.getBirthDate(), today);
                boolean capable = ageMonths >= animal.getLivestockCategory().getMinBreedingAgeMonths();
                breedingCapableMap.put(animal.getId(), capable);
            } else {
                breedingCapableMap.put(animal.getId(), null);
            }
        }

        long totalItems      = livestockPage.getTotalElements();
        long totalActive     = livestockRepository.countByStatus(Livestock.STATUS_ACTIVE);
        long totalSold       = livestockRepository.countByStatus(Livestock.STATUS_SOLD);
        long totalSick       = livestockRepository.countByStatus(Livestock.STATUS_SICK);
        long totalDead       = livestockRepository.countByStatus(Livestock.STATUS_DEAD);
        long totalBornOnFarm = livestockList.stream()
                .filter(a -> Livestock.ACQ_BIRTH.equals(a.getAcquisitionMethod()))
                .count();
        long totalTreatments = 0;
        long totalAbortions  = 0;

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
    // VIEW (single-animal detail page)
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/view/{id}")
    public String view(@PathVariable UUID id, Model model) {
        Optional<Livestock> livestockOpt = livestockService.getById(id);
        if (livestockOpt.isEmpty()) {
            return "redirect:/livestock/list?error=notfound";
        }
        Livestock livestock = livestockOpt.get();
        model.addAttribute("livestock", livestock);

        LocalDate today = LocalDate.now();

        // ── Age breakdown ──────────────────────────────────────────────────
        if (livestock.getBirthDate() != null) {
            long ageDays    = ChronoUnit.DAYS.between(livestock.getBirthDate(), today);
            long ageMonths  = ChronoUnit.MONTHS.between(livestock.getBirthDate(), today);
            model.addAttribute("ageDays", ageDays);
            model.addAttribute("ageMonths", ageMonths);
            model.addAttribute("ageYears", ageMonths / 12);
            model.addAttribute("ageRemainderMonths", ageMonths % 12);
        }

        // ── Breeding capability ────────────────────────────────────────────
        Boolean breedingCapable = null;
        if (livestock.getBirthDate() != null && livestock.getLivestockCategory() != null
                && livestock.getLivestockCategory().getMinBreedingAgeMonths() != null) {
            long ageMonths = ChronoUnit.MONTHS.between(livestock.getBirthDate(), today);
            breedingCapable = ageMonths >= livestock.getLivestockCategory().getMinBreedingAgeMonths();
        }
        model.addAttribute("breedingCapable", breedingCapable);

        // ── Lifecycle stage (for this one animal) ──────────────────────────
        model.addAttribute("lifecycleStage", resolveLifecycleStage(livestock, today));

        // ── Lineage: children born from this animal ────────────────────────
        model.addAttribute("children", livestockRepository.findByMotherId(id));

        // ── Birth record, if this animal was born on the farm ───────────────
        birthService.getByLivestockId(id).stream()
                .findFirst()
                .ifPresent(birth -> model.addAttribute("birthRecord", birth));

        // ── Location breadcrumb (Province > District > Sector > Cell > Village) ──
        if (livestock.getLocation() != null) {
            model.addAttribute("locationBreadcrumb", buildLocationBreadcrumb(livestock.getLocation()));
        } else {
            model.addAttribute("locationBreadcrumb", new ArrayList<Location>());
        }

        return "livestock-view";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE STAGE HELPER
    // Same staging logic implied by the lifecycle badges used elsewhere in the
    // app (NEWBORN / YOUNG / READY_TO_BREED / BREEDING_MALE / PREGNANT / MATURE),
    // computed here for a single animal rather than the whole herd.
    // ─────────────────────────────────────────────────────────────────────────
    private String resolveLifecycleStage(Livestock l, LocalDate today) {
        if (Boolean.TRUE.equals(l.getIsPregnant())) {
            return "PREGNANT";
        }
        if (l.getBirthDate() == null) {
            return "UNKNOWN";
        }

        long ageDays   = ChronoUnit.DAYS.between(l.getBirthDate(), today);
        long ageMonths = ChronoUnit.MONTHS.between(l.getBirthDate(), today);
        Integer minBreedAge = l.getLivestockCategory() != null
                ? l.getLivestockCategory().getMinBreedingAgeMonths() : null;

        if (ageDays <= 30) return "NEWBORN";
        if (ageMonths < 12) return "YOUNG";

        if ("MALE".equalsIgnoreCase(l.getGender())) {
            return (minBreedAge != null && ageMonths >= minBreedAge) ? "BREEDING_MALE" : "MATURE";
        }
        if ("FEMALE".equalsIgnoreCase(l.getGender())) {
            return (minBreedAge != null && ageMonths >= minBreedAge) ? "READY_TO_BREED" : "MATURE";
        }
        return "MATURE";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EDIT, REGISTER PAGES
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable UUID id, Model model) {
        Optional<Livestock> livestockOpt = livestockService.getById(id);
        if (livestockOpt.isEmpty()) {
            return "redirect:/livestock/list?error=notfound";
        }
        Livestock livestock = livestockOpt.get();

        // ── Pre-populate transient ID fields so th:field binding/selection works ──
        if (livestock.getLivestockCategory() != null) {
            livestock.setLivestockCategoryIdValue(livestock.getLivestockCategory().getId().toString());
        }
        if (livestock.getBeneficiary() != null) {
            livestock.setBeneficiaryIdValue(livestock.getBeneficiary().getId().toString());
        }

        model.addAttribute("livestock", livestock);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("beneficiaries", beneficiaryRepository.findAll());
        model.addAttribute("locations", locationRepository.findAll());

        // ── Build Province > District > Sector > Cell > Village breadcrumb ──
        if (livestock.getLocation() != null) {
            model.addAttribute("locationBreadcrumb", buildLocationBreadcrumb(livestock.getLocation()));
        } else {
            model.addAttribute("locationBreadcrumb", new ArrayList<Location>());
        }

        return "livestock-edit";
    }

    @PostMapping("/edit/{id}")
    public String update(@PathVariable UUID id,
                         @ModelAttribute Livestock livestock,
                         @RequestParam(required = false) UUID locationId,
                         RedirectAttributes redirectAttributes) {
        try {
            if (locationId != null) {
                locationRepository.findById(locationId).ifPresent(livestock::setLocation);
            }
            livestockService.update(id, livestock);
            redirectAttributes.addFlashAttribute("success", "Animal updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating animal: " + e.getMessage());
        }
        // ── After edit, go back to the livestock list (not the view page) ──
        return "redirect:/livestock/list";
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

    // ─────────────────────────────────────────────────────────────────────────
    // LOCATION BREADCRUMB HELPER
    // Walks up the self-referencing Location tree (Village -> Cell -> Sector ->
    // District -> Province) via getParent(), and returns the chain ordered
    // from the root (Province) down to the leaf (Village).
    // ─────────────────────────────────────────────────────────────────────────
    private List<Location> buildLocationBreadcrumb(Location location) {
        List<Location> chain = new ArrayList<>();
        Location current = location;
        while (current != null) {
            chain.add(0, current); // prepend so root (Province) ends up first
            current = current.getParent();
        }
        return chain;
    }
}
