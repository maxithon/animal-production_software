package rw.animalproduct.animal.production.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.services.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class LivestockCategoryAnimalsReportController {

    private final LivestockRepository livestockRepository;
    private final LivestockCategoryRepository livestockCategoryRepository;
    private final LivestockBirthService birthService;
    private final LivestockSaleService saleService;
    private final LivestockTreatmentService treatmentService;

    public LivestockCategoryAnimalsReportController(LivestockRepository livestockRepository,
                                                    LivestockCategoryRepository livestockCategoryRepository,
                                                    LivestockBirthService birthService,
                                                    LivestockSaleService saleService,
                                                    LivestockTreatmentService treatmentService) {
        this.livestockRepository = livestockRepository;
        this.livestockCategoryRepository = livestockCategoryRepository;
        this.birthService = birthService;
        this.saleService = saleService;
        this.treatmentService = treatmentService;
    }

    private <T> List<T> safe(List<T> l) { return l != null ? l : Collections.emptyList(); }
    private BigDecimal bd(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    // ── No category selected yet ────────────────────────────────────────
    @GetMapping("/livestock/category-animals-report")
    public String categoryAnimalsReport(Model model) {
        List<Livestock> all = safe(livestockRepository.findAll());
        model.addAttribute("categories", buildCategoryOptions(all));
        model.addAttribute("selectedCategory", null);
        return "livestock-category-animals-report";
    }

    // ── A category is selected ──────────────────────────────────────────
    @GetMapping("/livestock/category-animals-report/{categoryId}")
    public String categoryAnimalsReport(@PathVariable("categoryId") UUID categoryId, Model model) {
        List<Livestock> all = safe(livestockRepository.findAll());
        model.addAttribute("categories", buildCategoryOptions(all));

        LivestockCategory selectedCategory = livestockCategoryRepository.findById(categoryId).orElse(null);
        model.addAttribute("selectedCategory", selectedCategory);

        if (selectedCategory == null) {
            model.addAttribute("animals", Collections.emptyList());
            return "livestock-category-animals-report";
        }

        List<Livestock> animals = all.stream()
                .filter(l -> l.getLivestockCategory() != null
                        && categoryId.equals(l.getLivestockCategory().getId()))
                .sorted(Comparator.comparing(
                        l -> l.getTagNumber() != null ? l.getTagNumber() : "",
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        model.addAttribute("animals", animals);

        Set<UUID> animalIds = animals.stream().map(Livestock::getId).collect(Collectors.toSet());

        // ── Status counts ────────────────────────────────────────────────
        long activeCount = animals.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count();
        long soldCount = animals.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count();
        model.addAttribute("activeCount", activeCount);
        model.addAttribute("soldCount", soldCount);

        // ── Births on this category's animals ──────────────────────────
        List<LivestockBirth> allBirths = safe(birthService.getAll());
        List<LivestockBirth> categoryBirths = allBirths.stream()
                .filter(b -> b.getLivestock() != null && animalIds.contains(b.getLivestock().getId()))
                .collect(Collectors.toList());
        long totalBornCount = categoryBirths.size();
        long totalOffspringCount = categoryBirths.stream()
                .mapToLong(b -> b.getOffspringCount() != null ? b.getOffspringCount() : 0)
                .sum();
        model.addAttribute("totalBornCount", totalBornCount);
        model.addAttribute("totalOffspringCount", totalOffspringCount);

        // Map: mother-animal id -> most recent birth date (for the "Born on Farm" column)
        Map<UUID, LocalDate> animalBirthMap = new HashMap<>();
        for (LivestockBirth b : categoryBirths) {
            if (b.getLivestock() == null || b.getBirthDate() == null) continue;
            UUID id = b.getLivestock().getId();
            LocalDate existing = animalBirthMap.get(id);
            if (existing == null || b.getBirthDate().isAfter(existing)) {
                animalBirthMap.put(id, b.getBirthDate());
            }
        }
        model.addAttribute("animalBirthMap", animalBirthMap);

        // ── Sales for this category's animals ──────────────────────────
        List<LivestockSale> allSales = safe(saleService.getAll());
        List<LivestockSale> categorySales = allSales.stream()
                .filter(s -> s.getLivestock() != null && animalIds.contains(s.getLivestock().getId()))
                .collect(Collectors.toList());
        BigDecimal totalSaleRevenue = categorySales.stream()
                .map(LivestockSale::getSalePrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("totalSaleRevenue", totalSaleRevenue);

        Map<UUID, BigDecimal> animalSaleMap = new HashMap<>();
        for (LivestockSale s : categorySales) {
            if (s.getLivestock() == null || s.getSalePrice() == null) continue;
            animalSaleMap.put(s.getLivestock().getId(), s.getSalePrice());
        }
        model.addAttribute("animalSaleMap", animalSaleMap);

        // ── Treatments for this category's animals ──────────────────────
        List<LivestockTreatment> allTreatments = safe(treatmentService.getAll());
        List<LivestockTreatment> categoryTreatments = allTreatments.stream()
                .filter(t -> t.getLivestock() != null && animalIds.contains(t.getLivestock().getId()))
                .collect(Collectors.toList());
        long treatmentCount = categoryTreatments.size();
        BigDecimal totalTreatmentCost = categoryTreatments.stream()
                .map(LivestockTreatment::getTreatmentCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("treatmentCount", treatmentCount);
        model.addAttribute("totalTreatmentCost", totalTreatmentCost);

        Map<UUID, Long> animalTreatCountMap = categoryTreatments.stream()
                .filter(t -> t.getLivestock() != null)
                .collect(Collectors.groupingBy(t -> t.getLivestock().getId(), Collectors.counting()));
        model.addAttribute("animalTreatCountMap", animalTreatCountMap);

        // ── Active stock value ────────────────────────────────────────
        BigDecimal activeStockValue = animals.stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()) && l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("activeStockValue", activeStockValue);

        // ── Estimated born-animal value: use current value of animals that
        //    were themselves born on the farm (present in animalBirthMap) ──
        BigDecimal bornAnimalValue = animals.stream()
                .filter(l -> animalBirthMap.containsKey(l.getId())
                        && l.getCurrentValue() != null
                        && !Livestock.STATUS_DEAD.equals(l.getStatus())
                        && !Livestock.STATUS_SOLD.equals(l.getStatus()))
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("bornAnimalValue", bornAnimalValue);

        // ── Income / Expense / Net ──────────────────────────────────────
        BigDecimal totalIncome = totalSaleRevenue.add(activeStockValue).add(bornAnimalValue);
        BigDecimal netPosition = totalIncome.subtract(totalTreatmentCost);
        model.addAttribute("totalIncome", totalIncome);
        model.addAttribute("netPosition", netPosition);

        String businessStatus;
        if (netPosition.compareTo(BigDecimal.ZERO) > 0) businessStatus = "gain";
        else if (netPosition.compareTo(BigDecimal.ZERO) < 0) businessStatus = "loss";
        else businessStatus = "neutral";
        model.addAttribute("businessStatus", businessStatus);

        return "livestock-category-animals-report";
    }

    // ── Build the category selector list (id, name, livestockCount) ────
    private List<CategoryOption> buildCategoryOptions(List<Livestock> all) {
        Map<UUID, Long> countByCategory = all.stream()
                .filter(l -> l.getLivestockCategory() != null)
                .collect(Collectors.groupingBy(l -> l.getLivestockCategory().getId(), Collectors.counting()));

        List<LivestockCategory> categories = safe(livestockCategoryRepository.findAll());
        List<CategoryOption> options = new ArrayList<>();
        for (LivestockCategory cat : categories) {
            CategoryOption opt = new CategoryOption();
            opt.setId(cat.getId());
            opt.setName(cat.getName());
            opt.setLivestockCount(countByCategory.getOrDefault(cat.getId(), 0L));
            options.add(opt);
        }
        options.sort(Comparator.comparing(CategoryOption::getName, String.CASE_INSENSITIVE_ORDER));
        return options;
    }

    // ── DTO consumed by the th:each category selector ──────────────────
    public static class CategoryOption {
        private UUID id;
        private String name;
        private long livestockCount;

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public long getLivestockCount() { return livestockCount; }
        public void setLivestockCount(long livestockCount) { this.livestockCount = livestockCount; }
    }
}