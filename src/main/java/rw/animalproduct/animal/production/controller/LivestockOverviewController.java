package rw.animalproduct.animal.production.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import rw.animalproduct.animal.production.entity.VLivestockWithAge;
import rw.animalproduct.animal.production.services.VLivestockWithAgeService;

import java.util.Map;

@Controller
@RequestMapping("/livestock/overview")
public class LivestockOverviewController {

    private final VLivestockWithAgeService vLivestockService;

    public LivestockOverviewController(VLivestockWithAgeService vLivestockService) {
        this.vLivestockService = vLivestockService;
    }

    @GetMapping
    public String showOverview(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String search,
            Model model
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("tagNumber").ascending());
        Page<VLivestockWithAge> livestockPage;

        // Apply filters
        if (search != null && !search.isEmpty()) {
            livestockPage = vLivestockService.search(search, pageable);
        } else if (stage != null && !stage.isEmpty() && !stage.equals("ALL")) {
            livestockPage = vLivestockService.getByLifecycleStage(stage, pageable);
        } else if (status != null && !status.isEmpty() && !status.equals("ALL")) {
            livestockPage = vLivestockService.getByStatus(status, pageable);
        } else if (category != null && !category.isEmpty() && !category.equals("ALL")) {
            livestockPage = vLivestockService.getByCategory(category, pageable);
        } else {
            livestockPage = vLivestockService.getAll(pageable);
        }

        // Get statistics
        Map<String, Long> lifecycleStats = vLivestockService.getLifecycleStageStats();
        Map<String, Long> categoryStats = vLivestockService.getCategoryStats();
        Map<String, Long> statusStats = vLivestockService.getStatusStats();

        // Add to model
        model.addAttribute("livestockList", livestockPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", livestockPage.getTotalPages());
        model.addAttribute("totalItems", livestockPage.getTotalElements());
        model.addAttribute("pageSize", size);

        // Statistics
        model.addAttribute("lifecycleStats", lifecycleStats);
        model.addAttribute("categoryStats", categoryStats);
        model.addAttribute("statusStats", statusStats);

        // Filter values
        model.addAttribute("selectedStage", stage);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedGender", gender);
        model.addAttribute("searchQuery", search);

        return "livestock-overview";
    }
}