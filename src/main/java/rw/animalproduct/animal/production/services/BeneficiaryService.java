package rw.animalproduct.animal.production.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Beneficiary;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.entity.Representative;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.repository.BeneficiaryRepository;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.repository.RepresentativeRepository;
import rw.animalproduct.animal.production.repository.UsersRepository;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final RepresentativeRepository representativeRepository;
    private final LocationRepository locationRepository;
    private final UsersRepository usersRepository;

    public BeneficiaryService(BeneficiaryRepository beneficiaryRepository,
                              RepresentativeRepository representativeRepository,
                              LocationRepository locationRepository,
                              UsersRepository usersRepository) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.representativeRepository = representativeRepository;
        this.locationRepository = locationRepository;
        this.usersRepository = usersRepository;
    }

    public List<Beneficiary> getAll() {
        return beneficiaryRepository.findAll();
    }

    public Optional<Beneficiary> getById(UUID id) {
        return beneficiaryRepository.findById(id);
    }

    public Optional<Beneficiary> getByNid(String nid) {
        return beneficiaryRepository.findByNid(nid);
    }

    public List<Beneficiary> getByUhagarariye(UUID representativesId) {
        return beneficiaryRepository.findByRepresentativeId(representativesId);
    }

    // NEW: paginated version used by RepresentativeController.viewDetails()
    public Page<Beneficiary> getByUhagarariyePaginated(UUID representativesId, Pageable pageable) {
        return beneficiaryRepository.findByRepresentativeId(representativesId, pageable);
    }

    public List<Beneficiary> getByLocation(UUID locationId) {
        return beneficiaryRepository.findByLocationId(locationId);
    }

    public Map<UUID, Long> getBeneficiaryCountsByRepresentative() {
        List<Object[]> results = beneficiaryRepository.countByEachSupervisor();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : results) {
            UUID representativeId = (UUID) row[0];
            Long count = (Long) row[1];
            counts.put(representativeId, count);
        }
        return counts;
    }

    public Beneficiary addNew(Beneficiary beneficiaries) {
        String representativesIdString = beneficiaries.getRepresentativeIdValue();
        if (representativesIdString != null && !representativesIdString.isEmpty()) {
            UUID representativesId = UUID.fromString(representativesIdString);
            Representative representatives = representativeRepository.findById(representativesId)
                    .orElseThrow(() -> new RuntimeException("Uhagarariye aborora not found"));
            beneficiaries.setRepresentative(representatives);
        }

        if (beneficiaries.getLocation() == null && beneficiaries.getRepresentative() != null) {
            Location repLocation = beneficiaries.getRepresentative().getLocation();
            if (repLocation != null) {
                beneficiaries.setLocation(repLocation);
            }
        }

        beneficiaries.setCreatedDate(new Date());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            Optional<Users> userOpt = usersRepository.findByEmail(email);
            userOpt.ifPresent(beneficiaries::setCreatedBy);
        }

        return beneficiaryRepository.save(beneficiaries);
    }

    public Beneficiary update(UUID id, Beneficiary updatedData) {
        Optional<Beneficiary> existingOpt = beneficiaryRepository.findById(id);

        if (existingOpt.isPresent()) {
            Beneficiary existing = existingOpt.get();

            existing.setFirstName(updatedData.getFirstName());
            existing.setLastName(updatedData.getLastName());
            existing.setGender(updatedData.getGender());
            existing.setMaritialStatus(updatedData.getMaritialStatus());
            existing.setNid(updatedData.getNid());
            existing.setPhone(updatedData.getPhone());
            existing.setContractAgreement(updatedData.getContractAgreement());
            existing.setPhoto(updatedData.getPhoto());

            String representativesIdString = updatedData.getRepresentativeIdValue();
            if (representativesIdString != null && !representativesIdString.isEmpty()) {
                UUID representativesId = UUID.fromString(representativesIdString);
                Representative representatives = representativeRepository.findById(representativesId)
                        .orElseThrow(() -> new RuntimeException("Uhagarariye aborora not found"));
                existing.setRepresentative(representatives);
            }

            if (updatedData.getLocation() != null) {
                existing.setLocation(updatedData.getLocation());
            }

            return beneficiaryRepository.save(existing);
        }

        return null;
    }

    public void delete(UUID id) {
        beneficiaryRepository.deleteById(id);
    }

    public List<Beneficiary> search(String keyword) {
        return beneficiaryRepository.findByFirstNameContainingOrLastNameContaining(keyword, keyword);
    }
}