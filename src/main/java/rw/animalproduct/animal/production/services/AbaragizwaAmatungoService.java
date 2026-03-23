package rw.animalproduct.animal.production.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.AbaragizwaAmatungo;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.entity.UhagarariyeAborora;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.repository.AbaragizwaAmatungoRepository;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.repository.UhagarariyeAbororaRepository;
import rw.animalproduct.animal.production.repository.UsersRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AbaragizwaAmatungoService {

    private final AbaragizwaAmatungoRepository abaragizwaAmatungoRepository;
    private final UhagarariyeAbororaRepository uhagarariyeAbororaRepository;
    private final LocationRepository locationRepository;
    private final UsersRepository usersRepository;

    public AbaragizwaAmatungoService(AbaragizwaAmatungoRepository abaragizwaAmatungoRepository,
                                     UhagarariyeAbororaRepository uhagarariyeAbororaRepository,
                                     LocationRepository locationRepository,
                                     UsersRepository usersRepository) {
        this.abaragizwaAmatungoRepository = abaragizwaAmatungoRepository;
        this.uhagarariyeAbororaRepository = uhagarariyeAbororaRepository;
        this.locationRepository = locationRepository;
        this.usersRepository = usersRepository;
    }

    public List<AbaragizwaAmatungo> getAll() {
        return abaragizwaAmatungoRepository.findAll();
    }

    public Optional<AbaragizwaAmatungo> getById(UUID id) {
        return abaragizwaAmatungoRepository.findById(id);
    }

    public Optional<AbaragizwaAmatungo> getByNid(String nid) {
        return abaragizwaAmatungoRepository.findByNid(nid);
    }

    public List<AbaragizwaAmatungo> getByUhagarariye(UUID uhagarariyeId) {
        return abaragizwaAmatungoRepository.findByUhagarariyeAbororaId(uhagarariyeId);
    }

    public List<AbaragizwaAmatungo> getByLocation(UUID locationId) {
        return abaragizwaAmatungoRepository.findByLocationId(locationId);
    }

    public AbaragizwaAmatungo addNew(AbaragizwaAmatungo abaragizwa) {
        // Set uhagarariye aborora
        String uhagarariyeIdString = abaragizwa.getUhagarariyeAbororaIdValue();
        if (uhagarariyeIdString != null && !uhagarariyeIdString.isEmpty()) {
            UUID uhagarariyeId = UUID.fromString(uhagarariyeIdString);
            UhagarariyeAborora uhagarariye = uhagarariyeAbororaRepository.findById(uhagarariyeId)
                    .orElseThrow(() -> new RuntimeException("Uhagarariye aborora not found"));
            abaragizwa.setUhagarariyeAborora(uhagarariye);
        }

        // Set location if not already set
        if (abaragizwa.getLocation() == null && abaragizwa.getUhagarariyeAborora() != null) {
            // Optionally inherit location from representative
            Location repLocation = abaragizwa.getUhagarariyeAborora().getLocation();
            if (repLocation != null) {
                abaragizwa.setLocation(repLocation);
            }
        }

        // Set created date and user
        abaragizwa.setCreatedDate(new Date());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            Optional<Users> userOpt = usersRepository.findByEmail(email);
            userOpt.ifPresent(abaragizwa::setCreatedBy);
        }

        return abaragizwaAmatungoRepository.save(abaragizwa);
    }

    public AbaragizwaAmatungo update(UUID id, AbaragizwaAmatungo updatedData) {
        Optional<AbaragizwaAmatungo> existingOpt = abaragizwaAmatungoRepository.findById(id);

        if (existingOpt.isPresent()) {
            AbaragizwaAmatungo existing = existingOpt.get();

            existing.setFirstName(updatedData.getFirstName());
            existing.setLastName(updatedData.getLastName());
            existing.setGender(updatedData.getGender());
            existing.setMaritialStatus(updatedData.getMaritialStatus());
            existing.setNid(updatedData.getNid());
            existing.setPhone(updatedData.getPhone());
            existing.setAmasezerano(updatedData.getAmasezerano());
            existing.setPhoto(updatedData.getPhoto());

            // Update uhagarariye aborora
            String uhagarariyeIdString = updatedData.getUhagarariyeAbororaIdValue();
            if (uhagarariyeIdString != null && !uhagarariyeIdString.isEmpty()) {
                UUID uhagarariyeId = UUID.fromString(uhagarariyeIdString);
                UhagarariyeAborora uhagarariye = uhagarariyeAbororaRepository.findById(uhagarariyeId)
                        .orElseThrow(() -> new RuntimeException("Uhagarariye aborora not found"));
                existing.setUhagarariyeAborora(uhagarariye);
            }

            // Update location
            if (updatedData.getLocation() != null) {
                existing.setLocation(updatedData.getLocation());
            }

            return abaragizwaAmatungoRepository.save(existing);
        }

        return null;
    }

    public void delete(UUID id) {
        abaragizwaAmatungoRepository.deleteById(id);
    }

    public List<AbaragizwaAmatungo> search(String keyword) {
        return abaragizwaAmatungoRepository.findByFirstNameContainingOrLastNameContaining(keyword, keyword);
    }
}