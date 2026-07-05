package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.LivestockTreatment;
import rw.animalproduct.animal.production.entity.Medication;
import rw.animalproduct.animal.production.repository.LivestockTreatmentRepository;
import rw.animalproduct.animal.production.repository.MedicationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MedicationUsageService {

    private final LivestockTreatmentRepository treatmentRepository;
    private final MedicationRepository medicationRepository;

    public MedicationUsageService(LivestockTreatmentRepository treatmentRepository,
                                  MedicationRepository medicationRepository) {
        this.treatmentRepository = treatmentRepository;
        this.medicationRepository = medicationRepository;
    }

    /**
     * Get medication usage report for a date range
     */
    public MedicationUsageReport getMedicationUsageReport(LocalDate startDate, LocalDate endDate) {
        MedicationUsageReport report = new MedicationUsageReport();
        report.setStartDate(startDate);
        report.setEndDate(endDate);

        // Get all treatments in date range
        List<LivestockTreatment> treatments = treatmentRepository
                .findByTreatmentDateBetweenAndIsDeletedFalse(startDate, endDate);

        report.setTotalTreatments(treatments.size());

        // Group by medication
        Map<UUID, MedicationUsage> medicationUsageMap = new HashMap<>();

        for (LivestockTreatment treatment : treatments) {
            // FIX: Get medication ID from the medication entity
            Medication medication = treatment.getMedication();
            if (medication == null) {
                // Skip treatments without medication
                continue;
            }

            UUID medicationId = medication.getId();

            MedicationUsage usage = medicationUsageMap.computeIfAbsent(medicationId,
                    id -> new MedicationUsage(medicationId, medication.getName()));

            usage.incrementCount();

            if (treatment.getTreatmentCost() != null) {
                usage.addCost(treatment.getTreatmentCost());
            }

            // Track by treatment type
            if (treatment.getTreatmentType() != null) {
                usage.addTreatmentType(treatment.getTreatmentType().name());
            }

            // Track by livestock
            if (treatment.getLivestock() != null && treatment.getLivestock().getId() != null) {
                usage.addLivestock(treatment.getLivestock().getId());
            }
        }

        report.setMedicationUsage(new ArrayList<>(medicationUsageMap.values()));

        // Calculate totals
        int totalMedicationsUsed = medicationUsageMap.size();
        report.setTotalMedicationsUsed(totalMedicationsUsed);

        BigDecimal totalCost = medicationUsageMap.values().stream()
                .map(MedicationUsage::getTotalCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        report.setTotalCost(totalCost);

        // Find most used medication
        Optional<MedicationUsage> mostUsed = medicationUsageMap.values().stream()
                .max(Comparator.comparingInt(MedicationUsage::getUsageCount));
        mostUsed.ifPresent(report::setMostUsedMedication);

        return report;
    }

    /**
     * Get medication usage by medication ID
     */
    public MedicationUsage getMedicationUsageById(UUID medicationId, LocalDate startDate, LocalDate endDate) {
        Medication medication = medicationRepository.findById(medicationId)
                .orElseThrow(() -> new RuntimeException("Medication not found"));

        List<LivestockTreatment> treatments = treatmentRepository
                .findByMedicationIdAndTreatmentDateBetweenAndIsDeletedFalse(medicationId, startDate, endDate);

        MedicationUsage usage = new MedicationUsage(medicationId, medication.getName());

        for (LivestockTreatment treatment : treatments) {
            usage.incrementCount();
            if (treatment.getTreatmentCost() != null) {
                usage.addCost(treatment.getTreatmentCost());
            }
            if (treatment.getTreatmentType() != null) {
                usage.addTreatmentType(treatment.getTreatmentType().name());
            }
            if (treatment.getLivestock() != null && treatment.getLivestock().getId() != null) {
                usage.addLivestock(treatment.getLivestock().getId());
            }
        }

        return usage;
    }

    /**
     * Get medication usage by treatment type (CURATIVE, PREVENTIVE, etc.)
     */
    public Map<String, MedicationUsage> getMedicationUsageByType(LocalDate startDate, LocalDate endDate) {
        List<LivestockTreatment> treatments = treatmentRepository
                .findByTreatmentDateBetweenAndIsDeletedFalse(startDate, endDate);

        Map<String, MedicationUsage> usageByType = new HashMap<>();

        for (LivestockTreatment treatment : treatments) {
            if (treatment.getMedication() == null || treatment.getTreatmentType() == null) {
                continue;
            }

            String type = treatment.getTreatmentType().name();
            Medication medication = treatment.getMedication();

            MedicationUsage usage = usageByType.computeIfAbsent(type,
                    k -> new MedicationUsage(medication.getId(), medication.getName()));

            usage.incrementCount();
            if (treatment.getTreatmentCost() != null) {
                usage.addCost(treatment.getTreatmentCost());
            }
        }

        return usageByType;
    }

    // ── Inner DTO Class ──────────────────────────────────────────────────────

    public static class MedicationUsage {
        private UUID medicationId;
        private String medicationName;
        private int usageCount;
        private BigDecimal totalCost;
        private Set<String> treatmentTypes = new HashSet<>();
        private Set<UUID> livestockIds = new HashSet<>();

        public MedicationUsage(UUID medicationId, String medicationName) {
            this.medicationId = medicationId;
            this.medicationName = medicationName;
            this.totalCost = BigDecimal.ZERO;
            this.usageCount = 0;
        }

        public void incrementCount() {
            this.usageCount++;
        }

        public void addCost(BigDecimal cost) {
            if (cost != null) {
                this.totalCost = this.totalCost.add(cost);
            }
        }

        public void addTreatmentType(String type) {
            if (type != null) {
                this.treatmentTypes.add(type);
            }
        }

        public void addLivestock(UUID livestockId) {
            if (livestockId != null) {
                this.livestockIds.add(livestockId);
            }
        }

        // Getters
        public UUID getMedicationId() { return medicationId; }
        public String getMedicationName() { return medicationName; }
        public int getUsageCount() { return usageCount; }
        public BigDecimal getTotalCost() { return totalCost; }
        public Set<String> getTreatmentTypes() { return treatmentTypes; }
        public Set<UUID> getLivestockIds() { return livestockIds; }
        public int getUniqueLivestockCount() { return livestockIds.size(); }
    }

    public static class MedicationUsageReport {
        private LocalDate startDate;
        private LocalDate endDate;
        private int totalTreatments;
        private int totalMedicationsUsed;
        private BigDecimal totalCost;
        private List<MedicationUsage> medicationUsage;
        private MedicationUsage mostUsedMedication;

        // Getters and Setters
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

        public int getTotalTreatments() { return totalTreatments; }
        public void setTotalTreatments(int totalTreatments) { this.totalTreatments = totalTreatments; }

        public int getTotalMedicationsUsed() { return totalMedicationsUsed; }
        public void setTotalMedicationsUsed(int totalMedicationsUsed) {
            this.totalMedicationsUsed = totalMedicationsUsed;
        }

        public BigDecimal getTotalCost() { return totalCost; }
        public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

        public List<MedicationUsage> getMedicationUsage() { return medicationUsage; }
        public void setMedicationUsage(List<MedicationUsage> medicationUsage) {
            this.medicationUsage = medicationUsage;
        }

        public MedicationUsage getMostUsedMedication() { return mostUsedMedication; }
        public void setMostUsedMedication(MedicationUsage mostUsedMedication) {
            this.mostUsedMedication = mostUsedMedication;
        }
    }
}