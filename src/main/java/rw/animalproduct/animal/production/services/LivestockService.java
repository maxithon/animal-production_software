package rw.animalproduct.animal.production.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LivestockService — FAO / international standard alignment.
 *
 * Pregnancy status values (must match DB constraint chk_pregnancy_status):
 *   "PREGNANT"     — animal is currently pregnant
 *   "NOT_PREGNANT" — animal is not pregnant
 *
 * FAO rule: expectedDueDate is NEVER taken from the form as a raw user input.
 * It is always derived from conceptionDate + category gestation period by
 * calling livestock.recalculateDueDate() before saving.
 */
@Service
public class LivestockService {

    // ── Pregnancy status constants — MUST match chk_pregnancy_status constraint ──
    public static final String PREG_STATUS_PREGNANT     = "PREGNANT";
    public static final String PREG_STATUS_NOT_PREGNANT = "NOT_PREGNANT";

    private final LivestockRepository             livestockRepository;
    private final LivestockCategoryRepository     livestockCategoryRepository;
    private final BeneficiaryRepository           beneficiaryRepository;
    private final LocationRepository              locationRepository;

    @Autowired
    public LivestockService(LivestockRepository livestockRepository,
                            LivestockCategoryRepository livestockCategoryRepository,
                            BeneficiaryRepository beneficiaryRepository,
                            LocationRepository locationRepository) {
        this.livestockRepository         = livestockRepository;
        this.livestockCategoryRepository = livestockCategoryRepository;
        this.beneficiaryRepository       = beneficiaryRepository;
        this.locationRepository          = locationRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    public List<Livestock> getAll() {
        return livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .collect(Collectors.toList());
    }

    public List<Livestock> getAllIncludingDrafts() {
        return livestockRepository.findAll();
    }

    public Optional<Livestock> getById(UUID id) {
        return livestockRepository.findById(id);
    }

    public Optional<Livestock> getByTagNumber(String tagNumber) {
        return livestockRepository.findByTagNumber(tagNumber);
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

    public Livestock addNew(Livestock livestock) {

        // 1. Resolve category (must be done BEFORE recalculateDueDate)
        resolveCategory(livestock, livestock.getLivestockCategoryIdValue());

        // 2. Resolve beneficiary
        resolveBeneficiary(livestock, livestock.getBeneficiaryIdValue());

        // 3. Safe defaults
        if (livestock.getOffspringCount() == null) livestock.setOffspringCount(0);
        if (livestock.getIsDeleted()      == null) livestock.setIsDeleted(false);
        if (livestock.getIsDraft()        == null) livestock.setIsDraft(false);
        if (livestock.getDateReceived()   == null) livestock.setDateReceived(LocalDate.now());

        // 4. FAO rule: derive all pregnancy fields from conceptionDate
        applyPregnancyState(livestock);

        return livestockRepository.save(livestock);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────────────────

    public Livestock update(UUID id, Livestock updated) {
        Livestock existing = livestockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Livestock not found: " + id));

        // Basic fields
        existing.setTagNumber(updated.getTagNumber());
        existing.setGender(updated.getGender());
        existing.setStatus(updated.getStatus());
        existing.setAcquisitionMethod(updated.getAcquisitionMethod());
        existing.setAcquisitionSource(updated.getAcquisitionSource());
        existing.setDateReceived(updated.getDateReceived());
        existing.setCurrentValue(updated.getCurrentValue());
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

        // Category must be resolved before setConceptionDate (gestation recalc needs it)
        resolveCategory(existing, updated.getLivestockCategoryIdValue());
        resolveBeneficiary(existing, updated.getBeneficiaryIdValue());

        if (updated.getLocation() != null) {
            existing.setLocation(updated.getLocation());
        }

        // FAO rule: setConceptionDate triggers recalculateDueDate internally
        existing.setConceptionDate(updated.getConceptionDate());

        // Accept a manual due date only when there is no conception date to derive from
        if (updated.getConceptionDate() == null && updated.getExpectedDueDate() != null) {
            existing.setExpectedDueDate(updated.getExpectedDueDate());
        }

        // Re-apply pregnancy state (fixes status + pregnancyStatus)
        applyPregnancyState(existing);

        return livestockRepository.save(existing);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────────────────────

    public void delete(UUID id) {
        livestockRepository.deleteById(id);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATIC UTILITY
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE: FAO pregnancy state logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * FAO rule — derive all pregnancy fields from conceptionDate.
     *
     * pregnancyStatus values used here MUST satisfy chk_pregnancy_status:
     *   allowed: "PREGNANT" | "NOT_PREGNANT"
     *
     *  conceptionDate present
     *    → isPregnant = true
     *    → status = PREGNANT
     *    → pregnancyStatus = "PREGNANT"      ← DB-safe value
     *    → lastBreedingDate / firstBreedingDate synced if blank
     *    → expectedDueDate recalculated
     *
     *  conceptionDate absent + isPregnant toggle = true
     *    → status = PREGNANT
     *    → pregnancyStatus = "PREGNANT"
     *
     *  Neither
     *    → isPregnant = false
     *    → pregnancyStatus = "NOT_PREGNANT"
     *    → status reset to ACTIVE if it was PREGNANT
     */
    private void applyPregnancyState(Livestock l) {
        if (l.getConceptionDate() != null) {
            // ── Primary path: conception date entered ─────────────────────────
            l.setIsPregnant(true);
            l.setStatus(Livestock.STATUS_PREGNANT);
            l.setPregnancyStatus(PREG_STATUS_PREGNANT);     // "PREGNANT" — DB safe

            if (l.getLastBreedingDate()  == null) l.setLastBreedingDate(l.getConceptionDate());
            if (l.getFirstBreedingDate() == null) l.setFirstBreedingDate(l.getConceptionDate());

            // Recalculate due date from conception + category gestation
            l.recalculateDueDate();

        } else if (Boolean.TRUE.equals(l.getIsPregnant())) {
            // ── Fallback: checkbox ticked but no conception date known ─────────
            l.setStatus(Livestock.STATUS_PREGNANT);
            if (l.getPregnancyStatus() == null || l.getPregnancyStatus().isBlank()
                    || "CONFIRMED_PREGNANT".equals(l.getPregnancyStatus())) {
                l.setPregnancyStatus(PREG_STATUS_PREGNANT);  // "PREGNANT" — DB safe
            }

        } else {
            // ── Not pregnant ──────────────────────────────────────────────────
            l.setIsPregnant(false);
            l.setPregnancyStatus(PREG_STATUS_NOT_PREGNANT);  // "NOT_PREGNANT" — DB safe
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
}
