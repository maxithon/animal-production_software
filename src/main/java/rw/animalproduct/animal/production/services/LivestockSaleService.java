package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.repository.LivestockSaleRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockSaleService {

    private final LivestockSaleRepository saleRepository;
    private final LivestockRepository livestockRepository;

    public LivestockSaleService(LivestockSaleRepository saleRepository,
                                LivestockRepository livestockRepository) {
        this.saleRepository = saleRepository;
        this.livestockRepository = livestockRepository;
    }

    // ── Read ─────────────────────────────────────────────────────────

    public List<LivestockSale> getAll() {
        return saleRepository.findAll();
    }

    public Optional<LivestockSale> getById(UUID id) {
        return saleRepository.findById(id);
    }

    public List<LivestockSale> getByLivestock(UUID livestockId) {
        return saleRepository.findByLivestockId(livestockId);
    }

    // ── Create ───────────────────────────────────────────────────────

    /**
     * Record a new sale AND automatically mark the animal as SOLD.
     *
     * Flow:
     *  1. Resolve the livestock from the form's livestockIdValue string.
     *  2. Save the sale record.
     *  3. Set livestock.status = "SOLD" so the animal no longer appears
     *     in "available / on farm" lists.
     */
    @Transactional
    public LivestockSale addNew(LivestockSale sale) {
        resolveAndSetLivestock(sale);

        // ── Mark the animal as SOLD ───────────────────────────────────
        if (sale.getLivestock() != null) {
            Livestock animal = sale.getLivestock();

            // Guard: warn if trying to sell an already-sold animal
            if (Livestock.STATUS_SOLD.equals(animal.getStatus())) {
                throw new RuntimeException(
                        "Animal " + animal.getTagNumber() + " is already marked as SOLD. " +
                                "Delete the existing sale record first if you need to re-record this sale."
                );
            }

            animal.setStatus(Livestock.STATUS_SOLD);
            livestockRepository.save(animal);
        }
        // ─────────────────────────────────────────────────────────────

        return saleRepository.save(sale);
    }

    // ── Update ───────────────────────────────────────────────────────

    /**
     * Update a sale record.
     * If the livestock changes (unusual but possible), the old animal is
     * restored to ACTIVE and the new animal is marked SOLD.
     */
    @Transactional
    public LivestockSale update(UUID id, LivestockSale updated) {
        Optional<LivestockSale> existingOpt = saleRepository.findById(id);
        if (existingOpt.isEmpty()) return null;

        LivestockSale existing = existingOpt.get();

        // Remember the old livestock before we overwrite it
        Livestock oldAnimal = existing.getLivestock();

        // Apply field updates
        existing.setSaleReason(updated.getSaleReason());
        existing.setSalePrice(updated.getSalePrice());
        existing.setSaleDate(updated.getSaleDate());
        existing.setSaleLocation(updated.getSaleLocation());
        existing.setLivestockIdValue(updated.getLivestockIdValue());
        resolveAndSetLivestock(existing);

        Livestock newAnimal = existing.getLivestock();

        // If the animal changed, update statuses on both
        if (oldAnimal != null && newAnimal != null &&
                !oldAnimal.getId().equals(newAnimal.getId())) {

            // Restore old animal to ACTIVE (its sale is being reassigned)
            oldAnimal.setStatus(Livestock.STATUS_ACTIVE);
            livestockRepository.save(oldAnimal);

            // Mark new animal as SOLD
            newAnimal.setStatus(Livestock.STATUS_SOLD);
            livestockRepository.save(newAnimal);

        } else if (newAnimal != null && !Livestock.STATUS_SOLD.equals(newAnimal.getStatus())) {
            // Same animal but status drifted — re-apply SOLD
            newAnimal.setStatus(Livestock.STATUS_SOLD);
            livestockRepository.save(newAnimal);
        }

        return saleRepository.save(existing);
    }

    // ── Delete ───────────────────────────────────────────────────────

    /**
     * Delete a sale record AND restore the animal's status to ACTIVE.
     *
     * This handles the case where a sale was recorded by mistake — deleting
     * the sale puts the animal back on the farm.
     */
    @Transactional
    public void delete(UUID id) {
        Optional<LivestockSale> saleOpt = saleRepository.findById(id);

        saleOpt.ifPresent(sale -> {
            // ── Restore animal status to ACTIVE ──────────────────────
            if (sale.getLivestock() != null) {
                Livestock animal = sale.getLivestock();

                // Only restore if currently SOLD (don't override DEAD, etc.)
                if (Livestock.STATUS_SOLD.equals(animal.getStatus())) {
                    animal.setStatus(Livestock.STATUS_ACTIVE);
                    livestockRepository.save(animal);
                }
            }
            // ─────────────────────────────────────────────────────────

            saleRepository.delete(sale);
        });

        // Fallback: if sale wasn't found, just try to delete by id silently
        if (saleOpt.isEmpty()) {
            saleRepository.deleteById(id);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void resolveAndSetLivestock(LivestockSale sale) {
        String idStr = sale.getLivestockIdValue();
        if (idStr != null && !idStr.isEmpty()) {
            Livestock ls = livestockRepository.findById(UUID.fromString(idStr))
                    .orElseThrow(() -> new RuntimeException(
                            "Livestock not found with ID: " + idStr));
            sale.setLivestock(ls);
        }
    }
}
