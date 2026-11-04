package rw.animalproduct.animal.production.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import rw.animalproduct.animal.production.services.LocationService;
import rw.animalproduct.animal.production.services.TagAllocationService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livestock")
public class LivestockController {

    private static final Logger log = LoggerFactory.getLogger(LivestockController.class);
    private static final List<String> CHANGEABLE_STATUSES = List.of(
            Livestock.STATUS_ACTIVE,
            Livestock.STATUS_SOLD,
            Livestock.STATUS_DEAD,
            Livestock.STATUS_TRANSFERRED
    );

    @Autowired private LivestockService livestockService;
    @Autowired private LivestockBirthService birthService;
    @Autowired private LivestockRepository livestockRepository;
    @Autowired private LivestockCategoryRepository categoryRepository;
    @Autowired private BeneficiaryRepository beneficiaryRepository;
    @Autowired private LocationRepository locationRepository;
    @Autowired private AuditLogService auditLogService;
    @Autowired private LivestockValuationService valuationService;
    @Autowired private LifecycleEmailService emailService;
    @Autowired private LocationService locationService;
    @Autowired private VLivestockWithAgeRepository vLivestockWithAgeRepository;
    @Autowired private TagAllocationService tagAllocationService;

    @Autowired
    @Qualifier(AsyncConfig.NOTIFICATION_EXECUTOR)
    private Executor notificationExecutor;

