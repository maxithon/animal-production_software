package rw.animalproduct.animal.production.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBirth;
import rw.animalproduct.animal.production.entity.LivestockOffspring;
import rw.animalproduct.animal.production.repository.LivestockBirthRepository;
import rw.animalproduct.animal.production.repository.LivestockOffspringRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockBirthService {

    private final LivestockBirthRepository birthRepository;
    private final LivestockRepository livestockRepository;
    private final LivestockOffspringRepository offspringRepository;

    public LivestockBirthService(LivestockBirthRepository birthRepository,
                                 LivestockRepository livestockRepository,
                                 LivestockOffspringRepository offspringRepository) {
        this.birthRepository = birthRepository;
        this.livestockRepository = livestockRepository;
        this.offspringRepository = offspringRepository;
    }

    // ── CRUD ─────────────────────────────────────────────────────────

    public List<LivestockBirth> getAll() {
        return birthRepository.findAll();
    }

    public Page<LivestockBirth> getPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "birthDate");
        return birthRepository.findAll(pageable);
    }

    public Optional<LivestockBirth> getById(UUID id) {
        return birthRepository.findById(id);
    }

    public List<LivestockBirth> getByLivestockId(UUID livestockId) {
        return birthRepository.findByLivestockId(livestockId);
    }

    @Transactional
    public LivestockBirth addNew(LivestockBirth birth) {
        resolveAndSetLivestock(birth);

        // Update mother's last_birth_date and increment offspring_count
        if (birth.getLivestock() != null) {
            Livestock mother = birth.getLivestock();
            mother.setLastBirthDate(birth.getBirthDate());
            mother.setIsPregnant(false);  // no longer pregnant after birth
            if (birth.getOffspringCount() != null) {
                int current = mother.getOffspringCount() == null ? 0 : mother.getOffspringCount();
                mother.setOffspringCount(current + birth.getOffspringCount());
            }
            livestockRepository.save(mother);
        }

        return birthRepository.save(birth);
    }

    @Transactional
    public LivestockBirth update(UUID id, LivestockBirth updated) {
        Optional<LivestockBirth> existingOpt = birthRepository.findById(id);
        if (existingOpt.isPresent()) {
            LivestockBirth existing = existingOpt.get();
            existing.setBirthDate(updated.getBirthDate());
            existing.setOffspringCount(updated.getOffspringCount());
            existing.setOffspringGender(updated.getOffspringGender());
            existing.setWeaningDate(updated.getWeaningDate());
            existing.setNextBreedingDate(updated.getNextBreedingDate());
            existing.setNotes(updated.getNotes());
            existing.setLivestockIdValue(updated.getLivestockIdValue());
            resolveAndSetLivestock(existing);
            return birthRepository.save(existing);
        }
        return null;
    }

    @Transactional
    public void delete(UUID id) {
        birthRepository.deleteById(id);
    }

    // ── Child Linking ─────────────────────────────────────────────────

    /**
     * Link a child animal to a birth event.
     *
     * HOW MULTI-GENERATION WORKS:
     * ─────────────────────────────────────────────────────────────────
     * 1. Register Calf B as a Livestock record (normal livestock register flow).
     * 2. Call this method: birthId = the CowA birth event, childId = CalfB.
     *    → CalfB.mother = CowA, generation = 1.
     *
     * 3. Years later CalfB gives birth to CalfE:
     *    - Record a NEW LivestockBirth with livestock = CalfB.
     *    - Register CalfE as a Livestock record.
     *    - Call this method: birthId = CalfB birth event, childId = CalfE.
     *    → CalfE.mother = CalfB, generation = 2.
     *    → CalfE's grandmother = CalfB.mother = CowA  (found automatically).
     * ─────────────────────────────────────────────────────────────────
     */
    @Transactional
    public LivestockOffspring linkChild(UUID birthId, UUID childLivestockId) {
        LivestockBirth birth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));

        Livestock child = livestockRepository.findById(childLivestockId)
                .orElseThrow(() -> new RuntimeException("Child livestock not found"));

        Livestock mother = birth.getLivestock();
        if (mother == null) {
            throw new RuntimeException("Birth record has no mother assigned");
        }

        // Set mother reference on the child animal
        child.setMother(mother);
        livestockRepository.save(child);

        // Calculate generation depth by walking up the mother chain
        int generation = calculateGeneration(mother) + 1;

        LivestockOffspring link = new LivestockOffspring(birth, child, generation);
        return offspringRepository.save(link);
    }

    /**
     * Unlink a child from its birth event (clears mother_id too).
     */
    @Transactional
    public void unlinkChild(UUID childLivestockId) {
        offspringRepository.findByChildLivestockId(childLivestockId).ifPresent(link -> {
            Livestock child = link.getChildLivestock();
            if (child != null) {
                child.setMother(null);
                livestockRepository.save(child);
            }
            offspringRepository.delete(link);
        });
    }

    // ── Family queries ────────────────────────────────────────────────

    /** All direct children of a given animal */
    public List<Livestock> getDirectChildren(UUID livestockId) {
        return livestockRepository.findByMotherId(livestockId);
    }

    /** Does this animal have any children? */
    public boolean hasChildren(UUID livestockId) {
        return livestockRepository.existsByMotherId(livestockId);
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void resolveAndSetLivestock(LivestockBirth birth) {
        String idStr = birth.getLivestockIdValue();
        if (idStr != null && !idStr.isEmpty()) {
            Livestock ls = livestockRepository.findById(UUID.fromString(idStr))
                    .orElseThrow(() -> new RuntimeException("Livestock not found: " + idStr));
            birth.setLivestock(ls);
        }
    }

    /**
     * Walk up the mother chain to calculate how deep this animal is from founding stock.
     * Founding animal (no mother) = 0.  Its children = 1.  Grandchildren = 2. Etc.
     */
    private int calculateGeneration(Livestock animal) {
        int gen = 0;
        Livestock current = animal;
        // Load mother eagerly if needed (avoid lazy issues in loop)
        while (current.getMother() != null && gen < 50) {
            gen++;
            current = livestockRepository.findById(current.getMother().getId())
                    .orElse(null);
            if (current == null) break;
        }
        return gen;
    }
}
