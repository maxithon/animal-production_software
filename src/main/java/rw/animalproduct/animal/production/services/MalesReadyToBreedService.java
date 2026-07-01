package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.MaleReadyToBreedDTO;
import rw.animalproduct.animal.production.repository.MalesReadyToBreedRepository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MalesReadyToBreedService {

    private static final Logger log = LoggerFactory.getLogger(MalesReadyToBreedService.class);

    private final MalesReadyToBreedRepository repository;

    public MalesReadyToBreedService(MalesReadyToBreedRepository repository) {
        this.repository = repository;
    }

    public List<MaleReadyToBreedDTO> getAllReadyToBreed() {
        List<Object[]> rawResults = repository.findAllReadyToBreedRaw();
        log.info("Found {} raw male records", rawResults.size());
        if (!rawResults.isEmpty()) {
            log.info("First raw record has {} columns", rawResults.get(0).length);
        }
        return rawResults.stream()
                .map(MaleReadyToBreedDTO::new)
                .collect(Collectors.toList());
    }

    public List<MaleReadyToBreedDTO> search(String term) {
        if (term == null || term.isBlank()) return getAllReadyToBreed();
        List<Object[]> rawResults = repository.searchReadyToBreedRaw(term);
        return rawResults.stream()
                .map(MaleReadyToBreedDTO::new)
                .collect(Collectors.toList());
    }

    // ── Stats helpers ─────────────────────────────────────────────────────────

    public long countReadyToBreed() {
        return getAllReadyToBreed().size();
    }

    public double getAverageSuccessRate() {
        List<MaleReadyToBreedDTO> list = getAllReadyToBreed();
        if (list.isEmpty()) return 0.0;
        return list.stream()
                .mapToDouble(MaleReadyToBreedDTO::getSuccessRate)
                .average()
                .orElse(0.0);
    }

    public long countNeverBred() {
        return getAllReadyToBreed().stream()
                .filter(MaleReadyToBreedDTO::isNeverBred)
                .count();
    }

    /**
     * Returns an immutable empty list to be added to the Spring MVC model.
     * The Thymeleaf template uses this as the default value in:
     *   malesByCategory.getOrDefault(categoryName, emptyMaleList)
     *
     * This avoids using T(java.util.Collections).emptyList() directly in SpEL,
     * which is blocked by Thymeleaf 3.1+ for security reasons.
     */
    public List<MaleReadyToBreedDTO> emptyList() {
        return Collections.emptyList();
    }
}
