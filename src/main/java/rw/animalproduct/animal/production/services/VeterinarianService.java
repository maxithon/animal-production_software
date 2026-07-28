package rw.animalproduct.animal.production.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.entity.Veterinarian;
import rw.animalproduct.animal.production.repository.UsersRepository;
import rw.animalproduct.animal.production.repository.VeterinarianRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class VeterinarianService {

    private final VeterinarianRepository veterinarianRepository;
    private final UsersRepository usersRepository;

    public VeterinarianService(VeterinarianRepository veterinarianRepository,
                               UsersRepository usersRepository) {
        this.veterinarianRepository = veterinarianRepository;
        this.usersRepository = usersRepository;
    }

    public List<Veterinarian> getAll() {
        return veterinarianRepository.findAll();
    }

    public List<Veterinarian> getActive() {
        return veterinarianRepository.findByIsDeletedFalseAndIsActiveTrue();
    }

    public Optional<Veterinarian> getById(UUID id) {
        return veterinarianRepository.findById(id);
    }

    public List<Veterinarian> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return veterinarianRepository.findAllActive();
        }
        return veterinarianRepository.searchActive(query.trim());
    }

    public Veterinarian addNew(Veterinarian vet) {
        setAuditFields(vet);
        return veterinarianRepository.save(vet);
    }

    public void update(UUID id, Veterinarian updated) {
        veterinarianRepository.findById(id).ifPresent(existing -> {
            existing.setFirstName(updated.getFirstName());
            existing.setLastName(updated.getLastName());
            existing.setPhone(updated.getPhone());
            existing.setEmail(updated.getEmail());
            existing.setLicenseNumber(updated.getLicenseNumber());
            existing.setNationalId(updated.getNationalId());
            existing.setSpecialization(updated.getSpecialization());
            existing.setClinicName(updated.getClinicName());
            existing.setLocation(updated.getLocation());
            existing.setIsActive(updated.getIsActive());
            existing.setNotes(updated.getNotes());
            veterinarianRepository.save(existing);
        });
    }

    public void delete(UUID id) {
        veterinarianRepository.findById(id).ifPresent(vet -> {
            vet.setIsDeleted(true);
            veterinarianRepository.save(vet);
        });
    }

    private void setAuditFields(Veterinarian vet) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                String username = auth.getName();
                usersRepository.findByEmail(username).ifPresent(vet::setCreatedBy);
            }
        } catch (Exception ignored) {}
    }
}