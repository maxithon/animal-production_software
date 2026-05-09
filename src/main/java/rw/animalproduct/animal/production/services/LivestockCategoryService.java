package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockCategoryService {

    private final LivestockCategoryRepository livestockCategoryRepository;

    public LivestockCategoryService(LivestockCategoryRepository livestockCategoryRepository) {
        this.livestockCategoryRepository = livestockCategoryRepository;
    }

    public List<LivestockCategory> getAll() {
        return livestockCategoryRepository.findAll();
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

    public LivestockCategory addNew(LivestockCategory category) {
        return livestockCategoryRepository.save(category);
    }

    public LivestockCategory update(UUID id, LivestockCategory updatedData) {
        Optional<LivestockCategory> existingOpt = livestockCategoryRepository.findById(id);
        if (existingOpt.isPresent()) {
            LivestockCategory existing = existingOpt.get();
            existing.setName(updatedData.getName());
            existing.setCode(updatedData.getCode());
            existing.setGestationPeriodMonths(updatedData.getGestationPeriodMonths());
            existing.setMinBreedingAgeMonths(updatedData.getMinBreedingAgeMonths()); // ← ADDED
            existing.setDescription(updatedData.getDescription());
            return livestockCategoryRepository.save(existing);
        }
        return null;
    }

    public void delete(UUID id) {
        livestockCategoryRepository.deleteById(id);
    }

    public boolean existsByCode(String code) {
        return livestockCategoryRepository.existsByCode(code);
    }

    public boolean existsByName(String name) {
        return livestockCategoryRepository.existsByName(name);
    }
}
