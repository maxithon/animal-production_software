package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSickRepository;
import rw.animalproduct.animal.production.services.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.Map;


@Controller
@RequestMapping("/livestock")
public class LivestockEventsController {

    private final LivestockRepository       livestockRepository;
    private final LivestockAbortionService  abortionService;
    private final LivestockDeathService     deathService;
    private final LivestockSaleService      saleService;
    private final LivestockTreatmentService treatmentService;
    private final LivestockSickService      sickService;
    private final LivestockSickRepository   sickRepository;

    public LivestockEventsController(LivestockRepository livestockRepository,
                                     LivestockAbortionService abortionService,
                                     LivestockDeathService deathService,
                                     LivestockSaleService saleService,
                                     LivestockTreatmentService treatmentService,
                                     LivestockSickService sickService,
                                     LivestockSickRepository sickRepository) {
        this.livestockRepository = livestockRepository;
        this.abortionService     = abortionService;
        this.deathService        = deathService;
        this.saleService         = saleService;
        this.treatmentService    = treatmentService;
        this.sickService         = sickService;
        this.sickRepository      = sickRepository;
    }

    // ── Model helpers ─────────────────────────────────────────────────

    private void addLivestockToModel(Model model) {
        List<Livestock> available = livestockRepository.findAll().stream()
                .filter(ls -> !Livestock.STATUS_DEAD.equals(ls.getStatus())
                        && !"SOLD".equals(ls.getStatus()))
                .collect(Collectors.toList());
        model.addAttribute("livestockList", available);
    }

    private void addAllLivestockToModel(Model model) {
        model.addAttribute("livestockList", livestockRepository.findAll());
    }

    // =========================================================================
    // ABORTIONS
    // =========================================================================

    @GetMapping("/abortions")
    public String listAbortions(Model model) {
        model.addAttribute("abortions", abortionService.getAll());
        model.addAttribute("abortion", new LivestockAbortion());
        addLivestockToModel(model);
        return "livestock-abortions-list";
    }

