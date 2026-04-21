package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.FemaleReadyToBreedDTO;
import rw.animalproduct.animal.production.repository.FemalesReadyToBreedRepository;

import java.util.List;

@Service
public class FemalesReadyToBreedService {

    private final FemalesReadyToBreedRepository repo;

    public FemalesReadyToBreedService(FemalesReadyToBreedRepository repo) {
        this.repo = repo;
    }

    public List<FemaleReadyToBreedDTO> getAllReadyToBreed() {
        return repo.findAllReadyToBreedRaw()
                .stream()
                .map(FemaleReadyToBreedDTO::new)
                .toList();
    }

    public List<FemaleReadyToBreedDTO> search(String term) {
        if (term == null || term.isBlank()) return getAllReadyToBreed();
        return repo.searchReadyToBreedRaw(term)
                .stream()
                .map(FemaleReadyToBreedDTO::new)
                .toList();
    }

    public long countNeverBred() {
        return getAllReadyToBreed().stream()
                .filter(FemaleReadyToBreedDTO::isNeverBred)
                .count();
    }
}