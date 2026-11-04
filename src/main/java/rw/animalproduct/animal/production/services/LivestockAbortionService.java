package rw.animalproduct.animal.production.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockAbortion;
import rw.animalproduct.animal.production.repository.LivestockAbortionRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockAbortionService {

    private final LivestockAbortionRepository abortionRepository;
    private final LivestockRepository livestockRepository;
    private final AuditLogService auditLogService;
    private final AbortionEmailService abortionEmailService;

    public LivestockAbortionService(LivestockAbortionRepository abortionRepository,
                                    LivestockRepository livestockRepository,
                                    AuditLogService auditLogService,
                                    AbortionEmailService abortionEmailService) {
        this.abortionRepository = abortionRepository;
        this.livestockRepository = livestockRepository;
        this.auditLogService = auditLogService;
        this.abortionEmailService = abortionEmailService;
    }

    // ── READ ─────────────────────────────────────────────────────────────────

    public List<LivestockAbortion> getAll() {
        return abortionRepository.findByIsDeletedFalseOrderByAbortionDateDesc();
    }

    public Optional<LivestockAbortion> getById(UUID id) {
        return abortionRepository.findById(id);
    }

    public List<LivestockAbortion> getByLivestock(UUID livestockId) {
        return abortionRepository.findByLivestockIdAndIsDeletedFalseOrderByAbortionDateDesc(livestockId);
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    @Transactional
    public LivestockAbortion addNew(LivestockAbortion abortion) {
        if (abortion.getCreatedAt() == null) {
            abortion.setCreatedAt(LocalDateTime.now());
        }

        resolveAndSetLivestock(abortion);

        LivestockAbortion saved = abortionRepository.save(abortion);

        auditLogService.log(
                "livestock_abortion",
                saved.getId(),
                "CREATE",
                getCurrentUsername(),
                null,
                buildSnapshot(saved),
                "New abortion record created"
        );

        // ✅ Fire notification email (async — won't block the response)
        try {
            abortionEmailService.sendAbortionRecordedNotification(saved);
        } catch (Exception e) {
            // Never let email failure roll back the transaction
            System.err.println("⚠️ Abortion email failed: " + e.getMessage());
        }

        return saved;
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    @Transactional
    public LivestockAbortion update(UUID id, LivestockAbortion updated) {
        Optional<LivestockAbortion> existingOpt = abortionRepository.findById(id);
        if (existingOpt.isEmpty()) return null;

        LivestockAbortion existing = existingOpt.get();
        String oldSnapshot = buildSnapshot(existing);

        existing.setAbortionDate(updated.getAbortionDate());
        existing.setPregnancyNumber(updated.getPregnancyNumber());
        existing.setAbortionReason(updated.getAbortionReason());
        existing.setStageOfPregnancy(updated.getStageOfPregnancy());
        existing.setExpectedBirthDate(updated.getExpectedBirthDate());
        existing.setLivestockIdValue(updated.getLivestockIdValue());
        resolveAndSetLivestock(existing);

        LivestockAbortion saved = abortionRepository.save(existing);
        String newSnapshot = buildSnapshot(saved);

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

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        abortionRepository.findById(id).ifPresent(existing -> {
            String oldSnapshot = buildSnapshot(existing);
            existing.setIsDeleted(true);
            abortionRepository.save(existing);

            auditLogService.log(
                    "livestock_abortion",
                    id,
                    "SOFT_DELETE",
                    getCurrentUsername(),
                    oldSnapshot,
                    null,
                    "Abortion record soft-deleted."
            );
        });
    }

    // ── HELPERS ─────────────────────────────────────────────────────────────

    private String buildSnapshot(LivestockAbortion a) {
        return "Animal: " + (a.getLivestock() != null ? a.getLivestock().getTagNumber() : "unknown")
                + " | Abortion Date: " + a.getAbortionDate()
                + " | Pregnancy #: " + a.getPregnancyNumber()
                + " | Reason: " + a.getAbortionReason()
                + " | Stage: " + a.getStageOfPregnancy()
                + " | Expected Birth: " + a.getExpectedBirthDate();
    }

    private void resolveAndSetLivestock(LivestockAbortion abortion) {
        String idStr = abortion.getLivestockIdValue();
        if (idStr != null && !idStr.isEmpty()) {
            try {
                Livestock ls = livestockRepository.findById(UUID.fromString(idStr))
                        .orElseThrow(() -> new RuntimeException("Livestock not found with ID: " + idStr));
                abortion.setLivestock(ls);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid livestock ID format: " + idStr, e);
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