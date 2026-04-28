package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.FemaleReadyToBreedDTO;
import rw.animalproduct.animal.production.repository.FemalesReadyToBreedRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FemalesReadyToBreedService {

    private static final Logger log = LoggerFactory.getLogger(FemalesReadyToBreedService.class);

    private final FemalesReadyToBreedRepository repo;

    public FemalesReadyToBreedService(FemalesReadyToBreedRepository repo) {
        this.repo = repo;
    }

    public List<FemaleReadyToBreedDTO> getAllReadyToBreed() {
        List<Object[]> rawResults = repo.findAllReadyToBreedRaw();
        log.info("Found {} raw female records", rawResults.size());
        if (!rawResults.isEmpty()) {
            log.info("First raw record has {} columns", rawResults.get(0).length);
        }
        return rawResults.stream()
                .map(FemaleReadyToBreedDTO::new)
                .collect(Collectors.toList());
    }

    public List<FemaleReadyToBreedDTO> search(String term) {
        if (term == null || term.isBlank()) return getAllReadyToBreed();
        return repo.searchReadyToBreedRaw(term).stream()
                .map(FemaleReadyToBreedDTO::new)
                .collect(Collectors.toList());
    }

    public long countNeverBred() {
        return getAllReadyToBreed().stream()
                .filter(FemaleReadyToBreedDTO::isNeverBred)
                .count();
    }
}