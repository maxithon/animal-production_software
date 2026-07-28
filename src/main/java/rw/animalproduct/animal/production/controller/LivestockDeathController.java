package rw.animalproduct.animal.production.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockDeath;
import rw.animalproduct.animal.production.repository.LivestockDeathRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.services.LivestockDeathService;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
@RequestMapping("/livestock")
public class LivestockDeathController {

    private final LivestockDeathService deathService;
    private final LivestockRepository livestockRepository;
    private final LivestockDeathRepository deathRepository;

    private static final int DEFAULT_PAGE_SIZE = 10;

    @Autowired
    public LivestockDeathController(LivestockDeathService deathService,
                                    LivestockRepository livestockRepository,
                                    LivestockDeathRepository deathRepository) {
        this.deathService = deathService;
        this.livestockRepository = livestockRepository;
        this.deathRepository = deathRepository;
    }

    /**
     * GET /livestock/deaths - List all death records with pagination
     */
    @GetMapping("/deaths")
    public String listDeaths(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sort", defaultValue = "deathDate") String sort,
            @RequestParam(value = "direction", defaultValue = "desc") String direction,
            Model model) {

        if (size > 50) size = 50;
        if (size < 1) size = DEFAULT_PAGE_SIZE;

        Sort.Direction dir = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sort));

        Page<LivestockDeath> deathPage;
        if (search != null && !search.trim().isEmpty()) {
            deathPage = deathRepository.searchDeaths(search.trim(), pageable);
        } else {
            deathPage = deathRepository.findAllActive(pageable);
        }

        List<String> excludedStatuses = Arrays.asList("DEAD", "SOLD");
        List<Livestock> livestockList = livestockRepository.findByStatusNotIn(excludedStatuses);

        LivestockDeath death = new LivestockDeath();
        death.setDeathDate(LocalDate.now());

        int totalPages = deathPage.getTotalPages();
        List<Integer> pageNumbers = IntStream.rangeClosed(1, totalPages).boxed().collect(Collectors.toList());

        model.addAttribute("deaths", deathPage.getContent());
        model.addAttribute("deathPage", deathPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", size);
        model.addAttribute("pageNumbers", pageNumbers);
        model.addAttribute("search", search);
        model.addAttribute("sort", sort);
        model.addAttribute("direction", direction);
        model.addAttribute("livestockList", livestockList);
        model.addAttribute("death", death);

        return "livestock-deaths-list";
    }

    /**
     * POST /livestock/deaths/new - Create a new death record
     *
     * FIX: @RequestParam names now match the actual field names Thymeleaf
     * generates from th:field="*{livestockIdValue}" / *{causeOfDeath} —
     * this was the cause of the "Required parameter 'livestockId' is not
     * present" 400 error (the form was sending "livestockIdValue", the
     * controller was demanding "livestockId").
     *
     * NEW: "otherCauseDetail" — free-text box shown only when "Other" is
     * picked in the dropdown. If present, it REPLACES "Other" as the
     * stored cause of death (no schema change needed).
     */
    @PostMapping("/deaths/new")
    public String createDeath(
            @RequestParam("livestockIdValue") String livestockIdStr,
            @RequestParam("deathDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deathDate,
            @RequestParam(value = "causeOfDeath", required = false) String causeOfDeath,
            @RequestParam(value = "otherCauseDetail", required = false) String otherCauseDetail,
            RedirectAttributes redirectAttributes) {

        try {
            String resolvedCause = resolveCause(causeOfDeath, otherCauseDetail);

            LivestockDeath death = new LivestockDeath();
            death.setLivestockIdValue(livestockIdStr);
            death.setDeathDate(deathDate);
            death.setCauseOfDeath(resolvedCause);
            death.setIsDeleted(false);

            LivestockDeath saved = deathService.addNew(death);

            String animalTag = saved.getLivestock() != null ? saved.getLivestock().getTagNumber() : livestockIdStr;
            redirectAttributes.addFlashAttribute("success",
                    "Death recorded successfully for animal: " + animalTag +
                            (resolvedCause != null && !resolvedCause.isEmpty() ? " | Cause: " + resolvedCause : ""));

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to record death: " + e.getMessage());
        }

        return "redirect:/livestock/deaths";
    }

    /**
     * GET /livestock/deaths/edit/{id} - Show edit form
     */
    @GetMapping("/deaths/edit/{id}")
    public String editDeathForm(@PathVariable UUID id, Model model) {
        LivestockDeath death = deathService.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid death ID: " + id));

        List<Livestock> livestockList = livestockRepository.findAll();

        model.addAttribute("death", death);
        model.addAttribute("livestockList", livestockList);

        return "livestock-death-edit";
    }

    /**
     * POST /livestock/deaths/update/{id} - Update a death record
     */
    @PostMapping("/deaths/update/{id}")
    public String updateDeath(
            @PathVariable UUID id,
            @RequestParam("livestockIdValue") String livestockIdStr,
            @RequestParam("deathDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deathDate,
            @RequestParam(value = "causeOfDeath", required = false) String causeOfDeath,
            @RequestParam(value = "otherCauseDetail", required = false) String otherCauseDetail,
            RedirectAttributes redirectAttributes) {

        try {
            String resolvedCause = resolveCause(causeOfDeath, otherCauseDetail);

            LivestockDeath existing = deathService.getById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid death ID: " + id));

            existing.setLivestockIdValue(livestockIdStr);
            existing.setDeathDate(deathDate);
            existing.setCauseOfDeath(resolvedCause);

            deathService.update(id, existing);
            redirectAttributes.addFlashAttribute("success",
                    "Death record updated successfully");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to update death: " + e.getMessage());
        }

        return "redirect:/livestock/deaths";
    }

    /**
     * POST /livestock/deaths/delete/{id} - Delete a death record
     */
    @PostMapping("/deaths/delete/{id}")
    public String deleteDeath(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            deathService.delete(id);
            redirectAttributes.addFlashAttribute("success",
                    "Death record deleted successfully. Animal status restored to ACTIVE.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to delete death: " + e.getMessage());
        }
        return "redirect:/livestock/deaths";
    }

    // ── helper ───────────────────────────────────────────────────────
    private String resolveCause(String causeOfDeath, String otherCauseDetail) {
        if ("Other".equals(causeOfDeath) && otherCauseDetail != null && !otherCauseDetail.trim().isEmpty()) {
            return otherCauseDetail.trim();
        }
        return causeOfDeath;
    }
}