    @PostMapping("/abortions/new")
    public String saveAbortion(@Valid @ModelAttribute("abortion") LivestockAbortion abortion,
                               BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("abortions", abortionService.getAll());
            addLivestockToModel(model);
            return "livestock-abortions-list";
        }
        abortionService.addNew(abortion);
        ra.addFlashAttribute("success", "Abortion record saved successfully!");
        return "redirect:/livestock/abortions";
    }

    @GetMapping("/abortions/edit/{id}")
    public String editAbortionForm(@PathVariable UUID id, Model model) {
        Optional<LivestockAbortion> opt = abortionService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/abortions";
        LivestockAbortion a = opt.get();
        if (a.getLivestock() != null) a.setLivestockIdValue(a.getLivestock().getId().toString());
        model.addAttribute("abortion", a);
        addAllLivestockToModel(model);
        return "livestock-abortion-edit";
    }

    @PostMapping("/abortions/update/{id}")
    public String updateAbortion(@PathVariable UUID id,
                                 @Valid @ModelAttribute("abortion") LivestockAbortion abortion,
                                 BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            addAllLivestockToModel(model);
            return "livestock-abortion-edit";
        }
        abortionService.update(id, abortion);
        ra.addFlashAttribute("success", "Abortion record updated successfully!");
        return "redirect:/livestock/abortions";
    }

    @PostMapping("/abortions/delete/{id}")
    public String deleteAbortion(@PathVariable UUID id, RedirectAttributes ra) {
        abortionService.delete(id);
        ra.addFlashAttribute("success", "Abortion record deleted.");
        return "redirect:/livestock/abortions";
    }

    // =========================================================================
    // DEATHS
    // =========================================================================

    @GetMapping("/deaths")
    public String listDeaths(Model model) {
        model.addAttribute("deaths", deathService.getAll());
        model.addAttribute("death", new LivestockDeath());
        addLivestockToModel(model);
        return "livestock-deaths-list";
    }

    @PostMapping("/deaths/new")
    public String saveDeath(@Valid @ModelAttribute("death") LivestockDeath death,
                            BindingResult result, Model model, RedirectAttributes ra) {
        if (death.getLivestockIdValue() == null || death.getLivestockIdValue().trim().isEmpty()) {
            result.rejectValue("livestockIdValue", "error.death", "Animal is required");
        }
        if (result.hasErrors()) {
            model.addAttribute("deaths", deathService.getAll());
            model.addAttribute("error", "Please fix the errors below.");
            addLivestockToModel(model);
            return "livestock-deaths-list";
        }
        try {
            deathService.addNew(death);
            ra.addFlashAttribute("success",
                    "Death recorded successfully! Animal status has been updated to DEAD.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/deaths";
    }

    @GetMapping("/deaths/edit/{id}")
    public String editDeathForm(@PathVariable UUID id, Model model) {
        Optional<LivestockDeath> opt = deathService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/deaths";
        LivestockDeath d = opt.get();
        if (d.getLivestock() != null) d.setLivestockIdValue(d.getLivestock().getId().toString());
        model.addAttribute("death", d);
        addAllLivestockToModel(model);
        return "livestock-death-edit";
    }

    @PostMapping("/deaths/update/{id}")
    public String updateDeath(@PathVariable UUID id,
                              @Valid @ModelAttribute("death") LivestockDeath death,
                              BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            addAllLivestockToModel(model);
            return "livestock-death-edit";
        }
        try {
            deathService.update(id, death);
            ra.addFlashAttribute("success", "Death record updated successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/deaths";
    }

    @PostMapping("/deaths/delete/{id}")
    public String deleteDeath(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            deathService.delete(id);
            ra.addFlashAttribute("success", "Death record deleted. Animal status restored to ACTIVE.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/livestock/deaths";
    }

    // =========================================================================
    // SALES
    // =========================================================================

    @GetMapping("/sales")
    public String listSales(Model model) {
        model.addAttribute("sales", saleService.getAll());
        model.addAttribute("sale", new LivestockSale());
        addLivestockToModel(model);
        return "livestock-sales-list";
    }

    @PostMapping("/sales/new")
    public String saveSale(@Valid @ModelAttribute("sale") LivestockSale sale,
                           BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("sales", saleService.getAll());
            addLivestockToModel(model);
            return "livestock-sales-list";
        }
        try {
            saleService.addNew(sale);
            ra.addFlashAttribute("success", "Sale record saved successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/sales";
    }

    @GetMapping("/sales/edit/{id}")
    public String editSaleForm(@PathVariable UUID id, Model model) {
        Optional<LivestockSale> opt = saleService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/sales";
        LivestockSale s = opt.get();
        if (s.getLivestock() != null) s.setLivestockIdValue(s.getLivestock().getId().toString());
        model.addAttribute("sale", s);
        addAllLivestockToModel(model);
        return "livestock-sale-edit";
    }

    @PostMapping("/sales/update/{id}")
    public String updateSale(@PathVariable UUID id,
                             @Valid @ModelAttribute("sale") LivestockSale sale,
                             BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            addAllLivestockToModel(model);
            return "livestock-sale-edit";
        }
        try {
            saleService.update(id, sale);
            ra.addFlashAttribute("success", "Sale record updated successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/sales";
    }

    @PostMapping("/sales/delete/{id}")
    public String deleteSale(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            saleService.delete(id);
            ra.addFlashAttribute("success", "Sale record deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/livestock/sales";
    }

    // =========================================================================
    // TREATMENTS
    // =========================================================================

    @GetMapping("/treatments")
    public String listTreatments(Model model) {
        model.addAttribute("treatments", treatmentService.getAll());
        model.addAttribute("treatment", new LivestockTreatment());
        addLivestockToModel(model);
        return "livestock-treatments-list";
    }

    @PostMapping("/treatments/new")
    public String saveTreatment(@Valid @ModelAttribute("treatment") LivestockTreatment treatment,
                                BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("treatments", treatmentService.getAll());
            addLivestockToModel(model);
            return "livestock-treatments-list";
        }
        try {
            treatmentService.addNew(treatment);
            ra.addFlashAttribute("success", "Treatment record saved successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/treatments";
    }

    @GetMapping("/treatments/edit/{id}")
    public String editTreatmentForm(@PathVariable UUID id, Model model) {
        Optional<LivestockTreatment> opt = treatmentService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/treatments";
        LivestockTreatment t = opt.get();
        if (t.getLivestock() != null) t.setLivestockIdValue(t.getLivestock().getId().toString());
        model.addAttribute("treatment", t);
        addAllLivestockToModel(model);
        return "livestock-treatment-edit";
    }

    @PostMapping("/treatments/update/{id}")
    public String updateTreatment(@PathVariable UUID id,
                                  @Valid @ModelAttribute("treatment") LivestockTreatment treatment,
                                  BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            addAllLivestockToModel(model);
            return "livestock-treatment-edit";
        }
        try {
            treatmentService.update(id, treatment);
            ra.addFlashAttribute("success", "Treatment record updated successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/treatments";
    }

    @PostMapping("/treatments/delete/{id}")
    public String deleteTreatment(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            treatmentService.delete(id);
            ra.addFlashAttribute("success", "Treatment record deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/livestock/treatments";
    }

    // =========================================================================
    // SICK LIVESTOCK
    // =========================================================================

    @GetMapping("/sick")
    public String listSick(Model model) {
        model.addAttribute("sickRecords", sickService.getAll());
        model.addAttribute("sickRecord", new LivestockSick());
        addLivestockToModel(model);
        return "livestock-sick-list";
    }

    @PostMapping("/sick/new")
    public String saveSick(@Valid @ModelAttribute("sickRecord") LivestockSick sickRecord,
                           BindingResult result, Model model, RedirectAttributes ra) {
        if (sickRecord.getLivestockIdValue() == null
                || sickRecord.getLivestockIdValue().trim().isEmpty()) {
            result.rejectValue("livestockIdValue", "error.sick", "Animal is required");
        }
        if (result.hasErrors()) {
            model.addAttribute("sickRecords", sickService.getAll());
            model.addAttribute("error", "Please fix the errors below.");
            addLivestockToModel(model);
            return "livestock-sick-list";
        }
        try {
            sickService.addNew(sickRecord);
            ra.addFlashAttribute("success",
                    "Sick record saved! Animal status has been updated to SICK.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/sick";
    }

    @GetMapping("/sick/edit/{id}")
    public String editSickForm(@PathVariable UUID id, Model model) {
        Optional<LivestockSick> opt = sickService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/sick";
        LivestockSick s = opt.get();
        if (s.getLivestock() != null) s.setLivestockIdValue(s.getLivestock().getId().toString());
        model.addAttribute("sickRecord", s);
        model.addAttribute("history", sickService.getHistory(id));
        addAllLivestockToModel(model);
        return "livestock-sick-edit";
    }

    @PostMapping("/sick/update/{id}")
    public String updateSick(@PathVariable UUID id,
                             @Valid @ModelAttribute("sickRecord") LivestockSick sickRecord,
                             BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("history", sickService.getHistory(id));
            addAllLivestockToModel(model);
            return "livestock-sick-edit";
        }
        try {
            sickService.update(id, sickRecord);
            ra.addFlashAttribute("success", "Sick record updated successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/sick";
    }

    @PostMapping("/sick/delete/{id}")
    public String deleteSick(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            sickService.delete(id);
            ra.addFlashAttribute("success",
                    "Sick record deleted. Animal status restored to ACTIVE.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/livestock/sick";
    }

    @GetMapping("/sick/quick-status/{id}")
    public String quickStatus(@PathVariable UUID id,
                              @RequestParam("status") String statusStr,
                              @RequestParam(value = "notes", required = false) String notes,
                              RedirectAttributes ra) {
        try {
            LivestockSick.SickStatus newStatus =
                    LivestockSick.SickStatus.valueOf(statusStr.toUpperCase());
            sickService.quickStatusUpdate(id, newStatus, notes);
            ra.addFlashAttribute("success",
                    "Status updated to " + newStatus.name() + " and recorded in history.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", "Invalid status: " + statusStr);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error updating status: " + e.getMessage());
        }
        return "redirect:/livestock/sick";
    }

    @GetMapping("/sick/history/{id}")
    public String viewHistory(@PathVariable UUID id, Model model) {
        Optional<LivestockSick> opt = sickService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/sick";
        model.addAttribute("sickRecord", opt.get());
        model.addAttribute("history", sickService.getHistory(id));
        return "livestock-sick-history";
    }

    // =========================================================================
    // SICK REPORT
    // =========================================================================

    @GetMapping("/sick/report")
    public String sickReport(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to",   required = false) String toStr,
            Model model) {

        LocalDate fromDate = (fromStr != null && !fromStr.isBlank())
                ? LocalDate.parse(fromStr)
                : LocalDate.now().withDayOfMonth(1);

        LocalDate toDate = (toStr != null && !toStr.isBlank())
                ? LocalDate.parse(toStr)
                : LocalDate.now();

        LocalDateTime fromDt = fromDate.atStartOfDay();
        LocalDateTime toDt   = toDate.atTime(23, 59, 59);

        int year = fromDate.getYear();

        List<LivestockSickHistory> allHistory =
                sickService.getHistoryInRange(fromDt, toDt);

        List<LivestockSickHistory> sickCases =
                sickService.getSickCasesInRange(fromDt, toDt);

        List<LivestockSickHistory> criticalCases =
                sickService.getCriticalCasesInRange(fromDt, toDt);

        List<LivestockSickHistory> recoveredCases =
                sickService.getRecoveredCasesInRange(fromDt, toDt);

        List<LivestockSickHistory> recoveringCases = allHistory.stream()
                .filter(h -> h.getStatus() == LivestockSick.SickStatus.RECOVERING)
                .collect(Collectors.toList());

        List<LivestockSick> sickRecords =
                sickRepository.findByReportedDateBetweenWithHistory(fromDate, toDate);

        long yearSick      = sickService.countSickByYear(year);
        long yearCritical  = sickService.countCriticalByYear(year);
        long yearRecovered = sickService.countRecoveredByYear(year);

        model.addAttribute("fromDate",        fromDate);
        model.addAttribute("toDate",          toDate);
        model.addAttribute("allHistory",      allHistory);
        model.addAttribute("sickCases",       sickCases);
        model.addAttribute("criticalCases",   criticalCases);
        model.addAttribute("recoveringCases", recoveringCases);
        model.addAttribute("recoveredCases",  recoveredCases);
        model.addAttribute("sickRecords",     sickRecords);
        model.addAttribute("yearSick",        yearSick);
        model.addAttribute("yearCritical",    yearCritical);
        model.addAttribute("yearRecovered",   yearRecovered);

        return "livestock-sick-report";
    }

    // =========================================================================
    // CATEGORY REPORT
    // =========================================================================

    @GetMapping("/category-report")
    public String categoryReport(Model model) {

        List<LivestockCategory> allCategories = livestockRepository.findAll().stream()
                .map(Livestock::getLivestockCategory)
                .filter(cat -> cat != null)
                .distinct()
                .collect(Collectors.toList());

        model.addAttribute("totalCategories", (long) allCategories.size());

        long totalActiveLivestock = livestockRepository.findAll().stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count();
        model.addAttribute("totalActiveLivestock", totalActiveLivestock);

        String largestCategoryName = "—";
        long largestCount = 0;

        for (LivestockCategory cat : allCategories) {
            long count = livestockRepository.findAll().stream()
                    .filter(l -> l.getLivestockCategory() != null
                            && l.getLivestockCategory().getId().equals(cat.getId()))
                    .count();
            if (count > largestCount) {
                largestCount = count;
                largestCategoryName = cat.getName();
            }
        }
        model.addAttribute("largestCategoryName", largestCategoryName);

        long totalAnimals = livestockRepository.count();
        long avgAnimalsPerCategory = allCategories.isEmpty() ? 0 : totalAnimals / allCategories.size();
        model.addAttribute("avgAnimalsPerCategory", avgAnimalsPerCategory);

        List<CategoryData> categoryDataList = new ArrayList<>();

        for (LivestockCategory category : allCategories) {
            CategoryData data = new CategoryData();
            data.categoryName = category.getName();
            data.categoryCode = category.getCode();

            List<Livestock> categoryLivestock = livestockRepository.findAll().stream()
                    .filter(l -> l.getLivestockCategory() != null
                            && l.getLivestockCategory().getId().equals(category.getId()))
                    .collect(Collectors.toList());

            data.livestockList  = categoryLivestock;
            data.totalCount     = categoryLivestock.size();
            data.activeCount    = categoryLivestock.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count();
            data.soldCount      = categoryLivestock.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count();
            data.deadCount      = categoryLivestock.stream().filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus())).count();
            data.sickCount      = categoryLivestock.stream().filter(l -> Livestock.STATUS_SICK.equals(l.getStatus())).count();
            data.pregnantCount  = categoryLivestock.stream().filter(l -> Livestock.STATUS_PREGNANT.equals(l.getStatus())).count();
            data.maleCount      = categoryLivestock.stream().filter(l -> "MALE".equals(l.getGender())).count();
            data.femaleCount    = categoryLivestock.stream().filter(l -> "FEMALE".equals(l.getGender())).count();
            data.totalValue     = categoryLivestock.stream()
                    .filter(l -> l.getCurrentValue() != null)
                    .map(Livestock::getCurrentValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            categoryDataList.add(data);
        }

        categoryDataList.sort((a, b) -> Long.compare(b.totalCount, a.totalCount));
        model.addAttribute("categoryStatsList", categoryDataList);

        return "livestock-category-report";
    }

    // =========================================================================
    // CATEGORY ANIMALS REPORT
    // =========================================================================

    @GetMapping("/category-animals-report")
    public String categoryAnimalsReport(Model model) {
        List<LivestockCategory> allCategories = livestockRepository.findAll().stream()
                .map(Livestock::getLivestockCategory)
                .filter(cat -> cat != null)
                .distinct()
                .collect(Collectors.toList());

        List<CategoryWithCount> categoriesWithCount = new ArrayList<>();
        for (LivestockCategory cat : allCategories) {
            long count = livestockRepository.findAll().stream()
                    .filter(l -> l.getLivestockCategory() != null
                            && l.getLivestockCategory().getId().equals(cat.getId()))
                    .count();
            categoriesWithCount.add(new CategoryWithCount(cat, count));
        }

        model.addAttribute("categories",       categoriesWithCount);
        model.addAttribute("selectedCategory", null);
        model.addAttribute("animals",          new ArrayList<>());

        return "livestock-category-animals-report";
    }

    @GetMapping("/category-animals-report/{categoryId}")
    public String categoryAnimalsReportByCategory(@PathVariable UUID categoryId, Model model) {

        // ── Categories list (for the selector) ────────────────────────
        List<LivestockCategory> allCategories = livestockRepository.findAll().stream()
                .map(Livestock::getLivestockCategory)
                .filter(cat -> cat != null)
                .distinct()
                .collect(Collectors.toList());

        List<CategoryWithCount> categoriesWithCount = new ArrayList<>();
        for (LivestockCategory cat : allCategories) {
            long count = livestockRepository.findAll().stream()
                    .filter(l -> l.getLivestockCategory() != null
                            && l.getLivestockCategory().getId().equals(cat.getId()))
                    .count();
            categoriesWithCount.add(new CategoryWithCount(cat, count));
        }

        // ── Selected category & its animals ───────────────────────────
        LivestockCategory selectedCategory = allCategories.stream()
                .filter(cat -> cat.getId().equals(categoryId))
                .findFirst()
                .orElse(null);

        List<Livestock> animals = livestockRepository.findAll().stream()
                .filter(l -> l.getLivestockCategory() != null
                        && l.getLivestockCategory().getId().equals(categoryId))
                .collect(Collectors.toList());

        List<UUID> animalIds = animals.stream()
                .map(Livestock::getId)
                .collect(Collectors.toList());

        // ── Basic counts ───────────────────────────────────────────────
        long activeCount = animals.stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count();
        long soldCount = animals.stream()
                .filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count();

        // ── Sale revenue + per-animal sale map ────────────────────────
        // Uses LivestockSale.getSalePrice()
        List<LivestockSale> allSales = saleService.getAll();
        Map<UUID, BigDecimal> animalSaleMap = new HashMap<>();
        for (LivestockSale sale : allSales) {
            if (sale.getLivestock() != null
                    && animalIds.contains(sale.getLivestock().getId())
                    && sale.getSalePrice() != null) {
                UUID aid = sale.getLivestock().getId();
                animalSaleMap.merge(aid, sale.getSalePrice(), BigDecimal::add);
            }
        }
        BigDecimal totalSaleRevenue = animalSaleMap.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Active stock value ─────────────────────────────────────────
        BigDecimal activeStockValue = animals.stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Treatments + per-animal count map ────────────────────────
        // Uses LivestockTreatment.getTreatmentCost()
        List<LivestockTreatment> allTreatments = treatmentService.getAll();
        Map<UUID, Integer> animalTreatCountMap = new HashMap<>();
        BigDecimal totalTreatmentCost = BigDecimal.ZERO;
        for (LivestockTreatment t : allTreatments) {
            if (t.getLivestock() != null
                    && animalIds.contains(t.getLivestock().getId())) {
                UUID aid = t.getLivestock().getId();
                animalTreatCountMap.merge(aid, 1, Integer::sum);
                if (t.getTreatmentCost() != null) {
                    totalTreatmentCost = totalTreatmentCost.add(t.getTreatmentCost());
                }
            }
        }
        long treatmentCount = animalTreatCountMap.values().stream()
                .mapToLong(Integer::longValue).sum();

        // ── Born-on-farm: animals whose mother is set (self-join) ─────
        // Uses Livestock.getMother() — an animal born on farm has a mother record
        // Uses Livestock.getLastBirthDate() as the birth date to show in the table
        Map<UUID, LocalDate> animalBirthMap = new HashMap<>();
        for (Livestock animal : animals) {
            if (animal.getMother() != null) {
                // Use the mother's lastBirthDate as the birth date of this offspring,
                // or dateReceived as a fallback
                LocalDate birthDate = animal.getMother().getLastBirthDate() != null
                        ? animal.getMother().getLastBirthDate()
                        : animal.getDateReceived();
                animalBirthMap.put(animal.getId(), birthDate);
            }
        }
        long totalBornCount = animalBirthMap.size();

        // ── Total offspring: sum offspringCount of mothers in this category ──
        // offspringCount on a mother = how many children she has produced
        long totalOffspringCount = animals.stream()
                .filter(l -> l.getOffspringCount() != null)
                .mapToLong(Livestock::getOffspringCount)
                .sum();

        // ── Born animal value: current value of born-on-farm animals ──
        BigDecimal bornAnimalValue = animals.stream()
                .filter(l -> animalBirthMap.containsKey(l.getId()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Business analysis ──────────────────────────────────────────
        BigDecimal totalIncome  = totalSaleRevenue.add(activeStockValue).add(bornAnimalValue);
        BigDecimal netPosition  = totalIncome.subtract(totalTreatmentCost);

        String businessStatus;
        if      (netPosition.compareTo(BigDecimal.ZERO) > 0) businessStatus = "gain";
        else if (netPosition.compareTo(BigDecimal.ZERO) < 0) businessStatus = "loss";
        else                                                  businessStatus = "neutral";

        // ── Push everything to model ───────────────────────────────────
        model.addAttribute("categories",          categoriesWithCount);
        model.addAttribute("selectedCategory",    selectedCategory);
        model.addAttribute("animals",             animals);

        model.addAttribute("activeCount",         activeCount);
        model.addAttribute("soldCount",           soldCount);
        model.addAttribute("treatmentCount",      treatmentCount);
        model.addAttribute("totalBornCount",      totalBornCount);
        model.addAttribute("totalOffspringCount", totalOffspringCount);

        model.addAttribute("totalSaleRevenue",    totalSaleRevenue);
        model.addAttribute("activeStockValue",    activeStockValue);
        model.addAttribute("bornAnimalValue",     bornAnimalValue);
        model.addAttribute("totalTreatmentCost",  totalTreatmentCost);
        model.addAttribute("totalIncome",         totalIncome);
        model.addAttribute("netPosition",         netPosition);
        model.addAttribute("businessStatus",      businessStatus);

        model.addAttribute("animalSaleMap",       animalSaleMap);
        model.addAttribute("animalBirthMap",      animalBirthMap);
        model.addAttribute("animalTreatCountMap", animalTreatCountMap);

        return "livestock-category-animals-report";
    }

    // =========================================================================
    // AJAX — animals by category
    // =========================================================================

    @GetMapping("/category/{categoryId}/animals-all")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getAnimalsByCategory(
            @PathVariable UUID categoryId) {

        List<Livestock> animals = livestockRepository.findAll().stream()
                .filter(l -> l.getLivestockCategory() != null
                        && l.getLivestockCategory().getId().equals(categoryId))
                .collect(Collectors.toList());

        List<Map<String, Object>> result = animals.stream().map(animal -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id",           animal.getId());
            map.put("tagNumber",    animal.getTagNumber());
            map.put("gender",       animal.getGender());
            map.put("status",       animal.getStatus());
            map.put("dateReceived", animal.getDateReceived() != null
                    ? animal.getDateReceived().toString() : null);
            map.put("currentValue", animal.getCurrentValue());
            map.put("locationName", animal.getLocation() != null
                    ? animal.getLocation().getName() : null);
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // INNER CLASSES
    // =========================================================================

    public static class CategoryData {
        public String categoryName;
        public String categoryCode;
        public long totalCount;
        public long activeCount;
        public long soldCount;
        public long deadCount;
        public long sickCount;
        public long pregnantCount;
        public long maleCount;
        public long femaleCount;
        public BigDecimal totalValue = BigDecimal.ZERO;
        public List<Livestock> livestockList = new ArrayList<>();

        public String getCategoryName()         { return categoryName; }
        public String getCategoryCode()         { return categoryCode; }
        public long getTotalCount()             { return totalCount; }
        public long getActiveCount()            { return activeCount; }
        public long getSoldCount()              { return soldCount; }
        public long getDeadCount()              { return deadCount; }
        public long getSickCount()              { return sickCount; }
        public long getPregnantCount()          { return pregnantCount; }
        public long getMaleCount()              { return maleCount; }
        public long getFemaleCount()            { return femaleCount; }
        public BigDecimal getTotalValue()       { return totalValue; }
        public List<Livestock> getLivestockList(){ return livestockList; }
    }

    public static class CategoryWithCount {
        private final LivestockCategory category;
        private final long livestockCount;

        public CategoryWithCount(LivestockCategory category, long livestockCount) {
            this.category       = category;
            this.livestockCount = livestockCount;
        }

        public UUID getId()                     { return category.getId(); }
        public String getName()                 { return category.getName(); }
        public String getCode()                 { return category.getCode(); }
        public long getLivestockCount()         { return livestockCount; }
        public List<Livestock> getLivestock()   { return category.getLivestockList(); }
    }
}