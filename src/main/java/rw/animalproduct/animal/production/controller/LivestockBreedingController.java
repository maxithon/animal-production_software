package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.dto.ActiveBreedingRowDTO;
import rw.animalproduct.animal.production.dto.FemaleReadyToBreedDTO;
import rw.animalproduct.animal.production.dto.IneligibleFemaleDTO;
import rw.animalproduct.animal.production.dto.MaleReadyToBreedDTO;
import rw.animalproduct.animal.production.dto.ReadyToBreedReportDTO;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.VeterinarianRepository;
import rw.animalproduct.animal.production.services.BreedingReadinessNotificationService;
import rw.animalproduct.animal.production.services.LivestockBreedingService;
import rw.animalproduct.animal.production.services.MalesReadyToBreedService;
import rw.animalproduct.animal.production.services.FemalesReadyToBreedService;
import rw.animalproduct.animal.production.services.ReadyToBreedReportService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/livestock/breeding")
@RequiredArgsConstructor
public class LivestockBreedingController {

    private final LivestockBreedingService breedingService;
    private final LivestockRepository livestockRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final MalesReadyToBreedService malesReadyToBreedService;
    private final FemalesReadyToBreedService femalesReadyToBreedService;
    private final BreedingReadinessNotificationService readinessService;
    private final ReadyToBreedReportService readyToBreedReportService;

    // ─── REDIRECTS FOR OLD/BROKEN URLS ──────────────────────────────────────

    @GetMapping("/register")
    public String redirectRegister() {
        return "redirect:/livestock/breeding/form";
    }

    @GetMapping("/edit/{id}")
    public String redirectEdit(@PathVariable UUID id) {
        return "redirect:/livestock/breeding/form?id=" + id;
    }

    // ─── Dashboard ────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Map<String, Object> stats = breedingService.getDashboardStats();
        List<LivestockBreeding> recentBreedings = breedingService.findRecentBreedings(10);
        List<LivestockBreeding> activePregnancies = breedingService.findActivePregnancies();
        List<LivestockBreeding> overdueChecks = breedingService.findOverduePregnancyChecks();

        model.addAttribute("stats", stats);
        model.addAttribute("recentBreedings", recentBreedings);
        model.addAttribute("activePregnancies", activePregnancies);
        model.addAttribute("overdueChecks", overdueChecks);
        model.addAttribute("today", LocalDate.now());

