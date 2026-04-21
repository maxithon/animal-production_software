package rw.animalproduct.animal.production.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockTreatmentService {

    private final LivestockTreatmentRepository treatmentRepository;
    private final LivestockRepository          livestockRepository;
    private final MedicationRepository         medicationRepository;
    private final VeterinarianRepository       veterinarianRepository; // NEW
    private final UsersRepository              usersRepository;

    public LivestockTreatmentService(
            LivestockTreatmentRepository treatmentRepository,
            LivestockRepository          livestockRepository,
            MedicationRepository         medicationRepository,
            VeterinarianRepository       veterinarianRepository, // NEW
            UsersRepository              usersRepository
    ) {
        this.treatmentRepository    = treatmentRepository;
        this.livestockRepository    = livestockRepository;
        this.medicationRepository   = medicationRepository;
        this.veterinarianRepository = veterinarianRepository;  // NEW
        this.usersRepository        = usersRepository;
    }

    public List<LivestockTreatment> getAll() {
        return treatmentRepository.findAll();
    }

    public Optional<LivestockTreatment> getById(UUID id) {
        return treatmentRepository.findById(id);
    }

    public void addNew(LivestockTreatment treatment) {
        resolveAssociations(treatment);
        setAuditFields(treatment);
        treatmentRepository.save(treatment);
    }

    public void update(UUID id, LivestockTreatment updated) {
        treatmentRepository.findById(id).ifPresent(existing -> {

            resolveAssociations(updated);

            existing.setLivestock(updated.getLivestock());
            existing.setMedication(updated.getMedication());
            existing.setVeterinarian(updated.getVeterinarian());   // ← entity, not string
            existing.setTreatmentDate(updated.getTreatmentDate());
            existing.setNextTreatmentDate(updated.getNextTreatmentDate());
            existing.setDosage(updated.getDosage());
            existing.setDosageUnit(updated.getDosageUnit());
            existing.setFrequency(updated.getFrequency());
            existing.setTreatmentDuration(updated.getTreatmentDuration());
            existing.setTreatmentType(updated.getTreatmentType());
            existing.setTreatmentStatus(updated.getTreatmentStatus());
            existing.setTreatmentCost(updated.getTreatmentCost());
            existing.setDescription(updated.getDescription());
            existing.setIsPaid(updated.getIsPaid());
            existing.setPaymentDate(updated.getPaymentDate());

            treatmentRepository.save(existing);
        });
    }

    public void delete(UUID id) {
        treatmentRepository.deleteById(id);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void resolveAssociations(LivestockTreatment t) {

        // Livestock
        if (t.getLivestockIdValue() != null && !t.getLivestockIdValue().trim().isEmpty()) {
            UUID lid = UUID.fromString(t.getLivestockIdValue().trim());
            Livestock ls = livestockRepository.findById(lid)
                    .orElseThrow(() -> new IllegalArgumentException("Animal not found: " + lid));
            t.setLivestock(ls);
        }

        // Medication
        if (t.getMedicationIdValue() != null && !t.getMedicationIdValue().trim().isEmpty()) {
            UUID mid = UUID.fromString(t.getMedicationIdValue().trim());
            Medication med = medicationRepository.findById(mid)
                    .orElseThrow(() -> new IllegalArgumentException("Medication not found: " + mid));
            t.setMedication(med);

            if (t.getDosageUnit() == null && med.getDefaultDosageUnit() != null) {
                t.setDosageUnit(med.getDefaultDosageUnit());
            }
        }

        // Veterinarian (NEW — resolves UUID string → entity)
        if (t.getVeterinarianIdValue() != null && !t.getVeterinarianIdValue().trim().isEmpty()) {
            UUID vid = UUID.fromString(t.getVeterinarianIdValue().trim());
            Veterinarian vet = veterinarianRepository.findById(vid)
                    .orElseThrow(() -> new IllegalArgumentException("Veterinarian not found: " + vid));
            t.setVeterinarian(vet);
        } else {
            t.setVeterinarian(null);
        }
    }

    private void setAuditFields(LivestockTreatment t) {

        if (t.getCreatedAt() == null) {
            t.setCreatedAt(LocalDateTime.now());
        }

        if (t.getCreatedBy() == null) {
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()) {
                    String username = auth.getName();
                    Users user = usersRepository.findByEmail(username)
                            .orElseThrow(() -> new RuntimeException("User not found: " + username));
                    t.setCreatedBy(user);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
