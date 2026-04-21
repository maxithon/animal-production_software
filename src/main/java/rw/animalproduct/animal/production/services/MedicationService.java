package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Medication;
import rw.animalproduct.animal.production.repository.MedicationRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MedicationService {

    private final MedicationRepository medicationRepository;

    public MedicationService(MedicationRepository medicationRepository) {
        this.medicationRepository = medicationRepository;
    }

    public List<Medication> getAll() {
        return medicationRepository.findAllByOrderByNameAsc();
    }

    public List<Medication> getActive() {
        return medicationRepository.findByIsActiveTrueOrderByNameAsc();
    }

    public Optional<Medication> getById(UUID id) {
        return medicationRepository.findById(id);
    }

    public Medication save(Medication medication) {
        return medicationRepository.save(medication);
    }

    public void update(UUID id, Medication updated) {
        medicationRepository.findById(id).ifPresent(existing -> {
            existing.setName(updated.getName());
            existing.setGenericName(updated.getGenericName());
            existing.setCategory(updated.getCategory());
            existing.setDefaultDosage(updated.getDefaultDosage());
            existing.setDefaultDosageUnit(updated.getDefaultDosageUnit());
            existing.setManufacturer(updated.getManufacturer());
            existing.setDescription(updated.getDescription());
            existing.setIsActive(updated.getIsActive());
            medicationRepository.save(existing);
        });
    }

    public void delete(UUID id) {
        medicationRepository.deleteById(id);
    }

    public boolean nameExists(String name) {
        return medicationRepository.existsByNameIgnoreCase(name);
    }
}
