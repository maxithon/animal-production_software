package rw.animalproduct.animal.production.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockDeath;
import rw.animalproduct.animal.production.repository.LivestockDeathRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class LivestockDeathService {

    private final LivestockDeathRepository deathRepository;
    private final LivestockRepository      livestockRepository;
    private final AuditLogService          auditLogService;
    private final LifecycleEmailService    emailService;

    private static final String ENTITY_TYPE = "livestock_death";

    public LivestockDeathService(LivestockDeathRepository deathRepository,
                                 LivestockRepository livestockRepository,
                                 AuditLogService auditLogService,
                                 LifecycleEmailService emailService) {
        this.deathRepository     = deathRepository;
        this.livestockRepository = livestockRepository;
        this.auditLogService     = auditLogService;
        this.emailService        = emailService;
    }

    // ── READ ────────────────────────────────────────────────
    public List<LivestockDeath> getAll() { return deathRepository.findAll(); }
    public Optional<LivestockDeath> getById(UUID id) { return deathRepository.findById(id); }
    public List<LivestockDeath> getByLivestock(UUID livestockId) {
        return deathRepository.findByLivestockId(livestockId);
    }

    // ── CREATE ──────────────────────────────────────────────
    @Transactional
    public LivestockDeath addNew(LivestockDeath death) {
        resolveAndSetLivestock(death);

        if (death.getLivestock() != null) {
            Livestock animal = death.getLivestock();
            if (Livestock.STATUS_DEAD.equals(animal.getStatus())) {
                throw new RuntimeException(
                        "Animal " + animal.getTagNumber() +
                                " is already marked as DEAD. Delete the existing death record first if needed.");
            }
            animal.setStatus(Livestock.STATUS_DEAD);
            livestockRepository.save(animal);
        }

        LivestockDeath saved = deathRepository.save(death);

        try {
            auditLogService.log(
                    ENTITY_TYPE, saved.getId(), "CREATE", currentUsername(),
                    null,
                    auditLogService.snapshot(saved),
                    "Death recorded for animal " + tag(saved));
        } catch (Exception e) {
            log.warn("Audit-log CREATE failed for death {}: {}", saved.getId(), e.getMessage());
        }

        try {
            emailService.sendDeathChangeNotification(
                    "CREATED", tag(saved), category(saved), dateStr(saved),
                    strOr(saved.getCauseOfDeath(), "—"), null,
                    strOr(saved.getNotes(), ""), currentUsername());
        } catch (Exception e) {
            log.warn("Death-CREATED email failed for {}: {}", tag(saved), e.getMessage());
        }

        return saved;
    }

    // ── UPDATE ──────────────────────────────────────────────
    @Transactional
    public LivestockDeath update(UUID id, LivestockDeath updated) {
        Optional<LivestockDeath> existingOpt = deathRepository.findById(id);
        if (existingOpt.isEmpty()) return null;

        LivestockDeath existing  = existingOpt.get();
        Livestock      oldAnimal = existing.getLivestock();

        String beforeSnapshot = auditLogService.snapshot(existing);
        String oldCause       = existing.getCauseOfDeath();

        existing.setDeathDate(updated.getDeathDate());
        existing.setCauseOfDeath(updated.getCauseOfDeath());
        existing.setNotes(updated.getNotes());
        existing.setLivestockIdValue(updated.getLivestockIdValue());
        resolveAndSetLivestock(existing);

        Livestock newAnimal = existing.getLivestock();

        if (oldAnimal != null && newAnimal != null
                && !oldAnimal.getId().equals(newAnimal.getId())) {
            oldAnimal.setStatus(Livestock.STATUS_ACTIVE);
            livestockRepository.save(oldAnimal);
            newAnimal.setStatus(Livestock.STATUS_DEAD);
            livestockRepository.save(newAnimal);
        } else if (newAnimal != null && !Livestock.STATUS_DEAD.equals(newAnimal.getStatus())) {
            newAnimal.setStatus(Livestock.STATUS_DEAD);
            livestockRepository.save(newAnimal);
        }

        LivestockDeath saved = deathRepository.save(existing);

        try {
            auditLogService.log(
                    ENTITY_TYPE, saved.getId(), "UPDATE", currentUsername(),
                    beforeSnapshot,
                    auditLogService.snapshot(saved),
                    "Death record updated for animal " + tag(saved));
        } catch (Exception e) {
            log.warn("Audit-log UPDATE failed for death {}: {}", saved.getId(), e.getMessage());
        }

        try {
            emailService.sendDeathChangeNotification(
                    "UPDATED", tag(saved), category(saved), dateStr(saved),
                    strOr(saved.getCauseOfDeath(), "—"),
                    strOr(oldCause, "—"),
                    strOr(saved.getNotes(), ""),
                    currentUsername());
        } catch (Exception e) {
            log.warn("Death-UPDATED email failed for {}: {}", tag(saved), e.getMessage());
        }

        return saved;
    }

    // ── DELETE ──────────────────────────────────────────────
    @Transactional
    public void delete(UUID id) {
        Optional<LivestockDeath> deathOpt = deathRepository.findById(id);
        if (deathOpt.isEmpty()) {
            deathRepository.deleteById(id);
            return;
        }

        LivestockDeath death  = deathOpt.get();
        String beforeSnapshot = auditLogService.snapshot(death);

        String tag      = tag(death);
        String category = category(death);
        String dateStr  = dateStr(death);
        String cause    = strOr(death.getCauseOfDeath(), "—");

        if (death.getLivestock() != null) {
            Livestock animal = death.getLivestock();
            if (Livestock.STATUS_DEAD.equals(animal.getStatus())) {
                animal.setStatus(Livestock.STATUS_ACTIVE);
                livestockRepository.save(animal);
            }
        }

        deathRepository.delete(death);

        try {
            auditLogService.log(
                    ENTITY_TYPE, id, "DELETE", currentUsername(),
                    beforeSnapshot,
                    null,
                    "Death record deleted for animal " + tag + " — status restored to ACTIVE");
        } catch (Exception e) {
            log.warn("Audit-log DELETE failed for death {}: {}", id, e.getMessage());
        }

        try {
            emailService.sendDeathChangeNotification(
                    "DELETED", tag, category, dateStr, cause, null, "", currentUsername());
        } catch (Exception e) {
            log.warn("Death-DELETED email failed for {}: {}", tag, e.getMessage());
        }
    }

    // ── HELPERS ─────────────────────────────────────────────
    private void resolveAndSetLivestock(LivestockDeath death) {
        String idStr = death.getLivestockIdValue();
        if (idStr != null && !idStr.isEmpty()) {
            Livestock ls = livestockRepository.findById(UUID.fromString(idStr))
                    .orElseThrow(() -> new RuntimeException("Livestock not found with ID: " + idStr));
            death.setLivestock(ls);
        }
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String name = auth.getName();
            if (name != null && !"anonymousUser".equals(name)) return name;
        }
        return "system";
    }

    private static String tag(LivestockDeath d) {
        return d.getLivestock() != null && d.getLivestock().getTagNumber() != null
                ? d.getLivestock().getTagNumber() : "—";
    }
    private static String category(LivestockDeath d) {
        return d.getLivestock() != null && d.getLivestock().getLivestockCategory() != null
                ? d.getLivestock().getLivestockCategory().getName() : "—";
    }
    private static String dateStr(LivestockDeath d) {
        return d.getDeathDate() != null ? d.getDeathDate().toString() : "—";
    }
    private static String strOr(String v, String fallback) {
        return (v != null && !v.isBlank()) ? v : fallback;
    }
}