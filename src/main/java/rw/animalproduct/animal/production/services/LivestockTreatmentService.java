package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockTreatment;
import rw.animalproduct.animal.production.repository.LivestockTreatmentRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockTreatmentService {

    private final LivestockTreatmentRepository treatmentRepository;
    private final LivestockRepository livestockRepository;

    public LivestockTreatmentService(LivestockTreatmentRepository treatmentRepository,
                                     LivestockRepository livestockRepository) {
        this.treatmentRepository = treatmentRepository;
        this.livestockRepository = livestockRepository;
    }

    public List<LivestockTreatment> getAll() { return treatmentRepository.findAll(); }

    public Optional<LivestockTreatment> getById(UUID id) { return treatmentRepository.findById(id); }

    public List<LivestockTreatment> getByLivestock(UUID livestockId) {
        return treatmentRepository.findByLivestockId(livestockId);
    }

    public LivestockTreatment addNew(LivestockTreatment treatment) {
        resolveAndSetLivestock(treatment);
        return treatmentRepository.save(treatment);
    }

    public LivestockTreatment update(UUID id, LivestockTreatment updated) {
        Optional<LivestockTreatment> existing = treatmentRepository.findById(id);
        if (existing.isPresent()) {
            LivestockTreatment t = existing.get();
            t.setTreatmentDate(updated.getTreatmentDate());
            t.setMedication(updated.getMedication());
            t.setTreatmentCost(updated.getTreatmentCost());
            t.setNextTreatmentDate(updated.getNextTreatmentDate());
            t.setLivestockIdValue(updated.getLivestockIdValue());
            resolveAndSetLivestock(t);
            return treatmentRepository.save(t);
        }
        return null;
    }

    public void delete(UUID id) { treatmentRepository.deleteById(id); }

    private void resolveAndSetLivestock(LivestockTreatment treatment) {
        String idStr = treatment.getLivestockIdValue();
        if (idStr != null && !idStr.isEmpty()) {
            Livestock ls = livestockRepository.findById(UUID.fromString(idStr))
                    .orElseThrow(() -> new RuntimeException("Livestock not found"));
            treatment.setLivestock(ls);
        }
    }
}
