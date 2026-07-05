package rw.animalproduct.animal.production.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.services.BeneficiaryImpactService;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/beneficiary-impact")
public class BeneficiaryImpactController {

    private final BeneficiaryImpactService beneficiaryImpactService;

    public BeneficiaryImpactController(BeneficiaryImpactService beneficiaryImpactService) {
        this.beneficiaryImpactService = beneficiaryImpactService;
    }

    @GetMapping("/report/{beneficiaryId}")
    public ResponseEntity<?> getBeneficiaryReport(
            @PathVariable UUID beneficiaryId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {

        try {
            if (startDate == null) {
                Date now = new Date();
                startDate = new Date(now.getTime() - 365L * 24 * 60 * 60 * 1000);
            }
            if (endDate == null) {
                endDate = new Date();
            }

            if (startDate.after(endDate)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Start date cannot be after end date");
                return ResponseEntity.badRequest().body(error);
            }

            BeneficiaryImpactService.BeneficiaryImpactReport report =
                    beneficiaryImpactService.getBeneficiaryImpactReport(beneficiaryId, startDate, endDate);

            return ResponseEntity.ok(report);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to generate report: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/summary/{beneficiaryId}")
    public ResponseEntity<?> getBeneficiarySummary(@PathVariable UUID beneficiaryId) {
        try {
            Date endDate = new Date();
            Date startDate = new Date(endDate.getTime() - 365L * 24 * 60 * 60 * 1000);

            BeneficiaryImpactService.BeneficiaryImpactReport report =
                    beneficiaryImpactService.getBeneficiaryImpactReport(beneficiaryId, startDate, endDate);

            return ResponseEntity.ok(report);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to generate summary: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Beneficiary Impact Service is running");
    }
}