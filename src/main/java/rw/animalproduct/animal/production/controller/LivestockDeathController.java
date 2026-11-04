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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // ═════════════════════════════════════════════════════════════════
    // LIST
    // ═════════════════════════════════════════════════════════════════
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

    // ═════════════════════════════════════════════════════════════════
    // API: LIVE SEARCH ANIMALS (for the death form)
    // ═════════════════════════════════════════════════════════════════
    @GetMapping("/deaths/api/search-animals")
    @ResponseBody
    public List<Map<String, Object>> searchAnimalsForDeath(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "15") int limit) {

        List<String> excludedStatuses = Arrays.asList("DEAD", "SOLD");
        List<Livestock> candidates = livestockRepository.findByStatusNotIn(excludedStatuses);

        if (q != null && !q.trim().isEmpty()) {
            String query = q.trim().toLowerCase();
            candidates = candidates.stream()
                    .filter(a -> (a.getTagNumber() != null
                            && a.getTagNumber().toLowerCase().contains(query))
                            || (a.getLivestockCategory() != null
                            && a.getLivestockCategory().getName() != null
                            && a.getLivestockCategory().getName().toLowerCase().contains(query)))
                    .limit(limit)
                    .collect(Collectors.toList());
        } else {
            candidates = candidates.stream().limit(limit).collect(Collectors.toList());
        }

        return candidates.stream().map(a -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id",           a.getId().toString());
            map.put("tagNumber",    a.getTagNumber());
            map.put("category",     a.getLivestockCategory() != null
                    ? a.getLivestockCategory().getName() : "—");
            map.put("gender",       a.getGender());
            map.put("status",       a.getStatus());
            map.put("healthStatus", a.getHealthStatus());
            return map;
        }).collect(Collectors.toList());
    }

    // ═════════════════════════════════════════════════════════════════
    // CREATE
    // ═════════════════════════════════════════════════════════════════
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

    // ═════════════════════════════════════════════════════════════════
    // EDIT FORM
    // ═════════════════════════════════════════════════════════════════
    @GetMapping("/deaths/edit/{id}")
    public String editDeathForm(@PathVariable UUID id, Model model) {
        LivestockDeath death = deathService.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid death ID: " + id));

        List<Livestock> livestockList = livestockRepository.findAll();

        model.addAttribute("death", death);
        model.addAttribute("livestockList", livestockList);

        return "livestock-death-edit";
    }

    // ═════════════════════════════════════════════════════════════════
    // UPDATE
    // ═════════════════════════════════════════════════════════════════
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

    // ═════════════════════════════════════════════════════════════════
    // DELETE
    // ═════════════════════════════════════════════════════════════════
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

    // ═════════════════════════════════════════════════════════════════
    // REPORT — fixes the 404 on /livestock/deaths-report
    // ═════════════════════════════════════════════════════════════════
    @GetMapping("/deaths-report")
    public String deathsReport(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        final LocalDate fromDate = (from != null) ? from : LocalDate.now().minusMonths(12);
        final LocalDate toDate   = (to   != null) ? to   : LocalDate.now().plusMonths(12);

        List<LivestockDeath> allDeaths = deathRepository.findByDeathDateBetween(fromDate, toDate);

        long totalDeaths = allDeaths.size();

        long thisMonthCount = allDeaths.stream()
                .filter(d -> d.getDeathDate() != null
                        && d.getDeathDate().getMonthValue() == LocalDate.now().getMonthValue()
                        && d.getDeathDate().getYear() == LocalDate.now().getYear())
                .count();

        long distinctAnimals = allDeaths.stream()
                .filter(d -> d.getLivestock() != null)
                .map(d -> d.getLivestock().getId())
                .distinct()
                .count();

        long withCauseCount = allDeaths.stream()
                .filter(d -> d.getCauseOfDeath() != null && !d.getCauseOfDeath().isBlank())
                .count();

        Map<String, Long> causeBreakdown = allDeaths.stream()
                .filter(d -> d.getCauseOfDeath() != null && !d.getCauseOfDeath().isBlank())
                .collect(Collectors.groupingBy(
                        d -> normaliseCause(d.getCauseOfDeath()),
                        Collectors.counting()));

        Map<String, Long> categoryBreakdown = allDeaths.stream()
                .filter(d -> d.getLivestock() != null
                        && d.getLivestock().getLivestockCategory() != null)
                .collect(Collectors.groupingBy(
                        d -> d.getLivestock().getLivestockCategory().getName(),
                        Collectors.counting()));

        Map<String, Long> monthlyTrend = new LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) {
            LocalDate month = LocalDate.now().minusMonths(i);
            String key = month.getYear() + "-" + String.format("%02d", month.getMonthValue());
            long count = allDeaths.stream()
                    .filter(d -> d.getDeathDate() != null
                            && d.getDeathDate().getYear() == month.getYear()
                            && d.getDeathDate().getMonthValue() == month.getMonthValue())
                    .count();
            monthlyTrend.put(key, count);
        }

        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("deaths", allDeaths);
        model.addAttribute("totalDeaths", totalDeaths);
        model.addAttribute("thisMonthCount", thisMonthCount);
        model.addAttribute("distinctAnimals", distinctAnimals);
        model.addAttribute("withCauseCount", withCauseCount);
        model.addAttribute("causeBreakdown", causeBreakdown);
        model.addAttribute("categoryBreakdown", categoryBreakdown);
        model.addAttribute("monthlyTrend", monthlyTrend);

        return "livestock-deaths-report";
    }

    // ── helper ───────────────────────────────────────────────────────

    private String resolveCause(String causeOfDeath, String otherCauseDetail) {
        if ("Other".equals(causeOfDeath) && otherCauseDetail != null && !otherCauseDetail.trim().isEmpty()) {
            return otherCauseDetail.trim();
        }
        return causeOfDeath;
    }

    private String normaliseCause(String raw) {
        if (raw == null) return "Other";
        String r = raw.toLowerCase();
        if (r.contains("disease") || r.contains("infection") || r.contains("virus")) return "Disease";
        if (r.contains("injury") || r.contains("wound") || r.contains("fracture"))   return "Injury";
        if (r.contains("natural") || r.contains("old age"))                            return "Natural";
        if (r.contains("predator") || r.contains("attack"))                            return "Predator";
        if (r.contains("starv") || r.contains("malnutrition"))                         return "Starvation";
        if (r.contains("poison") || r.contains("toxic"))                               return "Poisoning";
        if (r.contains("birth") || r.contains("calving") || r.contains("dystocia"))    return "Birth Complication";
        if (r.contains("unknown"))                                                     return "Unknown";
        return "Other";
    }
}