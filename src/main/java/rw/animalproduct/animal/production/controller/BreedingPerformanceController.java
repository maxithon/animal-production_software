package rw.animalproduct.animal.production.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.services.BreedingPerformanceService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/breeding-performance")
public class BreedingPerformanceController {

    private final BreedingPerformanceService breedingPerformanceService;

    public BreedingPerformanceController(BreedingPerformanceService breedingPerformanceService) {
        this.breedingPerformanceService = breedingPerformanceService;
    }

    // ── 1. BREEDING PERFORMANCE FOR A SPECIFIC ANIMAL ──────────────────────

    /**
     * Get breeding performance for a specific animal
     * GET /api/breeding-performance/animal/{livestockId}
     */
    @GetMapping("/animal/{livestockId}")
    public ResponseEntity<?> getBreedingPerformance(@PathVariable UUID livestockId) {
        try {
            BreedingPerformanceService.BreedingPerformanceMetrics metrics =
                    breedingPerformanceService.getBreedingPerformance(livestockId);

            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to get breeding performance: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ── 2. BREEDING PERFORMANCE BY CATEGORY ──────────────────────────────────

    /**
     * Get breeding performance by category
     * GET /api/breeding-performance/by-category
     */
    @GetMapping("/by-category")
    public ResponseEntity<?> getBreedingPerformanceByCategory() {
        try {
            Map<String, BreedingPerformanceService.BreedingPerformanceMetrics> metrics =
                    breedingPerformanceService.getBreedingPerformanceByCategory();

            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to get breeding performance by category: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ── 3. BIRTHING REPORT ──────────────────────────────────────────────────

    /**
     * Get birthing report for a date range
     * GET /api/breeding-performance/birthing-report
     */
    @GetMapping("/birthing-report")
    public ResponseEntity<?> getBirthingReport(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {

        try {
            // If dates not provided, use default range (last 30 days)
            if (startDate == null) {
                startDate = LocalDate.now().minusDays(30);
            }
            if (endDate == null) {
                endDate = LocalDate.now();
            }

            // Validate date range
            if (startDate.isAfter(endDate)) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Start date cannot be after end date");
                return ResponseEntity.badRequest().body(error);
            }

            BreedingPerformanceService.BirthingReportDto report =
                    breedingPerformanceService.getBirthingReport(startDate, endDate);

            return ResponseEntity.ok(report);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to generate birthing report: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ── 4. CURRENT PREGNANCIES ──────────────────────────────────────────────

    /**
     * Get all currently pregnant animals
     * GET /api/breeding-performance/pregnancies
     */
    @GetMapping("/pregnancies")
    public ResponseEntity<?> getCurrentPregnancies() {
        try {
            List<BreedingPerformanceService.PregnancyTrackingDto> pregnancies =
                    breedingPerformanceService.getCurrentPregnancies();

            return ResponseEntity.ok(pregnancies);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to get current pregnancies: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ── 5. UPCOMING BIRTHS ──────────────────────────────────────────────────

    /**
     * Get upcoming births
     * GET /api/breeding-performance/upcoming-births?days=30
     */
    @GetMapping("/upcoming-births")
    public ResponseEntity<?> getUpcomingBirths(
            @RequestParam(defaultValue = "30") int days) {

        try {
            List<BreedingPerformanceService.PregnancyTrackingDto> upcoming =
                    breedingPerformanceService.getUpcomingBirths(days);

            return ResponseEntity.ok(upcoming);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to get upcoming births: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ── 6. CALVING INTERVAL ──────────────────────────────────────────────────

    /**
     * Get calving interval for a specific animal
     * GET /api/breeding-performance/calving-interval/{livestockId}
     */
    @GetMapping("/calving-interval/{livestockId}")
    public ResponseEntity<?> getCalvingInterval(@PathVariable UUID livestockId) {
        try {
            BreedingPerformanceService.CalvingIntervalDto interval =
                    breedingPerformanceService.getCalvingInterval(livestockId);

            return ResponseEntity.ok(interval);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to get calving interval: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ── 7. DASHBOARD SUMMARY ──────────────────────────────────────────────────

    /**
     * Get dashboard summary with all metrics
     * GET /api/breeding-performance/dashboard-summary
     */
    @GetMapping("/dashboard-summary")
    public ResponseEntity<?> getDashboardSummary() {
        try {
            Map<String, Object> summary = new HashMap<>();

            // Get pregnancies
            List<BreedingPerformanceService.PregnancyTrackingDto> pregnancies =
                    breedingPerformanceService.getCurrentPregnancies();
            summary.put("currentPregnancies", pregnancies.size());

            // Get upcoming births (next 30 days)
            List<BreedingPerformanceService.PregnancyTrackingDto> upcoming =
                    breedingPerformanceService.getUpcomingBirths(30);
            summary.put("upcomingBirths", upcoming.size());

            // Get performance by category
            Map<String, BreedingPerformanceService.BreedingPerformanceMetrics> byCategory =
                    breedingPerformanceService.getBreedingPerformanceByCategory();
            summary.put("performanceByCategory", byCategory);

            // Calculate overall stats
            double overallSuccessRate = byCategory.values().stream()
                    .mapToDouble(BreedingPerformanceService.BreedingPerformanceMetrics::getSuccessRate)
                    .average()
                    .orElse(0.0);
            summary.put("overallSuccessRate", Math.round(overallSuccessRate * 100.0) / 100.0);

            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to get dashboard summary: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // ── 8. HEALTH CHECK ──────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Breeding Performance Service is running");
    }
}