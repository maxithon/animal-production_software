package rw.animalproduct.animal.production.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import rw.animalproduct.animal.production.services.VeterinarianActivityService;

import java.time.LocalDate;

@Controller
@RequestMapping("/livestock/veterinarian-activity-report")
public class VeterinarianActivityController {

    private final VeterinarianActivityService veterinarianActivityService;

    public VeterinarianActivityController(VeterinarianActivityService veterinarianActivityService) {
        this.veterinarianActivityService = veterinarianActivityService;
    }

    @GetMapping
    public String show(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Model model) {

        LocalDate today = LocalDate.now();
        LocalDate fromDate = (from != null && !from.isEmpty()) ? LocalDate.parse(from) : today.withDayOfMonth(1);
        LocalDate toDate = (to != null && !to.isEmpty()) ? LocalDate.parse(to) : today;

        model.addAttribute("report", veterinarianActivityService.generateReport(fromDate, toDate));
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        return "veterinarian-activity-report";
    }
}
