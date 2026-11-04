package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockCategoryService {

    // FAO-standard average used to keep the legacy "months" column in sync with the
    // precise "days" column, so the two can never silently disagree.
    private static final double AVG_DAYS_PER_MONTH = 30.44;

    private final LivestockCategoryRepository livestockCategoryRepository;

    public LivestockCategoryService(LivestockCategoryRepository livestockCategoryRepository) {
        this.livestockCategoryRepository = livestockCategoryRepository;
    }

    public List<LivestockCategory> getAll() {
        return livestockCategoryRepository.findAllActiveOrderByName();
    }

    public Optional<LivestockCategory> getById(UUID id) {
        return livestockCategoryRepository.findById(id);
    }

    public Optional<LivestockCategory> getByCode(String code) {
        return livestockCategoryRepository.findByCode(code);
    }

    public Optional<LivestockCategory> getByName(String name) {
        return livestockCategoryRepository.findByName(name);
    }

    public boolean existsByCode(String code) {
        return livestockCategoryRepository.existsByCode(code);
    }

    public boolean existsByName(String name) {
        return livestockCategoryRepository.existsByName(name);
    }

    public LivestockCategory addNew(LivestockCategory category) {
        normalizeAndValidate(category, null);
        return livestockCategoryRepository.save(category);
    }

    public LivestockCategory update(UUID id, LivestockCategory updatedData) {
        LivestockCategory existing = livestockCategoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found."));

        normalizeAndValidate(updatedData, id);

        existing.setName(updatedData.getName());
        existing.setCode(updatedData.getCode());
        existing.setGestationPeriodMonths(updatedData.getGestationPeriodMonths());
        existing.setGestationPeriodDays(updatedData.getGestationPeriodDays());
        existing.setMinBreedingAgeMonths(updatedData.getMinBreedingAgeMonths());
        existing.setMaxBreedingAgeMonths(updatedData.getMaxBreedingAgeMonths());
        existing.setPostpartumRecoveryDays(updatedData.getPostpartumRecoveryDays());
        existing.setWeaningPeriodDays(updatedData.getWeaningPeriodDays());
        existing.setTypicalLitterSize(updatedData.getTypicalLitterSize());
        existing.setDescription(updatedData.getDescription());
        return livestockCategoryRepository.save(existing);
    }

    /** Soft delete, matching the is_deleted pattern used across the rest of the app
     *  (sick records, birth records). Avoids FK violations when livestock reference
     *  this category, and preserves history for audit/reporting. */
    public void delete(UUID id) {
        LivestockCategory category = livestockCategoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found."));
        category.setIsDeleted(true);
        livestockCategoryRepository.save(category);
    }

    private void normalizeAndValidate(LivestockCategory category, UUID excludeId) {
        if (category.getCode() != null) {
            category.setCode(category.getCode().trim().toUpperCase());
        }
        if (category.getName() != null) {
            category.setName(category.getName().trim());
        }

        boolean codeTaken = (excludeId == null)
                ? livestockCategoryRepository.existsByCode(category.getCode())
                : livestockCategoryRepository.existsByCodeAndIdNot(category.getCode(), excludeId);
        if (codeTaken) {
            throw new IllegalStateException("A category with this code already exists.");
        }

        boolean nameTaken = (excludeId == null)
                ? livestockCategoryRepository.existsByName(category.getName())
                : livestockCategoryRepository.existsByNameAndIdNot(category.getName(), excludeId);
        if (nameTaken) {
            throw new IllegalStateException("A category with this name already exists.");
        }

        // Days is the source of truth; months is derived so the two columns can't drift apart.
        if (category.getGestationPeriodDays() != null) {
            category.setGestationPeriodMonths(
                    (int) Math.round(category.getGestationPeriodDays() / AVG_DAYS_PER_MONTH));
        } else if (category.getGestationPeriodMonths() != null) {
            category.setGestationPeriodDays(
                    (int) Math.round(category.getGestationPeriodMonths() * AVG_DAYS_PER_MONTH));
        }

        if (category.getPostpartumRecoveryDays() == null) {
            category.setPostpartumRecoveryDays(45);
        }
        if (category.getWeaningPeriodDays() == null) {
            category.setWeaningPeriodDays(90);
        }
        if (category.getTypicalLitterSize() == null) {
            category.setTypicalLitterSize(1);
        }

        if (category.getMinBreedingAgeMonths() != null && category.getMaxBreedingAgeMonths() != null
                && category.getMaxBreedingAgeMonths() <= category.getMinBreedingAgeMonths()) {
            throw new IllegalStateException("Maximum breeding age must be greater than minimum breeding age.");
        }
    }
}