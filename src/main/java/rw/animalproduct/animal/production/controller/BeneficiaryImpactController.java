package rw.animalproduct.animal.production.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.dto.BeneficiaryImpactReportDto;
import rw.animalproduct.animal.production.services.BeneficiaryImpactService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for the Beneficiary Impact report.
 *
 *   GET /api/beneficiary-impact/summary/{beneficiaryId}
 *       → returns the program-level report (same DTO the HTML page uses)
 *
 *   GET /api/beneficiary-impact/report/{beneficiaryId}?startDate=&endDate=
 *       → returns the same report scoped to the given date range
 *         (beneficiaryId kept in the path for backward compatibility with
 *         existing clients; the current report is program-wide)
 *
 *   GET /api/beneficiary-impact/health
 *       → liveness check
 */
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
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "startDate cannot be after endDate");
                return ResponseEntity.badRequest().body(error);
            }

            BeneficiaryImpactReportDto report =
                    beneficiaryImpactService.buildReport(startDate, endDate);

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
            BeneficiaryImpactReportDto report =
                    beneficiaryImpactService.buildReport(null, null);
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