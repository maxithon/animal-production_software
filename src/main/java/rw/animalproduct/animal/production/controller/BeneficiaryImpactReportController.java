
package rw.animalproduct.animal.production.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import rw.animalproduct.animal.production.dto.BeneficiaryImpactReportDto;
import rw.animalproduct.animal.production.services.BeneficiaryImpactService;

import java.time.LocalDate;

/**
 * Serves the Beneficiary Impact report at /livestock/beneficiary-impact-report.
 *
 * Both date parameters are optional. Omit them to see all-time figures.
 */
@Controller
public class BeneficiaryImpactReportController {

    private final BeneficiaryImpactService beneficiaryImpactService;

    public BeneficiaryImpactReportController(BeneficiaryImpactService beneficiaryImpactService) {
        this.beneficiaryImpactService = beneficiaryImpactService;
    }

    @GetMapping("/livestock/beneficiary-impact-report")
    public String beneficiaryImpactReport(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,

            Model model) {

        if (from != null && to != null && from.isAfter(to)) {
            LocalDate tmp = from; from = to; to = tmp;
        }

        BeneficiaryImpactReportDto report = beneficiaryImpactService.buildReport(from, to);

        model.addAttribute("report", report);
        model.addAttribute("fromDate", from);
        model.addAttribute("toDate", to);
        model.addAttribute("generatedAt", LocalDate.now());

        return "beneficiary-impact-report";
    }
}