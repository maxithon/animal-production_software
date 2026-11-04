package rw.animalproduct.animal.production.controller;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.dto.FemaleReadyToBreedDTO;
import rw.animalproduct.animal.production.dto.MaleReadyToBreedDTO;
import rw.animalproduct.animal.production.dto.PregnancyRowDTO;
import rw.animalproduct.animal.production.dto.PurchasedPregnancyRowDTO;
import rw.animalproduct.animal.production.entity.Beneficiary;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBirth;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.notification.LivestockLifecycleService;
import rw.animalproduct.animal.production.repository.BeneficiaryRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.services.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Livestock Lifecycle Controller
 * Complete lifecycle tracking from birth to offspring with proper data integration.
 */
@Controller
@RequestMapping("/livestock/lifecycle")
public class LivestockLifecycleController {

    private final LivestockLifecycleService lifecycleService;
    private final LivestockBreedingService    breedingService;
    private final MalesReadyToBreedService    malesReadyToBreedService;
    private final FemalesReadyToBreedService  femalesReadyToBreedService;
    private final LifecycleEmailService       emailService;
    private final LivestockRepository         livestockRepository;
    private final LivestockBirthService       birthService;
    // ✅ NEW
    private final BeneficiaryRepository       beneficiaryRepository;
    private final LocationRepository          locationRepository;

    public LivestockLifecycleController(
            LivestockLifecycleService lifecycleService,
            LivestockBreedingService breedingService,
            MalesReadyToBreedService malesReadyToBreedService,
            FemalesReadyToBreedService femalesReadyToBreedService,
            LifecycleEmailService emailService,
            LivestockRepository livestockRepository,
            LivestockBirthService birthService,
            BeneficiaryRepository beneficiaryRepository,      // ✅ NEW
            LocationRepository locationRepository) {          // ✅ NEW
        this.lifecycleService          = lifecycleService;
        this.breedingService           = breedingService;
        this.malesReadyToBreedService  = malesReadyToBreedService;
        this.femalesReadyToBreedService= femalesReadyToBreedService;
        this.emailService              = emailService;
        this.livestockRepository       = livestockRepository;
        this.birthService              = birthService;
        this.beneficiaryRepository     = beneficiaryRepository;  // ✅ NEW
        this.locationRepository        = locationRepository;     // ✅ NEW
    }

    // =========================================================================
    // MAIN DASHBOARD
    // =========================================================================
    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        Map<String, Long> lifecycleSummary = new LinkedHashMap<>();
        lifecycleSummary.put("NEWBORN",        lifecycleService.countNewborns());
        lifecycleSummary.put("YOUNG",          lifecycleService.countYoung());
        lifecycleSummary.put("PRE_BREEDING",   lifecycleService.countPreBreeding());
        lifecycleSummary.put("READY_TO_BREED", lifecycleService.countReadyToBreed());
        lifecycleSummary.put("BREEDING_MALE",  countBreedingMales());
        lifecycleSummary.put("PREGNANT",       lifecycleService.countPregnant());
        lifecycleSummary.put("MATURE",         countMature());
        model.addAttribute("lifecycleSummary", lifecycleSummary);

        model.addAttribute("statusSummary",  getStatusDistribution());
        model.addAttribute("genderSummary",  getGenderDistribution());

        model.addAttribute("recentNewborns", lifecycleService.getRecentlyBorn(30).stream()
                .limit(10).map(this::enrichAnimalData).collect(Collectors.toList()));

        model.addAttribute("pregnantList", lifecycleService.getPregnantAnimals().stream()
                .limit(10).map(this::enrichAnimalData).collect(Collectors.toList()));

        model.addAttribute("readyToBreed", lifecycleService.getReadyToBreed().stream()
                .limit(10).map(this::enrichAnimalData).collect(Collectors.toList()));

        model.addAttribute("activeBreedings",     lifecycleService.getActiveBreedings());
        model.addAttribute("totalAnimals",        lifecycleService.countAll());
        model.addAttribute("dueSoonCount",        lifecycleService.countDueSoon(14));
        model.addAttribute("overdueCount",        lifecycleService.getOverdue().size());
        model.addAttribute("breedingSuccessRate",
                String.format("%.1f%%", lifecycleService.getBreedingSuccessRate()));

