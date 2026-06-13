package rw.animalproduct.animal.production.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockAbortion;
import rw.animalproduct.animal.production.repository.LivestockAbortionRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockAbortionService {

    private final LivestockAbortionRepository abortionRepository;
    private final LivestockRepository         livestockRepository;
    private final AuditLogService             auditLogService;

    public LivestockAbortionService(LivestockAbortionRepository abortionRepository,
                                    LivestockRepository livestockRepository,
                                    AuditLogService auditLogService) {
        this.abortionRepository  = abortionRepository;
        this.livestockRepository = livestockRepository;
        this.auditLogService     = auditLogService;
    }

    // ── READ ─────────────────────────────────────────────────────────────────

    public List<LivestockAbortion> getAll() {
        return abortionRepository.findAll();
    }

    public Optional<LivestockAbortion> getById(UUID id) {
        return abortionRepository.findById(id);
    }

    public List<LivestockAbortion> getByLivestock(UUID livestockId) {
        return abortionRepository.findByLivestockId(livestockId);
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Transactional
    public LivestockAbortion addNew(LivestockAbortion abortion) {
        resolveAndSetLivestock(abortion);
        LivestockAbortion saved = abortionRepository.save(abortion);

        // ── Audit: CREATE ────────────────────────────────────────────────────
        auditLogService.log(
                "livestock_abortion",
                saved.getId(),
                "CREATE",
                getCurrentUsername(),
                null,
                buildSnapshot(saved),
                "New abortion record created"
        );
        return saved;
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @Transactional
    public LivestockAbortion update(UUID id, LivestockAbortion updated) {
        Optional<LivestockAbortion> existingOpt = abortionRepository.findById(id);
        if (existingOpt.isEmpty()) return null;

        LivestockAbortion existing = existingOpt.get();

        // ── Step 1: Capture OLD snapshot BEFORE any changes ──────────────────
        String oldSnapshot = buildSnapshot(existing);

        // ── Step 2: Apply all changes ─────────────────────────────────────────
        existing.setAbortionDate(updated.getAbortionDate());
        existing.setPregnancyNumber(updated.getPregnancyNumber());
        existing.setAbortionReason(updated.getAbortionReason());
        existing.setStageOfPregnancy(updated.getStageOfPregnancy());
        existing.setLivestockIdValue(updated.getLivestockIdValue());
        resolveAndSetLivestock(existing);

        LivestockAbortion saved = abortionRepository.save(existing);

        // ── Step 3: Capture NEW snapshot AFTER changes ────────────────────────
        String newSnapshot = buildSnapshot(saved);

        // ── Step 4: Write audit log ───────────────────────────────────────────
        auditLogService.log(
                "livestock_abortion",
                id,
                "UPDATE",
                getCurrentUsername(),
                oldSnapshot,
                newSnapshot,
                "Abortion record updated"
        );

        return saved;
    }

    // ── DELETE (soft delete) ──────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        abortionRepository.findById(id).ifPresent(existing -> {

            // ── Capture snapshot before deletion ─────────────────────────────
            String oldSnapshot = buildSnapshot(existing);

            // ── Soft delete ───────────────────────────────────────────────────
            existing.setIsDeleted(true);
            abortionRepository.save(existing);

            // ── Write audit log ───────────────────────────────────────────────
            auditLogService.log(
                    "livestock_abortion",
                    id,
                    "SOFT_DELETE",
                    getCurrentUsername(),
                    oldSnapshot,
                    null,
                    "Abortion record soft-deleted. Still in DB with is_deleted=true."
            );
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds a readable text snapshot of an abortion record.
     * Called BEFORE changes for old snapshot, and AFTER changes for new snapshot.
     */
    private String buildSnapshot(LivestockAbortion a) {
        return "Animal: "           + (a.getLivestock() != null ? a.getLivestock().getTagNumber() : "unknown")
                + " | Abortion Date: " + a.getAbortionDate()
                + " | Pregnancy #: "   + a.getPregnancyNumber()
                + " | Reason: "        + a.getAbortionReason()
                + " | Stage: "         + a.getStageOfPregnancy()
                + " | Expected Birth: " + a.getExpectedBirthDate();
    }

    private void resolveAndSetLivestock(LivestockAbortion abortion) {
        String idStr = abortion.getLivestockIdValue();
        if (idStr != null && !idStr.isEmpty()) {
            Livestock ls = livestockRepository.findById(UUID.fromString(idStr))
                    .orElseThrow(() -> new RuntimeException("Livestock not found"));
            abortion.setLivestock(ls);
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