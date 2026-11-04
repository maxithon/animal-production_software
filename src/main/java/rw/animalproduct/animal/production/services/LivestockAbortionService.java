package rw.animalproduct.animal.production.services;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockAbortion;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.LivestockAbortionRepository;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockAbortionService {

    private static final int STALE_DUE_DATE_DAYS = 30;

    private final LivestockAbortionRepository abortionRepository;
    private final LivestockRepository livestockRepository;
    private final LivestockBreedingRepository breedingRepository;
    private final AuditLogService auditLogService;
    private final AbortionEmailService abortionEmailService;

    public LivestockAbortionService(LivestockAbortionRepository abortionRepository,
                                    LivestockRepository livestockRepository,
                                    LivestockBreedingRepository breedingRepository,
                                    AuditLogService auditLogService,
                                    AbortionEmailService abortionEmailService) {
        this.abortionRepository = abortionRepository;
        this.livestockRepository = livestockRepository;
        this.breedingRepository = breedingRepository;
        this.auditLogService = auditLogService;
        this.abortionEmailService = abortionEmailService;
    }

    // ── READ ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LivestockAbortion> getAll() {
        return abortionRepository.findByIsDeletedFalseOrderByAbortionDateDesc();
    }

    @Transactional(readOnly = true)
    public Optional<LivestockAbortion> getById(UUID id) {
        return abortionRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<LivestockAbortion> getByLivestock(UUID livestockId) {
        return abortionRepository.findByLivestockIdAndIsDeletedFalseOrderByAbortionDateDesc(livestockId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> searchPregnantForPicker(String query, int limit) {
        int capped = Math.min(Math.max(limit, 1), 50);
        String q = query == null ? "" : query.trim();

        List<Object[]> rows = livestockRepository.searchPregnantAnimals(
                q, PageRequest.of(0, capped));
        if (rows.isEmpty()) return List.of();

        List<UUID> ids = rows.stream().map(r -> (UUID) r[0]).toList();

        Map<UUID, Object[]> agg = new HashMap<>();
        for (Object[] a : breedingRepository.findDueAndCompletedForAnimals(ids)) {
            agg.put((UUID) a[0], a);
        }

        return rows.stream().map(r -> {
            UUID id = (UUID) r[0];
            String tag = (String) r[1];
            String category = (String) r[2];
            Object[] a = agg.get(id);

            String dueDate = null;
            long completed = 0;
            if (a != null) {
                if (a[1] != null) dueDate = a[1].toString();
                if (a[2] != null) completed = ((Number) a[2]).longValue();
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id.toString());
            m.put("tagNumber", tag);
            m.put("category", category);
            m.put("expectedDueDate", dueDate);
            m.put("pregnancyNumber", (int) completed + 1);
            return m;
        }).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> lookupAnimal(UUID animalId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expectedDueDate", null);
        result.put("pregnancyNumber", null);
        if (animalId == null) return result;

        List<Object[]> agg = breedingRepository.findDueAndCompletedForAnimals(List.of(animalId));
        if (agg.isEmpty()) {
            result.put("pregnancyNumber", 1);
            return result;
        }
        Object[] a = agg.get(0);
        if (a[1] != null) result.put("expectedDueDate", a[1].toString());
        long completed = a[2] == null ? 0 : ((Number) a[2]).longValue();
        result.put("pregnancyNumber", (int) completed + 1);
        return result;
    }

    // ── CREATE ──────────────────────────────────────────────────────────────

    @Transactional
    public LivestockAbortion addNew(LivestockAbortion abortion) {
        if (abortion.getCreatedAt() == null) {
            abortion.setCreatedAt(LocalDateTime.now());
        }

        resolveAndSetLivestock(abortion);
        autoFillDerivedFields(abortion);

        LivestockAbortion saved = abortionRepository.save(abortion);

        // Clear pregnancy state on animal AND breeding record
        clearPregnancyState(saved);

        auditLogService.log(
                "livestock_abortion",
                saved.getId(),
                "CREATE",
                getCurrentUsername(),
                null,
                buildSnapshot(saved),
                "New abortion record created; pregnancy state cleared on animal and breeding record"
        );

        try {
            abortionEmailService.sendAbortionRecordedNotification(saved);
        } catch (Exception e) {
            System.err.println("⚠️ Abortion email failed: " + e.getMessage());
        }

        return saved;
    }

    // ── DELETE ──────────────────────────────────────────────────────────────

    /**
     * Soft-deletes the abortion record AND restores the animal's pregnancy state.
     *
     * Why? Because recording an abortion was likely accidental. Deleting it
     * should act as an "undo" — the animal goes back to PREGNANT, and the
     * breeding record is reopened, so the animal reappears in Pregnancy Tracking.
     */
    @Transactional
    public void delete(UUID id) {
        abortionRepository.findById(id).ifPresent(existing -> {
            String oldSnapshot = buildSnapshot(existing);

            // 1. Soft-delete the abortion record
            existing.setIsDeleted(true);
            abortionRepository.save(existing);

            // 2. Restore the animal and breeding record to "pregnant"
            restorePregnancyState(existing);

            // 3. Audit trail
            auditLogService.log("livestock_abortion", id, "SOFT_DELETE", getCurrentUsername(),
                    oldSnapshot, null, "Abortion record soft-deleted; pregnancy state restored on animal and breeding record.");
        });
    }

    /**
     * If the abortion has a linked breeding record, re-open it.
     * Set the animal's reproductive status back to PREGNANT.
     * Also set the isPregnant flag back to TRUE.
     */
    private void restorePregnancyState(LivestockAbortion abortion) {
        Livestock animal = abortion.getLivestock();
        if (animal == null) return;

        // 1. Restore animal status
        animal.setReproductiveStatus(Livestock.REPRO_PREGNANT);
        animal.setIsPregnant(Boolean.TRUE);
        // If we know the expected birth date, put it back on the animal for easy display
        if (abortion.getExpectedBirthDate() != null) {
            animal.setExpectedDueDate(abortion.getExpectedBirthDate());
        }
        livestockRepository.save(animal);

        // 2. Re-open the breeding record that was closed
        if (abortion.getBreeding() != null) {
            LivestockBreeding b = abortion.getBreeding();
            // Reopen only if it isn't already active
            if (LivestockBreeding.STATUS_FAILED.equals(b.getStatus())) {
                b.setStatus(LivestockBreeding.STATUS_CONFIRMED_PREGNANT);
                b.setPregnancyStatus("CONFIRMED");
                b.setNotes((b.getNotes() != null ? b.getNotes() + " " : "")
                        + "[Re-opened on " + LocalDate.now() + " due to abortion record deletion]");
                breedingRepository.save(b);
            }
        }
    }

    /**
     * Sets livestock.reproductive_status = OPEN and closes the active breeding
     * record (status = FAILED). Both are required — different screens read
     * one or the other.
     */
    private void clearPregnancyState(LivestockAbortion abortion) {
        Livestock animal = abortion.getLivestock();
        if (animal == null) return;

        // 1. Reset animal reproductive status.
        animal.setReproductiveStatus(Livestock.REPRO_OPEN);
        animal.setIsPregnant(Boolean.FALSE);
        animal.setExpectedDueDate(null);
        livestockRepository.save(animal);

        // 2. Close active breeding records for this animal.
        List<LivestockBreeding> active = breedingRepository.findActiveByLivestockId(
                animal.getId(),
                List.of(LivestockBreeding.STATUS_CONFIRMED_PREGNANT,
                        LivestockBreeding.STATUS_PREGNANT));

        for (LivestockBreeding b : active) {
            b.setStatus(LivestockBreeding.STATUS_FAILED);

            String reason = abortion.getAbortionReason();
            String note = "[Abortion recorded on " + abortion.getAbortionDate() + "]"
                    + (reason != null && !reason.isBlank() ? " — " + reason : "");
            b.setNotes((b.getNotes() != null ? b.getNotes() + " " : "") + note);

            breedingRepository.save(b);
        }

        // 3. Link abortion → breeding record (audit trail).
        if (!active.isEmpty() && abortion.getBreeding() == null) {
            abortion.setBreeding(active.get(0));
            abortionRepository.save(abortion);
        }
    }

    private void autoFillDerivedFields(LivestockAbortion abortion) {
        if (abortion.getLivestock() == null) return;
        UUID animalId = abortion.getLivestock().getId();
        if (animalId == null) return;

        List<Object[]> agg = breedingRepository.findDueAndCompletedForAnimals(List.of(animalId));
        LocalDate dueDate = null;
        long completed = 0;
        if (!agg.isEmpty()) {
            Object[] a = agg.get(0);
            if (a[1] != null) dueDate = (LocalDate) a[1];
            if (a[2] != null) completed = ((Number) a[2]).longValue();
        }

        if (abortion.getExpectedBirthDate() == null && dueDate != null) {
            abortion.setExpectedBirthDate(dueDate);
        }
        if (abortion.getPregnancyNumber() == null) {
            abortion.setPregnancyNumber((int) completed + 1);
        }

        if (abortion.getStageOfPregnancy() == null) {
            LocalDate refDate = abortion.getExpectedBirthDate() != null
                    ? abortion.getExpectedBirthDate()
                    : dueDate;
            LocalDate eventDate = abortion.getAbortionDate() != null
                    ? abortion.getAbortionDate()
                    : LocalDate.now();

            if (refDate == null) {
                abortion.setStageOfPregnancy("Unknown");
            } else {
                long daysAfterDue = ChronoUnit.DAYS.between(refDate, eventDate);
                if (daysAfterDue > STALE_DUE_DATE_DAYS) {
                    abortion.setStageOfPregnancy("Unknown — breeding record overdue");
                } else if (daysAfterDue >= -14) {
                    abortion.setStageOfPregnancy("Post-term");
                } else {
                    long daysBeforeDue = -daysAfterDue;
                    if (daysBeforeDue <= 30)      abortion.setStageOfPregnancy("Late");
                    else if (daysBeforeDue <= 60) abortion.setStageOfPregnancy("Mid");
                    else                          abortion.setStageOfPregnancy("Early");
                }
            }
        }
    }

    // ── UPDATE ──────────────────────────────────────────────────────────────

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
        auditLogService.log("livestock_abortion", id, "UPDATE", getCurrentUsername(),
                oldSnapshot, buildSnapshot(saved), "Abortion record updated");
        return saved;
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
            if (auth != null && auth.isAuthenticated()) return auth.getName();
        } catch (Exception ignored) {}
        return "system";
    }
}