package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.entity.LivestockValuation;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.repository.LivestockValuationRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LivestockService {

    private final LivestockRepository livestockRepository;
    private final LivestockCategoryRepository livestockCategoryRepository;
    private final LivestockValuationRepository livestockValuationRepository;
    private final LifecycleEmailService emailService;

    /**
     * Fields that can be used for sorting.
     * Whitelist — prevents SQL injection via the ?sort= param.
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "tagNumber", "birthDate", "currentValue"
    );
    private static final String DEFAULT_SORT_FIELD = "createdAt";

    // ─── READ ──────────────────────────────────────────────────────────────────

    public List<Livestock> getAll() {
        return livestockRepository.findByIsDeletedFalse();
    }

    public List<Livestock> getAllIncludingDrafts() {
        return livestockRepository.findAll();
    }

    public Page<Livestock> getPaginated(Pageable pageable) {
        return livestockRepository.findByIsDeletedFalse(pageable);
    }

    /**
     * Default paged list — sorted by createdAt DESC, id DESC.
     * ✅ FIXED: previously had no Sort → random order with UUID PKs.
     */
    public Page<Livestock> getPaged(int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, DEFAULT_SORT_FIELD)
                .and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        return livestockRepository.findByIsDeletedFalse(pageable);
    }

    /**
     * ✅ NEW: Paged list with sort + optional category / status filters.
     * Server-side filtering — correct for 50k+ animals.
     */
    public Page<Livestock> getPaged(int page,
                                    int size,
                                    String sortField,
                                    String dir,
                                    String status,
                                    UUID categoryId) {

        String safeSort = (sortField != null && ALLOWED_SORT_FIELDS.contains(sortField))
                ? sortField : DEFAULT_SORT_FIELD;
        Sort.Direction direction = "ASC".equalsIgnoreCase(dir)
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        Sort sort = Sort.by(direction, safeSort)
                .and(Sort.by(Sort.Direction.DESC, "id"));

        Pageable pageable = PageRequest.of(page, size, sort);

        boolean hasStatus   = status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status);
        boolean hasCategory = categoryId != null;

        if (hasStatus && hasCategory) {
            return livestockRepository
                    .findByLivestockCategoryIdAndStatusAndIsDeletedFalse(categoryId, status, pageable);
        }
        if (hasStatus) {
            return livestockRepository.findByStatusAndIsDeletedFalse(status, pageable);
        }
        if (hasCategory) {
            return livestockRepository
                    .findByLivestockCategoryIdAndIsDeletedFalse(categoryId, pageable);
        }
        return livestockRepository.findByIsDeletedFalse(pageable);
    }

    public Optional<Livestock> getById(UUID id) {
        return livestockRepository.findByIdAndIsDeletedFalse(id);
    }

    public Optional<Livestock> getByIdIncludingDeleted(UUID id) {
        return livestockRepository.findById(id);
    }

    public Livestock findById(UUID id) {
        return livestockRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("Livestock not found: " + id));
    }

    public List<Livestock> getByStatus(String status) {
        return livestockRepository.findByStatusAndIsDeletedFalse(status);
    }

    public List<Livestock> getByCategory(UUID categoryId) {
        return livestockRepository.findByLivestockCategoryIdAndIsDeletedFalse(categoryId);
    }

    public List<Livestock> getByCategoryAndStatus(UUID categoryId, String status) {
        return livestockRepository.findByLivestockCategoryIdAndStatusAndIsDeletedFalse(categoryId, status);
    }

    public Page<Livestock> getByStatusPaginated(String status, Pageable pageable) {
        return livestockRepository.findByStatusAndIsDeletedFalse(status, pageable);
    }

    public Page<Livestock> getByStatusPaged(String status, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, DEFAULT_SORT_FIELD)
                .and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        return livestockRepository.findByStatusAndIsDeletedFalse(status, pageable);
    }

    // ─── FIND SOFT DELETED ──────────────────────────────────────────────────

    public List<Livestock> getAllSoftDeleted() {
        return livestockRepository.findAllSoftDeleted();
    }

    public Page<Livestock> findAllSoftDeleted(Pageable pageable) {
        return livestockRepository.findAllSoftDeleted(pageable);
    }

    public Page<Livestock> findAllSoftDeletedPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return livestockRepository.findAllSoftDeleted(pageable);
    }

    // ─── LIVESTOCK VALUATIONS ──────────────────────────────────────────────────

    public Map<UUID, LivestockValuation> getLatestValuationsForIds(List<UUID> livestockIds) {
        if (livestockIds == null || livestockIds.isEmpty()) {
            return Map.of();
        }

        List<LivestockValuation> valuations = livestockValuationRepository
                .findLatestValuationsForLivestockIds(livestockIds);

        return valuations.stream()
                .collect(Collectors.toMap(
                        valuation -> valuation.getLivestock().getId(),
                        valuation -> valuation,
                        (existing, replacement) -> existing
                ));
    }

    public Optional<LivestockValuation> getLatestValuationForLivestock(UUID livestockId) {
        return livestockValuationRepository.findFirstByLivestockIdOrderByValuationDateDesc(livestockId);
    }

    public List<LivestockValuation> getValuationsForLivestock(UUID livestockId) {
        return livestockValuationRepository.findByLivestockIdOrderByValuationDateDesc(livestockId);
    }

    public Double getCurrentValue(UUID livestockId) {
        return livestockValuationRepository.findFirstByLivestockIdOrderByValuationDateDesc(livestockId)
                .map(LivestockValuation::getEstimatedValue)
                .orElse(null);
    }

    // ─── CREATE ────────────────────────────────────────────────────────────────

    @Transactional
    public Livestock create(Livestock livestock) {
        livestock.setIsDeleted(false);
        livestock.setCreatedAt(LocalDateTime.now());

        if (livestock.getStatus() == null) {
            livestock.setStatus(Livestock.STATUS_ACTIVE);
        }

        if ("FEMALE".equalsIgnoreCase(livestock.getGender()) && livestock.getReproductiveStatus() == null) {
            livestock.setReproductiveStatus(Livestock.REPRO_OPEN);
        }

        Livestock saved = livestockRepository.save(livestock);

        try {
            emailService.sendAnimalRegisteredNotification(saved);
        } catch (Exception e) {
            log.warn("Failed to send registration email for {}: {}", saved.getTagNumber(), e.getMessage());
        }

        return saved;
    }

    @Transactional
    public Livestock addNew(Livestock livestock) {
        return create(livestock);
    }

    // ─── UPDATE ────────────────────────────────────────────────────────────────

    @Transactional
    public Livestock update(UUID id, Livestock updated) {
        Livestock existing = findById(id);

        existing.setTagNumber(updated.getTagNumber());
        existing.setGender(updated.getGender());
        existing.setStatus(updated.getStatus());
        existing.setHealthStatus(updated.getHealthStatus());
        existing.setReproductiveStatus(updated.getReproductiveStatus());
        existing.setAcquisitionMethod(updated.getAcquisitionMethod());
        existing.setAcquisitionSource(updated.getAcquisitionSource());
        existing.setDateReceived(updated.getDateReceived());
        existing.setBirthDate(updated.getBirthDate());
        existing.setSourceLocation(updated.getSourceLocation());
        existing.setOffspringCount(updated.getOffspringCount());
        existing.setPhoto(updated.getPhoto());

        if (updated.getLivestockCategory() != null) {
            existing.setLivestockCategory(updated.getLivestockCategory());
        }
        if (updated.getBeneficiary() != null) {
            existing.setBeneficiary(updated.getBeneficiary());
        }
        if (updated.getLocation() != null) {
            existing.setLocation(updated.getLocation());
        }
        if (updated.getFather() != null) {
            existing.setFather(updated.getFather());
        }
        if (updated.getMother() != null) {
            existing.setMother(updated.getMother());
        }

        existing.setUpdatedAt(LocalDateTime.now());

        return livestockRepository.save(existing);
    }

    // ─── DELETE (soft) ────────────────────────────────────────────────────────

    @Transactional
    public void softDelete(UUID id) {
        Livestock existing = findById(id);
        existing.setIsDeleted(true);
        existing.setDeletedAt(LocalDateTime.now());
        existing.setDeletedBy("system");
        livestockRepository.save(existing);
        log.info("Soft deleted livestock: {}", existing.getTagNumber());
    }

    @Transactional
    public void softDelete(UUID id, String deletedBy) {
        Livestock existing = findById(id);
        existing.setIsDeleted(true);
        existing.setDeletedAt(LocalDateTime.now());
        existing.setDeletedBy(deletedBy);
        livestockRepository.save(existing);
        log.info("Soft deleted livestock: {} by {}", existing.getTagNumber(), deletedBy);
    }

    // ─── HARD DELETE ───────────────────────────────────────────────────────────

    @Transactional
    public void hardDelete(UUID id) {
        Livestock existing = livestockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Livestock not found: " + id));
        livestockRepository.delete(existing);
        log.info("Hard deleted livestock: {}", existing.getTagNumber());
    }

    // ─── BULK OPERATIONS ──────────────────────────────────────────────────────

    @Transactional
    public void bulkSoftDelete(List<UUID> ids) {
        List<Livestock> animals = livestockRepository.findAllById(ids);
        for (Livestock animal : animals) {
            animal.setIsDeleted(true);
            animal.setDeletedAt(LocalDateTime.now());
            animal.setDeletedBy("system");
        }
        livestockRepository.saveAll(animals);
        log.info("Bulk soft deleted {} animals", animals.size());
    }

    @Transactional
    public void bulkRestore(List<UUID> ids) {
        List<Livestock> animals = livestockRepository.findAllById(ids);
        for (Livestock animal : animals) {
            animal.setIsDeleted(false);
            animal.setDeletedAt(null);
            animal.setDeletedBy(null);
        }
        livestockRepository.saveAll(animals);
        log.info("Bulk restored {} animals", animals.size());
    }

    // ─── RESTORE ───────────────────────────────────────────────────────────────

    @Transactional
    public void restore(UUID id) {
        Livestock existing = livestockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Livestock not found: " + id));
        existing.setIsDeleted(false);
        existing.setDeletedAt(null);
        existing.setDeletedBy(null);
        livestockRepository.save(existing);
        log.info("Restored livestock: {}", existing.getTagNumber());
    }

    // ─── STATUS UPDATES ──────────────────────────────────────────────────────

    @Transactional
    public void updateStatus(UUID id, String status) {
        Livestock existing = findById(id);
        existing.setStatus(status);
        existing.setUpdatedAt(LocalDateTime.now());
        livestockRepository.save(existing);
    }

    @Transactional
    public void updateReproductiveStatus(UUID id, String reproductiveStatus) {
        Livestock existing = findById(id);
        existing.setReproductiveStatus(reproductiveStatus);

        if (Livestock.REPRO_PREGNANT.equals(reproductiveStatus)) {
            existing.setIsPregnant(true);
        } else if (Livestock.REPRO_OPEN.equals(reproductiveStatus)) {
            existing.setIsPregnant(false);
            existing.setExpectedDueDate(null);
        }

        existing.setUpdatedAt(LocalDateTime.now());
        livestockRepository.save(existing);
    }

    // ─── BULK OPERATIONS ──────────────────────────────────────────────────────

    @Transactional
    public void updateStatusByCategory(UUID categoryId, String status) {
        List<Livestock> animals = livestockRepository.findByLivestockCategoryIdAndIsDeletedFalse(categoryId);
        for (Livestock animal : animals) {
            animal.setStatus(status);
            animal.setUpdatedAt(LocalDateTime.now());
        }
        livestockRepository.saveAll(animals);
        log.info("Updated status to {} for {} animals in category {}", status, animals.size(), categoryId);
    }

    // ─── QUERIES FOR BREEDING READINESS ─────────────────────────────────────

    public List<Livestock> findAllReadyToBreedFemales() {
        return livestockRepository.findAllReadyToBreedFemales();
    }

    public long countReadyToBreedFemales() {
        return livestockRepository.countReadyToBreedFemales();
    }

    public List<Livestock> findAllBreedingMales() {
        return livestockRepository.findAllBreedingMales();
    }

    public List<Livestock> findByGenderAndStatusAndReproductiveStatus(String gender, String status, String reproductiveStatus) {
        return livestockRepository.findByGenderAndStatusAndReproductiveStatusAndIsDeletedFalse(gender, status, reproductiveStatus);
    }

    // ─── DASHBOARD STATS ─────────────────────────────────────────────────────

    public long countActive() {
        return livestockRepository.countByStatus(Livestock.STATUS_ACTIVE);
    }

    public long countPregnant() {
        return livestockRepository.findByStatusAndIsDeletedFalse(Livestock.STATUS_ACTIVE).stream()
                .filter(l -> Boolean.TRUE.equals(l.getIsPregnant()))
                .count();
    }

    public long countByCategory(UUID categoryId) {
        return livestockRepository.countByCategoryId(categoryId);
    }

    // ─── ✅ Global counts (real, not page-scoped) ───────────────────────────

    public long countBornOnFarm() {
        return livestockRepository.countBornOnFarm();
    }

    public long countSold() {
        return livestockRepository.countSold();
    }

    public long countDead() {
        return livestockRepository.countDead();
    }
}