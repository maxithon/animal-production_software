package rw.animalproduct.animal.production.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockTreatment;
import rw.animalproduct.animal.production.entity.LivestockTreatmentPayment;
import rw.animalproduct.animal.production.entity.Veterinarian;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.MedicationRepository;
import rw.animalproduct.animal.production.repository.VeterinarianRepository;
import rw.animalproduct.animal.production.services.LivestockTreatmentService;
import rw.animalproduct.animal.production.services.TreatmentEmailService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livestock/treatments")
public class LivestockTreatmentController {

    private final LivestockTreatmentService treatmentService;
    private final LivestockRepository livestockRepository;
    private final MedicationRepository medicationRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final TreatmentEmailService emailService;

    public LivestockTreatmentController(
            LivestockTreatmentService treatmentService,
            LivestockRepository livestockRepository,
            MedicationRepository medicationRepository,
            VeterinarianRepository veterinarianRepository,
            TreatmentEmailService emailService) {
        this.treatmentService = treatmentService;
        this.livestockRepository = livestockRepository;
        this.medicationRepository = medicationRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.emailService = emailService;

        System.out.println("✅ LivestockTreatmentController initialized");
    }

    // ─── API: Live Search Animals ──────────────────────────────

    @GetMapping("/api/animals/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchAnimals(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int limit) {

        List<Livestock> animals;
        if (q != null && !q.trim().isEmpty()) {
            animals = livestockRepository.searchByTagNumber(q.trim(), PageRequest.of(0, limit));
        } else {
            animals = livestockRepository.findByIsDeletedFalse().stream().limit(limit).collect(Collectors.toList());
        }

        return ResponseEntity.ok(animals.stream().map(animal -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", animal.getId().toString());
            map.put("tagNumber", animal.getTagNumber());
            map.put("category", animal.getLivestockCategory() != null ?
                    animal.getLivestockCategory().getName() : "Uncategorized");
            map.put("gender", animal.getGender());
            map.put("healthStatus", animal.getHealthStatus());
            map.put("status", animal.getStatus());
            map.put("location", animal.getLocation() != null ?
                    animal.getLocation().getName() : "Unknown");
            return map;
        }).collect(Collectors.toList()));
    }

    // ─── API: Live Search Veterinarians ─────────────────────────

    @GetMapping("/api/veterinarians/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchVeterinarians(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int limit) {

        List<Veterinarian> vets;
        if (q != null && !q.trim().isEmpty()) {
            vets = veterinarianRepository.findAll().stream()
                    .filter(v -> v.getFullName().toLowerCase().contains(q.toLowerCase()) ||
                            (v.getLicenseNumber() != null && v.getLicenseNumber().toLowerCase().contains(q.toLowerCase())))
                    .limit(limit)
                    .collect(Collectors.toList());
        } else {
            vets = veterinarianRepository.findAll().stream().limit(limit).collect(Collectors.toList());
        }

        return ResponseEntity.ok(vets.stream().map(vet -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", vet.getId().toString());
            map.put("fullName", vet.getFullName());
            map.put("licenseNumber", vet.getLicenseNumber());
            map.put("clinicName", vet.getClinicName());
            map.put("phone", vet.getPhone());
            return map;
        }).collect(Collectors.toList()));
    }

    // ─── LIST ──────────────────────────────────────────────────

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) LivestockTreatment.TreatmentStatus status,
            @RequestParam(required = false) LivestockTreatment.TreatmentCategory type,
            @RequestParam(required = false) UUID livestockId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Boolean isPaid,
            @RequestParam(required = false) String search,
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "treatmentDate"));
        Page<LivestockTreatment> treatmentPage;

        if (search != null && !search.isEmpty()) {
            treatmentPage = treatmentService.search(search, pageable);
        } else {
            treatmentPage = treatmentService.getFiltered(status, type, livestockId, fromDate, toDate, isPaid, search, pageable);
        }

        model.addAttribute("treatments", treatmentPage.getContent());
        model.addAttribute("treatmentPage", treatmentPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", treatmentPage.getTotalPages());
        model.addAttribute("totalElements", treatmentPage.getTotalElements());

        model.addAttribute("filterStatus", status);
        model.addAttribute("filterType", type);
        model.addAttribute("filterLivestockId", livestockId);
        model.addAttribute("filterFromDate", fromDate);
        model.addAttribute("filterToDate", toDate);
        model.addAttribute("filterIsPaid", isPaid);
        model.addAttribute("filterSearch", search);

        model.addAttribute("livestockList", livestockRepository.findByIsDeletedFalse());
        model.addAttribute("medicationList", medicationRepository.findAll());
        model.addAttribute("veterinarianList", veterinarianRepository.findAll());

        model.addAttribute("totalTreatments", treatmentService.countTotal());
        model.addAttribute("ongoingCount", treatmentService.countByStatus(LivestockTreatment.TreatmentStatus.ONGOING));
        model.addAttribute("completedCount", treatmentService.countByStatus(LivestockTreatment.TreatmentStatus.COMPLETED));
        model.addAttribute("failedCount", treatmentService.countByStatus(LivestockTreatment.TreatmentStatus.FAILED));
        model.addAttribute("scheduledCount", treatmentService.countByStatus(LivestockTreatment.TreatmentStatus.SCHEDULED));

        model.addAttribute("statuses", LivestockTreatment.TreatmentStatus.values());
        model.addAttribute("types", LivestockTreatment.TreatmentCategory.values());

        LivestockTreatment treatment = new LivestockTreatment();
        treatment.setTreatmentDate(LocalDate.now());
        model.addAttribute("treatment", treatment);

        return "livestock-treatments-list";
    }

    // ─── NEW FORM ──────────────────────────────────────────────

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("treatment")) {
            LivestockTreatment treatment = new LivestockTreatment();
            treatment.setTreatmentDate(LocalDate.now());
            model.addAttribute("treatment", treatment);
        }
        model.addAttribute("medicationList", medicationRepository.findAll());
        model.addAttribute("veterinarianList", veterinarianRepository.findAll());
        return "livestock-treatments-form";
    }

    // ─── CREATE ─────────────────────────────────────────────────

    @PostMapping("/new")
    public String create(@ModelAttribute LivestockTreatment treatment, RedirectAttributes redirectAttributes) {
        try {
            System.out.println("📝 Creating treatment for animal: " + treatment.getLivestockIdValue());

            if (treatment.getTreatmentDate() != null && treatment.getTreatmentDate().isAfter(LocalDate.now())) {
                redirectAttributes.addFlashAttribute("error", "Treatment date cannot be in the future.");
                redirectAttributes.addFlashAttribute("treatment", treatment);
                return "redirect:/livestock/treatments/new";
            }

            if (treatment.getTreatmentCost() != null && treatment.getAmountPaid() != null &&
                    treatment.getAmountPaid().compareTo(treatment.getTreatmentCost()) > 0) {
                redirectAttributes.addFlashAttribute("error", "Amount paid cannot exceed treatment cost.");
                redirectAttributes.addFlashAttribute("treatment", treatment);
                return "redirect:/livestock/treatments/new";
            }

            LivestockTreatment saved = treatmentService.create(treatment);
            System.out.println("✅ Treatment saved with ID: " + saved.getId());

            redirectAttributes.addFlashAttribute("success", "Treatment created successfully! Email notification sent.");
        } catch (Exception e) {
            System.err.println("❌ Error creating treatment: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
            redirectAttributes.addFlashAttribute("treatment", treatment);
            return "redirect:/livestock/treatments/new";
        }
        return "redirect:/livestock/treatments";
    }

    // ─── EDIT FORM ─────────────────────────────────────────────

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable UUID id, Model model) {
        LivestockTreatment treatment = treatmentService.getById(id)
                .orElseThrow(() -> new RuntimeException("Treatment not found"));

        if (treatment.getLivestock() != null) {
            treatment.setLivestockIdValue(treatment.getLivestock().getId().toString());
        }
        if (treatment.getMedication() != null) {
            treatment.setMedicationIdValue(treatment.getMedication().getId().toString());
        }
        if (treatment.getVeterinarian() != null) {
            treatment.setVeterinarianIdValue(treatment.getVeterinarian().getId().toString());
        }

        model.addAttribute("treatment", treatment);
        model.addAttribute("medicationList", medicationRepository.findAll());
        model.addAttribute("veterinarianList", veterinarianRepository.findAll());
        return "livestock-treatments-form";
    }

    // ─── UPDATE ─────────────────────────────────────────────────

    @PostMapping("/update/{id}")
    public String update(@PathVariable UUID id, @ModelAttribute LivestockTreatment treatment, RedirectAttributes redirectAttributes) {
        try {
            if (treatment.getTreatmentDate() != null && treatment.getTreatmentDate().isAfter(LocalDate.now())) {
                redirectAttributes.addFlashAttribute("error", "Treatment date cannot be in the future.");
                return "redirect:/livestock/treatments/edit/" + id;
            }

            if (treatment.getTreatmentCost() != null && treatment.getAmountPaid() != null &&
                    treatment.getAmountPaid().compareTo(treatment.getTreatmentCost()) > 0) {
                redirectAttributes.addFlashAttribute("error", "Amount paid cannot exceed treatment cost.");
                return "redirect:/livestock/treatments/edit/" + id;
            }

            treatmentService.update(id, treatment);
            redirectAttributes.addFlashAttribute("success", "Treatment updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/treatments";
    }

    // ─── VIEW DETAILS ──────────────────────────────────────────

    @GetMapping("/details/{id}")
    public String details(@PathVariable UUID id, Model model) {
        LivestockTreatment treatment = treatmentService.getById(id)
                .orElseThrow(() -> new RuntimeException("Treatment not found"));

        List<LivestockTreatmentPayment> payments = treatmentService.getPayments(id);
        BigDecimal totalPaid = treatmentService.getTotalPaid(id);

        model.addAttribute("treatment", treatment);
        model.addAttribute("payments", payments);
        model.addAttribute("totalPaid", totalPaid);
        model.addAttribute("remainingBalance", treatment.getRemainingBalance());

        return "livestock-treatments-details";
    }

    // ─── RECORD PAYMENT ────────────────────────────────────────

    @PostMapping("/payment/{id}")
    public String recordPayment(@PathVariable UUID id,
                                @RequestParam BigDecimal amount,
                                @RequestParam(required = false) String paymentMethod,
                                @RequestParam(required = false) String paymentReference,
                                @RequestParam(required = false) String notes,
                                RedirectAttributes redirectAttributes) {
        try {
            treatmentService.recordPayment(id, amount, paymentMethod, paymentReference, notes);
            redirectAttributes.addFlashAttribute("success", "Payment of " + amount + " recorded successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/treatments/details/" + id;
    }

    // ─── DELETE ─────────────────────────────────────────────────

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            treatmentService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Treatment deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/treatments";
    }

    // ─── REPORT ─────────────────────────────────────────────────

    @GetMapping("/report")
    public String report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        // Set default dates
        if (fromDate == null) {
            fromDate = LocalDate.now().minusMonths(3);
        }
        if (toDate == null) {
            toDate = LocalDate.now();
        }

        // Get all treatments
        List<LivestockTreatment> allTreatments = treatmentService.getAll();

        // Apply status filter
        if (status != null && !status.isEmpty()) {
            List<LivestockTreatment> filtered = new java.util.ArrayList<>();
            for (LivestockTreatment t : allTreatments) {
                if (t.getTreatmentStatus() != null && t.getTreatmentStatus().name().equals(status)) {
                    filtered.add(t);
                }
            }
            allTreatments = filtered;
        }

        // Apply type filter
        if (type != null && !type.isEmpty()) {
            List<LivestockTreatment> filtered = new java.util.ArrayList<>();
            for (LivestockTreatment t : allTreatments) {
                if (t.getTreatmentType() != null && t.getTreatmentType().name().equals(type)) {
                    filtered.add(t);
                }
            }
            allTreatments = filtered;
        }

        // Apply payment status filter
        if (paymentStatus != null && !paymentStatus.isEmpty()) {
            List<LivestockTreatment> filtered = new java.util.ArrayList<>();
            for (LivestockTreatment t : allTreatments) {
                if (t.getPaymentStatus() != null && t.getPaymentStatus().name().equals(paymentStatus)) {
                    filtered.add(t);
                }
            }
            allTreatments = filtered;
        }

        // Apply search filter
        if (search != null && !search.isEmpty()) {
            String searchLower = search.toLowerCase();
            List<LivestockTreatment> filtered = new java.util.ArrayList<>();
            for (LivestockTreatment t : allTreatments) {
                String tagNumber = t.getLivestock() != null && t.getLivestock().getTagNumber() != null
                        ? t.getLivestock().getTagNumber().toLowerCase() : "";
                String medicationName = t.getMedication() != null && t.getMedication().getName() != null
                        ? t.getMedication().getName().toLowerCase() : "";
                String diagnosisName = t.getDiagnosisName() != null
                        ? t.getDiagnosisName().toLowerCase() : "";
                if (tagNumber.contains(searchLower) ||
                        medicationName.contains(searchLower) ||
                        diagnosisName.contains(searchLower)) {
                    filtered.add(t);
                }
            }
            allTreatments = filtered;
        }

        // Filter by date range
        List<LivestockTreatment> dateFiltered = new java.util.ArrayList<>();
        for (LivestockTreatment t : allTreatments) {
            if (t.getTreatmentDate() != null &&
                    !t.getTreatmentDate().isBefore(fromDate) &&
                    !t.getTreatmentDate().isAfter(toDate)) {
                dateFiltered.add(t);
            }
        }
        allTreatments = dateFiltered;

        // Pagination
        int total = allTreatments.size();
        int start = Math.min(page * size, total);
        int end = Math.min(start + size, total);
        List<LivestockTreatment> pagedTreatments = allTreatments.subList(start, end);

        // ─── Model Data ──────────────────────────────────────────────
        model.addAttribute("treatments", pagedTreatments);
        model.addAttribute("totalTreatments", total);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalPages", (int) Math.ceil((double) total / size));

        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("filterStatus", status);
        model.addAttribute("filterType", type);
        model.addAttribute("filterPaymentStatus", paymentStatus);
        model.addAttribute("filterSearch", search);

        // ─── Enums for dropdowns ──────────────────────────────────────
        model.addAttribute("statusOptions", LivestockTreatment.TreatmentStatus.values());
        model.addAttribute("typeOptions", LivestockTreatment.TreatmentCategory.values());
        model.addAttribute("paymentStatusOptions", LivestockTreatment.PaymentStatus.values());

        // ─── Report Data ──────────────────────────────────────────────
        model.addAttribute("financialSummary", treatmentService.getFinancialSummary(allTreatments));
        model.addAttribute("typeAnalysis", treatmentService.getTypeAnalysis(allTreatments));
        model.addAttribute("medicationAnalysis", treatmentService.getMedicationAnalysis(allTreatments));
        model.addAttribute("outcomeAnalysis", treatmentService.getOutcomeAnalysis(allTreatments));
        model.addAttribute("successRate", treatmentService.getSuccessRate(allTreatments));
        model.addAttribute("costTrends", treatmentService.getCostTrends(allTreatments));
        model.addAttribute("recurringDiseases", treatmentService.getRecurringDiseases(allTreatments));

        // ─── Top Medication (safe, pre-computed — avoids Map.get(0) crash in template) ──
        model.addAttribute("topMedicationName", treatmentService.getTopMedicationName(allTreatments));
        model.addAttribute("topExpensiveMedicationName", treatmentService.getTopExpensiveMedicationName(allTreatments));

        // ─── Type Stats ──────────────────────────────────────────────────
        model.addAttribute("curativeStats", treatmentService.getTypeStats(allTreatments, "CURATIVE"));
        model.addAttribute("preventiveStats", treatmentService.getTypeStats(allTreatments, "PREVENTIVE"));

        // ─── Alerts ────────────────────────────────────────────────────
        List<LivestockTreatment> overdueFollowUps = treatmentService.getFollowUpBetween(LocalDate.now());
        List<LivestockTreatment> activeWithdrawals = treatmentService.getActiveWithdrawals();
        model.addAttribute("overdueFollowUps", overdueFollowUps != null ? overdueFollowUps : new java.util.ArrayList<>());
        model.addAttribute("activeWithdrawals", activeWithdrawals != null ? activeWithdrawals : new java.util.ArrayList<>());

        // ─── Medication List for Filter ───────────────────────────────
        model.addAttribute("medicationList", medicationRepository.findAll());

        return "livestock-treatment-report";
    }

    // ─── TEST EMAIL ENDPOINT ────────────────────────────────────

    @GetMapping("/test-email/{id}")
    @ResponseBody
    public ResponseEntity<String> testEmail(@PathVariable UUID id) {
        try {
            System.out.println("📧 Testing email for treatment ID: " + id);

            LivestockTreatment treatment = treatmentService.getById(id)
                    .orElseThrow(() -> new RuntimeException("Treatment not found"));

            System.out.println("📧 Treatment found: " + treatment.getTreatmentReference());

            emailService.sendTreatmentCreatedNotification(treatment);

            return ResponseEntity.ok("✅ Email sent successfully for treatment: " + treatment.getTreatmentReference());
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("❌ Error: " + e.getMessage());
        }
    }
}