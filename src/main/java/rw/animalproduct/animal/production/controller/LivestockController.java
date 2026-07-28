package rw.animalproduct.animal.production.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.patches.AsyncConfig;
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
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livestock")
public class LivestockController {

    // NEW: logger so a failed registration email is never silent again.
    private static final Logger log = LoggerFactory.getLogger(LivestockController.class);

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

    @Autowired
    private LifecycleEmailService emailService;

    /**
     * PERFORMANCE FIX (Question 1 — "register livestock is slow to save"):
     * See AsyncConfig for the full explanation. In short: this executor lets
     * us fire the confirmation email off the request thread instead of
     * making the user's browser wait on an SMTP round trip before it can
     * redirect to the new animal's page.
     */
    @Autowired
    @Qualifier(AsyncConfig.NOTIFICATION_EXECUTOR)
    private Executor notificationExecutor;

    // ─────────────────────────────────────────────────────────────────────────
    // LIST PAGE
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/list")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) String filter,
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

                auditLogService.log(
                        "livestock", id, "SOFT_DELETE", "system",
                        before, null, "Animal " + tagNumber + " was soft-deleted"
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

                auditLogService.log(
                        "livestock", id, "DELETE", "system",
                        before, null, "Animal " + tagNumber + " was permanently deleted"
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
    // RESTORE
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/restore/{id}")
    public String restore(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            livestockService.restore(id);

            auditLogService.log(
                    "livestock", id, "RESTORE", "system",
                    null, null, "Animal was restored from soft-delete"
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
    // VIEW
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
        model.addAttribute("auditHistory", auditLogService.getLogsForEntity("livestock", id));

        // ── QUESTION 2: "Weeks Pregnant must be calculated automatically" ──
        // It already is: Livestock.getWeeksPregnant() derives it live from
        // conceptionDate vs. today's date every time it's read — nothing is
        // stored, so it can never go stale. It's exposed to the view template
        // simply as ${livestock.weeksPregnant} (Thymeleaf calls the getter
        // automatically), so no extra model attribute is technically required.
        // We add it explicitly here anyway, under a clearer name, purely so
        // the detail page's Thymeleaf markup reads self-documenting rather
        // than implicit:
        model.addAttribute("weeksPregnant", livestock.getWeeksPregnant());

        return "livestock-view";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VALUATION HISTORY
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
            Optional<Livestock> beforeOpt = livestockService.getByIdIncludingDeleted(id);

            if (locationId != null) {
                locationRepository.findById(locationId).ifPresent(livestock::setLocation);
            }
            livestockService.update(id, livestock);

            beforeOpt.ifPresent(before -> auditLogService.log(
                    "livestock", id, "UPDATE", "system",
                    before, livestock, "Animal " + before.getTagNumber() + " was updated"
            ));

            redirectAttributes.addFlashAttribute("success", "Animal updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating animal: " + e.getMessage());
        }
        return "redirect:/livestock/list";
    }

    /**
     * FIX #1: The register page (livestock-register.html) iterates over
     * ${beneficiariesList}, but this method used to only populate
     * "beneficiaries" — so the dropdown always rendered empty, and nothing
     * could ever be selected. We now populate BOTH attribute names so this
     * page (and any other template still expecting "beneficiaries") works.
     */
    @GetMapping("/register")
    public String registerForm(Model model) {
        List<Beneficiary> beneficiaries = beneficiaryRepository.findAll();

        model.addAttribute("livestock", new Livestock());
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("beneficiaries", beneficiaries);       // kept for compatibility
        model.addAttribute("beneficiariesList", beneficiaries);   // what livestock-register.html actually reads
        model.addAttribute("locations", locationRepository.findAll());
        return "livestock-register";
    }

    /**
     * PERFORMANCE FIX (Question 1 — "register livestock slows down to save"):
     *
     * Root cause: after livestockService.addNew(saved) commits to the
     * database (fast — a couple of indexed inserts), this method used to
     * call emailService.sendAnimalRegisteredNotification(saved) directly,
     * IN-LINE, on the same thread handling the HTTP request. Sending mail
     * means Java has to open a socket to the SMTP server, do the SMTP/TLS
     * handshake, authenticate, and wait for a "250 OK" — that's a real
     * network operation that can easily take 1-5+ seconds, and far longer
     * (or a timeout) if the mail server is briefly slow/unreachable, an app
     * password rotated, or the network throttles port 587. The browser
     * can't redirect to the new animal's page until that finishes, so the
     * whole registration *looked* slow even though the actual save was fast.
     *
     * Fix: submit the email call to notificationExecutor (see AsyncConfig)
     * instead of calling it directly. The controller method returns —
     * and the browser redirects — the instant the DB save + audit log are
     * done; the email goes out a moment later in the background. A slow or
     * failing mail server can no longer add so much as a millisecond to the
     * user-visible save time.
     */
    @PostMapping("/register")
    public String register(@ModelAttribute Livestock livestock,
                           RedirectAttributes redirectAttributes) {
        try {
            Livestock saved = livestockService.addNew(livestock);

            auditLogService.log(
                    "livestock", saved.getId(), "CREATE", "system",
                    null, saved, "New animal registered: " + saved.getTagNumber()
            );

            // CHANGED: now runs on a background thread (notificationExecutor)
            // instead of blocking this request. Still logged as a WARNING
            // (not ERROR — registration itself still succeeded) with the
            // tag number and root cause message if it fails, and it still
            // can never fail or delay the registration flow itself.
            notificationExecutor.execute(() -> {
                try {
                    emailService.sendAnimalRegisteredNotification(saved);
                } catch (Exception emailEx) {
                    log.warn("Registration email failed to send for animal '{}' (id={}): {}",
                            saved.getTagNumber(), saved.getId(), emailEx.getMessage(), emailEx);
                }
            });

            redirectAttributes.addFlashAttribute("success",
                    "Animal registered successfully with tag: " + saved.getTagNumber());
            return "redirect:/livestock/view/" + saved.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error registering animal: " + e.getMessage());
            return "redirect:/livestock/register";
        }
    }

    /**
     * FIX #2: This endpoint did not exist before, which is why the "Last in
     * this category" / suggested-tag UI on the register page always fell
     * back to "No animals registered in this category yet" — the fetch() call
     * in the page's JS was hitting a 404 every time.
     *
     * Logic: pull every non-deleted animal in the given category, match tag
     * numbers against the pattern "{CATEGORY_CODE}-{number}" (e.g. GOA-021),
     * find the highest existing number, and suggest the next one. If the
     * category has no tags yet, we still suggest a sensible first tag
     * ({CODE}-001) instead of just giving up — one less thing for the user
     * to manually figure out.
     */
    @GetMapping("/api/suggest-tag")
    @ResponseBody
    public Map<String, String> suggestTag(@RequestParam UUID categoryId) {
        Map<String, String> result = new HashMap<>();

        Optional<LivestockCategory> categoryOpt = categoryRepository.findById(categoryId);
        if (categoryOpt.isEmpty()) {
            result.put("lastTag", null);
            result.put("suggestedTag", null);
            return result;
        }

        LivestockCategory category = categoryOpt.get();
        String prefix = category.getCode() != null ? category.getCode().trim().toUpperCase() : "TAG";

        List<Livestock> categoryAnimals =
                livestockRepository.findByLivestockCategoryIdAndIsDeletedFalse(categoryId);

        Pattern tagPattern = Pattern.compile("^" + Pattern.quote(prefix) + "-(\\d+)$");

        int maxNumber = 0;
        String lastTag = null;

        for (Livestock animal : categoryAnimals) {
            String tag = animal.getTagNumber();
            if (tag == null) continue;
            Matcher matcher = tagPattern.matcher(tag.trim().toUpperCase());
            if (matcher.matches()) {
                int number = Integer.parseInt(matcher.group(1));
                if (number > maxNumber) {
                    maxNumber = number;
                    lastTag = tag;
                }
            }
        }

        result.put("lastTag", lastTag); // null if this category has no tags yet
        result.put("suggestedTag", String.format("%s-%03d", prefix, maxNumber + 1));

        return result;
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