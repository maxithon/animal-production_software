package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockTreatment;
import rw.animalproduct.animal.production.entity.LivestockTreatmentPayment;
import rw.animalproduct.animal.production.entity.Medication;
import rw.animalproduct.animal.production.entity.Veterinarian;
import rw.animalproduct.animal.production.repository.LivestockTreatmentRepository;
import rw.animalproduct.animal.production.repository.LivestockTreatmentPaymentRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

    public LivestockTreatmentService(
            LivestockTreatmentRepository treatmentRepository,
            LivestockTreatmentPaymentRepository paymentRepository,
            TreatmentEmailService emailService,
            LivestockService livestockService,
            MedicationService medicationService,
            VeterinarianService veterinarianService) {
        this.treatmentRepository = treatmentRepository;
        this.paymentRepository = paymentRepository;
        this.emailService = emailService;
        this.livestockService = livestockService;
        this.medicationService = medicationService;
        this.veterinarianService = veterinarianService;

        logger.info("✅ LivestockTreatmentService initialized with emailService: {}",
                emailService != null ? "Available" : "NULL");
    }

    // ─── CREATE ──────────────────────────────────────────────────────────────

    @Transactional
    public LivestockTreatment create(LivestockTreatment treatment) {
        return createTreatment(treatment);
    }

    @Transactional
    public LivestockTreatment createTreatment(LivestockTreatment treatment) {
        logger.info("📝 Creating new treatment for animal: {}", treatment.getLivestockIdValue());

        if (treatment.getLivestockIdValue() != null) {
            Livestock animal = livestockService.findById(UUID.fromString(treatment.getLivestockIdValue()));
            treatment.setLivestock(animal);
        }

        if (treatment.getMedicationIdValue() != null) {
            Medication medication = medicationService.getById(UUID.fromString(treatment.getMedicationIdValue()))
                    .orElseThrow(() -> new RuntimeException("Medication not found with id: " + treatment.getMedicationIdValue()));
            treatment.setMedication(medication);
        }

        if (treatment.getVeterinarianIdValue() != null) {
            Veterinarian vet = veterinarianService.getById(UUID.fromString(treatment.getVeterinarianIdValue()))
                    .orElseThrow(() -> new RuntimeException("Veterinarian not found with id: " + treatment.getVeterinarianIdValue()));
            treatment.setVeterinarian(vet);
        }

        if (treatment.getTreatmentReference() == null) {
            treatment.setTreatmentReference(generateTreatmentReference());
        }

        if (treatment.getTreatmentCost() != null && treatment.getTreatmentCost().compareTo(BigDecimal.ZERO) > 0) {
            if (treatment.getAmountPaid() == null) {
                treatment.setAmountPaid(BigDecimal.ZERO);
            }
            treatment.updatePaymentStatus();
        }

        LivestockTreatment saved = treatmentRepository.save(treatment);
        logger.info("✅ Treatment saved with ID: {}", saved.getId());

        try {
            emailService.sendTreatmentCreatedNotification(saved);
            logger.info("📧 Email notification queued for: {}", saved.getTreatmentReference());
        } catch (Exception e) {
            logger.error("❌ Failed to send treatment created notification: {}", e.getMessage(), e);
        }

        return saved;
    }

    // ─── UPDATE ──────────────────────────────────────────────────────────────

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

        List<TreatmentEmailService.ChangeRow> changes = new ArrayList<>();

        if (treatment.getDiagnosisName() != null && !treatment.getDiagnosisName().equals(existing.getDiagnosisName())) {
            changes.add(new TreatmentEmailService.ChangeRow("Diagnosis",
                    existing.getDiagnosisName(), treatment.getDiagnosisName()));
            existing.setDiagnosisName(treatment.getDiagnosisName());
        }

        if (treatment.getTreatmentStatus() != null && treatment.getTreatmentStatus() != existing.getTreatmentStatus()) {
            changes.add(new TreatmentEmailService.ChangeRow("Status",
                    existing.getTreatmentStatus().name(), treatment.getTreatmentStatus().name()));
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
                    existing.getTreatmentCost().toString(), treatment.getTreatmentCost().toString()));
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

        if (treatment.getMedicationIdValue() != null) {
            Medication med = medicationService.getById(UUID.fromString(treatment.getMedicationIdValue()))
                    .orElseThrow(() -> new RuntimeException("Medication not found with id: " + treatment.getMedicationIdValue()));
            if (existing.getMedication() == null || !med.getId().equals(existing.getMedication().getId())) {
                changes.add(new TreatmentEmailService.ChangeRow("Medication",
                        existing.getMedication() != null ? existing.getMedication().getName() : "None",
                        med.getName()));
                existing.setMedication(med);
            }
        }

        if (treatment.getVeterinarianIdValue() != null) {
            Veterinarian vet = veterinarianService.getById(UUID.fromString(treatment.getVeterinarianIdValue()))
                    .orElseThrow(() -> new RuntimeException("Veterinarian not found with id: " + treatment.getVeterinarianIdValue()));
            if (existing.getVeterinarian() == null || !vet.getId().equals(existing.getVeterinarian().getId())) {
                changes.add(new TreatmentEmailService.ChangeRow("Veterinarian",
                        existing.getVeterinarian() != null ? existing.getVeterinarian().getFullName() : "None",
                        vet.getFullName()));
                existing.setVeterinarian(vet);
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

        if (!changes.isEmpty()) {
            try {
                emailService.sendTreatmentUpdatedNotification(saved, changes);
            } catch (Exception e) {
                logger.error("Failed to send treatment updated notification: {}", e.getMessage());
            }
        }

        if (treatment.getTreatmentStatus() == LivestockTreatment.TreatmentStatus.COMPLETED && !wasCompleted) {
            try {
                emailService.sendTreatmentCompletedNotification(saved);
            } catch (Exception e) {
                logger.error("Failed to send treatment completed notification: {}", e.getMessage());
            }
        }

        return saved;
    }

    // ─── PAYMENT ────────────────────────────────────────────────────────────

    @Transactional
    public LivestockTreatmentPayment recordPayment(UUID treatmentId, BigDecimal amount,
                                                   String paymentMethod, String paymentReference, String notes) {
        logger.info("Recording payment for treatment: {}", treatmentId);

        LivestockTreatment treatment = treatmentRepository.findById(treatmentId)
                .orElseThrow(() -> new RuntimeException("Treatment not found"));

        if (treatment.isFullyPaid()) {
            throw new RuntimeException("Treatment is already fully paid");
        }

        if (amount.compareTo(treatment.getRemainingBalance()) > 0) {
            throw new RuntimeException("Payment amount exceeds remaining balance");
        }

        LivestockTreatmentPayment payment = new LivestockTreatmentPayment();
        payment.setTreatment(treatment);
        payment.setAmount(amount);
        payment.setPaymentDate(LocalDate.now());
        payment.setPaymentMethod(paymentMethod);
        payment.setPaymentReference(paymentReference);
        payment.setNotes(notes);
        payment.setPaymentStatus("COMPLETED");

        LivestockTreatmentPayment saved = paymentRepository.save(payment);

        BigDecimal newAmountPaid = treatment.getAmountPaid().add(amount);
        treatment.setAmountPaid(newAmountPaid);
        treatment.updatePaymentStatus();
        treatmentRepository.save(treatment);

        try {
            emailService.sendTreatmentPaymentNotification(treatment, amount, paymentMethod, paymentReference);
        } catch (Exception e) {
            logger.error("Failed to send treatment payment notification: {}", e.getMessage());
        }

        return saved;
    }

    // ─── DELETE ─────────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        deleteTreatment(id);
    }

    @Transactional
    public void deleteTreatment(UUID id) {
        logger.info("Deleting treatment: {}", id);
        LivestockTreatment treatment = treatmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Treatment not found"));

        treatment.setIsDeleted(true);
        treatmentRepository.save(treatment);
    }

    // ─── FIND METHODS ──────────────────────────────────────────────────────

    public LivestockTreatment findById(UUID id) {
        return treatmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Treatment not found"));
    }

    public java.util.Optional<LivestockTreatment> getById(UUID id) {
        return treatmentRepository.findById(id);
    }

    public List<LivestockTreatment> getAll() {
        List<LivestockTreatment> treatments = treatmentRepository.findAllActive();
        for (LivestockTreatment t : treatments) {
            if (t.getLivestock() != null) {
                t.setLocationPath(getFullLocationPath(t.getLivestock()));
            }
        }
        return treatments;
    }

    public Page<LivestockTreatment> getPage(Pageable pageable) {
        return treatmentRepository.findAllActive(pageable);
    }

    public Page<LivestockTreatment> findAll(Pageable pageable) {
        Page<LivestockTreatment> page = treatmentRepository.findAllActive(pageable);
        for (LivestockTreatment t : page.getContent()) {
            if (t.getLivestock() != null) {
                t.setLocationPath(getFullLocationPath(t.getLivestock()));
            }
        }
        return page;
    }

    public List<LivestockTreatment> getByLivestock(UUID livestockId) {
        List<LivestockTreatment> treatments = treatmentRepository.findByLivestockId(livestockId);
        for (LivestockTreatment t : treatments) {
            if (t.getLivestock() != null) {
                t.setLocationPath(getFullLocationPath(t.getLivestock()));
            }
        }
        return treatments;
    }

    public Page<LivestockTreatment> getFiltered(
            LivestockTreatment.TreatmentStatus status,
            LivestockTreatment.TreatmentCategory type,
            UUID livestockId,
            LocalDate fromDate,
            LocalDate toDate,
            Boolean isPaid,
            String search,
            Pageable pageable) {

        List<LivestockTreatment> allTreatments = treatmentRepository.findAllActive();

        List<LivestockTreatment> filtered = allTreatments.stream()
                .filter(t -> {
                    if (status != null && t.getTreatmentStatus() != status) return false;
                    if (type != null && t.getTreatmentType() != type) return false;
                    if (livestockId != null && (t.getLivestock() == null || !t.getLivestock().getId().equals(livestockId))) return false;
                    if (fromDate != null && (t.getTreatmentDate() == null || t.getTreatmentDate().isBefore(fromDate))) return false;
                    if (toDate != null && (t.getTreatmentDate() == null || t.getTreatmentDate().isAfter(toDate))) return false;
                    if (isPaid != null && t.getIsPaid() != isPaid) return false;
                    if (search != null && !search.isEmpty()) {
                        String searchLower = search.toLowerCase();
                        String tagNumber = t.getLivestock() != null && t.getLivestock().getTagNumber() != null
                                ? t.getLivestock().getTagNumber().toLowerCase() : "";
                        String medicationName = t.getMedication() != null && t.getMedication().getName() != null
                                ? t.getMedication().getName().toLowerCase() : "";
                        String diagnosisName = t.getDiagnosisName() != null
                                ? t.getDiagnosisName().toLowerCase() : "";
                        if (!tagNumber.contains(searchLower) && !medicationName.contains(searchLower) && !diagnosisName.contains(searchLower)) return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        for (LivestockTreatment t : filtered) {
            if (t.getLivestock() != null) {
                t.setLocationPath(getFullLocationPath(t.getLivestock()));
            }
        }

        return paginateList(filtered, pageable);
    }

    public Page<LivestockTreatment> findByStatus(String status, Pageable pageable) {
        LivestockTreatment.TreatmentStatus treatmentStatus = LivestockTreatment.TreatmentStatus.valueOf(status);
        List<LivestockTreatment> allResults = treatmentRepository.findByStatus(treatmentStatus);
        for (LivestockTreatment t : allResults) {
            if (t.getLivestock() != null) {
                t.setLocationPath(getFullLocationPath(t.getLivestock()));
            }
        }
        return paginateList(allResults, pageable);
    }

    public Page<LivestockTreatment> search(String searchTerm, Pageable pageable) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return treatmentRepository.findAllActive(pageable);
        }
        List<LivestockTreatment> results = treatmentRepository.searchNative(searchTerm);
        for (LivestockTreatment t : results) {
            if (t.getLivestock() != null) {
                t.setLocationPath(getFullLocationPath(t.getLivestock()));
            }
        }
        return paginateList(results, pageable);
    }

    public Page<LivestockTreatment> findByAnimal(UUID livestockId, Pageable pageable) {
        List<LivestockTreatment> results = treatmentRepository.findByLivestockId(livestockId);
        for (LivestockTreatment t : results) {
            if (t.getLivestock() != null) {
                t.setLocationPath(getFullLocationPath(t.getLivestock()));
            }
        }
        return paginateList(results, pageable);
    }

    public List<LivestockTreatmentPayment> getPayments(UUID treatmentId) {
        return paymentRepository.findByTreatmentId(treatmentId);
    }

    public BigDecimal getTotalPaid(UUID treatmentId) {
        return paymentRepository.sumAmountByTreatmentId(treatmentId);
    }

    public List<LivestockTreatment> getFollowUpBetween(LocalDate endDate) {
        return treatmentRepository.findFollowUpBetween(endDate);
    }

    public List<LivestockTreatment> getActiveWithdrawals() {
        return treatmentRepository.findActiveWithdrawals();
    }

    public String getFullLocationPath(Livestock livestock) {
        if (livestock == null || livestock.getLocation() == null) {
            return "—";
        }
        List<String> pathParts = new ArrayList<>();
        rw.animalproduct.animal.production.entity.Location current = livestock.getLocation();
        while (current != null) {
            pathParts.add(current.getName());
            current = current.getParent();
        }
        Collections.reverse(pathParts);
        return String.join(" > ", pathParts);
    }

    // ─── HELPERS ────────────────────────────────────────────────────────────

    private Page<LivestockTreatment> paginateList(List<LivestockTreatment> list, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());
        if (start > list.size()) {
            return new PageImpl<>(new ArrayList<>(), pageable, list.size());
        }
        List<LivestockTreatment> subList = list.subList(start, end);
        return new PageImpl<>(subList, pageable, list.size());
    }

    private String generateTreatmentReference() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String random = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "TXT-" + timestamp.substring(timestamp.length() - 8) + "-" + random;
    }

    // ─── STATS ──────────────────────────────────────────────────────────────

    public long countTotal() {
        return treatmentRepository.findAllActive().size();
    }

    public long countByStatus(LivestockTreatment.TreatmentStatus status) {
        return treatmentRepository.countByStatus(status);
    }

    public long countByStatus(String status) {
        return treatmentRepository.countByStatus(LivestockTreatment.TreatmentStatus.valueOf(status));
    }

    public long countUnpaid() {
        return treatmentRepository.countUnpaid();
    }

    public long countPartiallyPaid() {
        return treatmentRepository.countPartiallyPaid();
    }

    public long countPaid() {
        return treatmentRepository.countPaid();
    }

    public BigDecimal getTotalCost() {
        return treatmentRepository.getTotalCost();
    }

    public BigDecimal getTotalPaid() {
        return treatmentRepository.getTotalPaid();
    }

    public BigDecimal getTotalOutstanding() {
        return treatmentRepository.getTotalOutstanding();
    }

    // ─── REPORT METHODS ─────────────────────────────────────────────────────

    public Map<String, Object> getFinancialSummary(List<LivestockTreatment> treatments) {
        Map<String, Object> summary = new HashMap<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalBalance = BigDecimal.ZERO;
        int paidCount = 0;
        int unpaidCount = 0;
        int partialCount = 0;

        for (LivestockTreatment t : treatments) {
            if (t.getTreatmentCost() != null) totalCost = totalCost.add(t.getTreatmentCost());
            if (t.getAmountPaid() != null) totalPaid = totalPaid.add(t.getAmountPaid());
            totalBalance = totalBalance.add(t.getRemainingBalance());

            if (t.getPaymentStatus() != null) {
                switch (t.getPaymentStatus()) {
                    case PAID -> paidCount++;
                    case UNPAID -> unpaidCount++;
                    case PARTIALLY_PAID -> partialCount++;
                    default -> unpaidCount++;
                }
            }
        }

        summary.put("totalCost", totalCost);
        summary.put("totalPaid", totalPaid);
        summary.put("totalBalance", totalBalance);
        summary.put("totalOutstanding", totalBalance);
        summary.put("paidCount", paidCount);
        summary.put("unpaidCount", unpaidCount);
        summary.put("partialCount", partialCount);
        summary.put("count", treatments.size());

        return summary;
    }

    public Map<String, Object> getTypeAnalysis(List<LivestockTreatment> treatments) {
        Map<String, Object> analysis = new HashMap<>();
        Map<String, Long> countByType = new HashMap<>();
        Map<String, Double> successByType = new HashMap<>();

        for (LivestockTreatment t : treatments) {
            if (t.getTreatmentType() != null) {
                String type = t.getTreatmentType().name();
                countByType.put(type, countByType.getOrDefault(type, 0L) + 1);
            }
        }

        for (String type : countByType.keySet()) {
            long totalOfType = countByType.get(type);
            long successOfType = treatments.stream()
                    .filter(t -> t.getTreatmentType() != null
                            && t.getTreatmentType().name().equals(type)
                            && t.getOutcomeStatus() != null
                            && (t.getOutcomeStatus() == LivestockTreatment.OutcomeStatus.RECOVERED
                            || t.getOutcomeStatus() == LivestockTreatment.OutcomeStatus.IMPROVING))
                    .count();
            double successRate = totalOfType > 0 ? (successOfType * 100.0 / totalOfType) : 0;
            successByType.put(type, Math.round(successRate * 10.0) / 10.0);
        }

        analysis.put("countByType", countByType);
        analysis.put("successByType", successByType);

        return analysis;
    }

    public Map<String, Object> getMedicationAnalysis(List<LivestockTreatment> treatments) {
        Map<String, Object> analysis = new HashMap<>();
        Map<String, Long> medicationCount = new HashMap<>();
        Map<String, BigDecimal> medicationCost = new HashMap<>();

        for (LivestockTreatment t : treatments) {
            if (t.getMedication() != null) {
                String name = t.getMedication().getName();
                medicationCount.put(name, medicationCount.getOrDefault(name, 0L) + 1);
                if (t.getTreatmentCost() != null) {
                    medicationCost.put(name, medicationCost.getOrDefault(name, BigDecimal.ZERO).add(t.getTreatmentCost()));
                }
            }
        }

        Map<String, Long> mostUsed = medicationCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        Map<String, BigDecimal> mostExpensive = medicationCost.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        analysis.put("mostUsedMedications", mostUsed);
        analysis.put("mostExpensiveMedications", mostExpensive);

        return analysis;
    }

    public Map<String, Object> getOutcomeAnalysis(List<LivestockTreatment> treatments) {
        Map<String, Object> analysis = new HashMap<>();
        Map<String, Long> countByOutcome = new HashMap<>();
        Map<String, Double> percentageByOutcome = new HashMap<>();

        long total = treatments.size();

        for (LivestockTreatment t : treatments) {
            if (t.getOutcomeStatus() != null) {
                String outcome = t.getOutcomeStatus().name();
                countByOutcome.put(outcome, countByOutcome.getOrDefault(outcome, 0L) + 1);
            }
        }

        for (Map.Entry<String, Long> entry : countByOutcome.entrySet()) {
            double percentage = total > 0 ? (entry.getValue() * 100.0 / total) : 0;
            percentageByOutcome.put(entry.getKey(), Math.round(percentage * 10.0) / 10.0);
        }

        analysis.put("countByOutcome", countByOutcome);
        analysis.put("percentageByOutcome", percentageByOutcome);

        return analysis;
    }

    public Map<String, Object> getSuccessRate(List<LivestockTreatment> treatments) {
        Map<String, Object> result = new HashMap<>();
        long total = treatments.size();
        if (total == 0) {
            result.put("successRate", 0.0);
            result.put("recovered", 0L);
            result.put("improving", 0L);
            result.put("failed", 0L);
            result.put("total", 0L);
            return result;
        }

        long recovered = treatments.stream()
                .filter(t -> t.getOutcomeStatus() == LivestockTreatment.OutcomeStatus.RECOVERED)
                .count();
        long improving = treatments.stream()
                .filter(t -> t.getOutcomeStatus() == LivestockTreatment.OutcomeStatus.IMPROVING)
                .count();
        long failed = treatments.stream()
                .filter(t -> t.getOutcomeStatus() == LivestockTreatment.OutcomeStatus.FAILED)
                .count();

        double successRate = ((double) (recovered + improving) / total) * 100;

        result.put("successRate", Math.round(successRate * 10.0) / 10.0);
        result.put("recovered", recovered);
        result.put("improving", improving);
        result.put("failed", failed);
        result.put("total", total);

        return result;
    }

    public Map<String, BigDecimal> getCostTrends(List<LivestockTreatment> treatments) {
        Map<String, BigDecimal> trends = new HashMap<>();
        for (LivestockTreatment t : treatments) {
            if (t.getTreatmentDate() != null && t.getTreatmentCost() != null) {
                String month = t.getTreatmentDate().getYear() + "-" +
                        String.format("%02d", t.getTreatmentDate().getMonthValue());
                trends.put(month, trends.getOrDefault(month, BigDecimal.ZERO).add(t.getTreatmentCost()));
            }
        }
        return trends;
    }

    // ─── TOP MEDICATION HELPERS (safe for template use) ────────────────────

    public String getTopMedicationName(List<LivestockTreatment> treatments) {
        Map<String, Long> medicationCount = new HashMap<>();
        for (LivestockTreatment t : treatments) {
            if (t.getMedication() != null && t.getMedication().getName() != null) {
                medicationCount.merge(t.getMedication().getName(), 1L, Long::sum);
            }
        }
        return medicationCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");
    }

    public String getTopExpensiveMedicationName(List<LivestockTreatment> treatments) {
        Map<String, BigDecimal> medicationCost = new HashMap<>();
        for (LivestockTreatment t : treatments) {
            if (t.getMedication() != null && t.getMedication().getName() != null && t.getTreatmentCost() != null) {
                medicationCost.merge(t.getMedication().getName(), t.getTreatmentCost(), BigDecimal::add);
            }
        }
        return medicationCost.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("N/A");
    }

    public List<Map<String, Object>> getRecurringDiseases(List<LivestockTreatment> treatments) {
        Map<String, Long> diseaseCount = new HashMap<>();
        Map<String, Long> diseaseSuccess = new HashMap<>();

        for (LivestockTreatment t : treatments) {
            if (t.getDiagnosisName() != null && !t.getDiagnosisName().isEmpty()) {
                String disease = t.getDiagnosisName();
                diseaseCount.put(disease, diseaseCount.getOrDefault(disease, 0L) + 1);
                if (t.getOutcomeStatus() == LivestockTreatment.OutcomeStatus.RECOVERED
                        || t.getOutcomeStatus() == LivestockTreatment.OutcomeStatus.IMPROVING) {
                    diseaseSuccess.put(disease, diseaseSuccess.getOrDefault(disease, 0L) + 1);
                }
            }
        }

        return diseaseCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("disease", entry.getKey());
                    map.put("count", entry.getValue());
                    long success = diseaseSuccess.getOrDefault(entry.getKey(), 0L);
                    double rate = entry.getValue() > 0 ? (success * 100.0 / entry.getValue()) : 0;
                    map.put("successRate", Math.round(rate * 10.0) / 10.0);
                    return map;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getTypeStats(List<LivestockTreatment> treatments, String type) {
        Map<String, Object> stats = new HashMap<>();
        List<LivestockTreatment> filtered = treatments.stream()
                .filter(t -> t.getTreatmentType() != null && t.getTreatmentType().name().equals(type))
                .collect(Collectors.toList());

        stats.put("total", (long) filtered.size());
        stats.put("totalCost", filtered.stream()
                .map(LivestockTreatment::getTreatmentCost)
                .filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        stats.put("totalPaid", filtered.stream()
                .map(LivestockTreatment::getAmountPaid)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        double avgDuration = 0;
        int countWithDuration = 0;
        for (LivestockTreatment t : filtered) {
            if (t.getTreatmentDuration() != null && !t.getTreatmentDuration().isEmpty()) {
                try {
                    String numStr = t.getTreatmentDuration().replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        avgDuration += Integer.parseInt(numStr);
                        countWithDuration++;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        stats.put("avgDuration", countWithDuration > 0 ? Math.round(avgDuration / countWithDuration) : null);

        return stats;
    }
}