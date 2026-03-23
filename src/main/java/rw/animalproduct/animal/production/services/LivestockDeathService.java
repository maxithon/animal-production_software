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

    public LivestockDeathService(LivestockDeathRepository deathRepository,
                                 LivestockRepository livestockRepository) {
        this.deathRepository     = deathRepository;
        this.livestockRepository = livestockRepository;
    }

    // ── Read ─────────────────────────────────────────────────────────

    public List<LivestockDeath> getAll() { return deathRepository.findAll(); }

    public Optional<LivestockDeath> getById(UUID id) { return deathRepository.findById(id); }

    public List<LivestockDeath> getByLivestock(UUID livestockId) {
        return deathRepository.findByLivestockId(livestockId);
    }

    // ── Create ───────────────────────────────────────────────────────

    /**
     * Record a death AND automatically mark the animal as DEAD.
     *
     * Flow:
     *  1. Resolve the livestock from the form's livestockIdValue.
     *  2. Save the death record.
     *  3. Set livestock.status = "DEAD" so the animal is clearly
     *     marked as deceased everywhere in the system.
     */
    @Transactional
    public LivestockDeath addNew(LivestockDeath death) {
        resolveAndSetLivestock(death);

        // ── Mark the animal as DEAD ───────────────────────────────────
        if (death.getLivestock() != null) {
            Livestock animal = death.getLivestock();

            // Guard: warn if already dead
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
        // ─────────────────────────────────────────────────────────────

        return deathRepository.save(death);
    }

    // ── Update ───────────────────────────────────────────────────────

    /**
     * Update a death record (dates / cause).
     * If the livestock changes, old animal is restored to ACTIVE
     * and new animal is marked DEAD.
     */
    @Transactional
    public LivestockDeath update(UUID id, LivestockDeath updated) {
        Optional<LivestockDeath> existingOpt = deathRepository.findById(id);
        if (existingOpt.isEmpty()) return null;

        LivestockDeath existing   = existingOpt.get();
        Livestock      oldAnimal  = existing.getLivestock();

        existing.setDeathDate(updated.getDeathDate());
        existing.setCauseOfDeath(updated.getCauseOfDeath());
        existing.setLivestockIdValue(updated.getLivestockIdValue());
        resolveAndSetLivestock(existing);

        Livestock newAnimal = existing.getLivestock();

        // If the animal on the record changed, update both statuses
        if (oldAnimal != null && newAnimal != null &&
                !oldAnimal.getId().equals(newAnimal.getId())) {

            // Restore old animal to ACTIVE
            oldAnimal.setStatus(Livestock.STATUS_ACTIVE);
            livestockRepository.save(oldAnimal);

            // Mark new animal as DEAD
            newAnimal.setStatus(Livestock.STATUS_DEAD);
            livestockRepository.save(newAnimal);

        } else if (newAnimal != null && !Livestock.STATUS_DEAD.equals(newAnimal.getStatus())) {
            // Same animal but status drifted — re-apply DEAD
            newAnimal.setStatus(Livestock.STATUS_DEAD);
            livestockRepository.save(newAnimal);
        }

        return deathRepository.save(existing);
    }

    // ── Delete ───────────────────────────────────────────────────────

    /**
     * Delete a death record AND restore the animal's status to ACTIVE.
     * This handles the case where a death was recorded by mistake.
     */
    @Transactional
    public void delete(UUID id) {
        Optional<LivestockDeath> deathOpt = deathRepository.findById(id);

        deathOpt.ifPresent(death -> {
            // ── Restore animal status to ACTIVE ──────────────────────
            if (death.getLivestock() != null) {
                Livestock animal = death.getLivestock();
                if (Livestock.STATUS_DEAD.equals(animal.getStatus())) {
                    animal.setStatus(Livestock.STATUS_ACTIVE);
                    livestockRepository.save(animal);
                }
            }
            // ─────────────────────────────────────────────────────────
            deathRepository.delete(death);
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
