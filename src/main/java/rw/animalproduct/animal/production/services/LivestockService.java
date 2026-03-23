package rw.animalproduct.animal.production.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class LivestockService {

    private final LivestockRepository livestockRepository;
    private final LivestockCategoryRepository livestockCategoryRepository;
    private final AbaragizwaAmatungoRepository abaragizwaAmatungoRepository;
    private final LocationRepository locationRepository;
    private final UsersRepository usersRepository;

    public LivestockService(LivestockRepository livestockRepository,
                            LivestockCategoryRepository livestockCategoryRepository,
                            AbaragizwaAmatungoRepository abaragizwaAmatungoRepository,
                            LocationRepository locationRepository,
                            UsersRepository usersRepository) {
        this.livestockRepository = livestockRepository;
        this.livestockCategoryRepository = livestockCategoryRepository;
        this.abaragizwaAmatungoRepository = abaragizwaAmatungoRepository;
        this.locationRepository = locationRepository;
        this.usersRepository = usersRepository;
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

    public List<Livestock> getByStatus(String status) {
        return livestockRepository.findByStatus(status);
    }

    public List<Livestock> getByCategory(UUID categoryId) {
        return livestockRepository.findByLivestockCategoryId(categoryId);
    }

    public List<Livestock> getByAbaragizwa(UUID abaragizwaId) {
        return livestockRepository.findByAbaragizwaAmatungoId(abaragizwaId);
    }

    public Livestock addNew(Livestock livestock) {
        // Set livestock category
        String categoryIdStr = livestock.getLivestockCategoryIdValue();
        if (categoryIdStr != null && !categoryIdStr.isEmpty()) {
            UUID categoryId = UUID.fromString(categoryIdStr);
            LivestockCategory category = livestockCategoryRepository.findById(categoryId)
                    .orElseThrow(() -> new RuntimeException("Livestock category not found"));
            livestock.setLivestockCategory(category);
        }

        // Set beneficiary (abaragizwa)
        String abaragizwaIdStr = livestock.getAbaragizwaAmatungoIdValue();
        if (abaragizwaIdStr != null && !abaragizwaIdStr.isEmpty()) {
            UUID abaragizwaId = UUID.fromString(abaragizwaIdStr);
            AbaragizwaAmatungo abaragizwa = abaragizwaAmatungoRepository.findById(abaragizwaId)
                    .orElseThrow(() -> new RuntimeException("Beneficiary not found"));
            livestock.setAbaragizwaAmatungo(abaragizwa);

            // Inherit location from beneficiary if not set
            if (livestock.getLocation() == null && abaragizwa.getLocation() != null) {
                livestock.setLocation(abaragizwa.getLocation());
            }
        }

        // Set created by
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            Optional<Users> userOpt = usersRepository.findByEmail(email);
            userOpt.ifPresent(livestock::setCreatedBy);
        }

        // Default status
        if (livestock.getStatus() == null || livestock.getStatus().isEmpty()) {
            livestock.setStatus("ACTIVE");
        }

        // Default offspring count
        if (livestock.getOffspringCount() == null) {
            livestock.setOffspringCount(0);
        }

        // Default isPregnant
        if (livestock.getIsPregnant() == null) {
            livestock.setIsPregnant(false);
        }

        return livestockRepository.save(livestock);
    }

    public Livestock update(UUID id, Livestock updatedData) {
        Optional<Livestock> existingOpt = livestockRepository.findById(id);
        if (existingOpt.isPresent()) {
            Livestock existing = existingOpt.get();

            existing.setTagNumber(updatedData.getTagNumber());
            existing.setGender(updatedData.getGender());
            existing.setPhoto(updatedData.getPhoto());
            existing.setDateReceived(updatedData.getDateReceived());
            existing.setLastBirthDate(updatedData.getLastBirthDate());
            existing.setOffspringCount(updatedData.getOffspringCount());
            existing.setIsPregnant(updatedData.getIsPregnant());
            existing.setPregnancyMonths(updatedData.getPregnancyMonths());
            existing.setCurrentValue(updatedData.getCurrentValue());
            existing.setAcquisitionMethod(updatedData.getAcquisitionMethod());
            existing.setStatus(updatedData.getStatus());

            // Update category
            String categoryIdStr = updatedData.getLivestockCategoryIdValue();
            if (categoryIdStr != null && !categoryIdStr.isEmpty()) {
                UUID categoryId = UUID.fromString(categoryIdStr);
                LivestockCategory category = livestockCategoryRepository.findById(categoryId)
                        .orElseThrow(() -> new RuntimeException("Livestock category not found"));
                existing.setLivestockCategory(category);
            }

            // Update beneficiary
            String abaragizwaIdStr = updatedData.getAbaragizwaAmatungoIdValue();
            if (abaragizwaIdStr != null && !abaragizwaIdStr.isEmpty()) {
                UUID abaragizwaId = UUID.fromString(abaragizwaIdStr);
                AbaragizwaAmatungo abaragizwa = abaragizwaAmatungoRepository.findById(abaragizwaId)
                        .orElseThrow(() -> new RuntimeException("Beneficiary not found"));
                existing.setAbaragizwaAmatungo(abaragizwa);
            }

            // Update location
            if (updatedData.getLocation() != null) {
                existing.setLocation(updatedData.getLocation());
            }

            return livestockRepository.save(existing);
        }
        return null;
    }

    public void delete(UUID id) {
        livestockRepository.deleteById(id);
    }

    public List<Livestock> search(String tagNumber) {
        return livestockRepository.findByTagNumberContaining(tagNumber);
    }

    public long countByStatus(String status) {
        return livestockRepository.countByStatus(status);
    }
}
