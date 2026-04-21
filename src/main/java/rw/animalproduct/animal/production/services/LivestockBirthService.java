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

    private final LivestockBirthRepository   birthRepository;
    private final LivestockRepository        livestockRepository;
    private final LivestockOffspringRepository offspringRepository;

    public LivestockBirthService(LivestockBirthRepository birthRepository,
                                 LivestockRepository livestockRepository,
                                 LivestockOffspringRepository offspringRepository) {
        this.birthRepository      = birthRepository;
        this.livestockRepository  = livestockRepository;
        this.offspringRepository  = offspringRepository;
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

    /**
     * Save a new birth record.
     *
     * TWO CASES:
     *
     * Case 1 — Farm birth (isExternalBirth = false or null):
     *   - livestockIdValue must be provided — this is the mother.
     *   - Mother's last_birth_date, offspring_count, pregnancy status are updated.
     *
     * Case 2 — External / purchased birth (isExternalBirth = true):
     *   - No mother needed. livestockIdValue is ignored.
     *   - birth_date in livestock_births stores when the purchased animal was born.
     *   - This birth_date is used later to calculate breeding eligibility.
     *   - After saving, link the purchased animal via linkChild().
     */
    @Transactional
    public LivestockBirth addNew(LivestockBirth birth) {
        boolean isExternal = Boolean.TRUE.equals(birth.getIsExternalBirth());

        if (!isExternal) {
            // Farm birth — resolve and set the mother
            resolveAndSetLivestock(birth);

            if (birth.getLivestock() != null) {
                Livestock mother = birth.getLivestock();
                mother.setLastBirthDate(birth.getBirthDate());
                mother.setIsPregnant(false);
                mother.setPregnancyStatus("NOT_PREGNANT");
                mother.setStatus(Livestock.STATUS_ACTIVE);
                if (birth.getOffspringCount() != null) {
                    int current = mother.getOffspringCount() == null ? 0 : mother.getOffspringCount();
                    mother.setOffspringCount(current + birth.getOffspringCount());
                }
                livestockRepository.save(mother);
            }
        } else {
            // External birth — no mother on this farm
            birth.setLivestock(null);
        }

        return birthRepository.save(birth);
    }

    /**
     * Update an existing birth record.
     */
    @Transactional
    public LivestockBirth update(UUID id, LivestockBirth updated) {
        Optional<LivestockBirth> existingOpt = birthRepository.findById(id);
        if (existingOpt.isEmpty()) return null;

        LivestockBirth existing = existingOpt.get();
        existing.setBirthDate(updated.getBirthDate());
        existing.setOffspringCount(updated.getOffspringCount());
        existing.setOffspringGender(updated.getOffspringGender());
        existing.setWeaningDate(updated.getWeaningDate());
        existing.setNextBreedingDate(updated.getNextBreedingDate());
        existing.setNotes(updated.getNotes());
        existing.setIsExternalBirth(updated.getIsExternalBirth());
        existing.setSourceLocation(updated.getSourceLocation());

        boolean isExternal = Boolean.TRUE.equals(updated.getIsExternalBirth());
        if (!isExternal) {
            existing.setLivestockIdValue(updated.getLivestockIdValue());
            resolveAndSetLivestock(existing);
        } else {
            existing.setLivestock(null);
        }

        return birthRepository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        birthRepository.deleteById(id);
    }

    // ── Child Linking ─────────────────────────────────────────────────

    /**
     * Link a child / purchased animal to a birth event.
     *
     * Farm birth:    child.mother = birth.livestock (the known mother on this farm).
     * External birth: child.mother stays null (mother unknown / not on this farm).
     *
     * In both cases the birth_date from the LivestockBirth record tells you
     * when that animal was born, which is all you need to calculate breeding eligibility.
     */
    @Transactional
    public LivestockOffspring linkChild(UUID birthId, UUID childLivestockId) {
        LivestockBirth birth = birthRepository.findById(birthId)
                .orElseThrow(() -> new RuntimeException("Birth record not found"));

        Livestock child = livestockRepository.findById(childLivestockId)
                .orElseThrow(() -> new RuntimeException("Child livestock not found"));

        Livestock mother = birth.getLivestock();

        if (mother != null) {
            // Farm birth — we know the mother, set it on the child
            child.setMother(mother);
            livestockRepository.save(child);
        }
        // External birth — mother is null, child.mother stays null, no save needed for mother

        int generation = (mother != null) ? calculateGeneration(mother) + 1 : 0;

        LivestockOffspring link = new LivestockOffspring(birth, child, generation);
        return offspringRepository.save(link);
    }

    /**
     * Unlink a child from its birth event (also clears mother_id on the child).
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

    public List<Livestock> getDirectChildren(UUID livestockId) {
        return livestockRepository.findByMotherId(livestockId);
    }

    public boolean hasChildren(UUID livestockId) {
        return livestockRepository.existsByMotherId(livestockId);
    }

    // ── Private helpers ───────────────────────────────────────────────

    private void resolveAndSetLivestock(LivestockBirth birth) {
        String idStr = birth.getLivestockIdValue();
        if (idStr != null && !idStr.trim().isEmpty()) {
            Livestock ls = livestockRepository.findById(UUID.fromString(idStr))
                    .orElseThrow(() -> new RuntimeException("Livestock not found: " + idStr));
            birth.setLivestock(ls);
        }
    }

    /**
     * Walk up the mother chain to calculate how deep this animal is from founding stock.
     * Founding animal (no mother) = 0.  Direct child = 1.  Grandchild = 2.  Etc.
     */
    private int calculateGeneration(Livestock animal) {
        int gen = 0;
        Livestock current = animal;
        while (current.getMother() != null && gen < 50) {
            gen++;
            current = livestockRepository.findById(current.getMother().getId()).orElse(null);
            if (current == null) break;
        }
        return gen;
    }
}
