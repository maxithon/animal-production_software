package rw.animalproduct.animal.production.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.repository.LivestockRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lightweight AJAX search used by every "Animal (Tag)" live-search widget
 * (Sick, Breeding, Treatment, Sale forms, etc.).
 *
 * NOTE: If you already have a LivestockController mapped to "/livestock",
 * move this single @GetMapping method into that class instead of adding a
 * second controller with an overlapping @RequestMapping — Spring will throw
 * an ambiguous-mapping error at startup if two controllers both claim
 * "/livestock/search".
 */
@RestController
public class LivestockSearchController {

    private final LivestockRepository livestockRepository;

    public LivestockSearchController(LivestockRepository livestockRepository) {
        this.livestockRepository = livestockRepository;
    }

    @CrossOrigin
    @GetMapping("/livestock/search")
    public List<Map<String, Object>> search(@RequestParam("q") String q) {
        if (q == null || q.trim().length() < 1) {
            return List.of();
        }

        // Cap results to 20 — the dropdown is a picker, not a report.
        Pageable top20 = PageRequest.of(0, 20);

        // ✅ FIXED: this now calls searchByTagNumber(), the @Query method
        // added to LivestockRepository — the previous method name
        // (findByTagNumberContainingIgnoreCaseAndIsDeletedFalse) never
        // existed in the repository, which is what caused the compile error.
        List<Livestock> results = livestockRepository.searchByTagNumber(q.trim(), top20);

        return results.stream().map(this::toSearchResult).collect(Collectors.toList());
    }

    private Map<String, Object> toSearchResult(Livestock l) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", l.getId());
        m.put("tagNumber", l.getTagNumber());
        m.put("category", l.getLivestockCategory() != null ? l.getLivestockCategory().getName() : null);
        m.put("gender", l.getGender());
        m.put("status", l.getStatus());
        return m;
    }
}