        return "livestock-lifecycle-dashboard";
    }

    // =========================================================================
    // STAGE-SPECIFIC VIEWS
    // =========================================================================
    @GetMapping("/pre-breeding")
    public String preBreeding(Model model) {
        model.addAttribute("youngFemales", lifecycleService.getYoungFemales().stream()
                .map(this::enrichAnimalData).collect(Collectors.toList()));
        model.addAttribute("youngMales", lifecycleService.getYoungMales().stream()
                .map(this::enrichAnimalData).collect(Collectors.toList()));
        model.addAttribute("approachingBreedingAge", lifecycleService.getApproachingBreedingAge().stream()
                .map(this::enrichAnimalData).collect(Collectors.toList()));
        model.addAttribute("pageTitle", "Pre-Breeding Management");
        return "livestock-pre-breeding";
    }

    // =========================================================================
    // READY TO BREED
    // =========================================================================
    @GetMapping("/ready-to-breed")
    public String readyToBreed(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            Model model) {

        String selectedCategory = (category != null && !category.isEmpty()) ? category : "all";
        String searchTerm = (search != null && !search.isEmpty()) ? search : "";

        List<MaleReadyToBreedDTO>   allMales   = malesReadyToBreedService.getAllReadyToBreed();
        List<FemaleReadyToBreedDTO> allFemales = femalesReadyToBreedService.getAllReadyToBreed();

        if (!searchTerm.isEmpty()) {
            allMales = allMales.stream()
                    .filter(m -> (m.getTagNumber()    != null && m.getTagNumber().toLowerCase().contains(searchTerm.toLowerCase())) ||
                            (m.getCategoryName() != null && m.getCategoryName().toLowerCase().contains(searchTerm.toLowerCase())))
                    .collect(Collectors.toList());

            allFemales = allFemales.stream()
                    .filter(f -> (f.getTagNumber()    != null && f.getTagNumber().toLowerCase().contains(searchTerm.toLowerCase())) ||
                            (f.getCategoryName() != null && f.getCategoryName().toLowerCase().contains(searchTerm.toLowerCase())))
                    .collect(Collectors.toList());
        }

        Map<String, List<MaleReadyToBreedDTO>> malesByCategory = allMales.stream()
                .filter(m -> m.getCategoryName() != null)
                .collect(Collectors.groupingBy(MaleReadyToBreedDTO::getCategoryName,
                        LinkedHashMap::new, Collectors.toList()));

        Map<String, List<FemaleReadyToBreedDTO>> femalesByCategory = allFemales.stream()
                .filter(f -> f.getCategoryName() != null)
                .collect(Collectors.groupingBy(FemaleReadyToBreedDTO::getCategoryName,
                        LinkedHashMap::new, Collectors.toList()));

        Set<String> allCategories = new LinkedHashSet<>();
        allCategories.addAll(malesByCategory.keySet());
        allCategories.addAll(femalesByCategory.keySet());

        if (!"all".equals(selectedCategory)) {
            Map<String, List<MaleReadyToBreedDTO>>   filteredMales   = new LinkedHashMap<>();
            Map<String, List<FemaleReadyToBreedDTO>> filteredFemales = new LinkedHashMap<>();
            if (malesByCategory.containsKey(selectedCategory))
                filteredMales.put(selectedCategory, malesByCategory.get(selectedCategory));
            if (femalesByCategory.containsKey(selectedCategory))
                filteredFemales.put(selectedCategory, femalesByCategory.get(selectedCategory));
            malesByCategory   = filteredMales;
            femalesByCategory = filteredFemales;
        }

        model.addAttribute("malesByCategory",      malesByCategory);
        model.addAttribute("femalesByCategory",     femalesByCategory);
        model.addAttribute("allCategories",         allCategories);
        model.addAttribute("selectedCategory",      selectedCategory);
        model.addAttribute("maleTotal",             allMales.size());
        model.addAttribute("femaleTotal",           allFemales.size());
        model.addAttribute("maleAvgSuccessRate",    malesReadyToBreedService.getAverageSuccessRate());
        model.addAttribute("maleNeverBred",         malesReadyToBreedService.countNeverBred());
        model.addAttribute("femaleNeverBred",       femalesReadyToBreedService.countNeverBred());
        model.addAttribute("search",                searchTerm);
        model.addAttribute("pageTitle",             "Ready to Breed");

        return "livestock-ready-to-breed";
    }

    // =========================================================================
    // PREGNANCY TRACKING
    // =========================================================================
    @GetMapping("/pregnancy-tracking")
    public String pregnancyTracking(
            @RequestParam(value = "birthPage", defaultValue = "0") int birthPage,
            @RequestParam(value = "birthSize", defaultValue = "10") int birthSize,
            Model model) {

        LocalDate today = LocalDate.now();

        // ── SOURCE 1: Full breeding records ───────────────────────────────────
        List<LivestockBreeding> allPregnant = breedingService.getAll().stream()
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus()))
                // LivestockBreeding has no isDeleted flag; use isActive (null counts as active)
                .filter(b -> !Boolean.FALSE.equals(b.getIsActive()))
                .sorted(Comparator.comparingLong(b -> {
                    if (b.getExpectedDueDate() == null) return Long.MAX_VALUE;
                    return ChronoUnit.DAYS.between(today, b.getExpectedDueDate());
                }))
                .collect(Collectors.toList());

        List<PregnancyRowDTO> pregnancyRows = allPregnant.stream()
                .map(b -> {
                    Livestock ls = b.getLivestock();
                    PregnancyRowDTO row = new PregnancyRowDTO(
                            ls.getId(),
                            ls.getTagNumber(),
                            ls.getLivestockCategory() != null ? ls.getLivestockCategory().getName() : null,
                            ls.getAcquisitionMethod(),
                            b.getBreedingDate(),
                            b.getExpectedDueDate(),
                            b.getBreedingMethod());
                    // ✅ NEW: pass beneficiary + location IDs for lookup
                    if (ls.getBeneficiary() != null) row.setBeneficiaryId(ls.getBeneficiary().getId());
                    if (ls.getLocation()    != null) row.setLocationId(ls.getLocation().getId());
                    return row;
                })
                .collect(Collectors.toList());

        // ── SOURCE 2: Purchased/external animals without a breeding record ────
        Set<UUID> coveredByBreedingRecord = breedingService.getAll().stream()
                .filter(b -> b.getLivestock() != null)
                .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                        || LivestockBreeding.STATUS_PENDING.equals(b.getStatus()))
                .map(b -> b.getLivestock().getId())
                .collect(Collectors.toSet());

        List<PurchasedPregnancyRowDTO> purchasedRows = livestockRepository
                .findPregnantWithoutBreedingRecord()
                .stream()
                .filter(ls -> !coveredByBreedingRecord.contains(ls.getId()))
                .sorted(Comparator.comparing(ls ->
                        ls.getExpectedDueDate() != null ? ls.getExpectedDueDate() : LocalDate.MAX))
                .map(ls -> new PurchasedPregnancyRowDTO(ls, today))
                .collect(Collectors.toList());

        // ── KPIs ──────────────────────────────────────────────────────────────
        long breedingDueSoon  = pregnancyRows.stream().filter(PregnancyRowDTO::isDueSoon).count();
        long purchasedDueSoon = purchasedRows.stream().filter(PurchasedPregnancyRowDTO::isDueSoon).count();

        long checkupsDue = allPregnant.stream()
                .filter(b -> b.getExpectedPregnancyCheckDate() != null
                        && b.getExpectedPregnancyCheckDate().isBefore(today))
                .count();

        List<PregnancyRowDTO> criticalCases = pregnancyRows.stream()
                .filter(PregnancyRowDTO::isCritical)
                .collect(Collectors.toList());

        long purchasedHighRisk = purchasedRows.stream().filter(PurchasedPregnancyRowDTO::isCritical).count();

        long totalPregnantAll = pregnancyRows.size() + purchasedRows.size();

        model.addAttribute("pregnancyRows",            pregnancyRows);
        model.addAttribute("purchasedRows",            purchasedRows);
        model.addAttribute("purchasedPregnantCount",   purchasedRows.size());
        model.addAttribute("totalPregnant",            totalPregnantAll);
        model.addAttribute("dueSoon",                  breedingDueSoon + purchasedDueSoon);
        model.addAttribute("checkupsDue",              checkupsDue);
        model.addAttribute("highRisk",                 (long) criticalCases.size() + purchasedHighRisk);
        model.addAttribute("criticalCases",            criticalCases);
        model.addAttribute("today",                    today);
        model.addAttribute("pageTitle",                "Pregnancy Tracking");

        // ════════════════════════════════════════════════════════════════════
        // SECTION 3: "Given Birth" log, paginated
        // ════════════════════════════════════════════════════════════════════
        Page<LivestockBirth> birthPageResult = birthService.getPaged(birthPage, birthSize);

        List<Map<String, Object>> givenBirthRows = new ArrayList<>();
        for (LivestockBirth birth : birthPageResult.getContent()) {
            if (birth.getLivestockId() == null) continue;

            Livestock mother = livestockRepository.findById(birth.getLivestockId()).orElse(null);
            if (mother == null) continue;

            boolean pregnantAgain = Livestock.REPRO_PREGNANT.equals(mother.getReproductiveStatus());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("birthId",          birth.getId());
            row.put("motherId",         mother.getId());
            row.put("tagNumber",        mother.getTagNumber());
            row.put("categoryName",     mother.getLivestockCategory() != null
                    ? mother.getLivestockCategory().getName() : null);
            row.put("birthDate",        birth.getBirthDate());
            row.put("daysSinceBirth",   birth.getBirthDate() != null
                    ? ChronoUnit.DAYS.between(birth.getBirthDate(), today) : null);
            row.put("offspringCount",   birth.getOffspringCount() != null ? birth.getOffspringCount() : 0);
            row.put("linkedChildren",   birthService.countLiveOffspring(birth.getId()));
            row.put("isPregnantAgain",  pregnantAgain);
            row.put("reproductiveStatusLabel", mother.reproductiveStatusLabel());
            // ✅ NEW: pass beneficiary + location IDs for lookup
            if (mother.getBeneficiary() != null) row.put("beneficiaryId", mother.getBeneficiary().getId());
            if (mother.getLocation()    != null) row.put("locationId",    mother.getLocation().getId());
            givenBirthRows.add(row);
        }

        int totalBirthPages  = birthPageResult.getTotalPages();
        int currentBirthPage = birthPageResult.getNumber();

        int windowSize = 5;
        int startPage  = Math.max(0, currentBirthPage - windowSize / 2);
        int endPage    = Math.min(Math.max(totalBirthPages - 1, 0), startPage + windowSize - 1);
        startPage      = Math.max(0, endPage - windowSize + 1);

        List<Integer> givenBirthPageNumbers = new ArrayList<>();
        for (int i = startPage; i <= endPage; i++) {
            givenBirthPageNumbers.add(i);
        }

        model.addAttribute("givenBirthRows",         givenBirthRows);
        model.addAttribute("givenBirthCurrentPage",  currentBirthPage);
        model.addAttribute("givenBirthTotalPages",   totalBirthPages);
        model.addAttribute("givenBirthTotalItems",   birthPageResult.getTotalElements());
        model.addAttribute("givenBirthPageSize",     birthPageResult.getSize());
        model.addAttribute("givenBirthPageNumbers",  givenBirthPageNumbers);

        // ════════════════════════════════════════════════════════════════════
        // Beneficiary + Location lookup maps for ALL 3 sections
        // ════════════════════════════════════════════════════════════════════
        Set<UUID> benefIds = new HashSet<>();
        Set<UUID> locIds   = new HashSet<>();

        for (PregnancyRowDTO r : pregnancyRows) {
            if (r.getBeneficiaryId() != null) benefIds.add(r.getBeneficiaryId());
            if (r.getLocationId()    != null) locIds.add(r.getLocationId());
        }
        for (PurchasedPregnancyRowDTO r : purchasedRows) {
            if (r.getBeneficiaryId() != null) benefIds.add(r.getBeneficiaryId());
            if (r.getLocationId()    != null) locIds.add(r.getLocationId());
        }
        for (Map<String, Object> r : givenBirthRows) {
            Object bId = r.get("beneficiaryId");
            Object lId = r.get("locationId");
            if (bId instanceof UUID u) benefIds.add(u);
            if (lId instanceof UUID u) locIds.add(u);
        }

        Map<UUID, Beneficiary> beneficiaryMap = benefIds.isEmpty()
                ? Collections.emptyMap()
                : beneficiaryRepository.findAllById(benefIds).stream()
                .collect(Collectors.toMap(Beneficiary::getId, b -> b));

        Map<UUID, LocationHierarchy> hierarchyMap = new HashMap<>();
        for (UUID locId : locIds) {
            locationRepository.findById(locId)
                    .ifPresent(loc -> hierarchyMap.put(locId, LocationHierarchy.of(loc)));
        }

        model.addAttribute("beneficiaryMap", beneficiaryMap);
        model.addAttribute("hierarchyMap",   hierarchyMap);

        return "livestock-pregnancy-tracking";
    }

    // =========================================================================
    // OTHER LIFECYCLE VIEWS
    // =========================================================================
    @GetMapping("/male-management")
    public String maleManagement(Model model) {
        model.addAttribute("allMales", lifecycleService.getAllMales().stream()
                .map(this::enrichAnimalData).collect(Collectors.toList()));
        model.addAttribute("readyMales", lifecycleService.getMalesReadyToBreed().stream()
                .map(this::enrichAnimalData).collect(Collectors.toList()));
        model.addAttribute("breedingStats", lifecycleService.getMaleBreedingStats());
        model.addAttribute("pageTitle", "Male Management");
        return "livestock-male-management";
    }

    @GetMapping("/female-management")
    public String femaleManagement(Model model) {
        model.addAttribute("allFemales", lifecycleService.getAllFemales().stream()
                .map(this::enrichAnimalData).collect(Collectors.toList()));
        model.addAttribute("pregnantAnimals", lifecycleService.getPregnantAnimals().stream()
                .map(this::enrichAnimalData).collect(Collectors.toList()));
        model.addAttribute("nursingAnimals", lifecycleService.getNursingAnimals().stream()
                .map(this::enrichAnimalData).collect(Collectors.toList()));
        model.addAttribute("readyToBreed", lifecycleService.getReadyToBreed().stream()
                .map(this::enrichAnimalData).collect(Collectors.toList()));
        model.addAttribute("pageTitle", "Female Management");
        return "livestock-female-management";
    }

    @GetMapping("/notifications")
    public String notifications(Model model) {
        List<Map<String, Object>> dueSoonAnimals = lifecycleService.getDueSoon(14).stream()
                .map(this::enrichAnimalData).collect(Collectors.toList());
        List<Map<String, Object>> overdueAnimals = lifecycleService.getOverdue().stream()
                .map(this::enrichAnimalData).collect(Collectors.toList());
        List<Map<String, Object>> newbornAnimals = lifecycleService.getRecentlyBorn(30).stream()
                .map(this::enrichAnimalData).collect(Collectors.toList());
        List<Map<String, Object>> readyToBreed   = lifecycleService.getReadyToBreed().stream()
                .map(this::enrichAnimalData).collect(Collectors.toList());

        model.addAttribute("dueSoonAnimals",    dueSoonAnimals);
        model.addAttribute("overdueAnimals",    overdueAnimals);
        model.addAttribute("newbornAnimals",    newbornAnimals);
        model.addAttribute("readyToBreed",      readyToBreed);
        model.addAttribute("totalNotifications",
                dueSoonAnimals.size() + overdueAnimals.size()
                        + newbornAnimals.size() + readyToBreed.size());
        model.addAttribute("pageTitle", "Lifecycle Notifications");
        return "livestock-lifecycle-notifications";
    }

    @GetMapping("/animal/{id}")
    public String animalDetail(@PathVariable UUID id, Model model) {
        return "redirect:/livestock/view/" + id;
    }

    // =========================================================================
    // REST / API ENDPOINTS
    // =========================================================================
    @GetMapping("/api/summary")
    @ResponseBody
    public Map<String, Object> apiSummary() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("total",         lifecycleService.countAll());
        s.put("newborns",      lifecycleService.countNewborns());
        s.put("preBreeding",   countPreBreeding());
        s.put("readyToBreed",  lifecycleService.countReadyToBreed());
        s.put("inBreeding",    lifecycleService.countInBreeding());
        s.put("pregnant",      lifecycleService.countPregnant());
        s.put("dueSoon",       lifecycleService.countDueSoon(14));
        s.put("overdue",       lifecycleService.getOverdue().size());
        s.put("notifications", lifecycleService.countAllNotifications());
        return s;
    }

    @GetMapping("/api/stage-distribution")
    @ResponseBody
    public Map<String, Long> apiStageDistribution() {
        return lifecycleService.getStageDistribution();
    }

    @GetMapping("/api/age-bands")
    @ResponseBody
    public Map<String, Long> apiAgeBands() {
        return lifecycleService.getAgeBandBreakdown();
    }

    // =========================================================================
    // EMAIL TEST ENDPOINTS
    // =========================================================================
    @PostMapping("/test-email")
    @ResponseBody
    public Map<String, String> testEmail(@RequestParam UUID animalId) {
        try {
            Livestock animal = lifecycleService.getAnimalById(animalId);
            if (animal != null) {
                String stage = lifecycleService.getCurrentStage(animal);
                switch (stage) {
                    case "NEWBORN":      emailService.sendNewbornNotification(animal); break;
                    case "YOUNG":        emailService.sendYoungStageNotification(animal); break;
                    case "PRE_BREEDING": emailService.sendPreBreedingNotification(animal); break;
                    case "READY_TO_BREED": emailService.sendReadyToBreedNotification(animal); break;
                    case "PREGNANT":
                        emailService.sendPregnancyConfirmedNotification(
                                breedingService.getAll().stream()
                                        .filter(b -> b.getLivestock() != null
                                                && b.getLivestock().getId().equals(animalId))
                                        .findFirst().orElse(null));
                        break;
                    default: emailService.sendNewbornNotification(animal); break;
                }
                return Map.of("status", "success",
                        "message", "Test email sent for animal: " + animal.getTagNumber());
            }
            return Map.of("status", "error", "message", "Animal not found with ID: " + animalId);
        } catch (Exception e) {
            return Map.of("status", "error", "message", String.valueOf(e.getMessage()));
        }
    }

    @PostMapping("/test-due-soon")
    @ResponseBody
    public Map<String, String> testDueSoon() {
        try {
            List<Livestock> dueSoon = lifecycleService.getDueSoon(14);
            if (dueSoon.isEmpty())
                return Map.of("status", "info", "message", "No animals are due within the next 14 days");
            emailService.sendDueSoonNotification(dueSoon, 14);
            return Map.of("status", "success",
                    "message", "Due soon notification sent for " + dueSoon.size() + " animals");
        } catch (Exception e) {
            return Map.of("status", "error", "message", String.valueOf(e.getMessage()));
        }
    }

    @PostMapping("/test-overdue")
    @ResponseBody
    public Map<String, String> testOverdue() {
        try {
            List<Livestock> overdue = lifecycleService.getOverdue();
            if (overdue.isEmpty())
                return Map.of("status", "info", "message", "No overdue animals found");
            for (Livestock animal : overdue) {
                LivestockBreeding breeding = breedingService.getAll().stream()
                        .filter(b -> b.getLivestock() != null
                                && b.getLivestock().getId().equals(animal.getId())
                                && LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus()))
                        .findFirst()
                        .orElse(null);

                if (breeding != null) {
                    long daysOverdue = 0;
                    if (animal.getExpectedDueDate() != null) {
                        daysOverdue = ChronoUnit.DAYS.between(animal.getExpectedDueDate(), LocalDate.now());
                    }
                    int tier = lifecycleService.overdueTierCrossed(daysOverdue);
                    emailService.sendOverduePregnancyNotification(breeding, (int) daysOverdue, tier);
                } else {
                    logger.warn("No breeding record found for overdue animal: {}", animal.getTagNumber());
                }
            }
            return Map.of("status", "success",
                    "message", "Overdue notification sent for " + overdue.size() + " animals");
        } catch (Exception e) {
            return Map.of("status", "error", "message", String.valueOf(e.getMessage()));
        }
    }

    @PostMapping("/test-newborn")
    @ResponseBody
    public Map<String, String> testNewborn(@RequestParam UUID animalId) {
        try {
            Livestock animal = lifecycleService.getAnimalById(animalId);
            if (animal != null) {
                emailService.sendNewbornNotification(animal);
                return Map.of("status", "success",
                        "message", "Newborn test email sent for animal: " + animal.getTagNumber());
            }
            return Map.of("status", "error", "message", "Animal not found");
        } catch (Exception e) {
            return Map.of("status", "error", "message", String.valueOf(e.getMessage()));
        }
    }

    @PostMapping("/test-ready-to-breed")
    @ResponseBody
    public Map<String, String> testReadyToBreed(@RequestParam UUID animalId) {
        try {
            Livestock animal = lifecycleService.getAnimalById(animalId);
            if (animal != null) {
                emailService.sendReadyToBreedNotification(animal);
                return Map.of("status", "success",
                        "message", "Ready to breed test email sent for animal: " + animal.getTagNumber());
            }
            return Map.of("status", "error", "message", "Animal not found");
        } catch (Exception e) {
            return Map.of("status", "error", "message", String.valueOf(e.getMessage()));
        }
    }

    @GetMapping("/email-status")
    @ResponseBody
    public Map<String, Object> getEmailStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("emailEnabled",    true);
        status.put("recipientEmail",  "m.test4743@gmail.com");
        status.put("status",          "Email notifications are configured and ready to send");
        status.put("notificationTypes", List.of(
                "Newborn Registration",
                "Stage Changes (Young, Pre-Breeding, Ready to Breed)",
                "Breeding Events",
                "Pregnancy Confirmation",
                "Due Soon Alerts (14 days)",
                "Overdue Alerts",
                "Offspring Birth"
        ));
        return status;
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(LivestockLifecycleController.class);

    private Map<String, Object> enrichAnimalData(Livestock animal) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id",           animal.getId());
        data.put("tagNumber",    animal.getTagNumber());
        data.put("gender",       animal.getGender());
        data.put("status",       animal.getStatus());
        data.put("categoryName", animal.getLivestockCategory() != null
                ? animal.getLivestockCategory().getName() : null);

        long ageInDays   = lifecycleService.getAgeInDays(animal);
        long ageInMonths = lifecycleService.getAgeInMonths(animal);
        data.put("ageInDays",       ageInDays);
        data.put("ageInMonths",     ageInMonths);
        data.put("ageDisplay",      formatAge(ageInDays));
        data.put("lifecycleStage",  lifecycleService.getCurrentStage(animal));
        data.put("nextMilestone",   lifecycleService.getNextMilestone(animal));
        data.put("expectedDueDate", animal.getExpectedDueDate());
        data.put("lastBirthDate",   animal.getLastBirthDate());
        data.put("offspringCount",  animal.getOffspringCount());

        if (animal.getExpectedDueDate() != null) {
            data.put("daysUntilDue",
                    ChronoUnit.DAYS.between(LocalDate.now(), animal.getExpectedDueDate()));
        }

        List<Livestock> offspring = lifecycleService.getOffspring(animal.getId());
        data.put("offspring", offspring);
        data.put("offspringTotal", offspring != null ? offspring.size() : 0);

        return data;
    }

    private String formatAge(long days) {
        if (days < 30)  return days + " days";
        if (days < 365) { long m = days / 30; return m + " month" + (m > 1 ? "s" : ""); }
        long y = days / 365, rm = (days % 365) / 30;
        String r = y + " year" + (y > 1 ? "s" : "");
        if (rm > 0) r += ", " + rm + " month" + (rm > 1 ? "s" : "");
        return r;
    }

    private long countYoung()         { return lifecycleService.countYoung(); }
    private long countPreBreeding()   { return lifecycleService.countPreBreeding(); }
    private long countBreedingMales() { return lifecycleService.getMalesReadyToBreed().size(); }

    private long countMature() {
        return lifecycleService.getAllFemales().stream()
                .filter(a -> lifecycleService.getAgeInDays(a) > 365)
                .filter(a -> !"PREGNANT".equals(lifecycleService.getCurrentStage(a)))
                .filter(a -> !"IN_BREEDING".equals(lifecycleService.getCurrentStage(a)))
                .filter(a -> !"NURSING".equals(lifecycleService.getCurrentStage(a)))
                .count();
    }

    private Map<String, Long> getStatusDistribution() {
        Map<String, Long> d = new LinkedHashMap<>();
        d.put("ACTIVE",   lifecycleService.countAll());
        d.put("PREGNANT", lifecycleService.countPregnant());
        d.put("BREEDING", lifecycleService.countInBreeding());
        return d;
    }

    private Map<String, Long> getGenderDistribution() {
        Map<String, Long> d = new LinkedHashMap<>();
        d.put("MALE",   (long) lifecycleService.getAllMales().size());
        d.put("FEMALE", (long) lifecycleService.getAllFemales().size());
        return d;
    }

    // ══════════════════════════════════════════════════════════════════
    // ✅ NEW — Nested helper: location hierarchy resolved from a leaf Location
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
