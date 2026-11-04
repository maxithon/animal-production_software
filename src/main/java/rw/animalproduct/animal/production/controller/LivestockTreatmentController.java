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
import rw.animalproduct.animal.production.entity.LivestockSick;
import rw.animalproduct.animal.production.entity.LivestockTreatment;
import rw.animalproduct.animal.production.entity.LivestockTreatmentPayment;
import rw.animalproduct.animal.production.entity.Veterinarian;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSickRepository;
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
    private final LivestockSickRepository livestockSickRepository;
    private final MedicationRepository medicationRepository;
    private final VeterinarianRepository veterinarianRepository;
    private final TreatmentEmailService emailService;

    public LivestockTreatmentController(
            LivestockTreatmentService treatmentService,
            LivestockRepository livestockRepository,
            LivestockSickRepository livestockSickRepository,
            MedicationRepository medicationRepository,
            VeterinarianRepository veterinarianRepository,
            TreatmentEmailService emailService) {
        this.treatmentService = treatmentService;
        this.livestockRepository = livestockRepository;
        this.livestockSickRepository = livestockSickRepository;
        this.medicationRepository = medicationRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.emailService = emailService;
    }

    // ─── API: Live Search Animals ──────────────────────────────────────
    @GetMapping("/api/animals/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchAnimals(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int limit) {
        List<Livestock> animals;
        if (q != null && !q.trim().isEmpty()) {
            // ✅ FIXED: searchByTagNumber returns Page<Livestock>; unwrap with getContent().
            animals = livestockRepository
                    .searchByTagNumber(q.trim(), PageRequest.of(0, limit))
                    .getContent();
        } else {
            animals = livestockRepository.findAll().stream().limit(limit).collect(Collectors.toList());
        }
        return ResponseEntity.ok(animals.stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId().toString());
            m.put("tagNumber", a.getTagNumber());
            m.put("category", a.getLivestockCategory() != null ? a.getLivestockCategory().getName() : "Uncategorized");
            m.put("gender", a.getGender());
            m.put("healthStatus", a.getHealthStatus());
            m.put("status", a.getStatus());
            m.put("location", a.getLocation() != null ? a.getLocation().getName() : "Unknown");
            return m;
        }).collect(Collectors.toList()));
    }

    // ─── API: Live Search Vets ─────────────────────────────────────────
    @GetMapping("/api/veterinarians/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchVeterinarians(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "10") int limit) {
        List<Veterinarian> vets;
        if (q != null && !q.trim().isEmpty()) {
            vets = veterinarianRepository.findAll().stream()
                    .filter(v -> v.getFullName().toLowerCase().contains(q.toLowerCase())
                            || (v.getLicenseNumber() != null && v.getLicenseNumber().toLowerCase().contains(q.toLowerCase())))
                    .limit(limit).collect(Collectors.toList());
        } else {
            vets = veterinarianRepository.findAll().stream().limit(limit).collect(Collectors.toList());
        }
        return ResponseEntity.ok(vets.stream().map(v -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", v.getId().toString());
            m.put("fullName", v.getFullName());
            m.put("licenseNumber", v.getLicenseNumber());
            m.put("clinicName", v.getClinicName());
            m.put("phone", v.getPhone());
            return m;
        }).collect(Collectors.toList()));
    }

    // ─── LIST (with last-recorded-first sorting) ──────────────────────
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

        // 🔥 NEW SORT: latest treatment date first, then latest created record first
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "treatmentDate")
                        .and(Sort.by(Sort.Direction.DESC, "createdAt")));

        Page<LivestockTreatment> treatmentPage = treatmentService.getFiltered(
                status, type, livestockId, fromDate, toDate, isPaid, search, pageable);

        model.addAttribute("treatments", treatmentPage.getContent());
        model.addAttribute("treatmentPage", treatmentPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("totalPages", treatmentPage.getTotalPages());
        model.addAttribute("totalElements", treatmentPage.getTotalElements());

        model.addAttribute("filterStatus", status);
        model.addAttribute("filterType", type);
        model.addAttribute("filterLivestockId", livestockId);
        model.addAttribute("filterFromDate", fromDate);
        model.addAttribute("filterToDate", toDate);
        model.addAttribute("filterIsPaid", isPaid);
        model.addAttribute("filterSearch", search);

        model.addAttribute("medicationList", medicationRepository.findAll());
        model.addAttribute("veterinarianList", veterinarianRepository.findAll());

        boolean hasAnimals = livestockRepository.count() > 0;
        model.addAttribute("hasAnimals", hasAnimals);
        model.addAttribute("livestockList", hasAnimals
                ? livestockRepository.findAll()
                : java.util.Collections.emptyList());

        model.addAttribute("totalTreatments", treatmentService.countTotal());
        model.addAttribute("ongoingCount", treatmentService.countByStatus(LivestockTreatment.TreatmentStatus.ONGOING));
        model.addAttribute("completedCount", treatmentService.countByStatus(LivestockTreatment.TreatmentStatus.COMPLETED));
        model.addAttribute("failedCount", treatmentService.countByStatus(LivestockTreatment.TreatmentStatus.FAILED));
        model.addAttribute("scheduledCount", treatmentService.countByStatus(LivestockTreatment.TreatmentStatus.SCHEDULED));

        model.addAttribute("statuses", LivestockTreatment.TreatmentStatus.values());
        model.addAttribute("types", LivestockTreatment.TreatmentCategory.values());

        if (!model.containsAttribute("treatment")) {
            LivestockTreatment treatment = new LivestockTreatment();
            treatment.setTreatmentDate(LocalDate.now());
            model.addAttribute("treatment", treatment);
        }

        return "livestock-treatments-list";
    }

    // ─── NEW FORM ──────────────────────────────────────────────────────
    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("treatment")) {
            LivestockTreatment treatment = new LivestockTreatment();
            treatment.setTreatmentDate(LocalDate.now());
            model.addAttribute("treatment", treatment);
        }
        prepareFormModel(model, null);
        return "livestock-treatments-form";
    }

    // ─── CREATE ────────────────────────────────────────────────────────
    @PostMapping("/new")
    public String create(@ModelAttribute LivestockTreatment treatment, RedirectAttributes ra) {
        try {
            if (treatment.getTreatmentDate() != null && treatment.getTreatmentDate().isAfter(LocalDate.now())) {
                ra.addFlashAttribute("error", "Treatment date cannot be in the future.");
                ra.addFlashAttribute("treatment", treatment);
                return "redirect:/livestock/treatments";
            }
            if (treatment.getTreatmentCost() != null && treatment.getAmountPaid() != null
                    && treatment.getAmountPaid().compareTo(treatment.getTreatmentCost()) > 0) {
                ra.addFlashAttribute("error", "Amount paid cannot exceed treatment cost.");
                ra.addFlashAttribute("treatment", treatment);
                return "redirect:/livestock/treatments";
            }
            LivestockTreatment saved = treatmentService.create(treatment);
            ra.addFlashAttribute("success",
                    "Treatment " + saved.getTreatmentReference() + " created. Email notification sent.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("treatment", treatment);
            return "redirect:/livestock/treatments";
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not save treatment: " + e.getMessage());
            ra.addFlashAttribute("treatment", treatment);
            return "redirect:/livestock/treatments";
        }
        return "redirect:/livestock/treatments";
    }

    // ─── EDIT FORM ─────────────────────────────────────────────────────
    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable UUID id, Model model, RedirectAttributes ra) {
        LivestockTreatment treatment = treatmentService.getById(id).orElse(null);
        if (treatment == null) {
            ra.addFlashAttribute("error", "Treatment not found.");
            return "redirect:/livestock/treatments";
        }
        if (treatment.getLivestock() != null)
            treatment.setLivestockIdValue(treatment.getLivestock().getId().toString());
        if (treatment.getMedication() != null)
            treatment.setMedicationIdValue(treatment.getMedication().getId().toString());
        if (treatment.getVeterinarian() != null)
            treatment.setVeterinarianIdValue(treatment.getVeterinarian().getId().toString());

        model.addAttribute("treatment", treatment);
        prepareFormModel(model, id);
        return "livestock-treatments-form";
    }

    // ─── UPDATE ────────────────────────────────────────────────────────
    @PostMapping("/update/{id}")
    public String update(@PathVariable UUID id, @ModelAttribute LivestockTreatment treatment, RedirectAttributes ra) {
        try {
            if (treatment.getTreatmentDate() != null && treatment.getTreatmentDate().isAfter(LocalDate.now())) {
                ra.addFlashAttribute("error", "Treatment date cannot be in the future.");
                return "redirect:/livestock/treatments/edit/" + id;
            }
            if (treatment.getTreatmentCost() != null && treatment.getAmountPaid() != null
                    && treatment.getAmountPaid().compareTo(treatment.getTreatmentCost()) > 0) {
                ra.addFlashAttribute("error", "Amount paid cannot exceed treatment cost.");
                return "redirect:/livestock/treatments/edit/" + id;
            }
            treatmentService.update(id, treatment);
            ra.addFlashAttribute("success", "Treatment updated. Email notification sent.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/livestock/treatments/edit/" + id;
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
            return "redirect:/livestock/treatments/edit/" + id;
        }
        return "redirect:/livestock/treatments";
    }

    // ─── DETAILS ──────────────────────────────────────────────────────
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
        model.addAttribute("canPayRemaining",
                treatment.getRemainingBalance() != null
                        && treatment.getRemainingBalance().signum() > 0);
        return "livestock-treatments-details";
    }

    // ─── PAYMENT ──────────────────────────────────────────────────────
    @PostMapping("/payment/{id}")
    public String recordPayment(@PathVariable UUID id,
                                @RequestParam BigDecimal amount,
                                @RequestParam(required = false) String paymentMethod,
                                @RequestParam(required = false) String paymentReference,
                                @RequestParam(required = false) String notes,
                                RedirectAttributes ra) {
        try {
            LivestockTreatmentPayment p = treatmentService.recordPayment(
                    id, amount, paymentMethod, paymentReference, notes);
            ra.addFlashAttribute("success",
                    "Payment of " + amount + " RWF recorded. Ref: " + p.getPaymentReference());
            LivestockTreatment t = treatmentService.getById(id).orElseThrow();
            if (t.getRemainingBalance() != null && t.getRemainingBalance().signum() > 0
                    && (notes == null || notes.isBlank())) {
                ra.addFlashAttribute("askPartialReason", true);
                ra.addFlashAttribute("lastPaymentId", p.getId());
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/treatments/details/" + id;
    }

    @PostMapping("/payment/note/{pid}")
    public String updatePaymentNote(@PathVariable UUID pid,
                                    @RequestParam String notes,
                                    RedirectAttributes ra) {
        try {
            LivestockTreatmentPayment p = treatmentService.updatePaymentNotes(pid, notes);
            ra.addFlashAttribute("success", "Payment note saved.");
            return "redirect:/livestock/treatments/details/" + p.getTreatment().getId();
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
            return "redirect:/livestock/treatments";
        }
    }

    // ─── DELETE ────────────────────────────────────────────────────────
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            treatmentService.delete(id);
            ra.addFlashAttribute("success", "Treatment deleted. Audit trail preserved. Email sent.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/treatments";
    }

    // ─── REPORT ───────────────────────────────────────────────────────
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

        if (fromDate == null) fromDate = LocalDate.now().minusMonths(3);
        if (toDate == null) toDate = LocalDate.now();

        List<LivestockTreatment> all = treatmentService.getAll();

        if (status != null && !status.isEmpty())
            all = all.stream().filter(t -> t.getTreatmentStatus() != null
                    && t.getTreatmentStatus().name().equals(status)).collect(Collectors.toList());
        if (type != null && !type.isEmpty())
            all = all.stream().filter(t -> t.getTreatmentType() != null
                    && t.getTreatmentType().name().equals(type)).collect(Collectors.toList());
        if (paymentStatus != null && !paymentStatus.isEmpty())
            all = all.stream().filter(t -> t.getPaymentStatus() != null
                    && t.getPaymentStatus().name().equals(paymentStatus)).collect(Collectors.toList());
        if (search != null && !search.isEmpty()) {
            String s = search.toLowerCase();
            all = all.stream().filter(t -> {
                String tag = t.getLivestock() != null && t.getLivestock().getTagNumber() != null
                        ? t.getLivestock().getTagNumber().toLowerCase() : "";
                String med = t.getMedication() != null && t.getMedication().getName() != null
                        ? t.getMedication().getName().toLowerCase() : "";
                String dx = t.getDiagnosisName() != null ? t.getDiagnosisName().toLowerCase() : "";
                return tag.contains(s) || med.contains(s) || dx.contains(s);
            }).collect(Collectors.toList());
        }

        LocalDate fFrom = fromDate, fTo = toDate;
        all = all.stream().filter(t -> t.getTreatmentDate() != null
                && !t.getTreatmentDate().isBefore(fFrom)
                && !t.getTreatmentDate().isAfter(fTo)).collect(Collectors.toList());

        int total = all.size();
        int start = Math.min(page * size, total);
        int end = Math.min(start + size, total);
        List<LivestockTreatment> paged = all.subList(start, end);

        model.addAttribute("treatments", paged);
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
        model.addAttribute("statusOptions", LivestockTreatment.TreatmentStatus.values());
        model.addAttribute("typeOptions", LivestockTreatment.TreatmentCategory.values());
        model.addAttribute("paymentStatusOptions", LivestockTreatment.PaymentStatus.values());

        model.addAttribute("financialSummary", treatmentService.getFinancialSummary(all));
        model.addAttribute("typeAnalysis", treatmentService.getTypeAnalysis(all));
        model.addAttribute("medicationAnalysis", treatmentService.getMedicationAnalysis(all));
        model.addAttribute("outcomeAnalysis", treatmentService.getOutcomeAnalysis(all));
        model.addAttribute("successRate", treatmentService.getSuccessRate(all));
        model.addAttribute("costTrends", treatmentService.getCostTrends(all));
        model.addAttribute("recurringDiseases", treatmentService.getRecurringDiseases(all));
        model.addAttribute("topMedicationName", treatmentService.getTopMedicationName(all));
        model.addAttribute("topExpensiveMedicationName", treatmentService.getTopExpensiveMedicationName(all));
        model.addAttribute("curativeStats", treatmentService.getTypeStats(all, "CURATIVE"));
        model.addAttribute("preventiveStats", treatmentService.getTypeStats(all, "PREVENTIVE"));

        List<LivestockTreatment> overdueFollow = treatmentService.getFollowUpBetween(LocalDate.now());
        List<LivestockTreatment> activeWithdrawals = treatmentService.getActiveWithdrawals();
        model.addAttribute("overdueFollowUps", overdueFollow != null ? overdueFollow : new java.util.ArrayList<>());
        model.addAttribute("activeWithdrawals", activeWithdrawals != null ? activeWithdrawals : new java.util.ArrayList<>());

        model.addAttribute("medicationList", medicationRepository.findAll());

        return "livestock-treatment-report";
    }

    // ─── Helpers ──────────────────────────────────────────────────────
    private void prepareFormModel(Model model, UUID editingId) {
        model.addAttribute("formAction", editingId != null
                ? "/livestock/treatments/update/" + editingId
                : "/livestock/treatments/new");

        model.addAttribute("medicationList", medicationRepository.findAll());
        model.addAttribute("veterinarianList", veterinarianRepository.findAll());

        List<Livestock> livestock = livestockRepository.findAll();
        model.addAttribute("livestockList", livestock);

        List<LivestockSick> sick = livestockSickRepository.findAll().stream()
                .filter(s -> s.getStatus() != null
                        && !"RECOVERED".equals(s.getStatus().name())
                        && !"DEAD".equals(s.getStatus().name()))
                .collect(Collectors.toList());
        model.addAttribute("sickAnimals", sick);
    }
}