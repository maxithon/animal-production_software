package rw.animalproduct.animal.production.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.services.MedicationUsageService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/medication-usage")
public class MedicationUsageController {

    private final MedicationUsageService medicationUsageService;

    public MedicationUsageController(MedicationUsageService medicationUsageService) {
        this.medicationUsageService = medicationUsageService;
    }

    @GetMapping("/report")
    public ResponseEntity<?> getMedicationUsageReport(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {

        try {
            if (startDate == null) {
                startDate = LocalDate.now().minusDays(30);
            }
            if (endDate == null) {
                endDate = LocalDate.now();
            }

            if (startDate.isAfter(endDate)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Start date cannot be after end date");
                return ResponseEntity.badRequest().body(error);
            }

            MedicationUsageService.MedicationUsageReport report =
                    medicationUsageService.getMedicationUsageReport(startDate, endDate);

            return ResponseEntity.ok(report);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to generate report: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/{medicationId}")
    public ResponseEntity<?> getMedicationUsageById(
            @PathVariable UUID medicationId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {

        try {
            if (startDate == null) {
                startDate = LocalDate.now().minusDays(30);
            }
            if (endDate == null) {
                endDate = LocalDate.now();
            }

            if (startDate.isAfter(endDate)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Start date cannot be after end date");
                return ResponseEntity.badRequest().body(error);
            }

            MedicationUsageService.MedicationUsage usage =
                    medicationUsageService.getMedicationUsageById(medicationId, startDate, endDate);

            return ResponseEntity.ok(usage);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to get medication usage: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/by-type")
    public ResponseEntity<?> getMedicationUsageByType(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {

        try {
            if (startDate == null) {
                startDate = LocalDate.now().minusDays(30);
            }
            if (endDate == null) {
                endDate = LocalDate.now();
            }

            if (startDate.isAfter(endDate)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Start date cannot be after end date");
                return ResponseEntity.badRequest().body(error);
            }

            Map<String, MedicationUsageService.MedicationUsage> usageByType =
                    medicationUsageService.getMedicationUsageByType(startDate, endDate);

            return ResponseEntity.ok(usageByType);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to get medication usage by type: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Medication Usage Service is running");
    }
}