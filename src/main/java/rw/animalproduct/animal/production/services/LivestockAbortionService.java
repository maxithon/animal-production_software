package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockAbortion;
import rw.animalproduct.animal.production.repository.LivestockAbortionRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockAbortionService {

    private final LivestockAbortionRepository abortionRepository;
    private final LivestockRepository livestockRepository;

    public LivestockAbortionService(LivestockAbortionRepository abortionRepository,
                                    LivestockRepository livestockRepository) {
        this.abortionRepository = abortionRepository;
        this.livestockRepository = livestockRepository;
    }

    public List<LivestockAbortion> getAll() { return abortionRepository.findAll(); }

    public Optional<LivestockAbortion> getById(UUID id) { return abortionRepository.findById(id); }

    public List<LivestockAbortion> getByLivestock(UUID livestockId) {
        return abortionRepository.findByLivestockId(livestockId);
    }

    public LivestockAbortion addNew(LivestockAbortion abortion) {
        resolveAndSetLivestock(abortion);
        return abortionRepository.save(abortion);
    }

    public LivestockAbortion update(UUID id, LivestockAbortion updated) {
        Optional<LivestockAbortion> existing = abortionRepository.findById(id);
        if (existing.isPresent()) {
            LivestockAbortion a = existing.get();
            a.setAbortionDate(updated.getAbortionDate());
            a.setPregnancyNumber(updated.getPregnancyNumber());
            a.setExpectedBirthDate(updated.getExpectedBirthDate());
            a.setLivestockIdValue(updated.getLivestockIdValue());
            resolveAndSetLivestock(a);
            return abortionRepository.save(a);
        }
        return null;
    }

    public void delete(UUID id) { abortionRepository.deleteById(id); }

    private void resolveAndSetLivestock(LivestockAbortion abortion) {
        String idStr = abortion.getLivestockIdValue();
        if (idStr != null && !idStr.isEmpty()) {
            Livestock ls = livestockRepository.findById(UUID.fromString(idStr))
                    .orElseThrow(() -> new RuntimeException("Livestock not found"));
            abortion.setLivestock(ls);
        }
    }
}
