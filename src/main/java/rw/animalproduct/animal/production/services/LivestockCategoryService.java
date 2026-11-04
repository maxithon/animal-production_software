package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockCategoryService {

    private static final double AVG_DAYS_PER_MONTH = 30.44;

    private final LivestockCategoryRepository livestockCategoryRepository;
    private final LivestockRepository livestockRepository;
    private final AuditLogService auditLogService;

    public LivestockCategoryService(LivestockCategoryRepository livestockCategoryRepository,
                                    LivestockRepository livestockRepository,
                                    AuditLogService auditLogService) {
        this.livestockCategoryRepository = livestockCategoryRepository;
        this.livestockRepository = livestockRepository;
        this.auditLogService = auditLogService;
    }

    public record DeleteResult(String categoryName, long referenceCount) {
        public boolean wasInUse() { return referenceCount > 0; }
    }

    // ── Reads ────────────────────────────────────────────────────────
    public List<LivestockCategory> getAll() {
        return livestockCategoryRepository.findAllByOrderByNameAsc();
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

    public Map<UUID, Long> getUsageCounts() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : livestockRepository.countGroupedByCategoryAll()) {
            UUID id = (UUID) row[0];
            Long count = ((Number) row[1]).longValue();
            counts.put(id, count);
        }
        return counts;
    }

    public long getUsageCount(UUID categoryId) {
        return livestockRepository.countByCategoryId(categoryId);
    }

    // ── Create / Update ─────────────────────────────────────────────
    @Transactional
    public LivestockCategory addNew(LivestockCategory category) {
        normalizeAndValidate(category, null);
        LivestockCategory saved = livestockCategoryRepository.save(category);

        // ✅ AUDIT LOG: CREATE
        auditLogService.log("livestock_category", saved.getId(), "CREATE", "system",
                null, saved, "Created category " + saved.getName());

        return saved;
    }

    @Transactional
    public LivestockCategory update(UUID id, LivestockCategory updatedData) {
        LivestockCategory existing = livestockCategoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found."));

        // Keep a copy of the old values for the audit log
        LivestockCategory before = new LivestockCategory();
        before.setId(existing.getId());
        before.setName(existing.getName());
        before.setCode(existing.getCode());
        before.setGestationPeriodDays(existing.getGestationPeriodDays());
        // (You can expand this to copy all fields if you want the log to be thorough)

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

        LivestockCategory saved = livestockCategoryRepository.save(existing);

        // ✅ AUDIT LOG: UPDATE
        auditLogService.log("livestock_category", saved.getId(), "UPDATE", "system",
                before, saved, "Updated category " + saved.getName());

        return saved;
    }

    // ── DELETE POLICY ───────────────────────────────────────────────
    @Transactional
    public DeleteResult delete(UUID id) {
        LivestockCategory category = livestockCategoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Category not found."));

        long refCount = livestockRepository.countByCategoryId(id);

        if (refCount > 0) {
            throw new IllegalStateException("Category is used by " + refCount + " animal(s). You cannot delete it.");
        }

        // ✅ AUDIT LOG: DELETE (before actual deletion)
        auditLogService.log("livestock_category", category.getId(), "DELETE", "system",
                category, null, "Deleted category " + category.getName());

        livestockCategoryRepository.delete(category);

        return new DeleteResult(category.getName(), 0);
    }

    // ── Validation / normalization ──────────────────────────────────
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

        if (category.getGestationPeriodDays() != null) {
            category.setGestationPeriodMonths(
                    (int) Math.round(category.getGestationPeriodDays() / AVG_DAYS_PER_MONTH));
        } else if (category.getGestationPeriodMonths() != null) {
            category.setGestationPeriodDays(
                    (int) Math.round(category.getGestationPeriodMonths() * AVG_DAYS_PER_MONTH));
        }

        if (category.getPostpartumRecoveryDays() == null) category.setPostpartumRecoveryDays(45);
        if (category.getWeaningPeriodDays() == null)     category.setWeaningPeriodDays(90);
        if (category.getTypicalLitterSize() == null)     category.setTypicalLitterSize(1);

        if (category.getMinBreedingAgeMonths() != null && category.getMaxBreedingAgeMonths() != null
                && category.getMaxBreedingAgeMonths() <= category.getMinBreedingAgeMonths()) {
            throw new IllegalStateException(
                    "Maximum breeding age must be greater than minimum breeding age.");
        }
    }
}