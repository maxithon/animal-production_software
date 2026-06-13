package rw.animalproduct.animal.production.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Medication;
import rw.animalproduct.animal.production.repository.MedicationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MedicationService {

    private final MedicationRepository medicationRepository;
    private final AuditLogService      auditLogService;

    public MedicationService(MedicationRepository medicationRepository,
                             AuditLogService auditLogService) {
        this.medicationRepository = medicationRepository;
        this.auditLogService      = auditLogService;
    }

    // ── READ ─────────────────────────────────────────────────────────────────

    public List<Medication> getAll() {
        return medicationRepository.findAllByOrderByNameAsc();
    }

    public List<Medication> getActive() {
        return medicationRepository.findByIsActiveTrueOrderByNameAsc();
    }

    public Optional<Medication> getById(UUID id) {
        return medicationRepository.findById(id);
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Transactional
    public Medication save(Medication medication) {
        Medication saved = medicationRepository.save(medication);

        // ── Audit: CREATE ────────────────────────────────────────────────────
        auditLogService.log(
                "medication",
                saved.getId(),
                "CREATE",
                getCurrentUsername(),
                null,
                buildSnapshot(saved),
                "New medication created"
        );
        return saved;
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @Transactional
    public void update(UUID id, Medication updated) {
        medicationRepository.findById(id).ifPresent(existing -> {

            // ── Step 1: Capture OLD snapshot BEFORE any changes ──────────────
            String oldSnapshot = buildSnapshot(existing);

            // ── Step 2: Apply all changes ────────────────────────────────────
            existing.setName(updated.getName());
            existing.setGenericName(updated.getGenericName());
            existing.setCategory(updated.getCategory());
            existing.setDefaultDosage(updated.getDefaultDosage());
            existing.setDefaultDosageUnit(updated.getDefaultDosageUnit());
            existing.setManufacturer(updated.getManufacturer());
            existing.setDescription(updated.getDescription());
            existing.setIsActive(updated.getIsActive());

            Medication saved = medicationRepository.save(existing);

            // ── Step 3: Capture NEW snapshot AFTER changes ───────────────────
            String newSnapshot = buildSnapshot(saved);

            // ── Step 4: Write audit log ──────────────────────────────────────
            auditLogService.log(
                    "medication",
                    id,
                    "UPDATE",
                    getCurrentUsername(),
                    oldSnapshot,
                    newSnapshot,
                    "Medication record updated"
            );
        });
    }

    // ── DELETE (soft delete) ─────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        medicationRepository.findById(id).ifPresent(existing -> {

            // ── Capture snapshot before deletion ────────────────────────────
            String oldSnapshot = buildSnapshot(existing);

            // ── Soft delete ──────────────────────────────────────────────────
            existing.setIsDeleted(true);
            medicationRepository.save(existing);

            // ── Write audit log ──────────────────────────────────────────────
            auditLogService.log(
                    "medication",
                    id,
                    "SOFT_DELETE",
                    getCurrentUsername(),
                    oldSnapshot,
                    null,
                    "Medication soft-deleted. Still in DB with is_deleted=true."
            );
        });
    }

    public boolean nameExists(String name) {
        return medicationRepository.existsByNameIgnoreCase(name);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Builds a readable text snapshot of a medication record.
     * Called BEFORE changes for old snapshot, and AFTER changes for new snapshot.
     */
    private String buildSnapshot(Medication m) {
        return "Name: "         + m.getName()
                + " | Generic: "   + m.getGenericName()
                + " | Category: "  + m.getCategory()
                + " | Dosage: "    + m.getDefaultDosage() + " " + m.getDefaultDosageUnit()
                + " | Manufacturer: " + m.getManufacturer()
                + " | Active: "    + m.getIsActive()
                + " | Description: " + m.getDescription();
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