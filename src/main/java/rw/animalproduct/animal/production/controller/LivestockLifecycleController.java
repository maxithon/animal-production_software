package rw.animalproduct.animal.production.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.dto.FemaleReadyToBreedDTO;
import rw.animalproduct.animal.production.dto.MaleReadyToBreedDTO;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.services.FemalesReadyToBreedService;
import rw.animalproduct.animal.production.services.LivestockBreedingService;
import rw.animalproduct.animal.production.services.LivestockLifecycleService;
import rw.animalproduct.animal.production.services.MalesReadyToBreedService;

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

    private final LivestockLifecycleService   lifecycleService;
    private final LivestockBreedingService    breedingService;
    private final MalesReadyToBreedService    malesReadyToBreedService;
    private final FemalesReadyToBreedService  femalesReadyToBreedService;

    public LivestockLifecycleController(
            LivestockLifecycleService lifecycleService,
            LivestockBreedingService breedingService,
            MalesReadyToBreedService malesReadyToBreedService,
            FemalesReadyToBreedService femalesReadyToBreedService) {
        this.lifecycleService          = lifecycleService;
        this.breedingService           = breedingService;
        this.malesReadyToBreedService  = malesReadyToBreedService;
        this.femalesReadyToBreedService= femalesReadyToBreedService;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN DASHBOARD
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        // ── Lifecycle pipeline counts ──────────────────────────────────────────
        // IMPORTANT: YOUNG was previously missing from this map, causing the
        // "Young" pipeline step to always show 0 in the template.
        Map<String, Long> lifecycleSummary = new LinkedHashMap<>();
        lifecycleSummary.put("NEWBORN",        lifecycleService.countNewborns());
        lifecycleSummary.put("YOUNG",          lifecycleService.countYoung());       // ← WAS MISSING
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

        model.addAttribute("pregnantList",   lifecycleService.getPregnantAnimals().stream()
                .limit(10).map(this::enrichAnimalData).collect(Collectors.toList()));

        model.addAttribute("readyToBreed",   lifecycleService.getReadyToBreed().stream()
                .limit(10).map(this::enrichAnimalData).collect(Collectors.toList()));

        model.addAttribute("activeBreedings",     lifecycleService.getActiveBreedings());
        model.addAttribute("totalAnimals",        lifecycleService.countAll());
        model.addAttribute("dueSoonCount",        lifecycleService.countDueSoon(14));
        model.addAttribute("overdueCount",        lifecycleService.getOverdue().size());
        model.addAttribute("breedingSuccessRate",
                String.format("%.1f%%", lifecycleService.getBreedingSuccessRate()));

        return "livestock-lifecycle-dashboard";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STAGE-SPECIFIC VIEWS
    // ═══════════════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════════════
    // READY TO BREED
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/ready-to-breed")
    public String readyToBreed(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            Model model) {

        String selectedCategory = (category != null && !category.isEmpty()) ? category : "all";
        String searchTerm = (search != null && !search.isEmpty()) ? search : "";

        // Get all data
        List<MaleReadyToBreedDTO> allMales = malesReadyToBreedService.getAllReadyToBreed();
        List<FemaleReadyToBreedDTO> allFemales = femalesReadyToBreedService.getAllReadyToBreed();

        // Apply search filter
        if (!searchTerm.isEmpty()) {
            allMales = allMales.stream()
                    .filter(m -> (m.getTagNumber() != null && m.getTagNumber().toLowerCase().contains(searchTerm.toLowerCase())) ||
                            (m.getCategoryName() != null && m.getCategoryName().toLowerCase().contains(searchTerm.toLowerCase())))
                    .collect(Collectors.toList());

            allFemales = allFemales.stream()
                    .filter(f -> (f.getTagNumber() != null && f.getTagNumber().toLowerCase().contains(searchTerm.toLowerCase())) ||
                            (f.getCategoryName() != null && f.getCategoryName().toLowerCase().contains(searchTerm.toLowerCase())))
                    .collect(Collectors.toList());
        }

        // Group by category
        Map<String, List<MaleReadyToBreedDTO>> malesByCategory = allMales.stream()
                .filter(m -> m.getCategoryName() != null)
                .collect(Collectors.groupingBy(MaleReadyToBreedDTO::getCategoryName,
                        LinkedHashMap::new,
                        Collectors.toList()));

        Map<String, List<FemaleReadyToBreedDTO>> femalesByCategory = allFemales.stream()
                .filter(f -> f.getCategoryName() != null)
                .collect(Collectors.groupingBy(FemaleReadyToBreedDTO::getCategoryName,
                        LinkedHashMap::new,
                        Collectors.toList()));

        // Get all unique categories
        Set<String> allCategories = new LinkedHashSet<>();
        allCategories.addAll(malesByCategory.keySet());
        allCategories.addAll(femalesByCategory.keySet());

        // Apply category filter if needed
        if (!"all".equals(selectedCategory)) {
            Map<String, List<MaleReadyToBreedDTO>> filteredMalesByCategory = new LinkedHashMap<>();
            Map<String, List<FemaleReadyToBreedDTO>> filteredFemalesByCategory = new LinkedHashMap<>();

            if (malesByCategory.containsKey(selectedCategory)) {
                filteredMalesByCategory.put(selectedCategory, malesByCategory.get(selectedCategory));
            }
            if (femalesByCategory.containsKey(selectedCategory)) {
                filteredFemalesByCategory.put(selectedCategory, femalesByCategory.get(selectedCategory));
            }

            malesByCategory = filteredMalesByCategory;
            femalesByCategory = filteredFemalesByCategory;
        }

        // Calculate totals
        int maleTotal = allMales.size();
        int femaleTotal = allFemales.size();
        double maleAvgSuccessRate = malesReadyToBreedService.getAverageSuccessRate();
        long maleNeverBred = malesReadyToBreedService.countNeverBred();
        long femaleNeverBred = femalesReadyToBreedService.countNeverBred();

        // Add attributes
        model.addAttribute("malesByCategory", malesByCategory);
        model.addAttribute("femalesByCategory", femalesByCategory);
        model.addAttribute("allCategories", allCategories);
        model.addAttribute("selectedCategory", selectedCategory);
        model.addAttribute("maleTotal", maleTotal);
        model.addAttribute("femaleTotal", femaleTotal);
        model.addAttribute("maleAvgSuccessRate", maleAvgSuccessRate);
        model.addAttribute("maleNeverBred", maleNeverBred);
        model.addAttribute("femaleNeverBred", femaleNeverBred);
        model.addAttribute("search", searchTerm);
        model.addAttribute("pageTitle", "Ready to Breed");

        return "livestock-ready-to-breed";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BREEDING MANAGEMENT - COMMENTED OUT TO AVOID DUPLICATE
    // Use /livestock/lifecycle/breeding-management from LivestockBreedingController instead
    // ═══════════════════════════════════════════════════════════════════════════

    // @GetMapping("/breeding-management")
    // public String breedingManagement(Model model) {
    //     This method has been moved to LivestockBreedingController
    //     to avoid duplicate mapping error.
    //     Please use LivestockBreedingController for breeding management.
    // }

    // ═══════════════════════════════════════════════════════════════════════════
    // PREGNANCY TRACKING
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/pregnancy-tracking")
    public String pregnancyTracking(Model model) {

        LocalDate today = LocalDate.now();

        List<LivestockBreeding> allPregnant = breedingService.getAll().stream()
                .filter(b -> "CONFIRMED_PREGNANT".equals(b.getStatus()))
                .filter(b -> !Boolean.TRUE.equals(b.getIsDeleted()))
                .sorted(Comparator.comparingLong(b -> {
                    if (b.getExpectedDueDate() == null) return Long.MAX_VALUE;
                    return ChronoUnit.DAYS.between(today, b.getExpectedDueDate());
                }))
                .collect(Collectors.toList());

        long dueSoonCount = allPregnant.stream()
                .filter(b -> b.getExpectedDueDate() != null)
                .filter(b -> {
                    long d = ChronoUnit.DAYS.between(today, b.getExpectedDueDate());
                    return d >= 0 && d <= 30;
                }).count();

        long checkupsDue = allPregnant.stream()
                .filter(b -> b.getExpectedPregnancyCheckDate() != null
                        && b.getExpectedPregnancyCheckDate().isBefore(today))
                .count();

        List<LivestockBreeding> criticalCases = allPregnant.stream()
                .filter(b -> b.getExpectedDueDate() != null)
                .filter(b -> ChronoUnit.DAYS.between(today, b.getExpectedDueDate()) < 7)
                .collect(Collectors.toList());

        model.addAttribute("pregnancies",   allPregnant);
        model.addAttribute("totalPregnant", allPregnant.size());
        model.addAttribute("dueSoon",       dueSoonCount);
        model.addAttribute("checkupsDue",   checkupsDue);
        model.addAttribute("highRisk",      criticalCases.size());
        model.addAttribute("criticalCases", criticalCases);
        model.addAttribute("today",         today);
        model.addAttribute("pageTitle",     "Pregnancy Tracking");

        return "livestock-pregnancy-tracking";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // OTHER LIFECYCLE VIEWS
    // ═══════════════════════════════════════════════════════════════════════════

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
        List<Map<String, Object>> readyToBreed = lifecycleService.getReadyToBreed().stream()
                .map(this::enrichAnimalData).collect(Collectors.toList());

        model.addAttribute("dueSoonAnimals",     dueSoonAnimals);
        model.addAttribute("overdueAnimals",     overdueAnimals);
        model.addAttribute("newbornAnimals",     newbornAnimals);
        model.addAttribute("readyToBreed",       readyToBreed);
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

    // ═══════════════════════════════════════════════════════════════════════════
    // REST / API ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

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
        data.put("offspring",      offspring);
        data.put("offspringTotal", offspring.size());
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

    private long countYoung()        { return lifecycleService.countYoung(); }
    private long countPreBreeding()  { return lifecycleService.countPreBreeding(); }
    private long countBreedingMales(){ return lifecycleService.getMalesReadyToBreed().size(); }

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
}