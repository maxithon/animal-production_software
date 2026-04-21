package rw.animalproduct.animal.production.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.services.LivestockLifecycleService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * IMPROVED Livestock Lifecycle Controller
 *
 * Complete lifecycle tracking from birth to offspring with proper data integration
 */
@Controller
@RequestMapping("/livestock/lifecycle")
public class LivestockLifecycleController {

    private final LivestockLifecycleService lifecycleService;

    public LivestockLifecycleController(LivestockLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN DASHBOARD - Complete lifecycle overview
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        // ─── Lifecycle Stage Summary ─────────────────────────────────────────
        Map<String, Long> lifecycleSummary = new LinkedHashMap<>();
        lifecycleSummary.put("NEWBORN", lifecycleService.countNewborns());
        lifecycleSummary.put("YOUNG", countYoung());
        lifecycleSummary.put("PRE_BREEDING", countPreBreeding());
        lifecycleSummary.put("READY_TO_BREED", lifecycleService.countReadyToBreed());
        lifecycleSummary.put("BREEDING_MALE", countBreedingMales());
        lifecycleSummary.put("PREGNANT", lifecycleService.countPregnant());
        lifecycleSummary.put("MATURE", countMature());

        model.addAttribute("lifecycleSummary", lifecycleSummary);

        // ─── Status Distribution ──────────────────────────────────────────────
        Map<String, Long> statusSummary = getStatusDistribution();
        model.addAttribute("statusSummary", statusSummary);

        // ─── Gender Distribution ──────────────────────────────────────────────
        Map<String, Long> genderSummary = getGenderDistribution();
        model.addAttribute("genderSummary", genderSummary);

        // ─── Recent Newborns (last 30 days) ───────────────────────────────────
        List<Map<String, Object>> recentNewborns = lifecycleService.getRecentlyBorn(30)
                .stream()
                .limit(10)
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());
        model.addAttribute("recentNewborns", recentNewborns);

