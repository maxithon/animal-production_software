package rw.animalproduct.animal.production.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import rw.animalproduct.animal.production.dto.BusinessOverviewResponse;
import rw.animalproduct.animal.production.services.BusinessOverviewService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class BusinessOverviewController {

    private static final int MAX_MONTHS = 24;
    private static final int MIN_MONTHS = 3;
    private static final String[] VALID_TABS = { "overview", "herd", "financial", "animals" };

    private final BusinessOverviewService service;

    public BusinessOverviewController(BusinessOverviewService service) {
        this.service = service;
    }

    /** Main tabbed report page. */
    @GetMapping("/livestock/overview-report")
    public String show(@RequestParam(defaultValue = "12") int months,
                       @RequestParam(defaultValue = "overview") String tab,
                       Authentication auth,
                       Model model) {

        if (months < MIN_MONTHS) months = MIN_MONTHS;
        if (months > MAX_MONTHS) months = MAX_MONTHS;
        tab = normalizeTab(tab);

        BusinessOverviewResponse report = service.build(months);

        model.addAttribute("report", report);
        model.addAttribute("months", months);
        model.addAttribute("activeTab", tab);
        model.addAttribute("generatedBy", auth != null ? auth.getName() : "System");
        model.addAttribute("generatedAtFormatted",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")));

        return "livestock-overview-report";
    }

    /** Legacy URL — redirects old summary-report bookmarks into the merged report. */
    @GetMapping("/livestock/summary-report")
    public String summaryRedirect() {
        return "redirect:/livestock/overview-report?tab=animals";
    }

    private String normalizeTab(String tab) {
        for (String v : VALID_TABS) if (v.equals(tab)) return tab;
        return "overview";
    }
}