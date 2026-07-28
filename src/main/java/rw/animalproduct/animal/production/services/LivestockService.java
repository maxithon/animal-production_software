package rw.animalproduct.animal.production.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.patches.AsyncConfig;
import rw.animalproduct.animal.production.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
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

    // NEW: needed so update() can notify on every change, not just registration.
    private final LifecycleEmailService emailService;

    // PERFORMANCE FIX (same root cause as the register() flow — see AsyncConfig):
    // the "Animal Updated" email used to be sent synchronously on the same
    // thread that just handled the user's edit-save. That's the same SMTP
    // round-trip cost, just triggered on every edit instead of only on
    // registration. Routing it through this executor means editing an
    // animal returns to the user as soon as the DB write finishes, and the
    // notification email goes out a moment later in the background.
    private final Executor notificationExecutor;

    @Autowired
    public LivestockService(LivestockRepository livestockRepository,
                            LivestockCategoryRepository livestockCategoryRepository,
                            BeneficiaryRepository beneficiaryRepository,
                            LocationRepository locationRepository,
                            AuditLogService auditLogService,
                            LivestockValuationService valuationService,
                            LifecycleEmailService emailService,
                            @Qualifier(AsyncConfig.NOTIFICATION_EXECUTOR) Executor notificationExecutor) {
        this.livestockRepository = livestockRepository;
        this.livestockCategoryRepository = livestockCategoryRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.locationRepository = locationRepository;
        this.auditLogService = auditLogService;
        this.valuationService = valuationService;
        this.emailService = emailService;
        this.notificationExecutor = notificationExecutor;
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

        // NOTE: the "new animal registered" email is sent from
        // LivestockController.register() via emailService.sendAnimalRegisteredNotification(saved),
        // now dispatched through notificationExecutor there so it never
        // blocks the registration request (see AsyncConfig for details).

        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * FAO STANDARD — TRACEABILITY:
     * Every field change to an animal record must be traceable, not just
     * logged silently to an audit table. This method now:
     *   1. Snapshots every tracked, human-meaningful field BEFORE mutation.
     *   2. Applies the update exactly as before (unchanged logic).
     *   3. Snapshots the same fields AFTER save.
     *   4. Diffs the two snapshots and, if anything actually changed,
     *      sends a single "Animal Updated" email listing every changed
     *      field as old → new. No-op saves (nothing actually different)
     *      never trigger an email — avoids notification fatigue.
     * The email is dispatched on notificationExecutor (background thread)
     * so a slow or unreachable mail server can never add latency to an
     * edit-save, and it's still best-effort: a mail failure never blocks
     * the update itself or bubbles up to the caller.
     */
    @Transactional
    public Livestock update(UUID id, Livestock updated) {
        Livestock existing = livestockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Livestock not found: " + id));

        String oldSnapshot = "Tag: " + existing.getTagNumber()
                + " | Status: " + existing.getStatus()
                + " | Gender: " + existing.getGender()
                + " | Value: " + existing.getCurrentValue();

        // NEW: capture full field snapshot BEFORE any mutation, for the
        // change-notification email (separate from the short audit string above).
        Map<String, String> beforeFields = snapshotFields(existing);

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

        // NEW: diff full field snapshots and email only if something
        // meaningful actually changed.
        Map<String, String> afterFields = snapshotFields(saved);
        Map<String, String[]> changes = diffFields(beforeFields, afterFields);

        if (!changes.isEmpty()) {
            String currentUser = getCurrentUsername();
            // CHANGED: dispatched on notificationExecutor instead of being
            // called inline — this used to block every single edit-save on
            // an SMTP round trip. See AsyncConfig for the full explanation.
            notificationExecutor.execute(() -> {
                try {
                    emailService.sendAnimalUpdatedNotification(saved, changes, currentUser);
                } catch (Exception emailEx) {
                    // Never let a mail failure fail the update itself.
                    // LifecycleEmailService already logs its own errors internally.
                }
            });
        }

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

    /**
     * NEW: takes a snapshot of every human-meaningful, trackable field on a
     * Livestock record as display-ready strings. Used before AND after an
     * update() call so the two snapshots can be diffed to build the
     * "what actually changed" email. Intentionally excludes currentValue —
     * that field has its own dedicated valuation-history email flow via
     * LivestockValuationService, so including it here would double-notify.
     */
    private Map<String, String> snapshotFields(Livestock l) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Tag Number", l.getTagNumber());
        m.put("Status", l.getStatus());
        m.put("Gender", l.getGender());
        m.put("Category", l.getLivestockCategory() != null ? l.getLivestockCategory().getName() : null);
        m.put("Beneficiary", l.getBeneficiary() != null
                ? (nullToEmpty(l.getBeneficiary().getFirstName()) + " " + nullToEmpty(l.getBeneficiary().getLastName())).trim()
                : null);
        m.put("Location", l.getLocation() != null ? l.getLocation().getName() : null);
        m.put("Acquisition Method", l.getAcquisitionMethod());
        m.put("Acquisition Source", l.getAcquisitionSource());
        m.put("Date Received", l.getDateReceived() != null ? l.getDateReceived().toString() : null);
        m.put("Birth Date", l.getBirthDate() != null ? l.getBirthDate().toString() : null);
        m.put("Last Birth Date", l.getLastBirthDate() != null ? l.getLastBirthDate().toString() : null);
        m.put("Offspring Count", l.getOffspringCount() != null ? l.getOffspringCount().toString() : null);
        m.put("Is Pregnant", l.getIsPregnant() != null ? (l.getIsPregnant() ? "Yes" : "No") : null);
        m.put("Pregnancy Status", l.getPregnancyStatus());
        m.put("Conception Date", l.getConceptionDate() != null ? l.getConceptionDate().toString() : null);
        m.put("First Breeding Date", l.getFirstBreedingDate() != null ? l.getFirstBreedingDate().toString() : null);
        m.put("Last Breeding Date", l.getLastBreedingDate() != null ? l.getLastBreedingDate().toString() : null);
        m.put("Expected Due Date", l.getExpectedDueDate() != null ? l.getExpectedDueDate().toString() : null);
        m.put("Insemination Method", l.getInseminationMethod());
        m.put("Sold Price", l.getSoldPrice() != null ? l.getSoldPrice().toPlainString() + " RWF" : null);
        return m;
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * NEW: compares two field snapshots produced by snapshotFields() and
     * returns only the fields that actually differ, as
     * label -> {oldValue, newValue}. Fields that are blank/null on both
     * sides are treated as unchanged (avoids "changed" noise from
     * null → "" type mismatches).
     */
    private Map<String, String[]> diffFields(Map<String, String> before, Map<String, String> after) {
        Map<String, String[]> changes = new LinkedHashMap<>();
        for (String key : before.keySet()) {
            String oldVal = before.get(key);
            String newVal = after.get(key);
            boolean bothBlank = (oldVal == null || oldVal.isBlank()) && (newVal == null || newVal.isBlank());
            if (!bothBlank && !Objects.equals(oldVal, newVal)) {
                changes.put(key, new String[]{oldVal, newVal});
            }
        }
        return changes;
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