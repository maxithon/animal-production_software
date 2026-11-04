package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.FemaleReadyToBreedDTO;
import rw.animalproduct.animal.production.dto.IneligibleFemaleDTO;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.repository.FemalesReadyToBreedRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FemalesReadyToBreedService {

    private static final Logger log = LoggerFactory.getLogger(FemalesReadyToBreedService.class);

    private final FemalesReadyToBreedRepository repo;
    private final LivestockRepository livestockRepository;
    private final BreedingEligibilityService eligibilityService;

    public FemalesReadyToBreedService(FemalesReadyToBreedRepository repo,
                                      LivestockRepository livestockRepository,
                                      BreedingEligibilityService eligibilityService) {
        this.repo = repo;
        this.livestockRepository = livestockRepository;
        this.eligibilityService = eligibilityService;
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

    // ── "Not Yet Eligible" list ─────────────────────────────────────────
    // Every active female not already in the (view-backed) ready list,
    // paired with her specific blocking reason from
    // BreedingEligibilityService — the single source of truth shared with
    // the breeding form and readiness panel, so they can never disagree.
    public List<IneligibleFemaleDTO> getNotYetEligible() {
        Set<UUID> eligibleIds = getAllReadyToBreed().stream()
                .map(FemaleReadyToBreedDTO::getId)
                .collect(Collectors.toSet());

        return livestockRepository.findAll().stream()
                .filter(l -> "FEMALE".equalsIgnoreCase(l.getGender()))
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()))
                .filter(l -> !eligibleIds.contains(l.getId()))
                .map(l -> new IneligibleFemaleDTO(
                        l.getId(),
                        l.getTagNumber(),
                        l.getLivestockCategory() != null ? l.getLivestockCategory().getName() : "Uncategorized",
                        eligibilityService.getIneligibilityReason(l),
                        eligibilityService.getPostpartumReadyDate(l).orElse(null)))
                .filter(dto -> dto.getReason() != null)
                .sorted(Comparator.comparing(IneligibleFemaleDTO::getCategoryName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }
}
