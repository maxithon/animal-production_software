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
    private final BeneficiaryRepository beneficiaryRepository;
    private final LocationRepository               locationRepository;

    @Autowired
    public LivestockService(LivestockRepository livestockRepository,
                            LivestockCategoryRepository livestockCategoryRepository,
                            BeneficiaryRepository beneficiaryRepository,
                            LocationRepository locationRepository) {
        this.livestockRepository          = livestockRepository;
        this.livestockCategoryRepository  = livestockCategoryRepository;
        this.beneficiaryRepository = beneficiaryRepository;
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
        // Handle category relationship
        if (livestock.getLivestockCategoryIdValue() != null) {
            String categoryIdStr = livestock.getLivestockCategoryIdValue();
            try {
                UUID categoryId = UUID.fromString(categoryIdStr);
                livestockCategoryRepository.findById(categoryId)
                        .ifPresent(livestock::setLivestockCategory);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid category ID format: " + categoryIdStr);
            }
        }

        // Handle beneficiary relationship
        if (livestock.getBeneficiaryIdValue() != null) {
            String beneficiaryIdStr = livestock.getBeneficiaryIdValue();
            try {
                UUID beneficiaryId = UUID.fromString(beneficiaryIdStr);
                beneficiaryRepository.findById(beneficiaryId)
                        .ifPresent(livestock::setBeneficiary);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid beneficiary ID format: " + beneficiaryIdStr);
            }
        }

        if (livestock.getStatus() == null) {
            livestock.setStatus(Livestock.STATUS_ACTIVE);
        }
        if (livestock.getOffspringCount() == null) {
            livestock.setOffspringCount(0);
        }
        if (livestock.getIsDeleted() == null) {
            livestock.setIsDeleted(false);
        }
        if (livestock.getPregnancyStatus() == null) {
            livestock.setPregnancyStatus("NOT_PREGNANT");
        }
        if (livestock.getDateReceived() == null) {
            livestock.setDateReceived(LocalDate.now());
        }

        return livestockRepository.save(livestock);
    }

    public Livestock update(UUID id, Livestock updatedLivestock) {
        Optional<Livestock> existingOpt = livestockRepository.findById(id);
        if (existingOpt.isEmpty()) {
            throw new RuntimeException("Livestock not found with id: " + id);
        }

        Livestock existing = existingOpt.get();

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

        // Update category relationship
        if (updatedLivestock.getLivestockCategoryIdValue() != null) {
            String categoryIdStr = updatedLivestock.getLivestockCategoryIdValue();
            try {
                UUID categoryId = UUID.fromString(categoryIdStr);
                livestockCategoryRepository.findById(categoryId)
                        .ifPresent(existing::setLivestockCategory);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid category ID format: " + categoryIdStr);
            }
        }

        // Update beneficiary relationship
        if (updatedLivestock.getBeneficiaryIdValue() != null) {
            String beneficiaryIdStr = updatedLivestock.getBeneficiaryIdValue();
            try {
                UUID beneficiaryId = UUID.fromString(beneficiaryIdStr);
                beneficiaryRepository.findById(beneficiaryId)
                        .ifPresent(existing::setBeneficiary);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid beneficiary ID format: " + beneficiaryIdStr);
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
}
