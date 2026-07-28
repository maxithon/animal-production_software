package rw.animalproduct.animal.production.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.dto.CategoryStat;
import rw.animalproduct.animal.production.dto.CategoryWithCount;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * FIX: "All Categories" and "Animals by Category" reports were erroring
 * because no controller populated the model attributes their templates
 * expect:
 *   - livestock-category-filter-report.html needs `categories` where each
 *     entry exposes .livestockCount (LivestockCategory has no such field —
 *     see CategoryWithCount), plus a JSON endpoint for its "Apply Filter" AJAX call.
 *   - livestock-category-report.html / -paginated.html need `categoryStatsList`,
 *     `totalCategories`, `totalActiveLivestock`, `largestCategoryName`,
 *     `avgAnimalsPerCategory`, plus a JSON endpoint for the paginated variant.
 * Neither existed before, so both pages threw when Thymeleaf tried to
 * resolve missing expressions.
 */
@Controller
public class LivestockCategoryReportController {

    @Autowired
    private LivestockCategoryRepository categoryRepository;

    @Autowired
    private LivestockRepository livestockRepository;

    // ─────────────────────────────────────────────────────────────────────
    // "Animals by Category" — filter-style report
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/livestock/categories/filter-report")
    public String filterReport(Model model) {
        List<LivestockCategory> categories = categoryRepository.findAllByIsDeletedFalse();

        List<CategoryWithCount> categoriesWithCount = categories.stream()
                .map(c -> new CategoryWithCount(
                        c.getId(), c.getName(), c.getCode(),
                        livestockRepository.countByCategory(c.getId())))
                .sorted(Comparator.comparing(CategoryWithCount::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        model.addAttribute("categories", categoriesWithCount);
        return "livestock-category-filter-report";
    }

    /** Backs the "Apply Filter" AJAX call on livestock-category-filter-report.html */
    @GetMapping("/livestock/category/{id}/animals-all")
    @ResponseBody
    public List<Map<String, Object>> animalsForCategory(@PathVariable UUID id,
                                                        @RequestParam(required = false) String status) {
        List<Livestock> animals = livestockRepository.findByLivestockCategoryId(id);
        if (status != null && !status.isBlank()) {
            animals = animals.stream()
                    .filter(a -> status.equalsIgnoreCase(a.getStatus()))
                    .collect(Collectors.toList());
        }
        return animals.stream().map(this::toAnimalJson).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────
    // "All Categories" — full stats report
    // ─────────────────────────────────────────────────────────────────────

    @GetMapping("/livestock/categories/report")
    public String fullReport(Model model) {
        List<LivestockCategory> categories = categoryRepository.findAllByIsDeletedFalse();
        List<CategoryStat> statsList = categories.stream()
                .map(this::buildStat)
                .sorted(Comparator.comparing(CategoryStat::getCategoryName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        long totalActive = statsList.stream().mapToLong(CategoryStat::getActiveCount).sum();
        String largestName = statsList.stream()
                .max(Comparator.comparingLong(CategoryStat::getTotalCount))
                .map(CategoryStat::getCategoryName)
                .orElse("—");
        long avgPerCategory = statsList.isEmpty() ? 0
                : Math.round(statsList.stream().mapToLong(CategoryStat::getTotalCount).average().orElse(0));

        model.addAttribute("categoryStatsList", statsList);
        model.addAttribute("totalCategories", statsList.size());
        model.addAttribute("totalActiveLivestock", totalActive);
        model.addAttribute("largestCategoryName", largestName);
        model.addAttribute("avgAnimalsPerCategory", avgPerCategory);
        return "livestock-category-report";
    }

    @GetMapping("/livestock/categories/report/paginated")
    public String paginatedReport(Model model) {
        // Same summary data; the paginated template lazy-loads each
        // category's animals via /livestock/category/{id}/animals below.
        return fullReport(model).equals("livestock-category-report")
                ? "livestock-category-report-paginated"
                : "livestock-category-report-paginated";
    }

    /** Backs the collapsible/paginated animal table on the paginated report page */
    @GetMapping("/livestock/category/{id}/animals")
    @ResponseBody
    public Map<String, Object> animalsPaged(@PathVariable UUID id,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        List<Livestock> all = livestockRepository.findByLivestockCategoryId(id);
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        List<Map<String, Object>> content = all.subList(from, to).stream()
                .map(this::toAnimalJson).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("number", page);
        result.put("totalElements", all.size());
        result.put("totalPages", (int) Math.ceil(all.size() / (double) size));
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private CategoryStat buildStat(LivestockCategory category) {
        List<Livestock> animals = livestockRepository.findByLivestockCategoryId(category.getId());

        CategoryStat stat = new CategoryStat();
        stat.setCategoryId(category.getId());
        stat.setCategoryName(category.getName());
        stat.setTotalCount(animals.size());
        stat.setActiveCount(count(animals, Livestock.STATUS_ACTIVE));
        stat.setSoldCount(count(animals, Livestock.STATUS_SOLD));
        stat.setDeadCount(count(animals, Livestock.STATUS_DEAD));
        stat.setSickCount(count(animals, Livestock.STATUS_SICK));
        stat.setMaleCount(animals.stream().filter(a -> "MALE".equalsIgnoreCase(a.getGender())).count());
        stat.setFemaleCount(animals.stream().filter(a -> "FEMALE".equalsIgnoreCase(a.getGender())).count());
        stat.setPregnantCount(animals.stream().filter(a -> Boolean.TRUE.equals(a.getIsPregnant())).count());
        stat.setTotalValue(animals.stream()
                .map(Livestock::getCurrentValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        stat.setLivestockList(animals);
        return stat;
    }

    private long count(List<Livestock> animals, String status) {
        return animals.stream().filter(a -> status.equals(a.getStatus())).count();
    }

    private Map<String, Object> toAnimalJson(Livestock a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("tagNumber", a.getTagNumber());
        m.put("gender", a.getGender());
        m.put("status", a.getStatus());
        m.put("dateReceived", a.getDateReceived() != null ? a.getDateReceived().toString() : null);
        m.put("currentValue", a.getCurrentValue());
        m.put("locationName", a.getLocation() != null ? a.getLocation().getName() : null);
        return m;
    }
}
