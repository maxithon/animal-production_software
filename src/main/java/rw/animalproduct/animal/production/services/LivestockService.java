package rw.animalproduct.animal.production.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LivestockService {

    public static final String PREG_STATUS_PREGNANT = "PREGNANT";
    public static final String PREG_STATUS_NOT_PREGNANT = "NOT_PREGNANT";

    private final LivestockRepository livestockRepository;
    private final LivestockCategoryRepository livestockCategoryRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final LocationRepository locationRepository;
    private final AuditLogService auditLogService;
    private final LivestockValuationService valuationService; // FAO-standard valuation history

    @Autowired
    public LivestockService(LivestockRepository livestockRepository,
                            LivestockCategoryRepository livestockCategoryRepository,
                            BeneficiaryRepository beneficiaryRepository,
                            LocationRepository locationRepository,
                            AuditLogService auditLogService,
                            LivestockValuationService valuationService) {
        this.livestockRepository = livestockRepository;
        this.livestockCategoryRepository = livestockCategoryRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.locationRepository = locationRepository;
        this.auditLogService = auditLogService;
        this.valuationService = valuationService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    public List<Livestock> getAll() {
        return livestockRepository.findAll();
    }

    public List<Livestock> getAllIncludingDrafts() {
        return livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .collect(Collectors.toList());
    }

    public List<Livestock> getAllSoftDeleted() {
        return livestockRepository.findAllSoftDeleted();
    }

    public Page<Livestock> getPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");
        return livestockRepository.findAll(pageable);
    }

    public Optional<Livestock> getById(UUID id) {
        return livestockRepository.findByIdNotDeleted(id);
    }

    public Optional<Livestock> getByIdIncludingDeleted(UUID id) {
        return livestockRepository.findById(id);
    }

    public Optional<Livestock> getByTagNumber(String tagNumber) {
        return livestockRepository.findByTagNumberNotDeleted(tagNumber);
    }

    public List<Livestock> getByCategory(UUID categoryId) {
        return livestockRepository.findByLivestockCategoryId(categoryId);
    }

    public List<Livestock> getByBeneficiary(UUID beneficiaryId) {
        return livestockRepository.findByBeneficiaryId(beneficiaryId);
    }

    public List<Livestock> getPregnantLivestock() {
        return livestockRepository.findByStatus(Livestock.STATUS_PREGNANT);
    }

    public List<Livestock> getByStatus(String status) {
        return livestockRepository.findByStatus(status);
    }

    public long countByCategory(UUID categoryId) {
        return livestockRepository.countByCategory(categoryId);
    }

    public long countByStatus(String status) {
        return livestockRepository.countByStatus(status);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public Livestock addNew(Livestock livestock) {
        resolveCategory(livestock, livestock.getLivestockCategoryIdValue());
        resolveBeneficiary(livestock, livestock.getBeneficiaryIdValue());

        if (livestock.getOffspringCount() == null) livestock.setOffspringCount(0);
        if (livestock.getIsDeleted() == null) livestock.setIsDeleted(false);
        if (livestock.getIsDraft() == null) livestock.setIsDraft(false);
        if (livestock.getDateReceived() == null) livestock.setDateReceived(LocalDate.now());

        applyPregnancyState(livestock);

        Livestock saved = livestockRepository.save(livestock);

        // FAO STANDARD: seed the valuation history with an INITIAL record
        // instead of leaving current_value as a bare, directly-editable field.
        if (saved.getCurrentValue() != null) {
            valuationService.recordInitialValuation(saved, saved.getCurrentValue(), getCurrentUsername());
        }

        auditLogService.log(
                "livestock",
                saved.getId(),
                "CREATE",
                getCurrentUsername(),
                null,
                "Created livestock: " + saved.getTagNumber(),
                "New animal registered"
        );

        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public Livestock update(UUID id, Livestock updated) {
        Livestock existing = livestockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Livestock not found: " + id));

        String oldSnapshot = "Tag: " + existing.getTagNumber()
                + " | Status: " + existing.getStatus()
                + " | Gender: " + existing.getGender()
                + " | Value: " + existing.getCurrentValue();

        existing.setTagNumber(updated.getTagNumber());
        existing.setGender(updated.getGender());
        existing.setStatus(updated.getStatus());
        existing.setAcquisitionMethod(updated.getAcquisitionMethod());
        existing.setAcquisitionSource(updated.getAcquisitionSource());
        existing.setDateReceived(updated.getDateReceived());

        // FAO STANDARD CHANGE: currentValue is NO LONGER overwritten here.
        // It's a read-only cache maintained exclusively by
        // LivestockValuationService via the append-only valuation history.
        // existing.setCurrentValue(updated.getCurrentValue());   // ← REMOVED

        existing.setLastBirthDate(updated.getLastBirthDate());
        existing.setOffspringCount(updated.getOffspringCount());
        existing.setPhoto(updated.getPhoto());
        existing.setSoldPrice(updated.getSoldPrice());
        existing.setInseminationMethod(updated.getInseminationMethod());
        existing.setBirthDate(updated.getBirthDate());
        existing.setFirstBreedingDate(updated.getFirstBreedingDate());
        existing.setLastBreedingDate(updated.getLastBreedingDate());

        if (updated.getIsPregnant() != null) {
            existing.setIsPregnant(updated.getIsPregnant());
        }

        resolveCategory(existing, updated.getLivestockCategoryIdValue());
        resolveBeneficiary(existing, updated.getBeneficiaryIdValue());

        if (updated.getLocation() != null) {
            existing.setLocation(updated.getLocation());
        }

        existing.setConceptionDate(updated.getConceptionDate());

        if (updated.getConceptionDate() == null && updated.getExpectedDueDate() != null) {
            existing.setExpectedDueDate(updated.getExpectedDueDate());
        }

        applyPregnancyState(existing);

        Livestock saved = livestockRepository.save(existing);

        String newSnapshot = "Tag: " + saved.getTagNumber()
                + " | Status: " + saved.getStatus()
                + " | Gender: " + saved.getGender()
                + " | Value: " + saved.getCurrentValue();

        auditLogService.log(
                "livestock",
                id,
                "UPDATE",
                getCurrentUsername(),
                oldSnapshot,
                newSnapshot,
                "Livestock record updated"
        );

        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VALUATION (FAO STANDARD) — thin passthroughs kept here for controller
    // convenience so LivestockController doesn't need to know about both
    // services for simple cases. The real logic lives in
    // LivestockValuationService, which remains the single source of truth.
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public LivestockValuation recordValuation(UUID id, LocalDate valuationDate, java.math.BigDecimal value,
                                              String method, String notes, String recordedBy) {
        return valuationService.recordValuation(id, valuationDate, value, method, notes, recordedBy);
    }

    public List<LivestockValuation> getValuationHistory(UUID id) {
        return valuationService.getHistory(id);
    }

    public Optional<LivestockValuation> getLatestValuation(UUID id) {
        return valuationService.getLatest(id);
    }

    /**
     * NEW: bulk latest-valuation lookup for a page of animals, keyed by
     * livestock id. Used by the list page to render "Valued" /
     * "Needs Valuation" badges without an N+1 query per row.
     */
    public Map<UUID, LivestockValuation> getLatestValuationsForIds(List<UUID> ids) {
        return valuationService.getLatestForIds(ids);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SOFT DELETE (Recommended - hides from UI but keeps in DB)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void softDelete(UUID id) {
        Livestock livestock = livestockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Livestock not found: " + id));

        if (Boolean.TRUE.equals(livestock.getIsDeleted())) {
            throw new RuntimeException("Animal is already deleted");
        }

        String oldSnapshot = "Tag: " + livestock.getTagNumber()
                + " | Status: " + livestock.getStatus()
                + " | Gender: " + livestock.getGender()
                + " | Category: " + (livestock.getLivestockCategory() != null ? livestock.getLivestockCategory().getName() : "N/A")
                + " | Acquisition Method: " + livestock.getAcquisitionMethod();

        livestock.setIsDeleted(true);
        livestock.setDeletedAt(LocalDateTime.now());
        livestock.setDeletedBy(getCurrentUsername());
        livestock.setUpdatedAt(LocalDateTime.now());

        if (!Livestock.STATUS_DEAD.equals(livestock.getStatus())
                && !Livestock.STATUS_SOLD.equals(livestock.getStatus())) {
            livestock.setStatus(Livestock.STATUS_DEAD);
        }

        livestockRepository.save(livestock);

        auditLogService.log(
                "livestock",
                id,
                "SOFT_DELETE",
                getCurrentUsername(),
                oldSnapshot,
                null,
                "Livestock soft-deleted. Animal hidden from UI but remains in database for audit."
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HARD DELETE (Permanent - use with caution)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void hardDelete(UUID id) {
        Livestock livestock = livestockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Livestock not found: " + id));

        String snapshot = "Tag: " + livestock.getTagNumber()
                + " | Status: " + livestock.getStatus()
                + " | Gender: " + livestock.getGender()
                + " | Category: " + (livestock.getLivestockCategory() != null ? livestock.getLivestockCategory().getName() : "N/A");

        auditLogService.log(
                "livestock",
                id,
                "HARD_DELETE",
                getCurrentUsername(),
                snapshot,
                null,
                "Livestock permanently deleted from database. This action cannot be undone."
        );

        livestockRepository.deleteById(id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESTORE (Recover a soft-deleted animal)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void restore(UUID id) {
        Livestock livestock = livestockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Livestock not found: " + id));

        if (!Boolean.TRUE.equals(livestock.getIsDeleted())) {
            throw new RuntimeException("Animal is not deleted");
        }

        String oldSnapshot = "Was soft-deleted at: " + livestock.getDeletedAt() + " by: " + livestock.getDeletedBy();

        livestock.setIsDeleted(false);
        livestock.setDeletedAt(null);
        livestock.setDeletedBy(null);

        if (Livestock.STATUS_DEAD.equals(livestock.getStatus())) {
            livestock.setStatus(Livestock.STATUS_ACTIVE);
        }

        livestock.setUpdatedAt(LocalDateTime.now());
        livestockRepository.save(livestock);

        auditLogService.log(
                "livestock",
                id,
                "RESTORE",
                getCurrentUsername(),
                oldSnapshot,
                "Restored to active",
                "Animal restored from soft-delete"
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BULK OPERATIONS
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void bulkSoftDelete(List<UUID> ids) {
        for (UUID id : ids) {
            try {
                softDelete(id);
            } catch (Exception e) {
                System.err.println("Failed to delete animal " + id + ": " + e.getMessage());
            }
        }
    }

    @Transactional
    public void bulkRestore(List<UUID> ids) {
        for (UUID id : ids) {
            try {
                restore(id);
            } catch (Exception e) {
                System.err.println("Failed to restore animal " + id + ": " + e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void applyPregnancyState(Livestock l) {
        if (l.getConceptionDate() != null) {
            l.setIsPregnant(true);
            l.setStatus(Livestock.STATUS_PREGNANT);
            l.setPregnancyStatus(PREG_STATUS_PREGNANT);

            if (l.getLastBreedingDate() == null) l.setLastBreedingDate(l.getConceptionDate());
            if (l.getFirstBreedingDate() == null) l.setFirstBreedingDate(l.getConceptionDate());

            l.recalculateDueDate();

        } else if (Boolean.TRUE.equals(l.getIsPregnant())) {
            l.setStatus(Livestock.STATUS_PREGNANT);
            if (l.getPregnancyStatus() == null || l.getPregnancyStatus().isBlank()) {
                l.setPregnancyStatus(PREG_STATUS_PREGNANT);
            }

        } else {
            l.setIsPregnant(false);
            l.setPregnancyStatus(PREG_STATUS_NOT_PREGNANT);
            if (l.getStatus() == null || l.getStatus().isBlank()
                    || Livestock.STATUS_PREGNANT.equals(l.getStatus())) {
                l.setStatus(Livestock.STATUS_ACTIVE);
            }
        }
    }

    private void resolveCategory(Livestock livestock, String categoryIdValue) {
        if (categoryIdValue == null || categoryIdValue.isBlank()) return;
        try {
            UUID id = UUID.fromString(categoryIdValue.trim());
            livestockCategoryRepository.findById(id)
                    .ifPresent(livestock::setLivestockCategory);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid category ID format: " + categoryIdValue);
        }
    }

    private void resolveBeneficiary(Livestock livestock, String beneficiaryIdValue) {
        if (beneficiaryIdValue == null || beneficiaryIdValue.isBlank()) return;
        try {
            UUID id = UUID.fromString(beneficiaryIdValue.trim());
            beneficiaryRepository.findById(id)
                    .ifPresent(livestock::setBeneficiary);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid beneficiary ID format: " + beneficiaryIdValue);
        }
    }

    private String getCurrentUsername() {
        // TODO: Integrate with Spring Security when available
        try {
            return "system";
        } catch (Exception e) {
            return "system";
        }
    }

    public static String buildAcquisitionSource(String acquisitionMethod,
                                                String externalSource,
                                                String motherTag) {
        if (Livestock.ACQ_BIRTH.equals(acquisitionMethod)) {
            return (motherTag != null && !motherTag.isBlank())
                    ? "Born on this farm — Mother: " + motherTag
                    : "Born on this farm — Mother not recorded";
        } else if (Livestock.ACQ_PURCHASE.equals(acquisitionMethod)) {
            return (externalSource != null && !externalSource.isBlank())
                    ? "Purchased from: " + externalSource
                    : "Purchased (source not recorded)";
        } else if (Livestock.ACQ_DONATION.equals(acquisitionMethod)) {
            return (externalSource != null && !externalSource.isBlank())
                    ? "Donated from: " + externalSource
                    : "Donated (source not recorded)";
        } else if (Livestock.ACQ_TRANSFER.equals(acquisitionMethod)) {
            return (externalSource != null && !externalSource.isBlank())
                    ? "Transferred from: " + externalSource
                    : "Transferred (source not recorded)";
        }
        return "Unknown origin";
    }
}