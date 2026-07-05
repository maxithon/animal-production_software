package rw.animalproduct.animal.production.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import rw.animalproduct.animal.production.dto.HerdMovementReportDto;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.services.HerdMovementService;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livestock/herd-movement-report")
public class HerdMovementController {

    private final HerdMovementService herdMovementService;
    private final LivestockCategoryRepository livestockCategoryRepository;

    public HerdMovementController(HerdMovementService herdMovementService,
                                  LivestockCategoryRepository livestockCategoryRepository) {
        this.herdMovementService = herdMovementService;
        this.livestockCategoryRepository = livestockCategoryRepository;
    }

    @GetMapping
    public String showReport(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) UUID categoryId,
            Model model) {

        LocalDate today = LocalDate.now();
        LocalDate fromDate = (from != null && !from.isEmpty())
                ? LocalDate.parse(from) : today.withDayOfMonth(1);
        LocalDate toDate = (to != null && !to.isEmpty())
                ? LocalDate.parse(to) : today;

        HerdMovementReportDto report = herdMovementService.generateReport(fromDate, toDate, categoryId);

        List<LivestockCategory> allCategories = livestockCategoryRepository.findAll().stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsDeleted()))
                .sorted(Comparator.comparing(LivestockCategory::getName))
                .collect(Collectors.toList());

        String selectedCategoryName = "All Categories";
        if (categoryId != null) {
            selectedCategoryName = allCategories.stream()
                    .filter(c -> c.getId().equals(categoryId))
                    .map(LivestockCategory::getName)
                    .findFirst()
                    .orElse("Unknown Category");
        }

        model.addAttribute("report", report);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("allCategories", allCategories);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedCategoryName", selectedCategoryName);

        return "herd-movement-report";
    }
}