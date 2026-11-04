package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Medication;
import rw.animalproduct.animal.production.repository.MedicationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class MedicationService {

    private static final Logger log = LoggerFactory.getLogger(MedicationService.class);

    /**
     * Small dedicated pool for audit logging.
     * Fire-and-forget — the lifecycle action never waits for audit to finish.
     * Daemon threads so JVM shutdown isn't blocked.
     */
    private static final ExecutorService AUDIT_POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "medication-audit");
        t.setDaemon(true);
        return t;
    });

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            AUDIT_POOL.shutdown();
            try { AUDIT_POOL.awaitTermination(5, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }));
    }

    private final MedicationRepository medicationRepository;
    private final AuditLogService      auditLogService;

    public MedicationService(MedicationRepository medicationRepository,
                             AuditLogService auditLogService) {
        this.medicationRepository = medicationRepository;
        this.auditLogService      = auditLogService;
    }

    // ── READ ──────────────────────────────────────────────────────────────────
    public List<Medication> getAll() {
        return medicationRepository.findAllByOrderByNameAsc();
    }

    public List<Medication> getActive() {
        return medicationRepository.findByIsActiveTrueOrderByNameAsc();
    }

    public Optional<Medication> getById(UUID id) {
        return medicationRepository.findById(id);
    }

    // ── CREATE ────────────────────────────────────────────────────────────────
    @Transactional
    public Medication save(Medication medication) {
        if (medication.getIsActive() == null) {
            medication.setIsActive(true);
        }
        Medication saved = medicationRepository.save(medication);

        final String snap = snapshot(saved);
        final String user = currentUsername();
        auditAsync("medication", saved.getId(), "CREATE", user, null, snap,
                "New medication created");

        return saved;
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────
    @Transactional
    public void update(UUID id, Medication updated) {
        Medication existing = medicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Medication not found."));

        String before = snapshot(existing);

        existing.setName(updated.getName());
        existing.setGenericName(updated.getGenericName());
        existing.setCategory(updated.getCategory());
        existing.setDefaultDosage(updated.getDefaultDosage());
        existing.setDefaultDosageUnit(updated.getDefaultDosageUnit());
        existing.setManufacturer(updated.getManufacturer());
        existing.setDescription(updated.getDescription());
        // isActive intentionally NOT changed here — use activate/deactivate

        Medication saved = medicationRepository.save(existing);

        final String after = snapshot(saved);
        final String user  = currentUsername();
        auditAsync("medication", id, "CORRECTION", user, before, after,
                "Medication record corrected");
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    @Transactional
    public void delete(UUID id) {
        Medication existing = medicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Medication not found."));

        long usage = medicationRepository.countActiveTreatmentsByMedicationId(id);
        if (usage > 0) {
            throw new IllegalStateException(
                    "Cannot delete: this medication is used in " + usage +
                            " treatment record(s). Use Deactivate instead.");
        }

        String snap = snapshot(existing);
        medicationRepository.delete(existing);

        final String user = currentUsername();
        auditAsync("medication", id, "HARD_DELETE", user, snap, null,
                "Permanently deleted (never used in any treatment)");

        log.info("Medication {} permanently deleted by {}", id, user);
    }

    // ── DEACTIVATE ────────────────────────────────────────────────────────────
    @Transactional
    public void deactivate(UUID id, String reason) {
        Medication m = medicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Medication not found."));

        if (Boolean.FALSE.equals(m.getIsActive())) {
            throw new IllegalStateException("Medication is already inactive.");
        }

        String before = snapshot(m);
        m.setIsActive(false);
        Medication saved = medicationRepository.save(m);

        final String after = snapshot(saved);
        final String user  = currentUsername();
        auditAsync("medication", id, "DEACTIVATE", user, before, after,
                "Reason: " + (reason != null && !reason.isBlank() ? reason : "(not specified)"));
    }

    // ── REACTIVATE ────────────────────────────────────────────────────────────
    @Transactional
    public void reactivate(UUID id) {
        Medication m = medicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Medication not found."));

        if (Boolean.TRUE.equals(m.getIsActive())) {
            throw new IllegalStateException("Medication is already active.");
        }

        String before = snapshot(m);
        m.setIsActive(true);
        Medication saved = medicationRepository.save(m);

        final String after = snapshot(saved);
        final String user  = currentUsername();
        auditAsync("medication", id, "REACTIVATE", user, before, after,
                "Medication reactivated");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Fire-and-forget audit — never blocks, never throws to the caller. */
    private void auditAsync(String entityType, UUID entityId, String action,
                            String user, String oldData, String newData, String notes) {
        CompletableFuture.runAsync(() -> {
            try {
                auditLogService.log(entityType, entityId, action, user, oldData, newData, notes);
            } catch (Exception e) {
                log.error("Audit log failed for {} {} [{}]: {}",
                        entityType, entityId, action, e.getMessage(), e);
            }
        }, AUDIT_POOL);
    }

    private String snapshot(Medication m) {
        return "Name=" + m.getName()
                + "|generic=" + m.getGenericName()
                + "|cat=" + m.getCategory()
                + "|dosage=" + m.getDefaultDosage() + " " + m.getDefaultDosageUnit()
                + "|manufacturer=" + m.getManufacturer()
                + "|active=" + m.getIsActive()
                + "|desc=" + m.getDescription();
    }

    private String currentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return auth.getName();
            }
        } catch (Exception ignored) { }
        return "system";
    }
}