    // ══════════════════════════════════════════════════════════════════
    // LIST page — livestock-list.html
    // ══════════════════════════════════════════════════════════════════
    @GetMapping("/list")
    public String list(@RequestParam(defaultValue = "0")  int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) String filter,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) UUID   categoryId,
                       @RequestParam(defaultValue = "createdAt") String sort,
                       @RequestParam(defaultValue = "DESC")      String dir,
                       Model model) {

        Set<String> allowedSorts = Set.of(
                "createdAt", "updatedAt", "tagNumber", "birthDate", "currentValue"
        );
        String sortField = allowedSorts.contains(sort) ? sort : "createdAt";
        String sortDir   = "ASC".equalsIgnoreCase(dir) ? "ASC" : "DESC";

        Page<Livestock> livestockPage = livestockService.getPaged(
                page, size, sortField, sortDir, status, categoryId);

        List<Livestock> livestockList = livestockPage.getContent();

        List<UUID> pageIds = livestockList.stream().map(Livestock::getId).collect(Collectors.toList());

        Map<UUID, LivestockBirth> birthMap = birthService.getBirthMapForIds(pageIds);

        Map<UUID, Object> treatmentMap = new HashMap<>();
        Map<UUID, Object> abortionMap = new HashMap<>();
        Map<UUID, Object> saleMap = new HashMap<>();
        Map<UUID, Boolean> breedingCapableMap = new HashMap<>();

        LocalDate today = LocalDate.now();

        for (Livestock animal : livestockList) {
            if (animal.getBirthDate() != null && animal.getLivestockCategory() != null
                    && animal.getLivestockCategory().getMinBreedingAgeMonths() != null) {
                long ageMonths = ChronoUnit.MONTHS.between(animal.getBirthDate(), today);
                boolean capable = ageMonths >= animal.getLivestockCategory().getMinBreedingAgeMonths();
                breedingCapableMap.put(animal.getId(), capable);
            } else {
                breedingCapableMap.put(animal.getId(), null);
            }
        }

        Map<UUID, LivestockValuation> latestValuationMap = livestockService.getLatestValuationsForIds(pageIds);

        Set<UUID> beneficiaryIds = livestockList.stream()
                .map(Livestock::getBeneficiary)
                .filter(Objects::nonNull)
                .map(Beneficiary::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Beneficiary> beneficiaryMap = beneficiaryIds.isEmpty()
                ? Collections.emptyMap()
                : beneficiaryRepository.findAllById(beneficiaryIds).stream()
                .collect(Collectors.toMap(Beneficiary::getId, b -> b));

        Set<UUID> locationIds = livestockList.stream()
                .map(Livestock::getLocation)
                .filter(Objects::nonNull)
                .map(Location::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, LocationHierarchy> hierarchyMap = new HashMap<>();
        for (UUID locId : locationIds) {
            locationService.getLocationById(locId)
                    .ifPresent(loc -> hierarchyMap.put(locId, LocationHierarchy.of(loc)));
        }

        long totalItems      = livestockPage.getTotalElements();
        long totalActive     = livestockRepository.countByStatus(Livestock.STATUS_ACTIVE);
        long totalSold       = livestockRepository.countSold();
        long totalSick       = livestockRepository.countByHealthStatus(Livestock.HEALTH_SICK);
        long totalDead       = livestockRepository.countDead();
        long totalBornOnFarm = livestockRepository.countBornOnFarm();
        long totalTreatments = 0;
        long totalAbortions  = 0;
        long totalValued     = livestockRepository.countValued();
        long totalUnvalued   = livestockRepository.countUnvalued();
        long totalDeleted    = livestockRepository.countSoftDeleted();

        List<LivestockCategory> categories = categoryRepository.findAll().stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .sorted(Comparator.comparing(c -> c.getName() != null ? c.getName() : "",
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

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
        model.addAttribute("beneficiaryMap", beneficiaryMap);
        model.addAttribute("hierarchyMap", hierarchyMap);

        model.addAttribute("categories", categories);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);

        return "livestock-list";
    }

    // ══════════════════════════════════════════════════════════════════
    // OVERVIEW page
    // ══════════════════════════════════════════════════════════════════
    @GetMapping("/overview")
    public String overview(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String search,
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("tagNumber").ascending());

        Page<VLivestockWithAge> animalPage = vLivestockWithAgeRepository.findFiltered(
                stage, status, category, gender, search, pageable);

        List<VLivestockWithAge> livestockList = animalPage.getContent();

        Map<String, Long> lifecycleStats = new LinkedHashMap<>();
        for (Object[] row : vLivestockWithAgeRepository.getLifecycleStageCounts()) {
            String key = row[0] == null ? "UNKNOWN" : row[0].toString();
            Long   val = row[1] == null ? 0L : ((Number) row[1]).longValue();
            lifecycleStats.put(key, val);
        }

        Map<String, Long> categoryStats = new LinkedHashMap<>();
        for (Object[] row : vLivestockWithAgeRepository.getCategoryCounts()) {
            String key = row[0] == null ? "UNCATEGORIZED" : row[0].toString();
            Long   val = row[1] == null ? 0L : ((Number) row[1]).longValue();
            categoryStats.put(key, val);
        }

        Map<String, Long> statusStats = new LinkedHashMap<>();
        for (Object[] row : vLivestockWithAgeRepository.getStatusCounts()) {
            String key = row[0] == null ? "UNKNOWN" : row[0].toString();
            Long   val = row[1] == null ? 0L : ((Number) row[1]).longValue();
            statusStats.put(key, val);
        }

        Set<UUID> beneficiaryIds = livestockList.stream()
                .map(VLivestockWithAge::getBeneficiaryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Beneficiary> beneficiaryMap = beneficiaryIds.isEmpty()
                ? Collections.emptyMap()
                : beneficiaryRepository.findAllById(beneficiaryIds).stream()
                .collect(Collectors.toMap(Beneficiary::getId, b -> b));

        Set<UUID> locationIds = livestockList.stream()
                .map(VLivestockWithAge::getLocationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, LocationHierarchy> hierarchyMap = new HashMap<>();
        for (UUID locId : locationIds) {
            locationService.getLocationById(locId)
                    .ifPresent(loc -> hierarchyMap.put(locId, LocationHierarchy.of(loc)));
        }

        model.addAttribute("livestockList", livestockList);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", animalPage.getTotalPages());
        model.addAttribute("totalItems", animalPage.getTotalElements());
        model.addAttribute("pageSize", size);

        model.addAttribute("lifecycleStats", lifecycleStats);
        model.addAttribute("categoryStats", categoryStats);
        model.addAttribute("statusStats", statusStats);

        model.addAttribute("selectedStage", stage);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedGender", gender);
        model.addAttribute("searchQuery", search);

        model.addAttribute("beneficiaryMap", beneficiaryMap);
        model.addAttribute("hierarchyMap", hierarchyMap);

        return "livestock-overview";
    }

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
                auditLogService.log("livestock", id, "SOFT_DELETE", "system",
                        before, null, "Animal " + tagNumber + " was soft-deleted");
                redirectAttributes.addFlashAttribute("success",
                        "Animal " + tagNumber + " has been deleted.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting animal: " + e.getMessage());
        }
        return "redirect:/livestock/list";
    }

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
                auditLogService.log("livestock", id, "DELETE", "system",
                        before, null, "Animal " + tagNumber + " was permanently deleted");
                redirectAttributes.addFlashAttribute("success",
                        "Animal " + tagNumber + " has been permanently removed.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error permanently deleting: " + e.getMessage());
        }
        return "redirect:/livestock/deleted";
    }

    @PostMapping("/restore/{id}")
    public String restore(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            livestockService.restore(id);
            auditLogService.log("livestock", id, "RESTORE", "system",
                    null, null, "Animal was restored from soft-delete");
            redirectAttributes.addFlashAttribute("success", "Animal successfully restored.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error restoring animal: " + e.getMessage());
        }
        return "redirect:/livestock/deleted";
    }

    @PostMapping("/bulk-delete")
    public String bulkDelete(@RequestParam List<UUID> ids, RedirectAttributes redirectAttributes) {
        try {
            for (UUID id : ids) {
                livestockService.getByIdIncludingDeleted(id).ifPresent(before ->
                        auditLogService.log("livestock", id, "SOFT_DELETE", "system",
                                before, null, "Bulk soft-delete"));
            }
            livestockService.bulkSoftDelete(ids);
            redirectAttributes.addFlashAttribute("success", ids.size() + " animal(s) deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/list";
    }

    @PostMapping("/bulk-restore")
    public String bulkRestore(@RequestParam List<UUID> ids, RedirectAttributes redirectAttributes) {
        try {
            livestockService.bulkRestore(ids);
            ids.forEach(id -> auditLogService.log("livestock", id, "RESTORE", "system",
                    null, null, "Bulk restore"));
            redirectAttributes.addFlashAttribute("success", ids.size() + " animal(s) restored.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
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
            redirectAttributes.addFlashAttribute("success", ids.size() + " animal(s) permanently deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/deleted";
    }

    @GetMapping("/deleted")
    public String listDeleted(Model model) {
        List<Livestock> deletedAnimals = livestockService.getAllSoftDeleted();
        model.addAttribute("deletedAnimals", deletedAnimals);
        model.addAttribute("count", deletedAnimals.size());
        return "livestock-deleted-list";
    }

    @GetMapping("/view/{id}")
    public String view(@PathVariable UUID id, Model model) {
        Optional<Livestock> livestockOpt = livestockService.getById(id);
        if (livestockOpt.isEmpty()) {
            return "redirect:/livestock/list?error=notfound";
        }
        Livestock livestock = livestockOpt.get();
        model.addAttribute("animal", livestock);

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
        model.addAttribute("children", livestockRepository.findByMotherIdAndIsDeletedFalse(id));

        birthService.getByLivestockId(id).stream()
                .findFirst()
                .ifPresent(birth -> model.addAttribute("birthRecord", birth));

        if (livestock.getLocation() != null) {
            model.addAttribute("locationBreadcrumb", buildLocationBreadcrumb(livestock.getLocation()));
        } else {
            model.addAttribute("locationBreadcrumb", new ArrayList<Location>());
        }

        List<LivestockValuation> history = valuationService.getHistory(id);
        log.info("📊 view({}): {} valuation rows loaded", id, history.size());
        model.addAttribute("valuationHistory", history);
        model.addAttribute("latestValuation", valuationService.getLatest(id).orElse(null));
        model.addAttribute("valuationChangeSincePrevious", valuationService.changeSincePrevious(id));
        model.addAttribute("auditHistory", auditLogService.getLogsForEntity("livestock", id));

        model.addAttribute("changeableStatuses",
                CHANGEABLE_STATUSES.stream()
                        .filter(s -> !s.equals(livestock.getStatus()))
                        .collect(Collectors.toList()));

        return "livestock-view";
    }

    @PostMapping("/{id}/change-status")
    public String changeStatus(@PathVariable UUID id,
                               @RequestParam String status,
                               @RequestParam(required = false) String reason,
                               RedirectAttributes redirectAttributes) {
        try {
            Optional<Livestock> livestockOpt = livestockService.getById(id);
            if (livestockOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Animal not found");
                return "redirect:/livestock/list";
            }
            Livestock livestock = livestockOpt.get();
            String oldStatus = livestock.getStatus();

            if (!CHANGEABLE_STATUSES.contains(status)) {
                redirectAttributes.addFlashAttribute("error", "'" + status + "' is not a valid status.");
                return "redirect:/livestock/view/" + id;
            }
            if (status.equals(oldStatus)) {
                redirectAttributes.addFlashAttribute("error",
                        livestock.getTagNumber() + " is already " + status + ".");
                return "redirect:/livestock/view/" + id;
            }

            livestock.setStatus(status);
            if (!Livestock.STATUS_ACTIVE.equals(status)) {
                livestock.setReproductiveStatus(Livestock.REPRO_NOT_APPLICABLE);
            }
            livestockRepository.save(livestock);

            String note = "Status changed from " + oldStatus + " to " + status
                    + (reason != null && !reason.isBlank() ? " — Reason: " + reason.trim() : "");
            auditLogService.log("livestock", id, "STATUS_CHANGE", "system", null, null, note);

            redirectAttributes.addFlashAttribute("success",
                    "Status for " + livestock.getTagNumber() + " updated to " + status + ".");
        } catch (Exception e) {
            log.error("Error changing status for animal {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Error updating status: " + e.getMessage());
        }
        return "redirect:/livestock/view/" + id;
    }

    @GetMapping("/{id}/valuation-history")
    public String valuationHistory(@PathVariable UUID id, Model model) {
        Optional<Livestock> livestockOpt = livestockService.getById(id);
        if (livestockOpt.isEmpty()) {
            return "redirect:/livestock/list?error=notfound";
        }
        model.addAttribute("animal", livestockOpt.get());
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
            log.info("📥 POST /livestock/{}/valuation value={} method={} date={}",
                    id, value, valuationMethod, valuationDate);
            valuationService.recordValuation(id, valuationDate, value, valuationMethod, notes, "system");
            redirectAttributes.addFlashAttribute("success", "New valuation recorded successfully.");
        } catch (Exception e) {
            log.error("❌ Failed to record valuation for animal {}", id, e);
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

    @GetMapping("/valuation-report")
    public String valuationReport(@RequestParam(required = false) UUID animalId, Model model) {
        List<Livestock> allAnimals = livestockService.getAllIncludingDrafts();

        List<Livestock> sortedForSelect = allAnimals.stream()
                .sorted(Comparator.comparing(a -> a.getTagNumber() != null ? a.getTagNumber() : "",
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        long totalValued = allAnimals.stream().filter(a -> a.getCurrentValue() != null).count();
        long totalUnvalued = allAnimals.size() - totalValued;

        List<Livestock> unvaluedAnimals = allAnimals.stream()
                .filter(a -> a.getCurrentValue() == null)
                .sorted(Comparator.comparing(a -> a.getTagNumber() != null ? a.getTagNumber() : "",
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        model.addAttribute("allAnimals", sortedForSelect);
        model.addAttribute("totalValued", totalValued);
        model.addAttribute("totalUnvalued", totalUnvalued);
        model.addAttribute("unvaluedAnimals", unvaluedAnimals);

        if (animalId != null) {
            Optional<Livestock> selectedOpt = livestockService.getById(animalId);
            if (selectedOpt.isPresent()) {
                model.addAttribute("selectedAnimal", selectedOpt.get());
                model.addAttribute("valuationHistory", valuationService.getHistory(animalId));
                model.addAttribute("latestValuation", valuationService.getLatest(animalId).orElse(null));
                model.addAttribute("valuationChangeSincePrevious", valuationService.changeSincePrevious(animalId));
            }
        }
        return "livestock-valuation-report";
    }

    private String resolveLifecycleStage(Livestock l, LocalDate today) {
        if (Livestock.REPRO_PREGNANT.equals(l.getReproductiveStatus())) return "PREGNANT";
        if (l.getBirthDate() == null) return "UNKNOWN";

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

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable UUID id, Model model) {
        Optional<Livestock> livestockOpt = livestockService.getById(id);
        if (livestockOpt.isEmpty()) return "redirect:/livestock/list?error=notfound";
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

        List<Livestock> males = livestockRepository.findAllBreedingMales().stream()
                .filter(m -> !m.getId().equals(id)).collect(Collectors.toList());
        if (livestock.getFather() != null
                && males.stream().noneMatch(m -> m.getId().equals(livestock.getFather().getId()))) {
            males.add(0, livestock.getFather());
        }

        List<Livestock> females = livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE).stream()
                .filter(f -> "FEMALE".equalsIgnoreCase(f.getGender()))
                .filter(f -> !f.getId().equals(id)).collect(Collectors.toList());
        if (livestock.getMother() != null
                && females.stream().noneMatch(f -> f.getId().equals(livestock.getMother().getId()))) {
            females.add(0, livestock.getMother());
        }

        model.addAttribute("maleAnimals", males);
        model.addAttribute("femaleAnimals", females);

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
                         @RequestParam(required = false) String locationId,
                         @RequestParam(required = false) String fatherId,
                         @RequestParam(required = false) String motherId,
                         RedirectAttributes redirectAttributes) {
        try {
            Optional<Livestock> beforeOpt = livestockService.getByIdIncludingDeleted(id);
            if (locationId != null && !locationId.isBlank()) {
                try {
                    locationRepository.findById(UUID.fromString(locationId)).ifPresent(livestock::setLocation);
                } catch (IllegalArgumentException ignored) {}
            }
            if (fatherId != null && !fatherId.isBlank()) {
                try {
                    livestockRepository.findById(UUID.fromString(fatherId)).ifPresent(livestock::setFather);
                } catch (IllegalArgumentException ignored) {}
            }
            if (motherId != null && !motherId.isBlank()) {
                try {
                    livestockRepository.findById(UUID.fromString(motherId)).ifPresent(livestock::setMother);
                } catch (IllegalArgumentException ignored) {}
            }
            if (livestock.getLivestockCategoryIdValue() != null
                    && !livestock.getLivestockCategoryIdValue().isBlank()) {
                try {
                    categoryRepository.findById(UUID.fromString(livestock.getLivestockCategoryIdValue()))
                            .ifPresent(livestock::setLivestockCategory);
                } catch (IllegalArgumentException ignored) {}
            }
            if (livestock.getBeneficiaryIdValue() != null
                    && !livestock.getBeneficiaryIdValue().isBlank()) {
                try {
                    beneficiaryRepository.findById(UUID.fromString(livestock.getBeneficiaryIdValue()))
                            .ifPresent(livestock::setBeneficiary);
                } catch (IllegalArgumentException ignored) {}
            }
            livestockService.update(id, livestock);
            beforeOpt.ifPresent(before -> auditLogService.log(
                    "livestock", id, "UPDATE", "system",
                    before, livestock, "Animal " + before.getTagNumber() + " updated"));
            redirectAttributes.addFlashAttribute("success", "Animal updated successfully");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating animal: " + e.getMessage());
        }
        return "redirect:/livestock/list";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        List<Beneficiary> beneficiaries = beneficiaryRepository.findAll();
        Livestock livestock = new Livestock();
        livestock.setStatus(Livestock.STATUS_ACTIVE);
        livestock.setHealthStatus(Livestock.HEALTH_HEALTHY);
        livestock.setReproductiveStatus(Livestock.REPRO_OPEN);

        model.addAttribute("livestock", livestock);
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("beneficiaries", beneficiaries);
        model.addAttribute("beneficiariesList", beneficiaries);
        return "livestock-register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute Livestock livestock,
                           @RequestParam(required = false) String locationId,
                           RedirectAttributes redirectAttributes) {
        try {
            if (locationId != null && !locationId.isBlank()) {
                try {
                    locationRepository.findById(UUID.fromString(locationId)).ifPresent(livestock::setLocation);
                } catch (IllegalArgumentException ignored) {}
            }
            if (livestock.getLivestockCategoryIdValue() != null
                    && !livestock.getLivestockCategoryIdValue().isBlank()) {
                try {
                    categoryRepository.findById(UUID.fromString(livestock.getLivestockCategoryIdValue()))
                            .ifPresent(livestock::setLivestockCategory);
                } catch (IllegalArgumentException ignored) {}
            }
            if (livestock.getBeneficiaryIdValue() != null
                    && !livestock.getBeneficiaryIdValue().isBlank()) {
                try {
                    beneficiaryRepository.findById(UUID.fromString(livestock.getBeneficiaryIdValue()))
                            .ifPresent(livestock::setBeneficiary);
                } catch (IllegalArgumentException ignored) {}
            }
            Livestock saved = livestockService.addNew(livestock);
            redirectAttributes.addFlashAttribute("success",
                    "Animal registered successfully with tag: " + saved.getTagNumber());
            return "redirect:/livestock/list";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error registering: " + e.getMessage());
            return "redirect:/livestock/register";
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // SUGGEST TAG — now delegates to TagAllocationService so the
    // numbering is guaranteed to be identical to what bulk import uses.
    // ══════════════════════════════════════════════════════════════════
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
        TagAllocationService.TagSuggestion suggestion =
                tagAllocationService.suggest(categoryOpt.get());
        result.put("lastTag", suggestion.getLastTag());
        result.put("suggestedTag", suggestion.getSuggestedTag());
        return result;
    }

    private List<Location> buildLocationBreadcrumb(Location location) {
        List<Location> chain = new ArrayList<>();
        Location current = location;
        while (current != null) {
            chain.add(0, current);
            current = current.getParent();
        }
        return chain;
    }

    // ══════════════════════════════════════════════════════════════════
    // Nested helper: location hierarchy resolved from a leaf Location
    // ══════════════════════════════════════════════════════════════════
    public record LocationHierarchy(
            String province, String district, String sector,
            String cell, String village) {

        public static LocationHierarchy of(Location leaf) {
            String province = null, district = null, sector = null, cell = null, village = null;
            Location cur = leaf;
            while (cur != null) {
                String type = cur.getLocationType() == null ? "" : cur.getLocationType().toUpperCase();
                switch (type) {
                    case "PROVINCE" -> province = cur.getName();
                    case "DISTRICT" -> district = cur.getName();
                    case "SECTOR"   -> sector   = cur.getName();
                    case "CELL"     -> cell     = cur.getName();
                    case "VILLAGE"  -> village  = cur.getName();
                }
                cur = cur.getParent();
            }
            return new LocationHierarchy(province, district, sector, cell, village);
        }

        public String getDisplay() {
            StringBuilder sb = new StringBuilder();
            if (village  != null) sb.append(village).append(", ");
            if (cell     != null) sb.append(cell).append(", ");
            if (sector   != null) sb.append(sector).append(", ");
            if (district != null) sb.append(district).append(", ");
            if (province != null) sb.append(province);
            String out = sb.toString();
            return out.endsWith(", ") ? out.substring(0, out.length() - 2) : out;
        }
    }
}