        // ─── Pregnant Animals ─────────────────────────────────────────────────
        List<Map<String, Object>> pregnantList = lifecycleService.getPregnantAnimals()
                .stream()
                .limit(10)
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());
        model.addAttribute("pregnantList", pregnantList);

        // ─── Ready to Breed ───────────────────────────────────────────────────
        List<Map<String, Object>> readyToBreed = lifecycleService.getReadyToBreed()
                .stream()
                .limit(10)
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());
        model.addAttribute("readyToBreed", readyToBreed);

        // ─── Active Breedings ─────────────────────────────────────────────────
        List<LivestockBreeding> activeBreedings = lifecycleService.getActiveBreedings();
        model.addAttribute("activeBreedings", activeBreedings);

        // ─── Key Metrics ──────────────────────────────────────────────────────
        model.addAttribute("totalAnimals", lifecycleService.countAll());
        model.addAttribute("dueSoonCount", lifecycleService.countDueSoon(14));
        model.addAttribute("overdueCount", lifecycleService.getOverdue().size());
        model.addAttribute("breedingSuccessRate",
                String.format("%.1f%%", lifecycleService.getBreedingSuccessRate()));

        return "livestock-lifecycle-dashboard";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STAGE-SPECIFIC VIEWS
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/pre-breeding")
    public String preBreeding(Model model) {
        List<Map<String, Object>> youngFemales = lifecycleService.getYoungFemales()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<Map<String, Object>> youngMales = lifecycleService.getYoungMales()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<Map<String, Object>> approaching = lifecycleService.getApproachingBreedingAge()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        model.addAttribute("youngFemales", youngFemales);
        model.addAttribute("youngMales", youngMales);
        model.addAttribute("approachingBreedingAge", approaching);
        model.addAttribute("pageTitle", "Pre-Breeding Management");

        return "livestock-pre-breeding";
    }

    @GetMapping("/ready-to-breed")
    public String readyToBreed(Model model) {
        List<Map<String, Object>> readyFemales = lifecycleService.getReadyToBreed()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<Map<String, Object>> readyMales = lifecycleService.getMalesReadyToBreed()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<Map<String, Object>> suggestions = lifecycleService.getBreedingSuggestions();

        model.addAttribute("readyFemales", readyFemales);
        model.addAttribute("readyMales", readyMales);
        model.addAttribute("breedingSuggestions", suggestions);
        model.addAttribute("pageTitle", "Ready to Breed");

        return "livestock-ready-to-breed";
    }

    @GetMapping("/breeding-management")
    public String breedingManagement(Model model) {
        List<LivestockBreeding> activeBreedings = lifecycleService.getActiveBreedings();
        List<LivestockBreeding> pendingCheck = lifecycleService.getPendingPregnancyCheck();
        List<LivestockBreeding> recentBreedings = lifecycleService.getRecentlyBred(30);
        List<LivestockBreeding> failed = lifecycleService.getFailedBreedings();

        model.addAttribute("activeBreedings", activeBreedings);
        model.addAttribute("pendingPregnancyCheck", pendingCheck);
        model.addAttribute("recentBreedings", recentBreedings);
        model.addAttribute("failedBreedings", failed);
        model.addAttribute("successRate", lifecycleService.getBreedingSuccessRate());
        model.addAttribute("avgDaysToConception", lifecycleService.getAvgDaysToConception());
        model.addAttribute("pageTitle", "Breeding Management");

        return "livestock-breeding-management";
    }

    @GetMapping("/pregnancy-tracking")
    public String pregnancyTracking(Model model) {
        List<Map<String, Object>> pregnant = lifecycleService.getPregnantAnimals()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<Map<String, Object>> dueSoon = lifecycleService.getDueSoon(14)
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<Map<String, Object>> overdue = lifecycleService.getOverdue()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<Map<String, Object>> calendar = lifecycleService.getDueDateCalendar();

        model.addAttribute("pregnantAnimals", pregnant);
        model.addAttribute("dueSoon", dueSoon);
        model.addAttribute("overdue", overdue);
        model.addAttribute("dueDateCalendar", calendar);
        model.addAttribute("pageTitle", "Pregnancy Tracking");

        return "livestock-pregnancy-tracking";
    }

    @GetMapping("/male-management")
    public String maleManagement(Model model) {
        List<Map<String, Object>> allMales = lifecycleService.getAllMales()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<Map<String, Object>> readyMales = lifecycleService.getMalesReadyToBreed()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<Map<String, Object>> breedingStats = lifecycleService.getMaleBreedingStats();

        model.addAttribute("allMales", allMales);
        model.addAttribute("readyMales", readyMales);
        model.addAttribute("breedingStats", breedingStats);
        model.addAttribute("pageTitle", "Male Management");

        return "livestock-male-management";
    }

    @GetMapping("/female-management")
    public String femaleManagement(Model model) {
        List<Map<String, Object>> allFemales = lifecycleService.getAllFemales()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<Map<String, Object>> pregnant = lifecycleService.getPregnantAnimals()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<Map<String, Object>> nursing = lifecycleService.getNursingAnimals()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<Map<String, Object>> readyToBreed = lifecycleService.getReadyToBreed()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        model.addAttribute("allFemales", allFemales);
        model.addAttribute("pregnantAnimals", pregnant);
        model.addAttribute("nursingAnimals", nursing);
        model.addAttribute("readyToBreed", readyToBreed);
        model.addAttribute("pageTitle", "Female Management");

        return "livestock-female-management";
    }

    @GetMapping("/notifications")
    public String notifications(Model model) {
        List<Map<String, Object>> dueSoon = lifecycleService.getDueSoon(30)
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<Map<String, Object>> overdue = lifecycleService.getOverdue()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<LivestockBreeding> overdueCheck = lifecycleService.getOverduePregnancyCheck();

        List<Map<String, Object>> approaching = lifecycleService.getApproachingBreedingAge()
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<Map<String, Object>> recentBorn = lifecycleService.getRecentlyBorn(14)
                .stream()
                .map(this::enrichAnimalData)
                .collect(Collectors.toList());

        List<LivestockBreeding> failed = lifecycleService.getFailedBreedings();

        int totalNotifications = dueSoon.size() + overdue.size() + overdueCheck.size() +
                approaching.size() + recentBorn.size() + failed.size();

        model.addAttribute("dueSoon", dueSoon);
        model.addAttribute("overdue", overdue);
        model.addAttribute("overduePregnancyCheck", overdueCheck);
        model.addAttribute("approachingBreedingAge", approaching);
        model.addAttribute("recentBirths", recentBorn);
        model.addAttribute("failedBreedings", failed);
        model.addAttribute("totalNotifications", totalNotifications);
        model.addAttribute("pageTitle", "Lifecycle Notifications");

        return "livestock-lifecycle-notifications";
    }

    @GetMapping("/animal/{id}")
    public String animalDetail(@PathVariable UUID id, Model model) {
        // This would need to be implemented in the service
        // For now, redirect to the main livestock view
        return "redirect:/livestock/view/" + id;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // API ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/api/summary")
    @ResponseBody
    public Map<String, Object> apiSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", lifecycleService.countAll());
        summary.put("newborns", lifecycleService.countNewborns());
        summary.put("preBreeding", countPreBreeding());
        summary.put("readyToBreed", lifecycleService.countReadyToBreed());
        summary.put("inBreeding", lifecycleService.countInBreeding());
        summary.put("pregnant", lifecycleService.countPregnant());
        summary.put("dueSoon", lifecycleService.countDueSoon(14));
        summary.put("overdue", lifecycleService.getOverdue().size());
        summary.put("notifications", lifecycleService.countAllNotifications());
        return summary;
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

    /**
     * Enrich animal data with computed fields for display
     */
    private Map<String, Object> enrichAnimalData(Livestock animal) {
        Map<String, Object> data = new LinkedHashMap<>();

        data.put("id", animal.getId());
        data.put("tagNumber", animal.getTagNumber());
        data.put("gender", animal.getGender());
        data.put("status", animal.getStatus());
        data.put("category", animal.getLivestockCategory() != null ?
                animal.getLivestockCategory().getName() : null);

        // Age calculations
        long ageInDays = lifecycleService.getAgeInDays(animal);
        long ageInMonths = lifecycleService.getAgeInMonths(animal);
        data.put("ageInDays", ageInDays);
        data.put("ageInMonths", ageInMonths);
        data.put("ageDisplay", formatAge(ageInDays));

        // Lifecycle stage
        String stage = lifecycleService.getCurrentStage(animal);
        data.put("lifecycleStage", stage);
        data.put("nextMilestone", lifecycleService.getNextMilestone(animal));

        // Breeding/pregnancy data
        data.put("expectedDueDate", animal.getExpectedDueDate());
        data.put("lastBirthDate", animal.getLastBirthDate());
        data.put("offspringCount", animal.getOffspringCount());

        // Days until due (for pregnant animals)
        if (animal.getExpectedDueDate() != null) {
            long daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), animal.getExpectedDueDate());
            data.put("daysUntilDue", daysUntilDue);
        }

        // Offspring
        List<Livestock> offspring = lifecycleService.getOffspring(animal.getId());
        data.put("offspring", offspring);
        data.put("offspringTotal", offspring.size());

        return data;
    }

    /**
     * Format age in human-readable format
     */
    private String formatAge(long days) {
        if (days < 30) {
            return days + " days";
        } else if (days < 365) {
            long months = days / 30;
            return months + " month" + (months > 1 ? "s" : "");
        } else {
            long years = days / 365;
            long remainingMonths = (days % 365) / 30;
            String result = years + " year" + (years > 1 ? "s" : "");
            if (remainingMonths > 0) {
                result += ", " + remainingMonths + " month" + (remainingMonths > 1 ? "s" : "");
            }
            return result;
        }
    }

    /**
     * Count young animals (31-180 days)
     */
    private long countYoung() {
        return lifecycleService.getYoungFemales().size() +
                lifecycleService.getYoungMales().size();
    }

    /**
     * Count pre-breeding animals (181-365 days)
     */
    private long countPreBreeding() {
        return lifecycleService.countPreBreeding();
    }

    /**
     * Count breeding males (ready to breed)
     */
    private long countBreedingMales() {
        return lifecycleService.getMalesReadyToBreed().size();
    }

    /**
     * Count mature animals (>365 days, not in other categories)
     */
    private long countMature() {
        // Animals over 365 days that are not pregnant, breeding, or nursing
        return lifecycleService.getAllFemales().stream()
                .filter(a -> lifecycleService.getAgeInDays(a) > 365)
                .filter(a -> !"PREGNANT".equals(lifecycleService.getCurrentStage(a)))
                .filter(a -> !"IN_BREEDING".equals(lifecycleService.getCurrentStage(a)))
                .filter(a -> !"NURSING".equals(lifecycleService.getCurrentStage(a)))
                .count();
    }

    /**
     * Get status distribution
     */
    private Map<String, Long> getStatusDistribution() {
        Map<String, Long> dist = new LinkedHashMap<>();
        dist.put("ACTIVE", lifecycleService.countAll());
        dist.put("PREGNANT", lifecycleService.countPregnant());
        dist.put("BREEDING", lifecycleService.countInBreeding());
        return dist;
    }

    /**
     * Get gender distribution
     */
    private Map<String, Long> getGenderDistribution() {
        Map<String, Long> dist = new LinkedHashMap<>();
        dist.put("MALE", (long) lifecycleService.getAllMales().size());
        dist.put("FEMALE", (long) lifecycleService.getAllFemales().size());
        return dist;
    }
}
