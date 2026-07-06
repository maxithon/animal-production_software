package rw.animalproduct.animal.production.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;
import rw.animalproduct.animal.production.services.AuditLogService;
import rw.animalproduct.animal.production.services.LifecycleEmailService;
import rw.animalproduct.animal.production.services.LivestockBirthService;
import rw.animalproduct.animal.production.services.LivestockService;
import rw.animalproduct.animal.production.services.LivestockValuationService;

import java.math.BigDecimal;
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

    @Autowired
    private LivestockValuationService valuationService; // FAO-standard valuation history

    // NEW: needed to send "animal registered" / "animal updated" emails,
    // the same way births already trigger notifications.
    @Autowired
    private LifecycleEmailService emailService;

    // ─────────────────────────────────────────────────────────────────────────
    // LIST PAGE
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/list")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) String filter, // VALUED | UNVALUED | ACTIVE | SOLD | ...
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

        List<UUID> pageIds = livestockList.stream().map(Livestock::getId).collect(Collectors.toList());
        Map<UUID, LivestockValuation> latestValuationMap = livestockService.getLatestValuationsForIds(pageIds);

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

        List<Livestock> allActiveAnimals = livestockService.getAllIncludingDrafts();
        long totalValued   = allActiveAnimals.stream().filter(a -> a.getCurrentValue() != null).count();
        long totalUnvalued = allActiveAnimals.size() - totalValued;

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
        model.addAttribute("totalValued", totalValued);
        model.addAttribute("totalUnvalued", totalUnvalued);
        model.addAttribute("totalDeleted", totalDeleted);
        model.addAttribute("birthMap", birthMap);
        model.addAttribute("treatmentMap", treatmentMap);
        model.addAttribute("abortionMap", abortionMap);
        model.addAttribute("saleMap", saleMap);
        model.addAttribute("breedingCapableMap", breedingCapableMap);
        model.addAttribute("latestValuationMap", latestValuationMap);
        model.addAttribute("initialFilter", filter);

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
                Livestock before = livestockOpt.get();
                String tagNumber = before.getTagNumber();
                livestockService.softDelete(id);

                // NEW: audit trail for soft delete
                auditLogService.log(
                        "livestock",
                        id,
                        "SOFT_DELETE",
                        "system",
                        before,
                        null,
                        "Animal " + tagNumber + " was soft-deleted"
                );

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
                Livestock before = livestockOpt.get();
                String tagNumber = before.getTagNumber();
                livestockService.hardDelete(id);

                // NEW: audit trail for hard delete
                auditLogService.log(
                        "livestock",
                        id,
                        "DELETE",
                        "system",
                        before,
                        null,
                        "Animal " + tagNumber + " was permanently deleted"
                );

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

            // NEW: audit trail for restore
            auditLogService.log(
                    "livestock",
                    id,
                    "RESTORE",
                    "system",
                    null,
                    null,
                    "Animal was restored from soft-delete"
            );

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
            for (UUID id : ids) {
                livestockService.getByIdIncludingDeleted(id).ifPresent(before ->
                        auditLogService.log("livestock", id, "SOFT_DELETE", "system",
                                before, null, "Bulk soft-delete"));
            }
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
            ids.forEach(id -> auditLogService.log("livestock", id, "RESTORE", "system",
                    null, null, "Bulk restore"));
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
            for (UUID id : ids) {
                livestockService.getByIdIncludingDeleted(id).ifPresent(before ->
                        auditLogService.log("livestock", id, "DELETE", "system",
                                before, null, "Bulk hard-delete"));
                livestockService.hardDelete(id);
            }
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

        if (livestock.getBirthDate() != null) {
            long ageDays    = ChronoUnit.DAYS.between(livestock.getBirthDate(), today);
            long ageMonths  = ChronoUnit.MONTHS.between(livestock.getBirthDate(), today);
            model.addAttribute("ageDays", ageDays);
            model.addAttribute("ageMonths", ageMonths);
            model.addAttribute("ageYears", ageMonths / 12);
            model.addAttribute("ageRemainderMonths", ageMonths % 12);
        }

        Boolean breedingCapable = null;
        if (livestock.getBirthDate() != null && livestock.getLivestockCategory() != null
                && livestock.getLivestockCategory().getMinBreedingAgeMonths() != null) {
            long ageMonths = ChronoUnit.MONTHS.between(livestock.getBirthDate(), today);
            breedingCapable = ageMonths >= livestock.getLivestockCategory().getMinBreedingAgeMonths();
        }
        model.addAttribute("breedingCapable", breedingCapable);

        model.addAttribute("lifecycleStage", resolveLifecycleStage(livestock, today));

        model.addAttribute("children", livestockRepository.findByMotherId(id));

        birthService.getByLivestockId(id).stream()
                .findFirst()
                .ifPresent(birth -> model.addAttribute("birthRecord", birth));

        if (livestock.getLocation() != null) {
            model.addAttribute("locationBreadcrumb", buildLocationBreadcrumb(livestock.getLocation()));
        } else {
            model.addAttribute("locationBreadcrumb", new ArrayList<Location>());
        }

        model.addAttribute("valuationHistory", valuationService.getHistory(id));
        model.addAttribute("latestValuation", valuationService.getLatest(id).orElse(null));
        model.addAttribute("valuationChangeSincePrevious", valuationService.changeSincePrevious(id));

        // NEW: this animal's own audit history, so the "Audit Log" link on
        // this page can show only what happened to THIS animal.
        model.addAttribute("auditHistory", auditLogService.getLogsForEntity("livestock", id));

        return "livestock-view";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VALUATION HISTORY (FAO STANDARD) — dedicated page + record endpoint
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/valuation-history")
    public String valuationHistory(@PathVariable UUID id, Model model) {
        Optional<Livestock> livestockOpt = livestockService.getById(id);
        if (livestockOpt.isEmpty()) {
            return "redirect:/livestock/list?error=notfound";
        }
        model.addAttribute("livestock", livestockOpt.get());
        model.addAttribute("valuationHistory", valuationService.getHistory(id));
        model.addAttribute("changeSincePrevious", valuationService.changeSincePrevious(id));
        return "livestock-valuation-history";
    }

    /**
     * The ONLY endpoint that changes an animal's value.
     * Appends a new row to livestock_valuation_history and refreshes the
     * cached current_value on the Livestock row — never overwrites in place.
     * Audit logging AND email notification both happen inside
     * LivestockValuationService.recordValuation(...) so every code path
     * that changes a value (this endpoint, registration's initial valuation,
     * anything added later) gets logged and emailed consistently.
     */
    @PostMapping("/{id}/valuation")
    public String recordValuation(@PathVariable UUID id,
                                  @RequestParam BigDecimal value,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate valuationDate,
                                  @RequestParam String valuationMethod,
                                  @RequestParam(required = false) String notes,
                                  @RequestParam(required = false) String returnTo,
                                  RedirectAttributes redirectAttributes) {
        try {
            valuationService.recordValuation(id, valuationDate, value, valuationMethod, notes, "system");
            redirectAttributes.addFlashAttribute("success", "New valuation recorded successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error recording valuation: " + e.getMessage());
        }

        if ("list".equals(returnTo)) {
            return "redirect:/livestock/list";
        }
        if ("report".equals(returnTo)) {
            return "redirect:/livestock/valuation-report?animalId=" + id;
        }
        return "redirect:/livestock/view/" + id;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VALUATION REPORT
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/valuation-report")
    public String valuationReport(@RequestParam(required = false) UUID animalId, Model model) {
        List<Livestock> allAnimals = livestockService.getAllIncludingDrafts();

        List<Livestock> sortedForSelect = allAnimals.stream()
                .sorted(Comparator.comparing(
                        a -> a.getTagNumber() != null ? a.getTagNumber() : "",
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        long totalValued = allAnimals.stream().filter(a -> a.getCurrentValue() != null).count();
        long totalUnvalued = allAnimals.size() - totalValued;

        List<Livestock> unvaluedAnimals = allAnimals.stream()
                .filter(a -> a.getCurrentValue() == null)
                .sorted(Comparator.comparing(
                        a -> a.getTagNumber() != null ? a.getTagNumber() : "",
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        model.addAttribute("allAnimals", sortedForSelect);
        model.addAttribute("totalValued", totalValued);
        model.addAttribute("totalUnvalued", totalUnvalued);
        model.addAttribute("unvaluedAnimals", unvaluedAnimals);

        if (animalId != null) {
            Optional<Livestock> selectedOpt = livestockService.getById(animalId);
            if (selectedOpt.isPresent()) {
                Livestock selected = selectedOpt.get();
                model.addAttribute("selectedAnimal", selected);
                model.addAttribute("valuationHistory", valuationService.getHistory(animalId));
                model.addAttribute("latestValuation", valuationService.getLatest(animalId).orElse(null));
                model.addAttribute("valuationChangeSincePrevious", valuationService.changeSincePrevious(animalId));
            }
        }

        return "livestock-valuation-report";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LIFECYCLE STAGE HELPER
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
            // NEW: snapshot the animal BEFORE the update so we can log a
            // real before/after diff, not just "something changed".
            Optional<Livestock> beforeOpt = livestockService.getByIdIncludingDeleted(id);

            if (locationId != null) {
                locationRepository.findById(locationId).ifPresent(livestock::setLocation);
            }
            livestockService.update(id, livestock);

            beforeOpt.ifPresent(before -> auditLogService.log(
                    "livestock",
                    id,
                    "UPDATE",
                    "system",
                    before,
                    livestock,
                    "Animal " + before.getTagNumber() + " was updated"
            ));

            redirectAttributes.addFlashAttribute("success", "Animal updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating animal: " + e.getMessage());
        }
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

            // NEW: audit trail for every new animal registered
            auditLogService.log(
                    "livestock",
                    saved.getId(),
                    "CREATE",
                    "system",
                    null,
                    saved,
                    "New animal registered: " + saved.getTagNumber()
            );

            // NEW: same idea as the newborn-notification email — notify on
            // registration too. Wrapped so a mail failure never blocks
            // registration itself.
            try {
                emailService.sendAnimalRegisteredNotification(saved);
            } catch (Exception emailEx) {
                // swallow — registration must still succeed even if email fails
            }

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
    // ─────────────────────────────────────────────────────────────────────────
    private List<Location> buildLocationBreadcrumb(Location location) {
        List<Location> chain = new ArrayList<>();
        Location current = location;
        while (current != null) {
            chain.add(0, current);
            current = current.getParent();
        }
        return chain;
    }
}
