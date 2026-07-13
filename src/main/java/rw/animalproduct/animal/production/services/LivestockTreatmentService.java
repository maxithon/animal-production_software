package rw.animalproduct.animal.production.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LivestockTreatmentService {

    private final LivestockTreatmentRepository treatmentRepository;
    private final LivestockRepository          livestockRepository;
    private final MedicationRepository         medicationRepository;
    private final VeterinarianRepository       veterinarianRepository;
    private final UsersRepository              usersRepository;
    private final AuditLogService              auditLogService;

    public LivestockTreatmentService(
            LivestockTreatmentRepository treatmentRepository,
            LivestockRepository          livestockRepository,
            MedicationRepository         medicationRepository,
            VeterinarianRepository       veterinarianRepository,
            UsersRepository              usersRepository,
            AuditLogService              auditLogService
    ) {
        this.treatmentRepository    = treatmentRepository;
        this.livestockRepository    = livestockRepository;
        this.medicationRepository   = medicationRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.usersRepository        = usersRepository;
        this.auditLogService        = auditLogService;
    }

    // ── READ ─────────────────────────────────────────────────────────────────

    public List<LivestockTreatment> getAll() {
        return treatmentRepository.findAll();
    }

    public Optional<LivestockTreatment> getById(UUID id) {
        return treatmentRepository.findById(id);
    }

    /**
     * Returns all non-deleted treatment records for a given animal,
     * most recent treatment date first.
     *
     * Implemented via in-service filtering over findAll() so it works
     * regardless of what derived-query methods exist on the repository.
     * If treatment volume grows large, replace this with a proper
     * repository query, e.g.:
     *   findByLivestock_IdAndIsDeletedFalseOrderByTreatmentDateDesc(UUID livestockId)
     */
    public List<LivestockTreatment> getByLivestock(UUID livestockId) {
        if (livestockId == null) {
            return List.of();
        }
        return treatmentRepository.findAll().stream()
                .filter(t -> t.getLivestock() != null
                        && livestockId.equals(t.getLivestock().getId()))
                .filter(t -> t.getIsDeleted() == null || !t.getIsDeleted())
                .sorted(Comparator.comparing(
                        LivestockTreatment::getTreatmentDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Transactional
    public void addNew(LivestockTreatment treatment) {
        resolveAssociations(treatment);
        setAuditFields(treatment);
        LivestockTreatment saved = treatmentRepository.save(treatment);

        // ── Audit: CREATE ────────────────────────────────────────────────────
        String snapshot = buildSnapshot(saved);
        auditLogService.log(
                "livestock_treatment",
                saved.getId(),
                "CREATE",
                getCurrentUsername(),
                null,
                snapshot,
                "New treatment record created"
        );
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @Transactional
    public void update(UUID id, LivestockTreatment updated) {
        treatmentRepository.findById(id).ifPresent(existing -> {

            // ── Step 1: Capture OLD snapshot BEFORE any changes ──────────────
            String oldSnapshot = buildSnapshot(existing);

            // ── Step 2: Apply all changes ────────────────────────────────────
            resolveAssociations(updated);

            existing.setLivestock(updated.getLivestock());
            existing.setMedication(updated.getMedication());
            existing.setVeterinarian(updated.getVeterinarian());
            existing.setTreatmentDate(updated.getTreatmentDate());
            existing.setNextTreatmentDate(updated.getNextTreatmentDate());
            existing.setDosage(updated.getDosage());
            existing.setDosageUnit(updated.getDosageUnit());
            existing.setFrequency(updated.getFrequency());
            existing.setTreatmentDuration(updated.getTreatmentDuration());
            existing.setTreatmentType(updated.getTreatmentType());
            existing.setTreatmentStatus(updated.getTreatmentStatus());
            existing.setTreatmentCost(updated.getTreatmentCost());
            existing.setDescription(updated.getDescription());
            existing.setIsPaid(updated.getIsPaid());
            existing.setPaymentDate(updated.getPaymentDate());

            LivestockTreatment saved = treatmentRepository.save(existing);

            // ── Step 3: Capture NEW snapshot AFTER changes ───────────────────
            String newSnapshot = buildSnapshot(saved);

            // ── Step 4: Write audit log ──────────────────────────────────────
            auditLogService.log(
                    "livestock_treatment",
                    id,
                    "UPDATE",
                    getCurrentUsername(),
                    oldSnapshot,
                    newSnapshot,
                    "Treatment record updated"
            );
        });
    }

    // ── DELETE (soft delete) ─────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        treatmentRepository.findById(id).ifPresent(existing -> {

            // ── Capture snapshot before deletion ────────────────────────────
            String oldSnapshot = buildSnapshot(existing);

            // ── Soft delete: mark as deleted, do NOT remove from DB ──────────
            existing.setIsDeleted(true);
            treatmentRepository.save(existing);

            // ── Write audit log ──────────────────────────────────────────────
            auditLogService.log(
                    "livestock_treatment",
                    id,
                    "SOFT_DELETE",
                    getCurrentUsername(),
                    oldSnapshot,
                    null,
                    "Treatment record soft-deleted. Still in DB with is_deleted=true."
            );
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Builds a readable text snapshot of a treatment record.
     * Called BEFORE changes for old snapshot, and AFTER changes for new snapshot.
     */
    private String buildSnapshot(LivestockTreatment t) {
        return "Animal: "    + (t.getLivestock()   != null ? t.getLivestock().getTagNumber()   : "unknown")
                + " | Medication: " + (t.getMedication() != null ? t.getMedication().getName()       : "none")
                + " | Vet: "        + (t.getVeterinarian() != null ? t.getVeterinarian().getFullName() : "none")
                + " | Date: "       + t.getTreatmentDate()
                + " | Type: "       + t.getTreatmentType()
                + " | Status: "     + t.getTreatmentStatus()
                + " | Cost: "       + t.getTreatmentCost()
                + " | Paid: "       + t.getIsPaid()
                + " | Dosage: "     + t.getDosage() + " " + t.getDosageUnit()
                + " | Next: "       + t.getNextTreatmentDate();
    }

    private void resolveAssociations(LivestockTreatment t) {

        // Livestock
        if (t.getLivestockIdValue() != null && !t.getLivestockIdValue().trim().isEmpty()) {
            UUID lid = UUID.fromString(t.getLivestockIdValue().trim());
            Livestock ls = livestockRepository.findById(lid)
                    .orElseThrow(() -> new IllegalArgumentException("Animal not found: " + lid));
            t.setLivestock(ls);
        }

        // Medication
        if (t.getMedicationIdValue() != null && !t.getMedicationIdValue().trim().isEmpty()) {
            UUID mid = UUID.fromString(t.getMedicationIdValue().trim());
            Medication med = medicationRepository.findById(mid)
                    .orElseThrow(() -> new IllegalArgumentException("Medication not found: " + mid));
            t.setMedication(med);
            if (t.getDosageUnit() == null && med.getDefaultDosageUnit() != null) {
                t.setDosageUnit(med.getDefaultDosageUnit());
            }
        }

        // Veterinarian
        if (t.getVeterinarianIdValue() != null && !t.getVeterinarianIdValue().trim().isEmpty()) {
            UUID vid = UUID.fromString(t.getVeterinarianIdValue().trim());
            Veterinarian vet = veterinarianRepository.findById(vid)
                    .orElseThrow(() -> new IllegalArgumentException("Veterinarian not found: " + vid));
            t.setVeterinarian(vet);
        } else {
            t.setVeterinarian(null);
        }
    }

    private void setAuditFields(LivestockTreatment t) {
        if (t.getCreatedAt() == null) {
            t.setCreatedAt(LocalDateTime.now());
        }
        if (t.getCreatedBy() == null) {
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()) {
                    String username = auth.getName();
                    Users user = usersRepository.findByEmail(username)
                            .orElseThrow(() -> new RuntimeException("User not found: " + username));
                    t.setCreatedBy(user);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String getCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return auth.getName();
            }
        } catch (Exception e) {
            // ignore
        }
        return "system";
    }
}