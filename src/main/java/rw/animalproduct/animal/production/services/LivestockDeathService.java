package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockDeath;
import rw.animalproduct.animal.production.repository.LivestockDeathRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    // ── Read ─────────────────────────────────────────────────────────

    public List<LivestockDeath> getAll() { return deathRepository.findAll(); }

    public Optional<LivestockDeath> getById(UUID id) { return deathRepository.findById(id); }

    public List<LivestockDeath> getByLivestock(UUID livestockId) {
        return deathRepository.findByLivestockId(livestockId);
    }

    // ── Create ───────────────────────────────────────────────────────

    @Transactional
    public LivestockDeath addNew(LivestockDeath death) {
        resolveAndSetLivestock(death);

        if (death.getLivestock() != null) {
            Livestock animal = death.getLivestock();

            if (Livestock.STATUS_DEAD.equals(animal.getStatus())) {
                throw new RuntimeException(
                        "Animal " + animal.getTagNumber() +
                                " is already marked as DEAD. " +
                                "Delete the existing death record first if needed."
                );
            }

            animal.setStatus(Livestock.STATUS_DEAD);
            livestockRepository.save(animal);
        }

        LivestockDeath saved = deathRepository.save(death);

        // ── Audit log ──────────────────────────────────────────────
        auditLogService.log(
                ENTITY_TYPE,
                saved.getId(),
                "CREATE",
                null, // TODO: pass current username from SecurityContext if available
                null,
                auditLogService.snapshot(saved),
                "Death recorded for animal " +
                        (saved.getLivestock() != null ? saved.getLivestock().getTagNumber() : "unknown")
        );

        // ── Email notification ────────────────────────────────────
        try {
            emailService.sendDeathNotification(saved);
        } catch (Exception e) {
            // Never let an email failure roll back the death record
            System.err.println("⚠️ Failed to send death notification email: " + e.getMessage());
        }

        return saved;
    }

    // ── Update ───────────────────────────────────────────────────────

    @Transactional
    public LivestockDeath update(UUID id, LivestockDeath updated) {
        Optional<LivestockDeath> existingOpt = deathRepository.findById(id);
        if (existingOpt.isEmpty()) return null;

        LivestockDeath existing   = existingOpt.get();
        Livestock      oldAnimal  = existing.getLivestock();

        // Snapshot BEFORE mutating (see AuditLogService.snapshot javadoc)
        String beforeSnapshot = auditLogService.snapshot(existing);

        existing.setDeathDate(updated.getDeathDate());
        existing.setCauseOfDeath(updated.getCauseOfDeath());
        existing.setLivestockIdValue(updated.getLivestockIdValue());
        resolveAndSetLivestock(existing);

        Livestock newAnimal = existing.getLivestock();

        if (oldAnimal != null && newAnimal != null &&
                !oldAnimal.getId().equals(newAnimal.getId())) {

            oldAnimal.setStatus(Livestock.STATUS_ACTIVE);
            livestockRepository.save(oldAnimal);

            newAnimal.setStatus(Livestock.STATUS_DEAD);
            livestockRepository.save(newAnimal);

        } else if (newAnimal != null && !Livestock.STATUS_DEAD.equals(newAnimal.getStatus())) {
            newAnimal.setStatus(Livestock.STATUS_DEAD);
            livestockRepository.save(newAnimal);
        }

        LivestockDeath saved = deathRepository.save(existing);

        auditLogService.log(
                ENTITY_TYPE,
                saved.getId(),
                "UPDATE",
                null, // TODO: current username
                beforeSnapshot,
                auditLogService.snapshot(saved),
                "Death record updated for animal " +
                        (saved.getLivestock() != null ? saved.getLivestock().getTagNumber() : "unknown")
        );

        return saved;
    }

    // ── Delete ───────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        Optional<LivestockDeath> deathOpt = deathRepository.findById(id);

        deathOpt.ifPresent(death -> {
            String beforeSnapshot = auditLogService.snapshot(death);
            String tag = death.getLivestock() != null ? death.getLivestock().getTagNumber() : "unknown";

            if (death.getLivestock() != null) {
                Livestock animal = death.getLivestock();
                if (Livestock.STATUS_DEAD.equals(animal.getStatus())) {
                    animal.setStatus(Livestock.STATUS_ACTIVE);
                    livestockRepository.save(animal);
                }
            }

            deathRepository.delete(death);

            auditLogService.log(
                    ENTITY_TYPE,
                    id,
                    "DELETE",
                    null, // TODO: current username
                    beforeSnapshot,
                    null,
                    "Death record deleted for animal " + tag + " — status restored to ACTIVE"
            );
        });

        if (deathOpt.isEmpty()) {
            deathRepository.deleteById(id);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void resolveAndSetLivestock(LivestockDeath death) {
        String idStr = death.getLivestockIdValue();
        if (idStr != null && !idStr.isEmpty()) {
            Livestock ls = livestockRepository.findById(UUID.fromString(idStr))
                    .orElseThrow(() -> new RuntimeException(
                            "Livestock not found with ID: " + idStr));
            death.setLivestock(ls);
        }
    }
}