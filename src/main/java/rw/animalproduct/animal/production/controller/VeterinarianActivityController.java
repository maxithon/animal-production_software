package rw.animalproduct.animal.production.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.dto.VeterinarianActivityDtos.Report;
import rw.animalproduct.animal.production.services.VeterinarianActivityService;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Controller
@RequestMapping("/livestock/veterinarian-activity-report")
@RequiredArgsConstructor
public class VeterinarianActivityController {

    private final VeterinarianActivityService service;

    @GetMapping
    public String show(@RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       Model model) {
        LocalDate today = LocalDate.now();
        LocalDate fromDate = today.withDayOfMonth(1);
        LocalDate toDate   = today;

        try {
            if (from != null && !from.isBlank()) fromDate = LocalDate.parse(from);
            if (to   != null && !to.isBlank())   toDate   = LocalDate.parse(to);
            if (fromDate.isAfter(toDate)) {
                model.addAttribute("error", "'From' must be on or before 'To'.");
                fromDate = today.withDayOfMonth(1);
                toDate   = today;
            }
        } catch (DateTimeParseException e) {
            model.addAttribute("error", "Invalid date format.");
        }

        model.addAttribute("report",   service.generateReport(fromDate, toDate));
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate",   toDate);
        return "veterinarian-activity-report";
    }

    @GetMapping("/csv")
    public void exportCsv(@RequestParam String from,
                          @RequestParam String to,
                          HttpServletResponse response) throws IOException {
        Report r = service.generateReport(LocalDate.parse(from), LocalDate.parse(to));

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=vet-activity-" + from + "-to-" + to + ".csv");

        try (PrintWriter w = response.getWriter()) {
            w.println("Veterinarian,License,Clinic,Active,TreatmentRecords,"
                    + "DistinctAnimals,TotalCost,AvgCostPerAnimal,SickCases,"
                    + "Recovered,Recovering,Sick,Critical,Dead,RecoveryRate%,"
                    + "FollowUpsDue,FollowUpsOverdue");
            for (var row : r.getRows()) {
                w.printf("%s,%s,%s,%s,%d,%d,%s,%s,%d,%d,%d,%d,%d,%d,%.1f,%d,%d%n",
                        csv(row.getFullName()), csv(row.getLicenseNumber()),
                        csv(row.getClinicName()), row.isActive(),
                        row.getTreatmentRecords(), row.getDistinctAnimalsTreated(),
                        row.getTotalTreatmentCost(), row.getAvgCostPerAnimal(),
                        row.getSickCasesAttended(), row.getSickCasesRecovered(),
                        row.getSickCasesRecovering(), row.getSickCasesStillSick(),
                        row.getSickCasesCritical(), row.getSickCasesDead(),
                        row.getRecoveryRatePercent(),
                        row.getFollowUpsDue(), row.getFollowUpsOverdue());
            }
        }
    }

    private String csv(String s) {
        if (s == null) return "";
        return s.contains(",") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}