package rw.animalproduct.animal.production.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.VLivestockWithAge;
import rw.animalproduct.animal.production.repository.VLivestockWithAgeRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class VLivestockWithAgeService {

    private final VLivestockWithAgeRepository repository;

    public VLivestockWithAgeService(VLivestockWithAgeRepository repository) {
        this.repository = repository;
    }

    public List<VLivestockWithAge> getAll() {
        return repository.findAll();
    }

    public Page<VLivestockWithAge> getAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Optional<VLivestockWithAge> getById(UUID id) {
        return repository.findById(id);
    }

    public List<VLivestockWithAge> getByLifecycleStage(String stage) {
        return repository.findByLifecycleStage(stage);
    }

    public Page<VLivestockWithAge> getByLifecycleStage(String stage, Pageable pageable) {
        return repository.findByLifecycleStage(stage, pageable);
    }

    public List<VLivestockWithAge> getByStatus(String status) {
        return repository.findByStatus(status);
    }

    public Page<VLivestockWithAge> getByStatus(String status, Pageable pageable) {
        return repository.findByStatus(status, pageable);
    }

    public List<VLivestockWithAge> getByCategory(String categoryName) {
        return repository.findByCategoryName(categoryName);
    }

    public Page<VLivestockWithAge> getByCategory(String categoryName, Pageable pageable) {
        return repository.findByCategoryName(categoryName, pageable);
    }

    public List<VLivestockWithAge> getByGender(String gender) {
        return repository.findByGender(gender);
    }

    public Page<VLivestockWithAge> search(String tagNumber, Pageable pageable) {
        return repository.findByTagNumberContaining(tagNumber, pageable);
    }

    // Statistics methods
    public Map<String, Long> getLifecycleStageStats() {
        Map<String, Long> stats = new HashMap<>();
        List<Object[]> results = repository.getLifecycleStageCounts();
        for (Object[] result : results) {
            stats.put((String) result[0], (Long) result[1]);
        }
        return stats;
    }

    public Map<String, Long> getCategoryStats() {
        Map<String, Long> stats = new HashMap<>();
        List<Object[]> results = repository.getCategoryCounts();
        for (Object[] result : results) {
            stats.put((String) result[0], (Long) result[1]);
        }
        return stats;
    }

    public Map<String, Long> getStatusStats() {
        Map<String, Long> stats = new HashMap<>();
        List<Object[]> results = repository.getStatusCounts();
        for (Object[] result : results) {
            stats.put((String) result[0], (Long) result[1]);
        }
        return stats;
    }

    public long countByLifecycleStage(String stage) {
        return repository.countByLifecycleStage(stage);
    }

    public long countByStatus(String status) {
        return repository.countByStatus(status);
    }
}