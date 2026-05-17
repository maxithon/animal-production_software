package rw.animalproduct.animal.production.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;

import java.time.LocalDate;
import java.util.*;

@Service
public class LivestockService {

    private final LivestockRepository              livestockRepository;
    private final LivestockCategoryRepository      livestockCategoryRepository;
    private final BeneficiaryRepository            beneficiaryRepository;
    private final LocationRepository               locationRepository;

    @Autowired
    public LivestockService(LivestockRepository livestockRepository,
                            LivestockCategoryRepository livestockCategoryRepository,
                            BeneficiaryRepository beneficiaryRepository,
                            LocationRepository locationRepository) {
        this.livestockRepository          = livestockRepository;
        this.livestockCategoryRepository  = livestockCategoryRepository;
        this.beneficiaryRepository        = beneficiaryRepository;
        this.locationRepository           = locationRepository;
    }

    public List<Livestock> getAll() {
        return livestockRepository.findAll();
    }

    public Optional<Livestock> getById(UUID id) {
        return livestockRepository.findById(id);
    }

    public Optional<Livestock> getByTagNumber(String tagNumber) {
        return livestockRepository.findByTagNumber(tagNumber);
    }

    public Livestock addNew(Livestock livestock) {

        // ── Resolve category relationship ─────────────────────────────────────
        if (livestock.getLivestockCategoryIdValue() != null
                && !livestock.getLivestockCategoryIdValue().isBlank()) {
            try {
                UUID categoryId = UUID.fromString(livestock.getLivestockCategoryIdValue().trim());
                livestockCategoryRepository.findById(categoryId)
                        .ifPresent(livestock::setLivestockCategory);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid category ID format: "
                        + livestock.getLivestockCategoryIdValue());
            }
        }

        // ── Resolve beneficiary relationship ──────────────────────────────────
        if (livestock.getBeneficiaryIdValue() != null
                && !livestock.getBeneficiaryIdValue().isBlank()) {
            try {
                UUID beneficiaryId = UUID.fromString(livestock.getBeneficiaryIdValue().trim());
                beneficiaryRepository.findById(beneficiaryId)
                        .ifPresent(livestock::setBeneficiary);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid beneficiary ID format: "
                        + livestock.getBeneficiaryIdValue());
            }
        }

        // ── Safe defaults ─────────────────────────────────────────────────────
        if (livestock.getStatus() == null || livestock.getStatus().isBlank()) {
            livestock.setStatus(Livestock.STATUS_ACTIVE);
        }
        if (livestock.getOffspringCount() == null) {
            livestock.setOffspringCount(0);
        }
        if (livestock.getIsDeleted() == null) {
            livestock.setIsDeleted(false);
        }
        if (livestock.getIsPregnant() == null) {
            livestock.setIsPregnant(false);
        }
        if (livestock.getPregnancyStatus() == null || livestock.getPregnancyStatus().isBlank()) {
            livestock.setPregnancyStatus("NOT_PREGNANT");
        }
        if (livestock.getDateReceived() == null) {
            livestock.setDateReceived(LocalDate.now());
        }
        if (livestock.getIsDraft() == null) {
            livestock.setIsDraft(false);
        }

        return livestockRepository.save(livestock);
    }

    public Livestock update(UUID id, Livestock updatedLivestock) {
        Livestock existing = livestockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Livestock not found with id: " + id));

        existing.setTagNumber(updatedLivestock.getTagNumber());
        existing.setGender(updatedLivestock.getGender());
        existing.setStatus(updatedLivestock.getStatus());
        existing.setAcquisitionMethod(updatedLivestock.getAcquisitionMethod());
        existing.setDateReceived(updatedLivestock.getDateReceived());
        existing.setCurrentValue(updatedLivestock.getCurrentValue());
        existing.setLastBirthDate(updatedLivestock.getLastBirthDate());
        existing.setOffspringCount(updatedLivestock.getOffspringCount());
        existing.setPregnancyStatus(updatedLivestock.getPregnancyStatus());
        existing.setConceptionDate(updatedLivestock.getConceptionDate());
        existing.setLastBreedingDate(updatedLivestock.getLastBreedingDate());
        existing.setFirstBreedingDate(updatedLivestock.getFirstBreedingDate());
        existing.setExpectedDueDate(updatedLivestock.getExpectedDueDate());
        existing.setPhoto(updatedLivestock.getPhoto());
        existing.setSoldPrice(updatedLivestock.getSoldPrice());

        // Keep isPregnant in sync during updates
        if (updatedLivestock.getIsPregnant() != null) {
            existing.setIsPregnant(updatedLivestock.getIsPregnant());
        }

        // ── Copy insemination method ──────────────────────────────────────────
        // Always copy — null means "not recorded" which is a valid state,
        // and we don't want to accidentally keep a stale value if the user
        // cleared the field during edit.
        existing.setInseminationMethod(updatedLivestock.getInseminationMethod());

        // ── Update category relationship ──────────────────────────────────────
        if (updatedLivestock.getLivestockCategoryIdValue() != null
                && !updatedLivestock.getLivestockCategoryIdValue().isBlank()) {
            try {
                UUID categoryId = UUID.fromString(updatedLivestock.getLivestockCategoryIdValue().trim());
                livestockCategoryRepository.findById(categoryId)
                        .ifPresent(existing::setLivestockCategory);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid category ID format: "
                        + updatedLivestock.getLivestockCategoryIdValue());
            }
        }

        // ── Update beneficiary relationship ───────────────────────────────────
        if (updatedLivestock.getBeneficiaryIdValue() != null
                && !updatedLivestock.getBeneficiaryIdValue().isBlank()) {
            try {
                UUID beneficiaryId = UUID.fromString(updatedLivestock.getBeneficiaryIdValue().trim());
                beneficiaryRepository.findById(beneficiaryId)
                        .ifPresent(existing::setBeneficiary);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid beneficiary ID format: "
                        + updatedLivestock.getBeneficiaryIdValue());
            }
        }

        if (updatedLivestock.getLocation() != null) {
            existing.setLocation(updatedLivestock.getLocation());
        }

        return livestockRepository.save(existing);
    }

    public void delete(UUID id) {
        livestockRepository.deleteById(id);
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

    /**
     * Builds acquisition source description for livestock.
     */
    public static String buildAcquisitionSource(String acquisitionMethod,
                                                String externalSource,
                                                String motherTag) {
        if (Livestock.ACQ_BIRTH.equals(acquisitionMethod)) {
            if (motherTag != null && !motherTag.isBlank()) {
                return "Born on this farm — Mother: " + motherTag;
            }
            return "Born on this farm — Mother not recorded";
        } else if (Livestock.ACQ_PURCHASE.equals(acquisitionMethod)) {
            if (externalSource != null && !externalSource.isBlank()) {
                return "Purchased from: " + externalSource;
            }
            return "Purchased (source not recorded)";
        } else if (Livestock.ACQ_DONATION.equals(acquisitionMethod)) {
            if (externalSource != null && !externalSource.isBlank()) {
                return "Donated from: " + externalSource;
            }
            return "Donated (source not recorded)";
        } else if (Livestock.ACQ_TRANSFER.equals(acquisitionMethod)) {
            if (externalSource != null && !externalSource.isBlank()) {
                return "Transferred from: " + externalSource;
            }
            return "Transferred (source not recorded)";
        }
        return "Unknown origin";
    }
}