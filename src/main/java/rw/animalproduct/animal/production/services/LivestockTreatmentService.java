package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockTreatment;
import rw.animalproduct.animal.production.entity.LivestockTreatmentPayment;
import rw.animalproduct.animal.production.entity.Medication;
import rw.animalproduct.animal.production.entity.Veterinarian;
import rw.animalproduct.animal.production.repository.LivestockTreatmentRepository;
import rw.animalproduct.animal.production.repository.LivestockTreatmentPaymentRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LivestockTreatmentService {

    private static final Logger logger = LoggerFactory.getLogger(LivestockTreatmentService.class);

    private final LivestockTreatmentRepository treatmentRepository;
    private final LivestockTreatmentPaymentRepository paymentRepository;
    private final TreatmentEmailService emailService;
    private final LivestockService livestockService;
    private final MedicationService medicationService;
    private final VeterinarianService veterinarianService;
    private final AuditLogService auditLogService;

    public LivestockTreatmentService(
            LivestockTreatmentRepository treatmentRepository,
            LivestockTreatmentPaymentRepository paymentRepository,
            TreatmentEmailService emailService,
            LivestockService livestockService,
            MedicationService medicationService,
            VeterinarianService veterinarianService,
            AuditLogService auditLogService) {
        this.treatmentRepository = treatmentRepository;
        this.paymentRepository = paymentRepository;
        this.emailService = emailService;
        this.livestockService = livestockService;
        this.medicationService = medicationService;
        this.veterinarianService = veterinarianService;
        this.auditLogService = auditLogService;

        logger.info("✅ LivestockTreatmentService initialized");
    }

    // ─── CREATE ──────────────────────────────────────────────────────────
    @Transactional
    public LivestockTreatment create(LivestockTreatment treatment) {
        return createTreatment(treatment);
    }

    @Transactional
    public LivestockTreatment createTreatment(LivestockTreatment treatment) {
        logger.info("📝 Creating new treatment for animal: {}", treatment.getLivestockIdValue());

        if (treatment.getLivestockIdValue() == null || treatment.getLivestockIdValue().isBlank()) {
            throw new IllegalArgumentException("Animal is required. Please search and select an animal.");
        }
        Livestock animal = livestockService.findById(parseUuid(treatment.getLivestockIdValue(), "Animal"));
        treatment.setLivestock(animal);

        if (treatment.getMedicationIdValue() == null || treatment.getMedicationIdValue().isBlank()) {
            throw new IllegalArgumentException("Medication is required. Please select a medication.");
        }
        Medication medication = medicationService.getById(parseUuid(treatment.getMedicationIdValue(), "Medication"))
                .orElseThrow(() -> new RuntimeException("Medication not found: " + treatment.getMedicationIdValue()));
        treatment.setMedication(medication);

        if (treatment.getVeterinarianIdValue() != null && !treatment.getVeterinarianIdValue().isBlank()) {
            Veterinarian vet = veterinarianService.getById(parseUuid(treatment.getVeterinarianIdValue(), "Veterinarian"))
                    .orElseThrow(() -> new RuntimeException("Veterinarian not found: " + treatment.getVeterinarianIdValue()));
            treatment.setVeterinarian(vet);
        }

        if (treatment.getTreatmentReference() == null || treatment.getTreatmentReference().isBlank()) {
            treatment.setTreatmentReference(generateTreatmentReference());
        }
        if (treatment.getTreatmentCost() != null && treatment.getTreatmentCost().compareTo(BigDecimal.ZERO) > 0) {
            if (treatment.getAmountPaid() == null) treatment.setAmountPaid(BigDecimal.ZERO);
            treatment.updatePaymentStatus();
        }

        LivestockTreatment saved = treatmentRepository.save(treatment);
        logger.info("✅ Treatment saved with ID: {}", saved.getId());

        TreatmentEmailService.TreatmentEmailData emailData = buildEmailData(saved);

        registerAfterCommit(() -> {
            try {
                auditLogService.log("livestock_treatment", saved.getId(), "CREATE",
                        currentUsername(), null, buildSnapshot(saved), "Treatment created");
                emailService.sendTreatmentCreatedNotification(emailData);
            } catch (Exception e) {
                logger.error("❌ Post-save audit/email failed: {}", e.getMessage(), e);
            }
        });

        return saved;
    }

    // ─── UPDATE ──────────────────────────────────────────────────────────
    @Transactional
    public LivestockTreatment update(UUID id, LivestockTreatment treatment) {
        treatment.setId(id);
        return updateTreatment(treatment);
    }

    @Transactional
    public LivestockTreatment updateTreatment(LivestockTreatment treatment) {
        logger.info("📝 Updating treatment: {}", treatment.getId());

        LivestockTreatment existing = treatmentRepository.findById(treatment.getId())
                .orElseThrow(() -> new RuntimeException("Treatment not found"));

        String beforeSnapshot = buildSnapshot(existing);
        List<TreatmentEmailService.ChangeRow> changes = new ArrayList<>();

        if (treatment.getDiagnosisName() != null && !treatment.getDiagnosisName().equals(existing.getDiagnosisName())) {
            changes.add(new TreatmentEmailService.ChangeRow("Diagnosis",
                    existing.getDiagnosisName(), treatment.getDiagnosisName()));
            existing.setDiagnosisName(treatment.getDiagnosisName());
        }
        if (treatment.getTreatmentStatus() != null && treatment.getTreatmentStatus() != existing.getTreatmentStatus()) {
            changes.add(new TreatmentEmailService.ChangeRow("Status",
                    existing.getTreatmentStatus() != null ? existing.getTreatmentStatus().name() : "None",
                    treatment.getTreatmentStatus().name()));
            existing.setTreatmentStatus(treatment.getTreatmentStatus());
        }
        if (treatment.getOutcomeStatus() != null && treatment.getOutcomeStatus() != existing.getOutcomeStatus()) {
            changes.add(new TreatmentEmailService.ChangeRow("Outcome",
                    existing.getOutcomeStatus() != null ? existing.getOutcomeStatus().name() : "None",
                    treatment.getOutcomeStatus().name()));
            existing.setOutcomeStatus(treatment.getOutcomeStatus());
        }
        if (treatment.getTreatmentCost() != null && !treatment.getTreatmentCost().equals(existing.getTreatmentCost())) {
            changes.add(new TreatmentEmailService.ChangeRow("Cost",
                    String.valueOf(existing.getTreatmentCost()), String.valueOf(treatment.getTreatmentCost())));
            existing.setTreatmentCost(treatment.getTreatmentCost());
        }
        if (treatment.getSeverity() != null && treatment.getSeverity() != existing.getSeverity()) {
            changes.add(new TreatmentEmailService.ChangeRow("Severity",
                    existing.getSeverity() != null ? existing.getSeverity().name() : "None",
                    treatment.getSeverity().name()));
            existing.setSeverity(treatment.getSeverity());
        }
        if (treatment.getDescription() != null && !treatment.getDescription().equals(existing.getDescription())) {
            changes.add(new TreatmentEmailService.ChangeRow("Notes",
                    existing.getDescription(), treatment.getDescription()));
            existing.setDescription(treatment.getDescription());
        }

        if (treatment.getMedicationIdValue() != null && !treatment.getMedicationIdValue().isBlank()) {
            Medication med = medicationService.getById(parseUuid(treatment.getMedicationIdValue(), "Medication"))
                    .orElseThrow(() -> new RuntimeException("Medication not found"));
            if (existing.getMedication() == null || !med.getId().equals(existing.getMedication().getId())) {
                changes.add(new TreatmentEmailService.ChangeRow("Medication",
                        existing.getMedication() != null ? existing.getMedication().getName() : "None",
                        med.getName()));
                existing.setMedication(med);
            }
        }

        if (treatment.getVeterinarianIdValue() != null && !treatment.getVeterinarianIdValue().isBlank()) {
            Veterinarian vet = veterinarianService.getById(parseUuid(treatment.getVeterinarianIdValue(), "Veterinarian"))
                    .orElseThrow(() -> new RuntimeException("Veterinarian not found"));
            if (existing.getVeterinarian() == null || !vet.getId().equals(existing.getVeterinarian().getId())) {
                changes.add(new TreatmentEmailService.ChangeRow("Veterinarian",
                        existing.getVeterinarian() != null ? existing.getVeterinarian().getFullName() : "None",
                        vet.getFullName()));
                existing.setVeterinarian(vet);
            }
        }

        if (treatment.getLivestockIdValue() != null && !treatment.getLivestockIdValue().isBlank()) {
            Livestock animal = livestockService.findById(parseUuid(treatment.getLivestockIdValue(), "Animal"));
            if (existing.getLivestock() == null || !animal.getId().equals(existing.getLivestock().getId())) {
                changes.add(new TreatmentEmailService.ChangeRow("Animal",
                        existing.getLivestock() != null ? existing.getLivestock().getTagNumber() : "None",
                        animal.getTagNumber()));
                existing.setLivestock(animal);
            }
        }

        existing.setTreatmentDate(treatment.getTreatmentDate());
        existing.setTreatmentType(treatment.getTreatmentType());
        existing.setDosage(treatment.getDosage());
        existing.setDosageUnit(treatment.getDosageUnit());
        existing.setFrequency(treatment.getFrequency());
        existing.setTreatmentDuration(treatment.getTreatmentDuration());
        existing.setStartDate(treatment.getStartDate());
        existing.setEndDate(treatment.getEndDate());
        existing.setNextTreatmentDate(treatment.getNextTreatmentDate());
        existing.setSymptoms(treatment.getSymptoms());
        existing.setClinicalObservations(treatment.getClinicalObservations());
        existing.setFollowUpDate(treatment.getFollowUpDate());
        existing.setFollowUpInstructions(treatment.getFollowUpInstructions());
        existing.setOutcomeNotes(treatment.getOutcomeNotes());
        existing.setWithdrawalPeriodDays(treatment.getWithdrawalPeriodDays());
        existing.setWithdrawalEndDate(treatment.getWithdrawalEndDate());
        existing.setProductAffected(treatment.getProductAffected());
        existing.setBatchNumber(treatment.getBatchNumber());
        existing.setExpiryDate(treatment.getExpiryDate());
        existing.setManufacturer(treatment.getManufacturer());
        existing.updatePaymentStatus();

        boolean wasCompleted = existing.getTreatmentStatus() == LivestockTreatment.TreatmentStatus.COMPLETED;
        LivestockTreatment saved = treatmentRepository.save(existing);

        TreatmentEmailService.TreatmentEmailData emailData = buildEmailData(saved);
        boolean isNowCompleted = saved.getTreatmentStatus() == LivestockTreatment.TreatmentStatus.COMPLETED;

        registerAfterCommit(() -> {
            try {
                auditLogService.log("livestock_treatment", saved.getId(), "UPDATE",
                        currentUsername(), beforeSnapshot, buildSnapshot(saved), "Treatment updated");
                if (!changes.isEmpty()) {
                    emailService.sendTreatmentUpdatedNotification(emailData, changes);
                }
                if (isNowCompleted && !wasCompleted) {
                    emailService.sendTreatmentCreatedNotification(emailData);
                }
            } catch (Exception e) {
                logger.error("❌ Post-update audit/email failed: {}", e.getMessage(), e);
            }
        });

        return saved;
    }

    // ─── PAYMENT (enhanced: auto reference + safe method) ──────────────
    @Transactional
    public LivestockTreatmentPayment recordPayment(UUID treatmentId, BigDecimal amount,
                                                   String paymentMethod, String paymentReference, String notes) {
        logger.info("💵 Recording payment for treatment: {}", treatmentId);

        LivestockTreatment treatment = treatmentRepository.findById(treatmentId)
                .orElseThrow(() -> new RuntimeException("Treatment not found"));

        if (treatment.isFullyPaid()) throw new RuntimeException("Treatment is already fully paid");
        if (amount == null || amount.signum() <= 0)
            throw new RuntimeException("Payment amount must be greater than 0.");
        if (amount.compareTo(treatment.getRemainingBalance()) > 0) {
            throw new RuntimeException("Payment amount exceeds remaining balance");
        }

        LivestockTreatmentPayment payment = new LivestockTreatmentPayment();
        payment.setTreatment(treatment);
        payment.setAmount(amount);
        payment.setPaymentDate(LocalDate.now());

        // 🆕 Always store a concrete method
        payment.setPaymentMethod(
                (paymentMethod == null || paymentMethod.isBlank()) ? "CASH" : paymentMethod);

        // 🆕 Auto-generate reference if the user didn't type one
        payment.setPaymentReference(
                (paymentReference == null || paymentReference.isBlank())
                        ? generatePaymentReference()
                        : paymentReference.trim());

        payment.setNotes(notes);
        payment.setPaymentStatus("COMPLETED");

        LivestockTreatmentPayment saved = paymentRepository.save(payment);

        treatment.setAmountPaid(treatment.getAmountPaid().add(amount));
        treatment.updatePaymentStatus();

        // 🆕 Mirror the reason to the treatment-level payment_notes column
        if (notes != null && !notes.isBlank()) {
            try {
                treatment.setPaymentNotes(notes);
            } catch (Throwable ignored) {
                // If entity doesn't yet have setPaymentNotes, skip silently
            }
        }

        treatmentRepository.save(treatment);

        TreatmentEmailService.TreatmentEmailData emailData = buildEmailData(treatment);

        registerAfterCommit(() -> {
            try {
                auditLogService.log("livestock_treatment", treatment.getId(), "PAYMENT",
                        currentUsername(), null, null, "Payment of " + amount + " RWF recorded");
                emailService.sendTreatmentPaymentNotification(emailData, amount, paymentMethod, paymentReference);
            } catch (Exception e) {
                logger.error("❌ Post-payment audit/email failed: {}", e.getMessage(), e);
            }
        });

        return saved;
    }

    // 🆕 Update the notes/reason on an existing payment
    @Transactional
    public LivestockTreatmentPayment updatePaymentNotes(UUID paymentId, String notes) {
        logger.info("📝 Updating payment notes: {}", paymentId);
        LivestockTreatmentPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setNotes(notes);
        LivestockTreatmentPayment saved = paymentRepository.save(payment);

        LivestockTreatment treatment = payment.getTreatment();
        if (treatment != null) {
            try {
                treatment.setPaymentNotes(notes);
                treatmentRepository.save(treatment);
            } catch (Throwable ignored) {}
        }

        return saved;
    }

    // 🆕 Generate a human-readable reference
    private String generatePaymentReference() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String rand = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "PAY-" + date + "-" + rand;
    }

    // ─── DELETE PAYMENT ─────────────────────────────────────────────────
    @Transactional
    public void deletePayment(UUID paymentId) {
        logger.info("🗑️ Deleting payment: {}", paymentId);
        LivestockTreatmentPayment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        LivestockTreatment treatment = payment.getTreatment();
        BigDecimal amount = payment.getAmount();

        treatment.setAmountPaid(treatment.getAmountPaid().subtract(amount));
        treatment.updatePaymentStatus();
        treatmentRepository.save(treatment);
        paymentRepository.delete(payment);

        TreatmentEmailService.TreatmentEmailData emailData = buildEmailData(treatment);

        registerAfterCommit(() -> {
            try {
                auditLogService.log("livestock_treatment", treatment.getId(), "PAYMENT_DELETED",
                        currentUsername(), null, null, "Payment of " + amount + " RWF deleted");
                emailService.sendTreatmentPaymentDeletedNotification(emailData, amount);
            } catch (Exception e) {
                logger.error("❌ Post-payment-delete audit/email failed: {}", e.getMessage(), e);
            }
        });
    }

    // ─── DELETE TREATMENT ───────────────────────────────────────────────
    @Transactional
    public void delete(UUID id) { deleteTreatment(id); }

    @Transactional
    public void deleteTreatment(UUID id) {
        logger.info("🗑️ Deleting treatment: {}", id);
        LivestockTreatment treatment = treatmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Treatment not found"));

        String snapshot = buildSnapshot(treatment);
        TreatmentEmailService.TreatmentEmailData emailData = buildEmailData(treatment);

        treatmentRepository.delete(treatment);

        registerAfterCommit(() -> {
            try {
                auditLogService.log("livestock_treatment", id, "DELETE",
                        currentUsername(), snapshot, null, "Treatment permanently deleted");
                emailService.sendTreatmentDeletedNotification(emailData);
            } catch (Exception e) {
                logger.error("❌ Post-delete audit/email failed: {}", e.getMessage(), e);
            }
        });
    }

    // ─── TRANSACTION HOOK ────────────────────────────────────────────────
    private void registerAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    // ─── SAFE DTO BUILDER ────────────────────────────────────────────────
    private TreatmentEmailService.TreatmentEmailData buildEmailData(LivestockTreatment t) {
        String animalTag = null, animalCategory = null, animalGender = null, animalStatus = null, animalHealth = null;
        String beneficiaryName = null, beneficiaryPhone = null;

        if (t.getLivestock() != null) {
            Livestock a = t.getLivestock();
            animalTag = a.getTagNumber();
            animalCategory = a.getLivestockCategory() != null ? a.getLivestockCategory().getName() : null;
            animalGender = a.getGender();
            animalStatus = a.getStatus();
            try { animalHealth = a.healthStatusLabel(); } catch (Exception ignored) {}
            if (a.getBeneficiary() != null) {
                beneficiaryName = a.getBeneficiary().getFullName();
                beneficiaryPhone = a.getBeneficiary().getPhoneNumber();
            }
        }

        String vetName = null, vetPhone = null;
        if (t.getVeterinarian() != null) {
            Veterinarian v = t.getVeterinarian();
            vetName = v.getFullName();
            vetPhone = v.getPhone();
        }

        String med = t.getMedication() != null ? t.getMedication().getName() : null;

        return new TreatmentEmailService.TreatmentEmailData(
                t.getTreatmentReference(), animalTag, animalCategory, animalGender, animalStatus, animalHealth,
                beneficiaryName, beneficiaryPhone, vetName, vetPhone, t.getTreatmentDate(), t.getDiagnosisName(),
                t.getTreatmentType() != null ? t.getTreatmentType().name() : null,
                t.getTreatmentStatus() != null ? t.getTreatmentStatus().name() : null,
                med, t.getTreatmentCost(), t.getAmountPaid(), t.getRemainingBalance(),
                t.getOutcomeStatus() != null ? t.getOutcomeStatus().name() : null
        );
    }

    // ─── FINDERS ────────────────────────────────────────────────────────
    public LivestockTreatment findById(UUID id) {
        return treatmentRepository.findById(id).orElseThrow(() -> new RuntimeException("Treatment not found"));
    }
    public java.util.Optional<LivestockTreatment> getById(UUID id) { return treatmentRepository.findById(id); }

    public List<LivestockTreatment> getAll() {
        List<LivestockTreatment> ts = treatmentRepository.findAllActive();
        for (LivestockTreatment t : ts) if (t.getLivestock() != null) t.setLocationPath(getFullLocationPath(t.getLivestock()));
        return ts;
    }

    public Page<LivestockTreatment> getPage(Pageable pageable) { return treatmentRepository.findAllActive(pageable); }

    public Page<LivestockTreatment> findAll(Pageable pageable) {
        Page<LivestockTreatment> page = treatmentRepository.findAllActive(pageable);
        for (LivestockTreatment t : page.getContent())
            if (t.getLivestock() != null) t.setLocationPath(getFullLocationPath(t.getLivestock()));
        return page;
    }

    public List<LivestockTreatment> getByLivestock(UUID livestockId) {
        List<LivestockTreatment> ts = treatmentRepository.findByLivestockId(livestockId);
        for (LivestockTreatment t : ts) if (t.getLivestock() != null) t.setLocationPath(getFullLocationPath(t.getLivestock()));
        return ts;
    }

    public Page<LivestockTreatment> getFiltered(
            LivestockTreatment.TreatmentStatus status,
            LivestockTreatment.TreatmentCategory type,
            UUID livestockId, LocalDate fromDate, LocalDate toDate,
            Boolean isPaid, String search, Pageable pageable) {

        Page<LivestockTreatment> page = treatmentRepository.findFilteredPage(
                status, type, livestockId, fromDate, toDate, isPaid, search, pageable);

        for (LivestockTreatment t : page.getContent()) {
            if (t.getLivestock() != null) {
                t.setLocationPath(getFullLocationPath(t.getLivestock()));
            }
        }
        return page;
    }

    public Page<LivestockTreatment> findByStatus(String status, Pageable pageable) {
        LivestockTreatment.TreatmentStatus st = LivestockTreatment.TreatmentStatus.valueOf(status);
        List<LivestockTreatment> all = treatmentRepository.findByStatus(st);
        for (LivestockTreatment t : all) if (t.getLivestock() != null) t.setLocationPath(getFullLocationPath(t.getLivestock()));
        return paginateList(all, pageable);
    }

    public Page<LivestockTreatment> search(String searchTerm, Pageable pageable) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return treatmentRepository.findAllActive(pageable);
        List<LivestockTreatment> results = treatmentRepository.searchNative(searchTerm);
        for (LivestockTreatment t : results) if (t.getLivestock() != null) t.setLocationPath(getFullLocationPath(t.getLivestock()));
        return paginateList(results, pageable);
    }

    public Page<LivestockTreatment> findByAnimal(UUID livestockId, Pageable pageable) {
        List<LivestockTreatment> results = treatmentRepository.findByLivestockId(livestockId);
        for (LivestockTreatment t : results) if (t.getLivestock() != null) t.setLocationPath(getFullLocationPath(t.getLivestock()));
        return paginateList(results, pageable);
    }

    public List<LivestockTreatmentPayment> getPayments(UUID treatmentId) {
        return paymentRepository.findByTreatmentId(treatmentId);
    }
    public BigDecimal getTotalPaid(UUID treatmentId) {
        return paymentRepository.sumAmountByTreatmentId(treatmentId);
    }
    public List<LivestockTreatment> getFollowUpBetween(LocalDate endDate) { return treatmentRepository.findFollowUpBetween(endDate); }
    public List<LivestockTreatment> getActiveWithdrawals() { return treatmentRepository.findActiveWithdrawals(); }

    public String getFullLocationPath(Livestock livestock) {
        if (livestock == null || livestock.getLocation() == null) return "—";
        List<String> parts = new ArrayList<>();
        rw.animalproduct.animal.production.entity.Location current = livestock.getLocation();
        while (current != null) { parts.add(current.getName()); current = current.getParent(); }
        Collections.reverse(parts);
        return String.join(" > ", parts);
    }

    // ─── HELPERS ────────────────────────────────────────────────────────
    private UUID parseUuid(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " has an invalid identifier: " + raw);
        }
    }

    private Page<LivestockTreatment> paginateList(List<LivestockTreatment> list, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), list.size());
        if (start > list.size()) return new PageImpl<>(new ArrayList<>(), pageable, list.size());
        return new PageImpl<>(list.subList(start, end), pageable, list.size());
    }

    private String generateTreatmentReference() {
        String ts = String.valueOf(System.currentTimeMillis());
        String rand = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "TXT-" + ts.substring(ts.length() - 8) + "-" + rand;
    }

    private String buildSnapshot(LivestockTreatment t) {
        return "ref=" + t.getTreatmentReference()
                + "|animal=" + (t.getLivestock() != null ? t.getLivestock().getTagNumber() : "—")
                + "|med=" + (t.getMedication() != null ? t.getMedication().getName() : "—")
                + "|dx=" + t.getDiagnosisName()
                + "|cost=" + t.getTreatmentCost()
                + "|paid=" + t.getAmountPaid()
                + "|status=" + t.getTreatmentStatus()
                + "|date=" + t.getTreatmentDate();
    }

    private String currentUsername() {
        try {
            var a = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.isAuthenticated()) return a.getName();
        } catch (Exception ignored) {}
        return "system";
    }

    // ─── STATS ──────────────────────────────────────────────────────────
    public long countTotal() {
        return treatmentRepository.countAll();
    }

    public long countByStatus(LivestockTreatment.TreatmentStatus s) { return treatmentRepository.countByStatus(s); }
    public long countByStatus(String s) { return treatmentRepository.countByStatus(LivestockTreatment.TreatmentStatus.valueOf(s)); }
    public long countUnpaid() { return treatmentRepository.countUnpaid(); }
    public long countPartiallyPaid() { return treatmentRepository.countPartiallyPaid(); }
    public long countPaid() { return treatmentRepository.countPaid(); }
    public BigDecimal getTotalCost() { return treatmentRepository.getTotalCost(); }
    public BigDecimal getTotalPaid() { return treatmentRepository.getTotalPaid(); }
    public BigDecimal getTotalOutstanding() { return treatmentRepository.getTotalOutstanding(); }

    // ─── REPORT METHODS ────────────────────────────────────────────────
    public Map<String, Object> getFinancialSummary(List<LivestockTreatment> treatments) {
        Map<String, Object> s = new HashMap<>();
        BigDecimal cost = BigDecimal.ZERO, paid = BigDecimal.ZERO, bal = BigDecimal.ZERO;
        int pc = 0, uc = 0, ppc = 0;
        for (LivestockTreatment t : treatments) {
            if (t.getTreatmentCost() != null) cost = cost.add(t.getTreatmentCost());
            if (t.getAmountPaid() != null) paid = paid.add(t.getAmountPaid());
            bal = bal.add(t.getRemainingBalance());
            if (t.getPaymentStatus() != null) switch (t.getPaymentStatus()) {
                case PAID -> pc++;
                case UNPAID -> uc++;
                case PARTIALLY_PAID -> ppc++;
                default -> uc++;
            }
        }
        s.put("totalCost", cost); s.put("totalPaid", paid);
        s.put("totalBalance", bal); s.put("totalOutstanding", bal);
        s.put("paidCount", pc); s.put("unpaidCount", uc); s.put("partialCount", ppc);
        s.put("count", treatments.size());
        return s;
    }

    public Map<String, Object> getTypeAnalysis(List<LivestockTreatment> treatments) {
        Map<String, Long> countByType = new HashMap<>();
        Map<String, Double> successByType = new HashMap<>();
        for (LivestockTreatment t : treatments)
            if (t.getTreatmentType() != null)
                countByType.merge(t.getTreatmentType().name(), 1L, Long::sum);
        for (String type : countByType.keySet()) {
            long total = countByType.get(type);
            long succ = treatments.stream().filter(t -> t.getTreatmentType() != null
                    && t.getTreatmentType().name().equals(type)
                    && t.getOutcomeStatus() != null
                    && (t.getOutcomeStatus() == LivestockTreatment.OutcomeStatus.RECOVERED
                    || t.getOutcomeStatus() == LivestockTreatment.OutcomeStatus.IMPROVING)).count();
            successByType.put(type, total > 0 ? Math.round((succ * 100.0 / total) * 10) / 10.0 : 0.0);
        }
        Map<String, Object> a = new HashMap<>();
        a.put("countByType", countByType);
        a.put("successByType", successByType);
        return a;
    }

    public Map<String, Object> getMedicationAnalysis(List<LivestockTreatment> treatments) {
        Map<String, Long> count = new HashMap<>();
        Map<String, BigDecimal> costMap = new HashMap<>();
        for (LivestockTreatment t : treatments) if (t.getMedication() != null) {
            String n = t.getMedication().getName();
            count.merge(n, 1L, Long::sum);
            if (t.getTreatmentCost() != null) costMap.merge(n, t.getTreatmentCost(), BigDecimal::add);
        }
        Map<String, Long> most = count.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a,b)->a, LinkedHashMap::new));
        Map<String, BigDecimal> exp = costMap.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a,b)->a, LinkedHashMap::new));
        Map<String, Object> a = new HashMap<>();
        a.put("mostUsedMedications", most);
        a.put("mostExpensiveMedications", exp);
        return a;
    }

    public Map<String, Object> getOutcomeAnalysis(List<LivestockTreatment> treatments) {
        Map<String, Long> count = new HashMap<>();
        long total = treatments.size();
        for (LivestockTreatment t : treatments) if (t.getOutcomeStatus() != null)
            count.merge(t.getOutcomeStatus().name(), 1L, Long::sum);
        Map<String, Double> pct = new HashMap<>();
        for (var e : count.entrySet())
            pct.put(e.getKey(), total > 0 ? Math.round((e.getValue() * 100.0 / total) * 10) / 10.0 : 0.0);
        Map<String, Object> a = new HashMap<>();
        a.put("countByOutcome", count);
        a.put("percentageByOutcome", pct);
        return a;
    }

    public Map<String, Object> getSuccessRate(List<LivestockTreatment> treatments) {
        Map<String, Object> r = new HashMap<>();
        long total = treatments.size();
        if (total == 0) {
            r.put("successRate", 0.0); r.put("recovered", 0L); r.put("improving", 0L);
            r.put("failed", 0L); r.put("total", 0L); return r;
        }
        long rec = treatments.stream().filter(t -> t.getOutcomeStatus() == LivestockTreatment.OutcomeStatus.RECOVERED).count();
        long imp = treatments.stream().filter(t -> t.getOutcomeStatus() == LivestockTreatment.OutcomeStatus.IMPROVING).count();
        long fail = treatments.stream().filter(t -> t.getOutcomeStatus() == LivestockTreatment.OutcomeStatus.FAILED).count();
        r.put("successRate", Math.round(((double)(rec + imp) / total) * 1000) / 10.0);
        r.put("recovered", rec); r.put("improving", imp); r.put("failed", fail); r.put("total", total);
        return r;
    }

    public Map<String, BigDecimal> getCostTrends(List<LivestockTreatment> treatments) {
        Map<String, BigDecimal> trends = new HashMap<>();
        for (LivestockTreatment t : treatments)
            if (t.getTreatmentDate() != null && t.getTreatmentCost() != null) {
                String m = t.getTreatmentDate().getYear() + "-" + String.format("%02d", t.getTreatmentDate().getMonthValue());
                trends.merge(m, t.getTreatmentCost(), BigDecimal::add);
            }
        return trends;
    }

    public String getTopMedicationName(List<LivestockTreatment> treatments) {
        Map<String, Long> c = new HashMap<>();
        for (LivestockTreatment t : treatments) if (t.getMedication() != null && t.getMedication().getName() != null)
            c.merge(t.getMedication().getName(), 1L, Long::sum);
        return c.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("N/A");
    }

    public String getTopExpensiveMedicationName(List<LivestockTreatment> treatments) {
        Map<String, BigDecimal> c = new HashMap<>();
        for (LivestockTreatment t : treatments) if (t.getMedication() != null && t.getMedication().getName() != null && t.getTreatmentCost() != null)
            c.merge(t.getMedication().getName(), t.getTreatmentCost(), BigDecimal::add);
        return c.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("N/A");
    }

    public List<Map<String, Object>> getRecurringDiseases(List<LivestockTreatment> treatments) {
        Map<String, Long> count = new HashMap<>();
        Map<String, Long> succ = new HashMap<>();
        for (LivestockTreatment t : treatments) if (t.getDiagnosisName() != null && !t.getDiagnosisName().isEmpty()) {
            String d = t.getDiagnosisName();
            count.merge(d, 1L, Long::sum);
            if (t.getOutcomeStatus() == LivestockTreatment.OutcomeStatus.RECOVERED
                    || t.getOutcomeStatus() == LivestockTreatment.OutcomeStatus.IMPROVING)
                succ.merge(d, 1L, Long::sum);
        }
        return count.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(5)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("disease", e.getKey());
                    m.put("count", e.getValue());
                    long s = succ.getOrDefault(e.getKey(), 0L);
                    m.put("successRate", e.getValue() > 0 ? Math.round((s * 100.0 / e.getValue()) * 10) / 10.0 : 0.0);
                    return m;
                }).collect(Collectors.toList());
    }

    public Map<String, Object> getTypeStats(List<LivestockTreatment> treatments, String type) {
        Map<String, Object> stats = new HashMap<>();
        List<LivestockTreatment> filtered = treatments.stream()
                .filter(t -> t.getTreatmentType() != null && t.getTreatmentType().name().equals(type))
                .collect(Collectors.toList());
        stats.put("total", (long) filtered.size());
        stats.put("totalCost", filtered.stream().map(LivestockTreatment::getTreatmentCost).filter(c -> c != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        stats.put("totalPaid", filtered.stream().map(LivestockTreatment::getAmountPaid).filter(p -> p != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        double avg = 0; int cnt = 0;
        for (LivestockTreatment t : filtered) if (t.getTreatmentDuration() != null && !t.getTreatmentDuration().isEmpty()) {
            try { String n = t.getTreatmentDuration().replaceAll("[^0-9]", "");
                if (!n.isEmpty()) { avg += Integer.parseInt(n); cnt++; } } catch (NumberFormatException ignored) {}
        }
        stats.put("avgDuration", cnt > 0 ? Math.round(avg / cnt) : null);
        return stats;
    }
}