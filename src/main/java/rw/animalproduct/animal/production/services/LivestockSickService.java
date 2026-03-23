package rw.animalproduct.animal.production.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockSick;
import rw.animalproduct.animal.production.entity.LivestockSickHistory;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSickHistoryRepository;
import rw.animalproduct.animal.production.repository.LivestockSickRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockSickService {

    private final LivestockSickRepository        sickRepository;
    private final LivestockRepository            livestockRepository;
    private final LivestockSickHistoryRepository historyRepository;

    private static final String STATUS_SOLD = "SOLD";

    public LivestockSickService(LivestockSickRepository sickRepository,
                                LivestockRepository livestockRepository,
                                LivestockSickHistoryRepository historyRepository) {
        this.sickRepository      = sickRepository;
        this.livestockRepository = livestockRepository;
        this.historyRepository   = historyRepository;
    }

    // ── Read ──────────────────────────────────────────────────────────

    public List<LivestockSick> getAll() { return sickRepository.findAll(); }

    public Optional<LivestockSick> getById(UUID id) { return sickRepository.findById(id); }

    public List<LivestockSick> getByLivestock(UUID livestockId) {
        return sickRepository.findByLivestockId(livestockId);
    }

    /** Full timeline for one sick episode (oldest first). */
    public List<LivestockSickHistory> getHistory(UUID sickId) {
        return historyRepository.findByLivestockSickIdOrderByChangedAtAsc(sickId);
    }

    /** All health history for one livestock animal across all episodes. */
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

        // ✅ Write the first history entry automatically
        recordHistory(saved,
                saved.getStatus(),
                saved.getSeverityLevel(),
                "Initial sick record created",
                currentUsername());

        return saved;
    }

    // ── Update ────────────────────────────────────────────────────────

    @Transactional
    public LivestockSick update(UUID id, LivestockSick updated) {
        Optional<LivestockSick> existingOpt = sickRepository.findById(id);
        if (existingOpt.isEmpty()) return null;

        LivestockSick existing = existingOpt.get();

        // Capture old values BEFORE overwriting
        LivestockSick.SickStatus    oldStatus   = existing.getStatus();
        LivestockSick.SeverityLevel oldSeverity = existing.getSeverityLevel();
        Livestock                   oldAnimal   = existing.getLivestock();

        // Apply all field updates
        existing.setReportedDate(updated.getReportedDate());
        existing.setSymptoms(updated.getSymptoms());
        existing.setDiagnosis(updated.getDiagnosis());
        existing.setTreatmentNotes(updated.getTreatmentNotes());
        existing.setVetName(updated.getVetName());
        existing.setTemperature(updated.getTemperature());
        existing.setSeverityLevel(updated.getSeverityLevel());
        existing.setTreatmentCost(updated.getTreatmentCost());
        existing.setStatus(updated.getStatus());
        existing.setRecoveryDate(updated.getRecoveryDate());
        existing.setLivestockIdValue(updated.getLivestockIdValue());
        resolveAndSetLivestock(existing);

        Livestock newAnimal = existing.getLivestock();

        // Sync livestock status
        if (newAnimal != null) {
            if (LivestockSick.SickStatus.RECOVERED.equals(existing.getStatus())) {
                newAnimal.setStatus(Livestock.STATUS_ACTIVE);
            } else if (oldAnimal != null && !oldAnimal.getId().equals(newAnimal.getId())) {
                // Animal changed — restore old one, mark new one sick
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

        // ✅ Write history only when status OR severity actually changed
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

        return saved;
    }

    // ── Quick status update (for the quick-action buttons in the UI) ──

    @Transactional
    public LivestockSick quickStatusUpdate(UUID id,
                                           LivestockSick.SickStatus newStatus,
                                           String notes) {
        LivestockSick sick = sickRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sick record not found: " + id));

        LivestockSick.SickStatus    oldStatus   = sick.getStatus();
        LivestockSick.SeverityLevel oldSeverity = sick.getSeverityLevel();

        if (oldStatus.equals(newStatus)) return sick; // no change

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

        // ✅ Always record history for quick updates
        String changeNote = notes != null && !notes.isBlank()
                ? notes
                : buildChangeNote(oldStatus, newStatus, oldSeverity, oldSeverity, null);

        recordHistory(saved, newStatus, saved.getSeverityLevel(),
                changeNote, currentUsername());

        return saved;
    }

    // ── Delete ────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        Optional<LivestockSick> sickOpt = sickRepository.findById(id);

        sickOpt.ifPresent(sick -> {
            // History rows are deleted automatically by cascade (CascadeType.ALL on the entity)
            if (sick.getLivestock() != null) {
                Livestock animal = sick.getLivestock();
                if (Livestock.STATUS_SICK.equals(animal.getStatus())) {
                    animal.setStatus(Livestock.STATUS_ACTIVE);
                    livestockRepository.save(animal);
                }
            }
            sickRepository.delete(sick);
        });

        if (sickOpt.isEmpty()) {
            sickRepository.deleteById(id);
        }
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

    /** Writes one immutable row to livestock_sick_history. */
    private void recordHistory(LivestockSick sick,
                               LivestockSick.SickStatus status,
                               LivestockSick.SeverityLevel severity,
                               String notes,
                               String changedBy) {
        LivestockSickHistory h = new LivestockSickHistory(sick, status, severity, changedBy, notes);
        historyRepository.save(h);
    }

    /** Reads the currently authenticated username from Spring Security. */
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

    /** Builds a human-readable description of what changed. */
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
