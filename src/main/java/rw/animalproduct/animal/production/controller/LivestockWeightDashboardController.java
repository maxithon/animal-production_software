package rw.animalproduct.animal.production.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockWeightHistory;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.services.LivestockWeightService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Weight dashboard — sidebar entry point for the Weight module.
 *
 * <p>Kept separate from {@link LivestockWeightController} so its class-level
 * {@code @RequestMapping("/livestock/{livestockId}/weight")} does not clash with
 * {@code /livestock/weight/dashboard}.</p>
 *
 * <p>Pagination is server-side. Only the requested page of animals is loaded,
 * then the latest weight for those animals is fetched in a single pass.
 * The stat tiles at the top ("Total", "Weighed", "Not yet weighed") are
 * computed globally — not page-scoped — so they are always correct.</p>
 */
@Controller
@RequestMapping("/livestock/weight")
public class LivestockWeightDashboardController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE     = 100;

    private final LivestockWeightService weightService;
    private final LivestockRepository    livestockRepository;

    public LivestockWeightDashboardController(LivestockWeightService weightService,
                                              LivestockRepository livestockRepository) {
        this.weightService = weightService;
        this.livestockRepository = livestockRepository;
    }

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(defaultValue = "0")  int page,
                            @RequestParam(defaultValue = "10") int size,
                            Model model) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(
                safePage, safeSize,
                Sort.by(Sort.Direction.ASC, "tagNumber"));

        Page<Livestock> animalPage = livestockRepository.findByIsDeletedFalse(pageable);
        List<Livestock> animalsOnPage = animalPage.getContent();

        // Latest weight for the animals on THIS page only.
        Map<UUID, LivestockWeightHistory> latestByAnimal = new HashMap<>();
        for (Livestock a : animalsOnPage) {
            weightService.latest(a.getId())
                    .ifPresent(w -> latestByAnimal.put(a.getId(), w));
        }

        // Global counts for the tiles. Uses lightweight aggregate queries where
        // possible; falls back to a stream for the "with weight" number.
        long totalAnimals  = animalPage.getTotalElements();
        long animalsWithW  = weightService.countAnimalsWithWeight();
        long animalsWithout = Math.max(0, totalAnimals - animalsWithW);

        model.addAttribute("animals",        animalsOnPage);
        model.addAttribute("latestByAnimal", latestByAnimal);

        model.addAttribute("totalAnimals",   totalAnimals);
        model.addAttribute("withWeight",     animalsWithW);
        model.addAttribute("withoutWeight",  animalsWithout);

        model.addAttribute("currentPage",    safePage);
        model.addAttribute("pageSize",       safeSize);
        model.addAttribute("totalPages",     animalPage.getTotalPages());

        // Pre-compute the list of page numbers to show (window of 7).
        int windowSize = 7;
        int start = Math.max(0, safePage - windowSize / 2);
        int end   = Math.min(animalPage.getTotalPages() - 1, start + windowSize - 1);
        if (end - start < windowSize - 1) {
            start = Math.max(0, end - windowSize + 1);
        }
        List<Integer> pageNumbers = (end < start)
                ? List.of()
                : java.util.stream.IntStream.rangeClosed(start, end)
                .boxed()
                .collect(Collectors.toList());
        model.addAttribute("pageNumbers", pageNumbers);

        return "livestock-weight-dashboard";
    }
}