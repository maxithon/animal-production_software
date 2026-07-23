package rw.animalproduct.animal.production.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockSick;
import rw.animalproduct.animal.production.entity.LivestockSickHistory;
import rw.animalproduct.animal.production.entity.Veterinarian;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSickHistoryRepository;
import rw.animalproduct.animal.production.repository.LivestockSickRepository;
import rw.animalproduct.animal.production.repository.VeterinarianRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockSickService {

    private final LivestockSickRepository        sickRepository;
    private final LivestockRepository            livestockRepository;
    private final LivestockSickHistoryRepository historyRepository;
    private final VeterinarianRepository         veterinarianRepository;
    private final AuditLogService                auditLogService;
    private final SickAnimalNotificationService   notificationService;

    private static final String STATUS_SOLD      = "SOLD";
    private static final String ENTITY_TYPE      = "livestock_sick";

    public LivestockSickService(LivestockSickRepository sickRepository,
                                LivestockRepository livestockRepository,
                                LivestockSickHistoryRepository historyRepository,
                                VeterinarianRepository veterinarianRepository,
                                AuditLogService auditLogService,
                                SickAnimalNotificationService notificationService) {
        this.sickRepository         = sickRepository;
        this.livestockRepository    = livestockRepository;
        this.historyRepository      = historyRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.auditLogService        = auditLogService;
        this.notificationService    = notificationService;
    }

    // ── Read ──────────────────────────────────────────────────────────

    public List<LivestockSick> getAll() { return sickRepository.findAll(); }

    public List<LivestockSick> getAllActive() {
        return sickRepository.findByIsDeletedFalseOrderByReportedDateDesc();
    }

    public Optional<LivestockSick> getById(UUID id) { return sickRepository.findById(id); }

    public List<LivestockSick> getByLivestock(UUID livestockId) {
        return sickRepository.findByLivestockId(livestockId);
    }

    public List<LivestockSickHistory> getHistory(UUID sickId) {
        return historyRepository.findByLivestockSickIdOrderByChangedAtAsc(sickId);
    }

    public List<LivestockSickHistory> getHistoryByAnimal(UUID livestockId) {
        return historyRepository.findByLivestockId(livestockId);
    }

    // ── Date-range report helpers ─────────────────────────────────────

    public List<LivestockSickHistory> getHistoryInRange(LocalDateTime from, LocalDateTime to) {
        return historyRepository.findByDateRange(from, to);
    }

    public List<LivestockSickHistory> getSickCasesInRange(LocalDateTime from, LocalDateTime to) {
        return historyRepository.findNewSickCasesByDateRange(from, to);
    }

    public List<LivestockSickHistory> getCriticalCasesInRange(LocalDateTime from, LocalDateTime to) {
        return historyRepository.findCriticalCasesByDateRange(from, to);
    }

    public List<LivestockSickHistory> getRecoveredCasesInRange(LocalDateTime from, LocalDateTime to) {
        return historyRepository.findRecoveredCasesByDateRange(from, to);
    }

    public long countCriticalByYear(int year)  { return historyRepository.countCriticalByYear(year); }
    public long countRecoveredByYear(int year) { return historyRepository.countRecoveredByYear(year); }
    public long countSickByYear(int year)      { return historyRepository.countSickByYear(year); }

    // ── Create ────────────────────────────────────────────────────────

    @Transactional
    public LivestockSick addNew(LivestockSick sick) {
        resolveAndSetLivestock(sick);
        resolveAndSetVeterinarian(sick);

        if (sick.getLivestock() != null) {
            Livestock animal = sick.getLivestock();

            if (Livestock.STATUS_DEAD.equals(animal.getStatus())) {
                throw new RuntimeException(
                        "Animal " + animal.getTagNumber() +
                                " is already marked as DEAD. Cannot record a sick event for a deceased animal.");
            }
            if (STATUS_SOLD.equals(animal.getStatus())) {
                throw new RuntimeException(
                        "Animal " + animal.getTagNumber() +
                                " has already been SOLD. Cannot record a sick event for a sold animal.");
            }

            animal.setStatus(Livestock.STATUS_SICK);
            livestockRepository.save(animal);
        }

        LivestockSick saved = sickRepository.save(sick);

        recordHistory(saved,
                saved.getStatus(),
                saved.getSeverityLevel(),
                "Initial sick record created",
                currentUsername());

        auditLogService.log(
                ENTITY_TYPE,
                saved.getId(),
                "CREATE",
                currentUsername(),
                null,
                buildSnapshot(saved),
                "Sick record created for animal: " +
                        (saved.getLivestock() != null ? saved.getLivestock().getTagNumber() : "unknown")
        );

        // Fire the notification email. This is @Async on the notification
        // service, so it does NOT block this request/response.
        notificationService.sendSickRecordedEmail(saved);

        return saved;
    }

    // ── Update ────────────────────────────────────────────────────────

    @Transactional
    public LivestockSick update(UUID id, LivestockSick updated) {
        Optional<LivestockSick> existingOpt = sickRepository.findById(id);
        if (existingOpt.isEmpty()) return null;

        LivestockSick existing = existingOpt.get();

        String snapshotBefore = buildSnapshot(existing);

        LivestockSick.SickStatus    oldStatus   = existing.getStatus();
        LivestockSick.SeverityLevel oldSeverity = existing.getSeverityLevel();
        Livestock                   oldAnimal   = existing.getLivestock();

        existing.setReportedDate(updated.getReportedDate());
        existing.setSymptoms(updated.getSymptoms());
        existing.setDiagnosis(updated.getDiagnosis());
        existing.setTreatmentNotes(updated.getTreatmentNotes());
        existing.setTemperature(updated.getTemperature());
        existing.setSeverityLevel(updated.getSeverityLevel());
        existing.setStatus(updated.getStatus());
        existing.setRecoveryDate(updated.getRecoveryDate());
        existing.setLivestockIdValue(updated.getLivestockIdValue());
        existing.setVeterinarianIdValue(updated.getVeterinarianIdValue());

        resolveAndSetVeterinarian(existing);
        resolveAndSetLivestock(existing);

        Livestock newAnimal = existing.getLivestock();

        if (newAnimal != null) {
            if (LivestockSick.SickStatus.RECOVERED.equals(existing.getStatus())) {
                newAnimal.setStatus(Livestock.STATUS_ACTIVE);
            } else if (oldAnimal != null && !oldAnimal.getId().equals(newAnimal.getId())) {
                oldAnimal.setStatus(Livestock.STATUS_ACTIVE);
                livestockRepository.save(oldAnimal);
                newAnimal.setStatus(Livestock.STATUS_SICK);
            } else {
                if (!Livestock.STATUS_DEAD.equals(newAnimal.getStatus())
                        && !STATUS_SOLD.equals(newAnimal.getStatus())) {
                    newAnimal.setStatus(Livestock.STATUS_SICK);
                }
            }
            livestockRepository.save(newAnimal);
        }

        LivestockSick saved = sickRepository.save(existing);

        boolean statusChanged   = !oldStatus.equals(saved.getStatus());
        boolean severityChanged = (oldSeverity == null && saved.getSeverityLevel() != null)
                || (oldSeverity != null && !oldSeverity.equals(saved.getSeverityLevel()));

        if (statusChanged || severityChanged) {
            String notes = buildChangeNote(oldStatus, saved.getStatus(),
                    oldSeverity, saved.getSeverityLevel(),
                    saved.getTreatmentNotes());
            recordHistory(saved, saved.getStatus(), saved.getSeverityLevel(),
                    notes, currentUsername());
        }

        auditLogService.log(
                ENTITY_TYPE,
                saved.getId(),
                "UPDATE",
                currentUsername(),
                snapshotBefore,
                buildSnapshot(saved),
                "Sick record updated for animal: " +
                        (saved.getLivestock() != null ? saved.getLivestock().getTagNumber() : "unknown")
        );

        return saved;
    }

    // ── Quick status update ─────────────────────────────────────────

    @Transactional
    public LivestockSick quickStatusUpdate(UUID id,
                                           LivestockSick.SickStatus newStatus,
                                           String notes) {
        LivestockSick sick = sickRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sick record not found: " + id));

        LivestockSick.SickStatus    oldStatus   = sick.getStatus();
        LivestockSick.SeverityLevel oldSeverity = sick.getSeverityLevel();

        if (oldStatus.equals(newStatus)) return sick;

        String snapshotBefore = buildSnapshot(sick);

        sick.setStatus(newStatus);

        if (LivestockSick.SickStatus.RECOVERED.equals(newStatus)) {
            if (sick.getRecoveryDate() == null) {
                sick.setRecoveryDate(java.time.LocalDate.now());
            }
            if (sick.getLivestock() != null) {
                sick.getLivestock().setStatus(Livestock.STATUS_ACTIVE);
                livestockRepository.save(sick.getLivestock());
            }
        }

        LivestockSick saved = sickRepository.save(sick);

        String changeNote = notes != null && !notes.isBlank()
                ? notes
                : buildChangeNote(oldStatus, newStatus, oldSeverity, oldSeverity, null);

        recordHistory(saved, newStatus, saved.getSeverityLevel(),
                changeNote, currentUsername());

        auditLogService.log(
                ENTITY_TYPE,
                saved.getId(),
                "UPDATE",
                currentUsername(),
                snapshotBefore,
                buildSnapshot(saved),
                "Quick status update: " + oldStatus.name() + " → " + newStatus.name() +
                        (notes != null && !notes.isBlank() ? " | " + notes : "")
        );

        return saved;
    }

    // ── Mark as recovered ────────────────────────────────────────────

    /**
     * ✅ ADDED — this is what LivestockEventsController.markAsRecovered(id)
     * calls. It didn't exist before, which is why the build failed. Reuses
     * quickStatusUpdate() so recovery gets the same recovery-date default,
     * livestock status sync back to ACTIVE, history entry, and audit log
     * write as every other status transition — no logic duplicated.
     */
    @Transactional
    public LivestockSick markAsRecovered(UUID id) {
        return quickStatusUpdate(id, LivestockSick.SickStatus.RECOVERED, "Marked as recovered");
    }

    // ── Delete (soft-delete) ────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        Optional<LivestockSick> sickOpt = sickRepository.findById(id);

        if (sickOpt.isEmpty()) return;

        LivestockSick sick = sickOpt.get();

        String snapshotBefore = buildSnapshot(sick);

        if (sick.getLivestock() != null) {
            Livestock animal = sick.getLivestock();
            if (Livestock.STATUS_SICK.equals(animal.getStatus())) {
                animal.setStatus(Livestock.STATUS_ACTIVE);
                livestockRepository.save(animal);
            }
        }

        sick.setIsDeleted(true);
        sickRepository.save(sick);

        auditLogService.log(
                ENTITY_TYPE,
                sick.getId(),
                "SOFT_DELETE",
                currentUsername(),
                snapshotBefore,
                null,
                "Sick record soft-deleted for animal: " +
                        (sick.getLivestock() != null ? sick.getLivestock().getTagNumber() : "unknown")
        );
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void resolveAndSetLivestock(LivestockSick sick) {
        String idStr = sick.getLivestockIdValue();
        if (idStr != null && !idStr.isBlank()) {
            Livestock ls = livestockRepository.findById(UUID.fromString(idStr))
                    .orElseThrow(() -> new RuntimeException("Livestock not found: " + idStr));
            sick.setLivestock(ls);
        }
    }

    private void resolveAndSetVeterinarian(LivestockSick sick) {
        String vetIdStr = sick.getVeterinarianIdValue();
        if (vetIdStr != null && !vetIdStr.isBlank()) {
            Veterinarian vet = veterinarianRepository.findById(UUID.fromString(vetIdStr))
                    .orElseThrow(() -> new RuntimeException("Veterinarian not found: " + vetIdStr));
            sick.setVeterinarian(vet);
        }
    }

    private void recordHistory(LivestockSick sick,
                               LivestockSick.SickStatus status,
                               LivestockSick.SeverityLevel severity,
                               String notes,
                               String changedBy) {
        LivestockSickHistory h = new LivestockSickHistory(sick, status, severity, changedBy, notes);
        historyRepository.save(h);
    }

    private String buildSnapshot(LivestockSick sick) {
        StringBuilder sb = new StringBuilder();
        sb.append("animal=").append(sick.getLivestock() != null ? sick.getLivestock().getTagNumber() : "null");
        sb.append(", status=").append(sick.getStatus() != null ? sick.getStatus().name() : "null");
        sb.append(", severity=").append(sick.getSeverityLevel() != null ? sick.getSeverityLevel().name() : "null");
        sb.append(", reported=").append(sick.getReportedDate());
        sb.append(", recovery=").append(sick.getRecoveryDate());
        sb.append(", temp=").append(sick.getTemperature());
        sb.append(", symptoms=").append(sick.getSymptoms());
        sb.append(", diagnosis=").append(sick.getDiagnosis());
        sb.append(", vet=").append(sick.getVeterinarian() != null
                ? sick.getVeterinarian().getFirstName() + " " + sick.getVeterinarian().getLastName()
                : "null");
        sb.append(", isDeleted=").append(sick.getIsDeleted());
        return sb.toString();
    }

    private String currentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception ignored) {}
        return "system";
    }

    private String buildChangeNote(LivestockSick.SickStatus    oldStatus,
                                   LivestockSick.SickStatus    newStatus,
                                   LivestockSick.SeverityLevel oldSeverity,
                                   LivestockSick.SeverityLevel newSeverity,
                                   String treatmentNotes) {
        StringBuilder sb = new StringBuilder();

        if (!oldStatus.equals(newStatus)) {
            sb.append("Status changed: ")
                    .append(oldStatus.name())
                    .append(" → ")
                    .append(newStatus.name());
        }

        boolean severityChanged = (oldSeverity == null && newSeverity != null)
                || (oldSeverity != null && !oldSeverity.equals(newSeverity));
        if (severityChanged) {
            if (sb.length() > 0) sb.append(". ");
            sb.append("Severity: ")
                    .append(oldSeverity != null ? oldSeverity.name() : "none")
                    .append(" → ")
                    .append(newSeverity != null ? newSeverity.name() : "none");
        }

        if (treatmentNotes != null && !treatmentNotes.isBlank()) {
            if (sb.length() > 0) sb.append(". ");
            sb.append("Notes: ").append(treatmentNotes);
        }

        return sb.length() > 0 ? sb.toString() : "Record updated";
    }
}