        return "livestock-breeding-dashboard";
    }

    // ─── List ─────────────────────────────────────────────────────────────────

    @GetMapping("/list")
    public String list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID livestockId,
            Model model) {

        Page<LivestockBreeding> breedingPage = breedingService.findAll(pageable, status, livestockId);

        model.addAttribute("breedings", breedingPage.getContent());
        model.addAttribute("currentPage", breedingPage.getNumber());
        model.addAttribute("totalPages", breedingPage.getTotalPages());
        model.addAttribute("totalItems", breedingPage.getTotalElements());
        model.addAttribute("pageSize", breedingPage.getSize());
        model.addAttribute("statusFilter", status);
        model.addAttribute("livestockIdFilter", livestockId);

        return "livestock-breeding-list";
    }

    // ─── Form ─────────────────────────────────────────────────────────────────

    @GetMapping("/form")
    public String showForm(
            @RequestParam(required = false) UUID id,
            @RequestParam(required = false) UUID livestockId,
            Model model) {

        LivestockBreeding breeding = new LivestockBreeding();
        Livestock currentFemale = null;

        if (id != null) {
            try {
                LivestockBreeding existing = breedingService.findById(id);
                breeding = existing;
                model.addAttribute("editingId", id);
                currentFemale = existing.getLivestock();
            } catch (Exception e) {
                model.addAttribute("error", "Breeding record not found");
                return "redirect:/livestock/breeding/list";
            }
        } else if (livestockId != null) {
            breeding.setLivestock(livestockRepository.findById(livestockId).orElse(null));
        }

        addFormData(model, currentFemale);
        model.addAttribute("breeding", breeding);

        return "livestock-breeding-form";
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    @PostMapping("/save")
    public String save(
            @Valid @ModelAttribute("breeding") LivestockBreeding breeding,
            BindingResult result,
            @RequestParam(required = false) UUID editingId,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (breeding.getLivestockIdValue() != null) {
            livestockRepository.findById(breeding.getLivestockIdValue())
                    .ifPresentOrElse(
                            selected -> {
                                breeding.setLivestock(selected);
                                boolean isPregnant = Livestock.REPRO_PREGNANT.equals(selected.getReproductiveStatus());
                                boolean isSameRecordsFemale = editingId != null
                                        && selected.getId().equals(breedingService.getById(editingId)
                                        .map(b -> b.getLivestock() != null ? b.getLivestock().getId() : null)
                                        .orElse(null));
                                if (isPregnant && !isSameRecordsFemale) {
                                    result.rejectValue("livestockIdValue", "pregnant",
                                            "This female is already pregnant and cannot be bred again.");
                                }
                                if (!isSameRecordsFemale) {
                                    String ineligibilityReason = breedingService.getIneligibilityReason(selected);
                                    if (ineligibilityReason != null) {
                                        result.rejectValue("livestockIdValue", "notEligible",
                                                selected.getTagNumber() + " is not eligible for breeding: "
                                                        + ineligibilityReason);
                                    }
                                }
                            },
                            () -> result.rejectValue("livestockIdValue", "invalid",
                                    "Selected female animal could not be found")
                    );
        } else {
            result.rejectValue("livestockIdValue", "required",
                    "Please select the female animal for this breeding record");
        }

        if (breeding.getMaleLivestockIdValue() != null) {
            livestockRepository.findById(breeding.getMaleLivestockIdValue())
                    .ifPresent(breeding::setMale);
        }

        if (breeding.getVeterinarianIdValue() != null) {
            veterinarianRepository.findById(breeding.getVeterinarianIdValue())
                    .ifPresent(breeding::setVeterinarian);
        }

        if (breeding.getBreedingDate() == null) {
            result.rejectValue("breedingDate", "required", "Breeding date is required");
        } else if (breeding.getBreedingDate().isAfter(LocalDate.now())) {
            result.rejectValue("breedingDate", "future", "Breeding date cannot be in the future");
        }

        if (result.hasErrors()) {
            Livestock currentFemale = breeding.getLivestock();
            addFormData(model, currentFemale);
            return "livestock-breeding-form";
        }

        try {
            LivestockBreeding saved;
            if (editingId != null) {
                saved = breedingService.updateBreeding(editingId, breeding);
                redirectAttributes.addFlashAttribute("success", "Breeding record updated successfully!");
            } else {
                saved = breedingService.createBreeding(breeding);
                redirectAttributes.addFlashAttribute("success", "Breeding record created successfully!");
            }
            return "redirect:/livestock/breeding/view/" + saved.getId();
        } catch (Exception e) {
            log.error("Error saving breeding record", e);
            model.addAttribute("error", "An error occurred while saving: " + e.getMessage());
            addFormData(model, breeding.getLivestock());
            return "livestock-breeding-form";
        }
    }

    // ─── View ─────────────────────────────────────────────────────────────────

    @GetMapping("/view/{id}")
    public String view(@PathVariable UUID id, Model model) {
        try {
            LivestockBreeding breeding = breedingService.findById(id);
            model.addAttribute("breeding", breeding);

            if (breeding.getLivestock() != null) {
                model.addAttribute("femaleContext",
                        breedingService.getBreedingContext(breeding.getLivestock().getId()));
            }

            return "livestock-breeding-view";
        } catch (Exception e) {
            model.addAttribute("error", "Breeding record not found");
            return "redirect:/livestock/breeding/list";
        }
    }

    // ─── Delete ──────────────────────────────────────────────────────────────

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            breedingService.softDeleteBreeding(id, "system");
            redirectAttributes.addFlashAttribute("success", "Breeding record deleted successfully!");
        } catch (Exception e) {
            log.error("Error deleting breeding record", e);
            redirectAttributes.addFlashAttribute("error", "An error occurred while deleting");
        }
        return "redirect:/livestock/breeding/list";
    }

    // ─── Status Transitions ─────────────────────────────────────────────────

    @PostMapping("/{id}/confirm-pregnancy")
    public String confirmPregnancy(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate expectedDueDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate pregnancyConfirmedDate,
            RedirectAttributes redirectAttributes) {
        try {
            LivestockBreeding saved = breedingService.confirmPregnancy(id, expectedDueDate, pregnancyConfirmedDate);
            redirectAttributes.addFlashAttribute("success",
                    "Pregnancy confirmed on " + saved.getPregnancyConfirmedDate()
                            + ". The animal's reproductive status has been updated.");

            if (saved.getBreedingDate() != null && saved.getPregnancyConfirmedDate() != null) {
                long daysAfterBreeding = ChronoUnit.DAYS.between(saved.getBreedingDate(), saved.getPregnancyConfirmedDate());
                if (daysAfterBreeding < 21) {
                    redirectAttributes.addFlashAttribute("warning",
                            "Note: Pregnancy was confirmed only " + daysAfterBreeding + " day(s) after breeding — "
                                    + "earlier than the typical 21-day minimum reliable-detection window. "
                                    + "Double-check the date if this wasn't an early vet-confirmed (e.g. ultrasound) result.");
                }
            }
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error confirming pregnancy for breeding {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Could not confirm pregnancy: " + e.getMessage());
        }
        return "redirect:/livestock/breeding/view/" + id;
    }

    @PostMapping("/{id}/mark-failed")
    public String markFailed(
            @PathVariable UUID id,
            @RequestParam(required = false) String failureReason,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate checkDate,
            RedirectAttributes redirectAttributes) {
        try {
            breedingService.markFailed(id, failureReason, checkDate);
            redirectAttributes.addFlashAttribute("success",
                    "Breeding record marked as failed. The animal is now open for a new breeding attempt.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error marking breeding {} as failed", id, e);
            redirectAttributes.addFlashAttribute("error", "Could not update record: " + e.getMessage());
        }
        return "redirect:/livestock/breeding/view/" + id;
    }

    // ─── AJAX Endpoints ─────────────────────────────────────────────────────

    @GetMapping("/api/stats")
    @ResponseBody
    public Map<String, Object> getStats() {
        return breedingService.getDashboardStats();
    }

    @GetMapping("/api/recent")
    @ResponseBody
    public List<LivestockBreeding> getRecent(@RequestParam(defaultValue = "10") int limit) {
        return breedingService.findRecentBreedings(limit);
    }

    @GetMapping("/api/calculate")
    @ResponseBody
    public Map<String, Object> calculate(
            @RequestParam(required = false) UUID livestockId,
            @RequestParam(required = false) String breedingMethod,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate breedingDate) {
        return breedingService.calculatePreview(livestockId, breedingMethod, breedingDate);
    }

    @GetMapping("/api/context")
    @ResponseBody
    public Map<String, Object> getBreedingContext(@RequestParam(required = false) UUID livestockId) {
        return breedingService.getBreedingContext(livestockId);
    }

    // ─── Manual trigger for breeding readiness notifications ──────────────

    @PostMapping("/api/trigger-notifications")
    @ResponseBody
    public Map<String, Object> triggerNotifications() {
        int count = readinessService.sendImmediateNotifications();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("notificationsSent", count);
        result.put("message", "Sent " + count + " breeding readiness notifications");
        return result;
    }

    @GetMapping("/api/check-ready/{id}")
    @ResponseBody
    public Map<String, Object> checkReady(@PathVariable UUID id) {
        boolean isReady = readinessService.isReadyToBreed(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("livestockId", id);
        result.put("readyToBreed", isReady);
        return result;
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    /**
     * Adds form data to the model for the breeding form.
     *
     * <p><strong>CRITICAL FIX:</strong> Males are now filtered using
     * {@link LivestockBreedingService#getMaleIneligibilityReason(Livestock)}
     * to ensure only eligible males (active, healthy, mature, within breeding age)
     * appear in the dropdown. This prevents newborn males like GOAT-004 (5 days old)
     * from being incorrectly displayed in the breeding form.</p>
     *
     * @param model the Spring Model to add attributes to
     * @param currentFemale the currently selected female (if any), used to preserve selection in edit mode
     */
    private void addFormData(Model model, Livestock currentFemale) {
        // Get all non-deceased, non-sold, non-transferred livestock
        List<Livestock> allLivestock = livestockRepository.findAll().stream()
                .filter(ls -> !Livestock.STATUS_DEAD.equals(ls.getStatus())
                        && !Livestock.STATUS_SOLD.equals(ls.getStatus())
                        && !Livestock.STATUS_TRANSFERRED.equals(ls.getStatus()))
                .collect(Collectors.toList());

        // ─── FEMALES: Filter using isEligibleForBreeding() ───
        List<Livestock> females = allLivestock.stream()
                .filter(ls -> "FEMALE".equalsIgnoreCase(ls.getGender()))
                .filter(breedingService::isEligibleForBreeding)
                .collect(Collectors.toList());

        // If editing, ensure the current female is included even if she would normally be filtered out
        if (currentFemale != null && females.stream().noneMatch(f -> f.getId().equals(currentFemale.getId()))) {
            females.add(currentFemale);
        }

        // Group females by category for the dropdown optgroups
        Map<String, List<Livestock>> femalesByCategory = females.stream()
                .collect(Collectors.groupingBy(
                        ls -> ls.getLivestockCategory() != null
                                ? ls.getLivestockCategory().getName()
                                : "Uncategorized",
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // Count eligible females per category (displayed as badges in the UI)
        Map<String, Integer> femaleEligibleCountByCategory = femalesByCategory.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().size(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        // ─── MALES: FILTERED using getMaleIneligibilityReason() ───
        // This is the CRITICAL FIX that excludes immature males like GOAT-004
        // (born 2026-08-14, only 5 days old) from the breeding dropdown.
        // The eligibility check verifies:
        //   1. Gender = MALE
        //   2. Status = ACTIVE
        //   3. Health = HEALTHY
        //   4. Birth date is recorded
        //   5. Age >= category's minimum breeding age (e.g., 6 months for goats)
        //   6. Age <= category's maximum breeding age
        List<Livestock> eligibleMales = allLivestock.stream()
                .filter(ls -> "MALE".equalsIgnoreCase(ls.getGender()))
                .filter(ls -> breedingService.getMaleIneligibilityReason(ls) == null) // ← KEY FIX
                .collect(Collectors.toList());

        // Group eligible males by category (useful for category filtering in the UI)
        Map<String, List<Livestock>> malesByCategory = eligibleMales.stream()
                .collect(Collectors.groupingBy(
                        ls -> ls.getLivestockCategory() != null
                                ? ls.getLivestockCategory().getName()
                                : "Uncategorized",
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // Count eligible males per category (for UI badges)
        Map<String, Integer> maleEligibleCountByCategory = malesByCategory.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().size(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        // ─── Add all attributes to the model ───
        model.addAttribute("vets", veterinarianRepository.findByIsDeletedFalse());
        model.addAttribute("femalesByCategory", femalesByCategory);
        model.addAttribute("femaleEligibleCountByCategory", femaleEligibleCountByCategory);
        model.addAttribute("males", eligibleMales); // ← FILTERED list (GOAT-004 excluded!)
        model.addAttribute("malesByCategory", malesByCategory);
        model.addAttribute("maleEligibleCountByCategory", maleEligibleCountByCategory);
        model.addAttribute("statuses", List.of(
                "NOT_PREGNANT", "PREGNANT", "CONFIRMED", "FAILED"
        ));
        model.addAttribute("methods", List.of(
                "NATURAL", "ARTIFICIAL_INSEMINATION", "EMBRYO_TRANSFER"
        ));

        // Log for debugging
        log.debug("Added form data: {} females across {} categories, {} eligible males across {} categories",
                females.size(), femalesByCategory.size(), eligibleMales.size(), malesByCategory.size());

        if (eligibleMales.isEmpty()) {
            log.warn("No eligible males found for breeding form. Check livestock categories and male eligibility criteria.");
        }
    }

    private void addFormData(Model model) {
        addFormData(model, null);
    }

    // ─── Breeding Management ──────────────────────────────────────────────────

    @GetMapping("/management")
    public String management(Model model) {

        LocalDate today = LocalDate.now();

        // ── KPIs (spec D.1) ──
        model.addAttribute("kpis", breedingService.getManagementKpis());

        // ── Actions Required (spec D.2) ──
        model.addAttribute("actionsRequired", breedingService.getActionsRequired());

        // ── Females Ready to Breed (spec D.3) ──
        List<MaleReadyToBreedDTO> allMales = malesReadyToBreedService.getAllReadyToBreed();
        Map<String, List<MaleReadyToBreedDTO>> groupedMales = allMales.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getCategoryName() != null ? m.getCategoryName() : "Uncategorized",
                        LinkedHashMap::new,
                        Collectors.toList()));

        model.addAttribute("breedingMales", allMales);
        model.addAttribute("groupedMales", groupedMales);
        model.addAttribute("maleTotal", allMales.size());
        model.addAttribute("maleNeverBred", malesReadyToBreedService.countNeverBred());
        model.addAttribute("maleAvgSuccessRate", malesReadyToBreedService.getAverageSuccessRate());
        model.addAttribute("maleNaturalAvgSuccessRate", malesReadyToBreedService.getAverageNaturalSuccessRate());

        List<FemaleReadyToBreedDTO> allFemales = femalesReadyToBreedService.getAllReadyToBreed();
        Map<String, List<FemaleReadyToBreedDTO>> groupedFemales = allFemales.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getCategoryName() != null ? f.getCategoryName() : "Uncategorized",
                        LinkedHashMap::new,
                        Collectors.toList()));

        model.addAttribute("readyFemales", allFemales);
        model.addAttribute("groupedFemales", groupedFemales);

        // ── Females Not Ready for Breeding (renamed, spec D.6) ──
        List<IneligibleFemaleDTO> notReadyFemales = femalesReadyToBreedService.getNotYetEligible();
        Map<String, List<IneligibleFemaleDTO>> groupedNotReadyFemales = notReadyFemales.stream()
                .collect(Collectors.groupingBy(
                        IneligibleFemaleDTO::getCategoryName,
                        LinkedHashMap::new,
                        Collectors.toList()));
        model.addAttribute("ineligibleFemales", notReadyFemales);
        model.addAttribute("groupedIneligibleFemales", groupedNotReadyFemales);

        // ── Active Breedings (spec D.4 — now with overdue auto-flagging) ──
        List<ActiveBreedingRowDTO> activeBreedingRows = breedingService.getActiveBreedingRows();
        model.addAttribute("activeBreedingRows", activeBreedingRows);
        model.addAttribute("overdueActiveCount",
                activeBreedingRows.stream().filter(ActiveBreedingRowDTO::isOverdue).count());

        // ── Pregnant Animals (spec D.5) ──
        List<LivestockBreeding> activePregnancies = breedingService.getAllActivePregnancies();
        List<Map<String, Object>> pregnantAnimals = new ArrayList<>();

        for (LivestockBreeding b : activePregnancies) {
            if (b.getLivestock() == null) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tagNumber", b.getLivestock().getTagNumber());
            row.put("categoryName", b.getLivestock().getLivestockCategory() != null
                    ? b.getLivestock().getLivestockCategory().getName()
                    : "Uncategorized");
            row.put("expectedDueDate", b.getExpectedDueDate());
            row.put("daysUntilDue", b.getExpectedDueDate() != null
                    ? ChronoUnit.DAYS.between(today, b.getExpectedDueDate())
                    : null);
            pregnantAnimals.add(row);
        }

        Map<String, List<Map<String, Object>>> groupedPregnant = pregnantAnimals.stream()
                .collect(Collectors.groupingBy(
                        r -> (String) r.get("categoryName"),
                        LinkedHashMap::new,
                        Collectors.toList()));

        model.addAttribute("pregnantAnimals", pregnantAnimals);
        model.addAttribute("groupedPregnant", groupedPregnant);

        // ── Failed Breedings (spec D.8 — kept at the bottom) ──
        List<LivestockBreeding> failedBreedings = breedingService
                .findAll(Pageable.unpaged(), LivestockBreeding.STATUS_FAILED, null)
                .getContent();
        model.addAttribute("failedBreedings", failedBreedings);

        double successRate = breedingService.getSuccessRate();
        model.addAttribute("successRate", Math.round(successRate * 10.0) / 10.0);

        List<LivestockBreeding> confirmedPregnant = breedingService
                .findAll(Pageable.unpaged(), LivestockBreeding.STATUS_CONFIRMED_PREGNANT, null)
                .getContent();

        OptionalDouble avgDays = confirmedPregnant.stream()
                .filter(b -> b.getBreedingDate() != null && b.getExpectedPregnancyCheckDate() != null)
                .mapToLong(b -> ChronoUnit.DAYS.between(b.getBreedingDate(), b.getExpectedPregnancyCheckDate()))
                .average();

        model.addAttribute("avgDaysToConception", avgDays.isPresent() ? Math.round(avgDays.getAsDouble()) : null);
        model.addAttribute("today", today);

        return "livestock-breeding-management";
    }

    // ─── NEW: Ready to Breed Dashboard (spec C) ────────────────────────────

    @GetMapping("/ready-to-breed")
    public String readyToBreedDashboard(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            Model model) {

        List<ReadyToBreedReportDTO> readyFemales = readyToBreedReportService.getAllReady(categoryId, search);
        List<MaleReadyToBreedDTO> eligibleMales = malesReadyToBreedService.getAllReadyToBreed();
        List<IneligibleFemaleDTO> needAttention = femalesReadyToBreedService.getNotYetEligible();

        long categoriesReady = readyFemales.stream()
                .map(ReadyToBreedReportDTO::getCategoryName)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();

        Map<String, List<ReadyToBreedReportDTO>> femalesByCategory = readyFemales.stream()
                .collect(Collectors.groupingBy(
                        f -> f.getCategoryName() != null ? f.getCategoryName() : "Uncategorized",
                        LinkedHashMap::new, Collectors.toList()));

        Map<String, List<MaleReadyToBreedDTO>> malesByCategory = eligibleMales.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getCategoryName() != null ? m.getCategoryName() : "Uncategorized",
                        LinkedHashMap::new, Collectors.toList()));

        model.addAttribute("femalesReadyCount", readyFemales.size());
        model.addAttribute("malesEligibleCount", eligibleMales.size());
        model.addAttribute("categoriesReadyCount", categoriesReady);
        model.addAttribute("needAttentionCount", needAttention.size());

        model.addAttribute("femalesByCategory", femalesByCategory);
        model.addAttribute("malesByCategory", malesByCategory);
        model.addAttribute("needAttention", needAttention);
        model.addAttribute("today", LocalDate.now());

        return "livestock-ready-to-breed-dashboard";
    }
}