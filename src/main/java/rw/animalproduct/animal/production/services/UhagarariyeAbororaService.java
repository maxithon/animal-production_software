package rw.animalproduct.animal.production.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.entity.UhagarariyeAborora;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.repository.UhagarariyeAbororaRepository;
import rw.animalproduct.animal.production.repository.UsersRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UhagarariyeAbororaService {

    private final UhagarariyeAbororaRepository uhagarariyeAbororaRepository;
    private final LocationRepository locationRepository;
    private final UsersRepository usersRepository;

    public UhagarariyeAbororaService(UhagarariyeAbororaRepository uhagarariyeAbororaRepository,
                                     LocationRepository locationRepository,
                                     UsersRepository usersRepository) {
        this.uhagarariyeAbororaRepository = uhagarariyeAbororaRepository;
        this.locationRepository = locationRepository;
        this.usersRepository = usersRepository;
    }

    public List<UhagarariyeAborora> getAll() {
        return uhagarariyeAbororaRepository.findAll();
    }

    public Optional<UhagarariyeAborora> getById(UUID id) {
        return uhagarariyeAbororaRepository.findById(id);
    }

    public Optional<UhagarariyeAborora> getByNid(String nid) {
        return uhagarariyeAbororaRepository.findByNid(nid);
    }

    public List<UhagarariyeAborora> getByLocation(UUID locationId) {
        return uhagarariyeAbororaRepository.findByLocationId(locationId);
    }

    public UhagarariyeAborora addNew(UhagarariyeAborora uhagarariye) {
        // Set location
        String locationIdString = uhagarariye.getLocationIdValue();
        if (locationIdString != null && !locationIdString.isEmpty()) {
            UUID locationId = UUID.fromString(locationIdString);
            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new RuntimeException("Location not found"));
            uhagarariye.setLocation(location);
        }

        // Set created date and user
        uhagarariye.setCreatedDate(new Date());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            Optional<Users> userOpt = usersRepository.findByEmail(email);
            userOpt.ifPresent(uhagarariye::setCreatedBy);
        }

        return uhagarariyeAbororaRepository.save(uhagarariye);
    }

    public UhagarariyeAborora update(UUID id, UhagarariyeAborora updatedData) {
        Optional<UhagarariyeAborora> existingOpt = uhagarariyeAbororaRepository.findById(id);

        if (existingOpt.isPresent()) {
            UhagarariyeAborora existing = existingOpt.get();

            existing.setFirstName(updatedData.getFirstName());
            existing.setLastName(updatedData.getLastName());
            existing.setGender(updatedData.getGender());
            existing.setMaritialStatus(updatedData.getMaritialStatus());
            existing.setNid(updatedData.getNid());
            existing.setPhone(updatedData.getPhone());
            existing.setEmail(updatedData.getEmail());
            existing.setIcyoAkora(updatedData.getIcyoAkora());
            existing.setAmasezerano(updatedData.getAmasezerano());

            // Update location
            String locationIdString = updatedData.getLocationIdValue();
            if (locationIdString != null && !locationIdString.isEmpty()) {
                UUID locationId = UUID.fromString(locationIdString);
                Location location = locationRepository.findById(locationId)
                        .orElseThrow(() -> new RuntimeException("Location not found"));
                existing.setLocation(location);
            }

            return uhagarariyeAbororaRepository.save(existing);
        }

        return null;
    }

    public void delete(UUID id) {
        uhagarariyeAbororaRepository.deleteById(id);
    }

    public List<UhagarariyeAborora> search(String keyword) {
        return uhagarariyeAbororaRepository.findByFirstNameContainingOrLastNameContaining(keyword, keyword);
    }
}