package rw.animalproduct.animal.production.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.entity.Representative;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.repository.RepresentativeRepository;
import rw.animalproduct.animal.production.repository.UsersRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RepresentativeService {

    private final RepresentativeRepository representativeRepository;
    private final LocationRepository locationRepository;
    private final UsersRepository usersRepository;

    public RepresentativeService(RepresentativeRepository representativeRepository,
                                 LocationRepository locationRepository,
                                 UsersRepository usersRepository) {
        this.representativeRepository = representativeRepository;
        this.locationRepository = locationRepository;
        this.usersRepository = usersRepository;
    }

    public List<Representative> getAll() {
        return representativeRepository.findAll();
    }

    public Optional<Representative> getById(UUID id) {
        return representativeRepository.findById(id);
    }

    public Optional<Representative> getByNid(String nid) {
        return representativeRepository.findByNid(nid);
    }

    public List<Representative> getByLocation(UUID locationId) {
        return representativeRepository.findByLocationId(locationId);
    }

    public Representative addNew(Representative representatives) {
        // Set location
        String locationIdString = representatives.getLocationIdValue();
        if (locationIdString != null && !locationIdString.isEmpty()) {
            UUID locationId = UUID.fromString(locationIdString);
            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new RuntimeException("Location not found"));
            representatives.setLocation(location);
        }

        // Set created date and user
        representatives.setCreatedDate(new Date());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            Optional<Users> userOpt = usersRepository.findByEmail(email);
            userOpt.ifPresent(representatives::setCreatedBy);
        }

        return representativeRepository.save(representatives);
    }

    public Representative update(UUID id, Representative updatedData) {
        Optional<Representative> existingOpt = representativeRepository.findById(id);

        if (existingOpt.isPresent()) {
            Representative existing = existingOpt.get();

            existing.setFirstName(updatedData.getFirstName());
            existing.setLastName(updatedData.getLastName());
            existing.setGender(updatedData.getGender());
            existing.setMaritialStatus(updatedData.getMaritialStatus());
            existing.setNid(updatedData.getNid());
            existing.setPhone(updatedData.getPhone());
            existing.setEmail(updatedData.getEmail());
            existing.setOccupation(updatedData.getOccupation());              // ✅ Fixed (was icyoAkora)
            existing.setContractAgreement(updatedData.getContractAgreement()); // ✅ Fixed (was amasezerano)

            // Update location
            String locationIdString = updatedData.getLocationIdValue();
            if (locationIdString != null && !locationIdString.isEmpty()) {
                UUID locationId = UUID.fromString(locationIdString);
                Location location = locationRepository.findById(locationId)
                        .orElseThrow(() -> new RuntimeException("Location not found"));
                existing.setLocation(location);
            }

            return representativeRepository.save(existing);
        }

        return null;
    }

    public void delete(UUID id) {
        representativeRepository.deleteById(id);
    }

    public List<Representative> search(String keyword) {
        return representativeRepository.findByFirstNameContainingOrLastNameContaining(keyword, keyword);
    }
}