package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSickRepository;
import rw.animalproduct.animal.production.services.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import java.util.HashSet;
import java.util.Set;


@Controller
@RequestMapping("/livestock")
public class LivestockEventsController {


    private static final Logger logger = LoggerFactory.getLogger(LivestockEventsController.class);

    private final LivestockRepository         livestockRepository;
    private final LivestockAbortionService    abortionService;
    private final LivestockDeathService       deathService;
    private final LivestockSaleService        saleService;
    private final LivestockTreatmentService   treatmentService;
    private final LivestockSickService        sickService;
    private final LivestockSickRepository     sickRepository;
    private final LivestockCategoryRepository livestockCategoryRepository;
    private final MedicationService           medicationService;
    private final BuyerService                buyerService;

    public LivestockEventsController(LivestockRepository livestockRepository,
                                     LivestockAbortionService abortionService,
                                     LivestockDeathService deathService,
                                     LivestockSaleService saleService,
                                     LivestockTreatmentService treatmentService,
                                     LivestockSickService sickService,
                                     LivestockSickRepository sickRepository,
                                     LivestockCategoryRepository livestockCategoryRepository,
                                     MedicationService medicationService,
                                     BuyerService buyerService) {
        this.livestockRepository         = livestockRepository;
        this.abortionService             = abortionService;
        this.deathService                = deathService;
        this.saleService                 = saleService;
        this.treatmentService            = treatmentService;
        this.sickService                 = sickService;
        this.sickRepository              = sickRepository;
        this.livestockCategoryRepository = livestockCategoryRepository;
        this.medicationService           = medicationService;
        this.buyerService = buyerService;
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

    // Update sales list endpoint
    @GetMapping("/sales")
    public String listSales(Model model) {
        model.addAttribute("sales", saleService.getAll());
        model.addAttribute("sale", new LivestockSale());
        model.addAttribute("buyers", buyerService.getActive());
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

    // Update sales edit endpoint
    @GetMapping("/sales/edit/{id}")
    public String editSaleForm(@PathVariable UUID id, Model model) {
        Optional<LivestockSale> opt = saleService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/sales";
        LivestockSale s = opt.get();
        if (s.getLivestock() != null) s.setLivestockIdValue(s.getLivestock().getId().toString());
        if (s.getBuyer() != null) s.setBuyerIdValue(s.getBuyer().getId().toString());
        model.addAttribute("sale", s);
        model.addAttribute("buyers", buyerService.getActive());
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
    // TREATMENTS CRUD
    // =========================================================================

    @GetMapping("/treatments")
    public String listTreatments(Model model) {
        model.addAttribute("treatments", treatmentService.getAll());
        model.addAttribute("treatment", new LivestockTreatment());
        addLivestockToModel(model);
        model.addAttribute("medicationList", medicationService.getActive());
        return "livestock-treatments-list";
    }

    @PostMapping("/treatments/new")
    public String saveTreatment(@Valid @ModelAttribute("treatment") LivestockTreatment treatment,
                                BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("treatments", treatmentService.getAll());
            addLivestockToModel(model);
            model.addAttribute("medicationList", medicationService.getActive());
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

        // Pre-populate transient ID fields for the form
        if (t.getLivestock()   != null) t.setLivestockIdValue(t.getLivestock().getId().toString());
        if (t.getMedication()  != null) t.setMedicationIdValue(t.getMedication().getId().toString());
        if (t.getVeterinarian() != null) t.setVeterinarianIdValue(t.getVeterinarian().getId().toString()); // ← NEW

        model.addAttribute("treatment", t);
        addAllLivestockToModel(model);
        model.addAttribute("medicationList", medicationService.getActive());
        return "livestock-treatment-edit";
    }

    @PostMapping("/treatments/update/{id}")
    public String updateTreatment(@PathVariable UUID id,
                                  @Valid @ModelAttribute("treatment") LivestockTreatment treatment,
                                  BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            addAllLivestockToModel(model);
            model.addAttribute("medicationList", medicationService.getActive());
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
    // DASHBOARD SUMMARY HELPER
    // =========================================================================

    private void addTreatmentSummaryToModel(Model model) {
        List<LivestockTreatment> all = treatmentService.getAll();

        long totalTreatments = all.size();

        BigDecimal totalTreatmentCost = all.stream()
                .filter(t -> t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String mostTreatedAnimalTag = all.stream()
                .filter(t -> t.getLivestock() != null)
                .collect(Collectors.groupingBy(t -> t.getLivestock().getTagNumber(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("—");

        long treatmentsThisMonth = all.stream()
                .filter(t -> t.getTreatmentDate() != null
                        && t.getTreatmentDate().getMonth() == LocalDate.now().getMonth()
                        && t.getTreatmentDate().getYear()  == LocalDate.now().getYear())
                .count();

        long unpaidTreatmentCount = all.stream()
                .filter(t -> t.getIsPaid() == null || !t.getIsPaid())
                .count();

        long ongoingTreatmentCount = all.stream()
                .filter(t -> t.getTreatmentStatus() == LivestockTreatment.TreatmentStatus.ONGOING)
                .count();

        model.addAttribute("treatmentList",         all);
        model.addAttribute("totalTreatments",       totalTreatments);
        model.addAttribute("totalTreatmentCost",    totalTreatmentCost);
        model.addAttribute("mostTreatedAnimalTag",  mostTreatedAnimalTag);
        model.addAttribute("treatmentsThisMonth",   treatmentsThisMonth);
        model.addAttribute("unpaidTreatmentCount",  unpaidTreatmentCount);
        model.addAttribute("ongoingTreatmentCount", ongoingTreatmentCount);
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

        // Pre-populate transient ID fields for the form
        if (s.getLivestock() != null) {
            s.setLivestockIdValue(s.getLivestock().getId().toString());
        }
        if (s.getVeterinarian() != null) {
            // ← NEW: lets the edit template pre-render the vet chip via JS
            s.setVeterinarianIdValue(s.getVeterinarian().getId().toString());
        }

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

        long activeCount = animals.stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count();
        long soldCount = animals.stream()
                .filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count();

        List<LivestockSale> allSales = saleService.getAll();
        Map<UUID, BigDecimal> animalSaleMap = new LinkedHashMap<>();
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

        BigDecimal activeStockValue = animals.stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LivestockTreatment> allTreatments = treatmentService.getAll();
        Map<UUID, Integer> animalTreatCountMap = new LinkedHashMap<>();
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

        Map<UUID, LocalDate> animalBirthMap = new LinkedHashMap<>();
        for (Livestock animal : animals) {
            if (animal.getMother() != null) {
                LocalDate birthDate = animal.getMother().getLastBirthDate() != null
                        ? animal.getMother().getLastBirthDate()
                        : animal.getDateReceived();
                animalBirthMap.put(animal.getId(), birthDate);
            }
        }
        long totalBornCount = animalBirthMap.size();

        long totalOffspringCount = animals.stream()
                .filter(l -> l.getOffspringCount() != null)
                .mapToLong(Livestock::getOffspringCount)
                .sum();

        BigDecimal bornAnimalValue = animals.stream()
                .filter(l -> animalBirthMap.containsKey(l.getId()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalIncome  = totalSaleRevenue.add(activeStockValue).add(bornAnimalValue);
        BigDecimal netPosition  = totalIncome.subtract(totalTreatmentCost);

        String businessStatus;
        if      (netPosition.compareTo(BigDecimal.ZERO) > 0) businessStatus = "gain";
        else if (netPosition.compareTo(BigDecimal.ZERO) < 0) businessStatus = "loss";
        else                                                  businessStatus = "neutral";

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
            Map<String, Object> map = new LinkedHashMap<>();
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

        public String getCategoryName()           { return categoryName; }
        public String getCategoryCode()           { return categoryCode; }
        public long getTotalCount()               { return totalCount; }
        public long getActiveCount()              { return activeCount; }
        public long getSoldCount()                { return soldCount; }
        public long getDeadCount()                { return deadCount; }
        public long getSickCount()                { return sickCount; }
        public long getPregnantCount()            { return pregnantCount; }
        public long getMaleCount()                { return maleCount; }
        public long getFemaleCount()              { return femaleCount; }
        public BigDecimal getTotalValue()         { return totalValue; }
        public List<Livestock> getLivestockList() { return livestockList; }
    }

    public static class CategoryWithCount {
        private final LivestockCategory category;
        private final long livestockCount;

        public CategoryWithCount(LivestockCategory category, long livestockCount) {
            this.category       = category;
            this.livestockCount = livestockCount;
        }

        public UUID getId()                   { return category.getId(); }
        public String getName()               { return category.getName(); }
        public String getCode()               { return category.getCode(); }
        public long getLivestockCount()       { return livestockCount; }
        public List<Livestock> getLivestock() { return category.getLivestockList(); }
    }

    // =========================================================================
    // COMPREHENSIVE REPORTS FOR BUSINESS OVERVIEW
    // =========================================================================

    @GetMapping("/inventory-report")
    public String inventoryReport(
            @RequestParam(value = "category", required = false) UUID categoryId,
            @RequestParam(value = "location", required = false) UUID locationId,
            @RequestParam(value = "status", required = false) String status,
            Model model) {

        List<Livestock> allLivestock = livestockRepository.findAll();

        if (categoryId != null) {
            allLivestock = allLivestock.stream()
                    .filter(l -> l.getLivestockCategory() != null &&
                            l.getLivestockCategory().getId().equals(categoryId))
                    .collect(Collectors.toList());
        }

        if (locationId != null) {
            allLivestock = allLivestock.stream()
                    .filter(l -> l.getLocation() != null &&
                            l.getLocation().getId().equals(locationId))
                    .collect(Collectors.toList());
        }

        if (status != null && !status.isEmpty()) {
            allLivestock = allLivestock.stream()
                    .filter(l -> status.equals(l.getStatus()))
                    .collect(Collectors.toList());
        }

        long totalAnimals  = allLivestock.size();
        long activeCount   = allLivestock.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count();
        long sickCount     = allLivestock.stream().filter(l -> Livestock.STATUS_SICK.equals(l.getStatus())).count();
        long pregnantCount = allLivestock.stream().filter(l -> Livestock.STATUS_PREGNANT.equals(l.getStatus())).count();
        long deadCount     = allLivestock.stream().filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus())).count();
        long soldCount     = allLivestock.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count();

        Map<String, Long> byCategory = allLivestock.stream()
                .filter(l -> l.getLivestockCategory() != null)
                .collect(Collectors.groupingBy(l -> l.getLivestockCategory().getName(), Collectors.counting()));

        Map<String, Long> byLocation = allLivestock.stream()
                .filter(l -> l.getLocation() != null)
                .collect(Collectors.groupingBy(l -> l.getLocation().getName(), Collectors.counting()));

        BigDecimal totalValue = allLivestock.stream()
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("livestock",     allLivestock);
        model.addAttribute("totalAnimals",  totalAnimals);
        model.addAttribute("activeCount",   activeCount);
        model.addAttribute("sickCount",     sickCount);
        model.addAttribute("pregnantCount", pregnantCount);
        model.addAttribute("deadCount",     deadCount);
        model.addAttribute("soldCount",     soldCount);
        model.addAttribute("byCategory",    byCategory);
        model.addAttribute("byLocation",    byLocation);
        model.addAttribute("totalValue",    totalValue);

        model.addAttribute("allCategories", livestockRepository.findAll().stream()
                .map(Livestock::getLivestockCategory)
                .filter(cat -> cat != null)
                .distinct()
                .collect(Collectors.toList()));

        model.addAttribute("allLocations", livestockRepository.findAll().stream()
                .map(Livestock::getLocation)
                .filter(loc -> loc != null)
                .distinct()
                .collect(Collectors.toList()));

        return "livestock-inventory-report";
    }
    @GetMapping("/movement-report")
    public String movementReport(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            Model model) {

        LocalDate fromDate = (fromStr != null && !fromStr.isBlank())
                ? LocalDate.parse(fromStr) : LocalDate.now().minusMonths(1);
        LocalDate toDate = (toStr != null && !toStr.isBlank())
                ? LocalDate.parse(toStr) : LocalDate.now();

        // ========== 1. GET ALL DATA ==========
        List<Livestock> allLivestock = livestockRepository.findAll();
        List<LivestockSale> allSales = saleService.getAll();
        List<LivestockDeath> allDeaths = deathService.getAll();

        // ========== DEBUG: Log all animals ==========
        logger.info("=== Total animals in database: {} ===", allLivestock.size());
        for (Livestock l : allLivestock) {
            logger.info("Animal: ID={}, Tag={}, DateReceived={}, Mother={}, AcquisitionMethod={}, Status={}",
                    l.getId(), l.getTagNumber(), l.getDateReceived(),
                    l.getMother() != null ? l.getMother().getTagNumber() : "null",
                    l.getAcquisitionMethod(), l.getStatus());
        }

        // ========== 2. OPENING STOCK ==========
        long openingStock = allLivestock.stream()
                .filter(l -> l.getDateReceived() != null)
                .filter(l -> l.getDateReceived().isBefore(fromDate))
                .filter(l -> {
                    boolean soldBefore = allSales.stream()
                            .anyMatch(s -> s.getLivestock() != null &&
                                    s.getLivestock().getId().equals(l.getId()) &&
                                    s.getSaleDate() != null &&
                                    s.getSaleDate().isBefore(fromDate));

                    boolean diedBefore = allDeaths.stream()
                            .anyMatch(d -> d.getLivestock() != null &&
                                    d.getLivestock().getId().equals(l.getId()) &&
                                    d.getDeathDate() != null &&
                                    d.getDeathDate().isBefore(fromDate));

                    return !soldBefore && !diedBefore;
                })
                .count();

        // ========== 3. PURCHASED ANIMALS (MORE ROBUST) ==========
        // Method 1: Check by acquisition_method = 'PURCHASE'
        List<Livestock> purchasedByMethod = allLivestock.stream()
                .filter(l -> l.getDateReceived() != null)
                .filter(l -> !l.getDateReceived().isBefore(fromDate) &&
                        !l.getDateReceived().isAfter(toDate))
                .filter(l -> l.getAcquisitionMethod() != null &&
                        "PURCHASE".equalsIgnoreCase(l.getAcquisitionMethod().trim()))
                .collect(Collectors.toList());

        // Method 2: Check by mother being null AND not being born on farm
        List<Livestock> purchasedByMotherNull = allLivestock.stream()
                .filter(l -> l.getDateReceived() != null)
                .filter(l -> !l.getDateReceived().isBefore(fromDate) &&
                        !l.getDateReceived().isAfter(toDate))
                .filter(l -> l.getMother() == null)
                .filter(l -> l.getAcquisitionMethod() == null ||
                        !"BORN".equalsIgnoreCase(l.getAcquisitionMethod()))
                .collect(Collectors.toList());

        // Method 3: Check by status and typical purchase indicators
        List<Livestock> purchasedByStatus = allLivestock.stream()
                .filter(l -> l.getDateReceived() != null)
                .filter(l -> !l.getDateReceived().isBefore(fromDate) &&
                        !l.getDateReceived().isAfter(toDate))
                .filter(l -> l.getMother() == null && l.getDateReceived() != null)
                .filter(l -> l.getOffspringCount() == null || l.getOffspringCount() == 0)
                .collect(Collectors.toList());

        // Combine all methods and remove duplicates
        Set<UUID> purchasedIds = new HashSet<>();
        purchasedByMethod.forEach(l -> purchasedIds.add(l.getId()));
        purchasedByMotherNull.forEach(l -> purchasedIds.add(l.getId()));
        purchasedByStatus.forEach(l -> purchasedIds.add(l.getId()));

        List<Livestock> purchased = allLivestock.stream()
                .filter(l -> purchasedIds.contains(l.getId()))
                .collect(Collectors.toList());

        logger.info("Purchased - By Method: {}, By Mother Null: {}, By Status: {}, Total Unique: {}",
                purchasedByMethod.size(), purchasedByMotherNull.size(),
                purchasedByStatus.size(), purchased.size());

        // ========== 4. BORN ANIMALS ==========
        List<Livestock> born = allLivestock.stream()
                .filter(l -> l.getDateReceived() != null)
                .filter(l -> !l.getDateReceived().isBefore(fromDate) &&
                        !l.getDateReceived().isAfter(toDate))
                .filter(l -> l.getMother() != null)
                .collect(Collectors.toList());

        // Also check for acquisition_method = 'BORN'
        List<Livestock> bornByMethod = allLivestock.stream()
                .filter(l -> l.getDateReceived() != null)
                .filter(l -> !l.getDateReceived().isBefore(fromDate) &&
                        !l.getDateReceived().isAfter(toDate))
                .filter(l -> l.getAcquisitionMethod() != null &&
                        "BORN".equalsIgnoreCase(l.getAcquisitionMethod().trim()))
                .collect(Collectors.toList());

        // Merge born animals
        Set<UUID> bornIds = new HashSet<>();
        born.forEach(l -> bornIds.add(l.getId()));
        bornByMethod.forEach(l -> bornIds.add(l.getId()));

        born = allLivestock.stream()
                .filter(l -> bornIds.contains(l.getId()))
                .collect(Collectors.toList());

        // ========== 5. SOLD ANIMALS ==========
        List<LivestockSale> sold = allSales.stream()
                .filter(s -> s.getSaleDate() != null)
                .filter(s -> !s.getSaleDate().isBefore(fromDate) &&
                        !s.getSaleDate().isAfter(toDate))
                .collect(Collectors.toList());

        // ========== 6. DIED ANIMALS ==========
        List<LivestockDeath> died = allDeaths.stream()
                .filter(d -> d.getDeathDate() != null)
                .filter(d -> !d.getDeathDate().isBefore(fromDate) &&
                        !d.getDeathDate().isAfter(toDate))
                .collect(Collectors.toList());

        // ========== 7. CALCULATE MOVEMENTS ==========
        int additions = purchased.size() + born.size();
        int removals = sold.size() + died.size();
        long closingStock = openingStock + additions - removals;

        if (closingStock < 0) closingStock = 0;
        int netChange = additions - removals;

        // ========== 8. FINANCIAL CALCULATIONS ==========
        BigDecimal totalSalesRevenue = sold.stream()
                .filter(s -> s.getSalePrice() != null)
                .map(LivestockSale::getSalePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDeathLoss = died.stream()
                .filter(d -> d.getLivestock() != null &&
                        d.getLivestock().getCurrentValue() != null)
                .map(d -> d.getLivestock().getCurrentValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPurchasesCost = purchased.stream()
                .filter(p -> p.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalLoss = totalDeathLoss.add(totalPurchasesCost);
        BigDecimal netFinancialImpact = totalSalesRevenue.subtract(totalLoss);

        // Closing Herd Value
        BigDecimal closingStockValue = allLivestock.stream()
                .filter(l -> l.getDateReceived() != null)
                .filter(l -> !l.getDateReceived().isAfter(toDate))
                .filter(l -> {
                    boolean notSold = allSales.stream()
                            .noneMatch(s -> s.getLivestock() != null &&
                                    s.getLivestock().getId().equals(l.getId()) &&
                                    s.getSaleDate() != null &&
                                    !s.getSaleDate().isAfter(toDate));

                    boolean notDead = allDeaths.stream()
                            .noneMatch(d -> d.getLivestock() != null &&
                                    d.getLivestock().getId().equals(l.getId()) &&
                                    d.getDeathDate() != null &&
                                    !d.getDeathDate().isAfter(toDate));

                    return notSold && notDead;
                })
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Average calculations
        BigDecimal averageSalePrice = sold.isEmpty() ? BigDecimal.ZERO :
                totalSalesRevenue.divide(BigDecimal.valueOf(sold.size()), 2, RoundingMode.HALF_UP);

        BigDecimal averagePurchasePrice = purchased.isEmpty() ? BigDecimal.ZERO :
                totalPurchasesCost.divide(BigDecimal.valueOf(purchased.size()), 2, RoundingMode.HALF_UP);

        // ========== 9. DEBUG STATS ==========
        long livestockWithDateReceived = allLivestock.stream()
                .filter(l -> l.getDateReceived() != null).count();

        long purchasedByMethodCount = allLivestock.stream()
                .filter(l -> l.getAcquisitionMethod() != null &&
                        "PURCHASE".equalsIgnoreCase(l.getAcquisitionMethod().trim()))
                .count();

        long livestockWithNullMother = allLivestock.stream()
                .filter(l -> l.getMother() == null).count();

        logger.info("=== Movement Report Summary ===");
        logger.info("Period: {} to {}", fromDate, toDate);
        logger.info("Total Livestock: {}", allLivestock.size());
        logger.info("Livestock with Date Received: {}", livestockWithDateReceived);
        logger.info("Purchased (final list): {}", purchased.size());
        logger.info("Born (final list): {}", born.size());
        logger.info("Opening Stock: {}", openingStock);
        logger.info("Closing Stock: {}", closingStock);
        logger.info("===============================");

        // ========== 10. ADD TO MODEL ==========
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("purchased", purchased);
        model.addAttribute("born", born);
        model.addAttribute("sold", sold);
        model.addAttribute("died", died);
        model.addAttribute("openingStock", openingStock);
        model.addAttribute("additions", additions);
        model.addAttribute("removals", removals);
        model.addAttribute("closingStock", closingStock);
        model.addAttribute("netChange", netChange);
        model.addAttribute("closingStockValue", closingStockValue);
        model.addAttribute("totalSalesRevenue", totalSalesRevenue);
        model.addAttribute("totalDeathLoss", totalDeathLoss);
        model.addAttribute("totalPurchasesCost", totalPurchasesCost);
        model.addAttribute("totalLoss", totalLoss);
        model.addAttribute("netFinancialImpact", netFinancialImpact);
        model.addAttribute("averageSalePrice", averageSalePrice);
        model.addAttribute("averagePurchasePrice", averagePurchasePrice);

        // Debug attributes
        model.addAttribute("totalLivestockCount", allLivestock.size());
        model.addAttribute("livestockWithDateReceived", livestockWithDateReceived);
        model.addAttribute("purchasedByMethod", purchasedByMethodCount);
        model.addAttribute("livestockWithNullMother", livestockWithNullMother);

        return "livestock-movement-report";
    }

    @GetMapping("/sales-report-detailed")
    public String salesReportDetailed(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            @RequestParam(value = "category", required = false) UUID categoryId,
            Model model) {

        LocalDate fromDate = (fromStr != null && !fromStr.isBlank())
                ? LocalDate.parse(fromStr) : LocalDate.now().withDayOfYear(1);
        LocalDate toDate = (toStr != null && !toStr.isBlank())
                ? LocalDate.parse(toStr) : LocalDate.now();

        List<LivestockSale> allSales = saleService.getAll().stream()
                .filter(s -> s.getSaleDate() != null &&
                        !s.getSaleDate().isBefore(fromDate) &&
                        !s.getSaleDate().isAfter(toDate))
                .collect(Collectors.toList());

        if (categoryId != null) {
            allSales = allSales.stream()
                    .filter(s -> s.getLivestock() != null &&
                            s.getLivestock().getLivestockCategory() != null &&
                            s.getLivestock().getLivestockCategory().getId().equals(categoryId))
                    .collect(Collectors.toList());
        }

        int totalSalesCount = allSales.size();
        BigDecimal totalRevenue = allSales.stream()
                .filter(s -> s.getSalePrice() != null)
                .map(LivestockSale::getSalePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageSalePrice = totalSalesCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalSalesCount), 2, BigDecimal.ROUND_HALF_UP)
                : BigDecimal.ZERO;

        Map<String, SalesCategoryStats> salesByCategory = new LinkedHashMap<>();
        for (LivestockSale sale : allSales) {
            if (sale.getLivestock() != null && sale.getLivestock().getLivestockCategory() != null) {
                String catName = sale.getLivestock().getLivestockCategory().getName();
                SalesCategoryStats stats = salesByCategory.getOrDefault(catName, new SalesCategoryStats(catName));
                stats.count++;
                if (sale.getSalePrice() != null) stats.revenue = stats.revenue.add(sale.getSalePrice());
                salesByCategory.put(catName, stats);
            }
        }

        Map<String, Long> salesByReason = allSales.stream()
                .filter(s -> s.getSaleReason() != null)
                .collect(Collectors.groupingBy(LivestockSale::getSaleReason, Collectors.counting()));

        Map<LocalDate, BigDecimal> dailySales = allSales.stream()
                .filter(s -> s.getSalePrice() != null)
                .collect(Collectors.groupingBy(LivestockSale::getSaleDate,
                        Collectors.reducing(BigDecimal.ZERO, LivestockSale::getSalePrice, BigDecimal::add)));

        List<LivestockSale> topSales = allSales.stream()
                .filter(s -> s.getSalePrice() != null)
                .sorted((a, b) -> b.getSalePrice().compareTo(a.getSalePrice()))
                .limit(10)
                .collect(Collectors.toList());

        model.addAttribute("fromDate",         fromDate);
        model.addAttribute("toDate",           toDate);
        model.addAttribute("sales",            allSales);
        model.addAttribute("totalSalesCount",  totalSalesCount);
        model.addAttribute("totalRevenue",     totalRevenue);
        model.addAttribute("averageSalePrice", averageSalePrice);
        model.addAttribute("salesByCategory",  salesByCategory.values());
        model.addAttribute("salesByReason",    salesByReason);
        model.addAttribute("dailySales",       dailySales);
        model.addAttribute("topSales",         topSales);

        model.addAttribute("allCategories", livestockRepository.findAll().stream()
                .map(Livestock::getLivestockCategory)
                .filter(cat -> cat != null)
                .distinct()
                .collect(Collectors.toList()));

        return "sales-report-detailed";
    }

    @GetMapping("/health-report")
    public String healthReport(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            Model model) {

        LocalDate fromDate = (fromStr != null && !fromStr.isBlank())
                ? LocalDate.parse(fromStr) : LocalDate.now().minusMonths(1);
        LocalDate toDate = (toStr != null && !toStr.isBlank())
                ? LocalDate.parse(toStr) : LocalDate.now();

        List<LivestockSick> sickRecords = sickRepository.findAll().stream()
                .filter(s -> s.getReportedDate() != null &&
                        !s.getReportedDate().isBefore(fromDate) &&
                        !s.getReportedDate().isAfter(toDate))
                .collect(Collectors.toList());

        List<LivestockTreatment> treatments = treatmentService.getAll().stream()
                .filter(t -> t.getTreatmentDate() != null &&
                        !t.getTreatmentDate().isBefore(fromDate) &&
                        !t.getTreatmentDate().isAfter(toDate))
                .collect(Collectors.toList());

        BigDecimal totalTreatmentCost = treatments.stream()
                .filter(t -> t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> diseaseFrequency = sickRecords.stream()
                .filter(s -> s.getDiagnosis() != null && !s.getDiagnosis().isEmpty())
                .collect(Collectors.groupingBy(LivestockSick::getDiagnosis, Collectors.counting()));

        Map<String, Long> treatmentByType = treatments.stream()
                .filter(t -> t.getTreatmentType() != null)
                .collect(Collectors.groupingBy(
                        t -> t.getTreatmentType().name(),
                        Collectors.counting()));

        long totalSick  = sickRecords.size();
        long recovered  = sickRecords.stream()
                .filter(s -> s.getStatus() == LivestockSick.SickStatus.RECOVERED).count();
        double recoveryRate = totalSick > 0 ? (recovered * 100.0 / totalSick) : 0;

        model.addAttribute("fromDate",           fromDate);
        model.addAttribute("toDate",             toDate);
        model.addAttribute("sickRecords",        sickRecords);
        model.addAttribute("treatments",         treatments);
        model.addAttribute("totalSickCount",     totalSick);
        model.addAttribute("totalTreatments",    treatments.size());
        model.addAttribute("totalTreatmentCost", totalTreatmentCost);
        model.addAttribute("diseaseFrequency",   diseaseFrequency);
        model.addAttribute("treatmentByType",    treatmentByType);
        model.addAttribute("recoveryRate",       recoveryRate);
        model.addAttribute("recoveredCount",     recovered);

        return "health-report";
    }

    // =========================================================================
    // HEALTH REPORT BY CATEGORY
    // =========================================================================

    @GetMapping("/health-report-by-category")
    public String healthReportByCategory(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            Model model) {

        LocalDate fromDate = (fromStr != null && !fromStr.isBlank())
                ? LocalDate.parse(fromStr) : LocalDate.now().minusMonths(1);
        LocalDate toDate = (toStr != null && !toStr.isBlank())
                ? LocalDate.parse(toStr) : LocalDate.now();

        List<LivestockCategory> allCategories = livestockCategoryRepository.findAll();

        model.addAttribute("allCategories",      allCategories);
        model.addAttribute("fromDate",           fromDate);
        model.addAttribute("toDate",             toDate);
        model.addAttribute("selectedCategoryId", categoryId);

        if (categoryId == null || categoryId.isBlank()) {
            model.addAttribute("categoryResult", null);
            return "health-report-by-category";
        }

        LivestockCategory selectedCategory = allCategories.stream()
                .filter(c -> c.getId().toString().equals(categoryId))
                .findFirst().orElse(null);

        if (selectedCategory == null) {
            model.addAttribute("categoryResult", null);
            return "health-report-by-category";
        }

        List<LivestockSick> sickRecords = sickRepository.findAll().stream()
                .filter(s -> s.getReportedDate() != null
                        && !s.getReportedDate().isBefore(fromDate)
                        && !s.getReportedDate().isAfter(toDate)
                        && s.getLivestock() != null
                        && s.getLivestock().getLivestockCategory() != null
                        && s.getLivestock().getLivestockCategory().getId().toString().equals(categoryId))
                .collect(Collectors.toList());

        List<LivestockTreatment> treatments = treatmentService.getAll().stream()
                .filter(t -> t.getTreatmentDate() != null
                        && !t.getTreatmentDate().isBefore(fromDate)
                        && !t.getTreatmentDate().isAfter(toDate)
                        && t.getLivestock() != null
                        && t.getLivestock().getLivestockCategory() != null
                        && t.getLivestock().getLivestockCategory().getId().toString().equals(categoryId))
                .collect(Collectors.toList());

        long sickCount      = sickRecords.size();
        long recoveredCount = sickRecords.stream()
                .filter(s -> s.getStatus() == LivestockSick.SickStatus.RECOVERED).count();
        double recoveryRate = sickCount > 0 ? (recoveredCount * 100.0 / sickCount) : 0;
        BigDecimal totalCost = treatments.stream()
                .filter(t -> t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> diseaseBreakdown = sickRecords.stream()
                .filter(s -> s.getDiagnosis() != null && !s.getDiagnosis().isEmpty())
                .collect(Collectors.groupingBy(LivestockSick::getDiagnosis,
                        LinkedHashMap::new, Collectors.counting()));

        List<Map<String, Object>> sickRecordMaps = sickRecords.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tagNumber",    s.getLivestock() != null ? s.getLivestock().getTagNumber() : "N/A");
            m.put("reportedDate", s.getReportedDate());
            m.put("diagnosis",    s.getDiagnosis());
            m.put("status",       s.getStatus() != null ? s.getStatus().name() : "UNKNOWN");
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> treatmentMaps = treatments.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tagNumber",      t.getLivestock() != null ? t.getLivestock().getTagNumber() : "N/A");
            m.put("treatmentDate",  t.getTreatmentDate());
            m.put("treatmentType",  t.getTreatmentType());
            m.put("medicineName",   t.getDescription());
            m.put("administeredBy", t.getVetName());
            m.put("treatmentCost",  t.getTreatmentCost());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> categoryResult = new LinkedHashMap<>();
        categoryResult.put("categoryName",    selectedCategory.getName());
        categoryResult.put("sickCount",       sickCount);
        categoryResult.put("recoveredCount",  recoveredCount);
        categoryResult.put("recoveryRate",    recoveryRate);
        categoryResult.put("treatmentCount",  treatments.size());
        categoryResult.put("treatmentCost",   totalCost);
        categoryResult.put("sickRecords",     sickRecordMaps);
        categoryResult.put("treatments",      treatmentMaps);
        categoryResult.put("diseaseBreakdown", diseaseBreakdown);


        model.addAttribute("categoryResult", categoryResult);
        return "health-report-by-category";
    }

    @GetMapping("/financial-summary")
    public String financialSummary(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            Model model) {

        LocalDate fromDate = (fromStr != null && !fromStr.isBlank())
                ? LocalDate.parse(fromStr) : LocalDate.now().withDayOfYear(1);
        LocalDate toDate = (toStr != null && !toStr.isBlank())
                ? LocalDate.parse(toStr) : LocalDate.now();

        BigDecimal salesRevenue = saleService.getAll().stream()
                .filter(s -> s.getSaleDate() != null &&
                        !s.getSaleDate().isBefore(fromDate) &&
                        !s.getSaleDate().isAfter(toDate))
                .filter(s -> s.getSalePrice() != null)
                .map(LivestockSale::getSalePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal currentLivestockValue = livestockRepository.findAll().stream()
                .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal bornAnimalsValue = livestockRepository.findAll().stream()
                .filter(l -> l.getMother() != null)
                .filter(l -> l.getDateReceived() != null &&
                        !l.getDateReceived().isBefore(fromDate) &&
                        !l.getDateReceived().isAfter(toDate))
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalIncome = salesRevenue.add(currentLivestockValue).add(bornAnimalsValue);

        BigDecimal treatmentCosts = treatmentService.getAll().stream()
                .filter(t -> t.getTreatmentDate() != null &&
                        !t.getTreatmentDate().isBefore(fromDate) &&
                        !t.getTreatmentDate().isAfter(toDate))
                .filter(t -> t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal purchaseCosts = livestockRepository.findAll().stream()
                .filter(l -> l.getDateReceived() != null &&
                        !l.getDateReceived().isBefore(fromDate) &&
                        !l.getDateReceived().isAfter(toDate))
                .filter(l -> l.getMother() == null)
                .filter(l -> l.getCurrentValue() != null)
                .map(Livestock::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal deathLoss = deathService.getAll().stream()
                .filter(d -> d.getDeathDate() != null &&
                        !d.getDeathDate().isBefore(fromDate) &&
                        !d.getDeathDate().isAfter(toDate))
                .filter(d -> d.getLivestock() != null && d.getLivestock().getCurrentValue() != null)
                .map(d -> d.getLivestock().getCurrentValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // LivestockSick has no cost field — sick care costs are already
        // captured under treatmentCosts via LivestockTreatment records
        BigDecimal sickCareCosts = BigDecimal.ZERO;

        BigDecimal totalExpenses = treatmentCosts.add(purchaseCosts).add(deathLoss);
        BigDecimal netProfit     = totalIncome.subtract(totalExpenses);
        String profitStatus      = netProfit.compareTo(BigDecimal.ZERO) >= 0 ? "PROFIT" : "LOSS";

        BigDecimal profitMargin;
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            profitMargin = netProfit
                    .divide(totalIncome, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        } else {
            profitMargin = BigDecimal.ZERO;
        }

        model.addAttribute("fromDate",              fromDate);
        model.addAttribute("toDate",                toDate);
        model.addAttribute("salesRevenue",          salesRevenue);
        model.addAttribute("currentLivestockValue", currentLivestockValue);
        model.addAttribute("bornAnimalsValue",      bornAnimalsValue);
        model.addAttribute("totalIncome",           totalIncome);
        model.addAttribute("treatmentCosts",        treatmentCosts);
        model.addAttribute("sickCareCosts",         sickCareCosts);
        model.addAttribute("purchaseCosts",         purchaseCosts);
        model.addAttribute("deathLoss",             deathLoss);
        model.addAttribute("totalExpenses",         totalExpenses);
        model.addAttribute("netProfit",             netProfit);
        model.addAttribute("profitStatus",          profitStatus);
        model.addAttribute("profitMargin",          profitMargin);

        return "financial-summary";
    }

    @GetMapping("/reproduction-report")
    public String reproductionReport(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            Model model) {

        LocalDate fromDate = (fromStr != null && !fromStr.isBlank())
                ? LocalDate.parse(fromStr) : LocalDate.now().minusYears(1);
        LocalDate toDate = (toStr != null && !toStr.isBlank())
                ? LocalDate.parse(toStr) : LocalDate.now();

        List<Livestock> births = livestockRepository.findAll().stream()
                .filter(l -> l.getMother() != null && l.getDateReceived() != null &&
                        !l.getDateReceived().isBefore(fromDate) &&
                        !l.getDateReceived().isAfter(toDate))
                .collect(Collectors.toList());

        List<Livestock> pregnant = livestockRepository.findAll().stream()
                .filter(l -> Livestock.STATUS_PREGNANT.equals(l.getStatus()))
                .collect(Collectors.toList());

        List<Livestock> mothers = livestockRepository.findAll().stream()
                .filter(l -> l.getOffspringCount() != null && l.getOffspringCount() > 0)
                .collect(Collectors.toList());

        Map<String, Integer> offspringPerAnimal = new LinkedHashMap<>();
        for (Livestock mother : mothers) {
            String key = mother.getTagNumber() + " - " +
                    (mother.getLivestockCategory() != null ? mother.getLivestockCategory().getName() : "Unknown");
            offspringPerAnimal.put(key, mother.getOffspringCount());
        }

        List<LivestockAbortion> abortions = abortionService.getAll().stream()
                .filter(a -> a.getAbortionDate() != null &&
                        !a.getAbortionDate().isBefore(fromDate) &&
                        !a.getAbortionDate().isAfter(toDate))
                .collect(Collectors.toList());

        double avgOffspring = mothers.isEmpty() ? 0 :
                mothers.stream().mapToInt(Livestock::getOffspringCount).average().orElse(0);

        model.addAttribute("fromDate",           fromDate);
        model.addAttribute("toDate",             toDate);
        model.addAttribute("births",             births);
        model.addAttribute("totalBirths",        births.size());
        model.addAttribute("pregnant",           pregnant);
        model.addAttribute("totalPregnant",      pregnant.size());
        model.addAttribute("mothers",            mothers);
        model.addAttribute("totalMothers",       mothers.size());
        model.addAttribute("offspringPerAnimal", offspringPerAnimal);
        model.addAttribute("abortions",          abortions);
        model.addAttribute("totalAbortions",     abortions.size());
        model.addAttribute("avgOffspring",       avgOffspring);

        return "reproduction-report";
    }

    @GetMapping("/owner-report")
    public String ownerReport(Model model) {

        List<Livestock> allLivestock = livestockRepository.findAll();
        Map<String, OwnerStats> ownerStatsMap = new LinkedHashMap<>();

        for (Livestock animal : allLivestock) {
            // ✅ CHANGED: Use getBeneficiary() instead of getFarmer()
            if (animal.getBeneficiary() != null) {
                Beneficiary owner = animal.getBeneficiary();
                String ownerKey = owner.getFullName();
                OwnerStats stats = ownerStatsMap.getOrDefault(ownerKey,
                        new OwnerStats(ownerKey, owner.getNid(), owner.getPhone()));
                stats.animalCount++;
                if (animal.getCurrentValue() != null) stats.totalValue = stats.totalValue.add(animal.getCurrentValue());
                ownerStatsMap.put(ownerKey, stats);
            }
        }

        for (LivestockSale sale : saleService.getAll()) {
            if (sale.getLivestock() != null && sale.getLivestock().getBeneficiary() != null) {
                String ownerKey = sale.getLivestock().getBeneficiary().getFullName();
                OwnerStats stats = ownerStatsMap.get(ownerKey);
                if (stats != null) {
                    stats.salesCount++;
                    if (sale.getSalePrice() != null) stats.salesRevenue = stats.salesRevenue.add(sale.getSalePrice());
                }
            }
        }

        for (LivestockTreatment treatment : treatmentService.getAll()) {
            if (treatment.getLivestock() != null && treatment.getLivestock().getBeneficiary() != null) {
                String ownerKey = treatment.getLivestock().getBeneficiary().getFullName();
                OwnerStats stats = ownerStatsMap.get(ownerKey);
                if (stats != null && treatment.getTreatmentCost() != null)
                    stats.treatmentCosts = stats.treatmentCosts.add(treatment.getTreatmentCost());
            }
        }

        for (OwnerStats stats : ownerStatsMap.values())
            stats.netPosition = stats.totalValue.add(stats.salesRevenue).subtract(stats.treatmentCosts);

        List<OwnerStats> sortedOwners = ownerStatsMap.values().stream()
                .sorted((a, b) -> b.totalValue.compareTo(a.totalValue))
                .collect(Collectors.toList());

        model.addAttribute("owners",      sortedOwners);
        model.addAttribute("totalOwners", sortedOwners.size());

        return "owner-report";
    }

    @GetMapping("/mortality-report")
    public String mortalityReport(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            Model model) {

        LocalDate fromDate = (fromStr != null && !fromStr.isBlank())
                ? LocalDate.parse(fromStr) : LocalDate.now().minusYears(1);
        LocalDate toDate = (toStr != null && !toStr.isBlank())
                ? LocalDate.parse(toStr) : LocalDate.now();

        List<LivestockDeath> deaths = deathService.getAll().stream()
                .filter(d -> d.getDeathDate() != null &&
                        !d.getDeathDate().isBefore(fromDate) &&
                        !d.getDeathDate().isAfter(toDate))
                .collect(Collectors.toList());

        BigDecimal totalLoss = deaths.stream()
                .filter(d -> d.getLivestock() != null && d.getLivestock().getCurrentValue() != null)
                .map(d -> d.getLivestock().getCurrentValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> deathsByCause = deaths.stream()
                .filter(d -> d.getCause() != null && !d.getCause().isEmpty())
                .collect(Collectors.groupingBy(LivestockDeath::getCause, Collectors.counting()));

        Map<String, Long> deathsByCategory = deaths.stream()
                .filter(d -> d.getLivestock() != null && d.getLivestock().getLivestockCategory() != null)
                .collect(Collectors.groupingBy(d -> d.getLivestock().getLivestockCategory().getName(), Collectors.counting()));

        long totalLivestock  = livestockRepository.count();
        long totalDeaths     = deaths.size();
        double mortalityRate = totalLivestock > 0 ? (totalDeaths * 100.0 / totalLivestock) : 0;

        model.addAttribute("fromDate",         fromDate);
        model.addAttribute("toDate",           toDate);
        model.addAttribute("deaths",           deaths);
        model.addAttribute("totalDeaths",      totalDeaths);
        model.addAttribute("totalLoss",        totalLoss);
        model.addAttribute("deathsByCause",    deathsByCause);
        model.addAttribute("deathsByCategory", deathsByCategory);
        model.addAttribute("mortalityRate",    mortalityRate);
        model.addAttribute("totalLivestock",   totalLivestock);

        return "mortality-report";
    }

    @GetMapping("/category-performance")
    public String categoryPerformance(
            @RequestParam(value = "from", required = false) String fromStr,
            @RequestParam(value = "to", required = false) String toStr,
            Model model) {

        LocalDate fromDate = (fromStr != null && !fromStr.isBlank())
                ? LocalDate.parse(fromStr) : LocalDate.now().minusYears(1);
        LocalDate toDate = (toStr != null && !toStr.isBlank())
                ? LocalDate.parse(toStr) : LocalDate.now();

        List<LivestockCategory> categories = livestockRepository.findAll().stream()
                .map(Livestock::getLivestockCategory)
                .filter(cat -> cat != null)
                .distinct()
                .collect(Collectors.toList());

        List<CategoryPerformance> performances = new ArrayList<>();

        for (LivestockCategory category : categories) {
            CategoryPerformance perf = new CategoryPerformance();
            perf.categoryName = category.getName();

            List<Livestock> categoryAnimals = livestockRepository.findAll().stream()
                    .filter(l -> l.getLivestockCategory() != null &&
                            l.getLivestockCategory().getId().equals(category.getId()))
                    .collect(Collectors.toList());

            perf.totalAnimals = categoryAnimals.size();

            perf.revenue = saleService.getAll().stream()
                    .filter(s -> s.getSaleDate() != null &&
                            !s.getSaleDate().isBefore(fromDate) &&
                            !s.getSaleDate().isAfter(toDate))
                    .filter(s -> s.getLivestock() != null &&
                            s.getLivestock().getLivestockCategory() != null &&
                            s.getLivestock().getLivestockCategory().getId().equals(category.getId()))
                    .filter(s -> s.getSalePrice() != null)
                    .map(LivestockSale::getSalePrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            perf.costs = treatmentService.getAll().stream()
                    .filter(t -> t.getTreatmentDate() != null &&
                            !t.getTreatmentDate().isBefore(fromDate) &&
                            !t.getTreatmentDate().isAfter(toDate))
                    .filter(t -> t.getLivestock() != null &&
                            t.getLivestock().getLivestockCategory() != null &&
                            t.getLivestock().getLivestockCategory().getId().equals(category.getId()))
                    .filter(t -> t.getTreatmentCost() != null)
                    .map(LivestockTreatment::getTreatmentCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            perf.profit = perf.revenue.subtract(perf.costs);

            perf.currentValue = categoryAnimals.stream()
                    .filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus()))
                    .filter(l -> l.getCurrentValue() != null)
                    .map(Livestock::getCurrentValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            performances.add(perf);
        }

        performances.sort((a, b) -> b.profit.compareTo(a.profit));

        model.addAttribute("fromDate",     fromDate);
        model.addAttribute("toDate",       toDate);
        model.addAttribute("performances", performances);

        return "category-performance";
    }

    @GetMapping("/animal-detail/{id}")
    public String animalDetail(@PathVariable UUID id, Model model) {

        Optional<Livestock> optAnimal = livestockRepository.findById(id);
        if (optAnimal.isEmpty()) return "redirect:/livestock";

        Livestock animal = optAnimal.get();

        List<LivestockTreatment> treatments = treatmentService.getAll().stream()
                .filter(t -> t.getLivestock() != null && t.getLivestock().getId().equals(id))
                .sorted((a, b) -> b.getTreatmentDate().compareTo(a.getTreatmentDate()))
                .collect(Collectors.toList());

        Optional<LivestockSale> sale = saleService.getAll().stream()
                .filter(s -> s.getLivestock() != null && s.getLivestock().getId().equals(id))
                .findFirst();

        List<Livestock> offspring = livestockRepository.findAll().stream()
                .filter(l -> l.getMother() != null && l.getMother().getId().equals(id))
                .collect(Collectors.toList());

        List<LivestockSick> sickRecords = sickRepository.findAll().stream()
                .filter(s -> s.getLivestock() != null && s.getLivestock().getId().equals(id))
                .collect(Collectors.toList());

        Optional<LivestockDeath> death = deathService.getAll().stream()
                .filter(d -> d.getLivestock() != null && d.getLivestock().getId().equals(id))
                .findFirst();

        BigDecimal totalTreatmentCost = treatments.stream()
                .filter(t -> t.getTreatmentCost() != null)
                .map(LivestockTreatment::getTreatmentCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<TimelineEvent> timeline = new ArrayList<>();
        timeline.add(new TimelineEvent(animal.getDateReceived(), "ACQUISITION",
                animal.getMother() != null ? "Born on farm" : "Purchased",
                animal.getMother() != null ? null : animal.getCurrentValue()));

        for (LivestockTreatment t : treatments)
            timeline.add(new TimelineEvent(t.getTreatmentDate(), "TREATMENT",
                    t.getTreatmentType() + " - " + t.getDescription(), t.getTreatmentCost()));

        for (Livestock child : offspring)
            timeline.add(new TimelineEvent(child.getDateReceived(), "BIRTH",
                    "Gave birth to " + child.getTagNumber(), null));

        sale.ifPresent(s -> timeline.add(new TimelineEvent(s.getSaleDate(), "SALE",
                "Sold - " + s.getSaleReason(), s.getSalePrice())));

        death.ifPresent(d -> timeline.add(new TimelineEvent(d.getDeathDate(), "DEATH",
                "Died - " + d.getCause(), animal.getCurrentValue())));

        timeline.sort((a, b) -> a.date.compareTo(b.date));

        model.addAttribute("animal",             animal);
        model.addAttribute("treatments",         treatments);
        model.addAttribute("sale",               sale.orElse(null));
        model.addAttribute("offspring",          offspring);
        model.addAttribute("sickRecords",        sickRecords);
        model.addAttribute("death",              death.orElse(null));
        model.addAttribute("totalTreatmentCost", totalTreatmentCost);
        model.addAttribute("timeline",           timeline);

        return "animal-detail";
    }

    // =========================================================================
    // HELPER CLASSES
    // =========================================================================

    public static class SalesCategoryStats {
        public String categoryName;
        public int count = 0;
        public BigDecimal revenue = BigDecimal.ZERO;

        public SalesCategoryStats(String categoryName) { this.categoryName = categoryName; }

        public String getCategoryName()     { return categoryName; }
        public int getCount()               { return count; }
        public BigDecimal getRevenue()      { return revenue; }
        public BigDecimal getAveragePrice() {
            return count > 0 ? revenue.divide(BigDecimal.valueOf(count), 2, BigDecimal.ROUND_HALF_UP) : BigDecimal.ZERO;
        }
    }

    public static class OwnerStats {
        public String ownerName;
        public String nid;
        public String phone;
        public int animalCount            = 0;
        public BigDecimal totalValue      = BigDecimal.ZERO;
        public int salesCount             = 0;
        public BigDecimal salesRevenue    = BigDecimal.ZERO;
        public BigDecimal treatmentCosts  = BigDecimal.ZERO;
        public BigDecimal netPosition     = BigDecimal.ZERO;

        public OwnerStats(String ownerName, String nid, String phone) {
            this.ownerName = ownerName;
            this.nid       = nid;
            this.phone     = phone;
        }

        public String getOwnerName()          { return ownerName; }
        public String getNid()                { return nid; }
        public String getPhone()              { return phone; }
        public int getAnimalCount()           { return animalCount; }
        public BigDecimal getTotalValue()     { return totalValue; }
        public int getSalesCount()            { return salesCount; }
        public BigDecimal getSalesRevenue()   { return salesRevenue; }
        public BigDecimal getTreatmentCosts() { return treatmentCosts; }
        public BigDecimal getNetPosition()    { return netPosition; }
    }

    public static class CategoryPerformance {
        public String categoryName;
        public long totalAnimals;
        public BigDecimal revenue      = BigDecimal.ZERO;
        public BigDecimal costs        = BigDecimal.ZERO;
        public BigDecimal profit       = BigDecimal.ZERO;
        public BigDecimal currentValue = BigDecimal.ZERO;

        public String getCategoryName()     { return categoryName; }
        public long getTotalAnimals()       { return totalAnimals; }
        public BigDecimal getRevenue()      { return revenue; }
        public BigDecimal getCosts()        { return costs; }
        public BigDecimal getProfit()       { return profit; }
        public BigDecimal getCurrentValue() { return currentValue; }
        public String getProfitStatus() {
            return profit.compareTo(BigDecimal.ZERO) >= 0 ? "PROFIT" : "LOSS";
        }
    }

    public static class TimelineEvent {
        public LocalDate date;
        public String type;
        public String description;
        public BigDecimal amount;

        public TimelineEvent(LocalDate date, String type, String description, BigDecimal amount) {
            this.date        = date;
            this.type        = type;
            this.description = description;
            this.amount      = amount;
        }

        public LocalDate getDate()     { return date; }
        public String getType()        { return type; }
        public String getDescription() { return description; }
        public BigDecimal getAmount()  { return amount; }
    }
    // AJAX endpoint for buyer search
    @GetMapping("/buyers/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchBuyers(
            @RequestParam(value = "q", required = false) String query) {

        List<Buyer> buyers = buyerService.search(query);

        List<Map<String, Object>> result = buyers.stream().map(buyer -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", buyer.getId());
            map.put("name", buyer.getBuyerName());
            map.put("phone", buyer.getBuyerPhone());
            map.put("address", buyer.getBuyerAddress());
            map.put("nationalId", buyer.getBuyerNationalId());
            map.put("displayName", buyer.getDisplayName());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // AJAX endpoint to add quick buyer
    @PostMapping("/buyers/quick-add")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> quickAddBuyer(@RequestBody Map<String, String> payload) {
        try {
            Buyer buyer = new Buyer();
            buyer.setBuyerName(payload.get("name"));
            buyer.setBuyerPhone(payload.get("phone"));
            buyer.setBuyerAddress(payload.get("address"));
            buyer.setBuyerNationalId(payload.get("nationalId"));
            buyer.setIsActive(true);

            Buyer saved = buyerService.addNew(buyer);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("buyer", Map.of(
                    "id", saved.getId(),
                    "name", saved.getBuyerName(),
                    "phone", saved.getBuyerPhone(),
                    "displayName", saved.getDisplayName()
            ));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

}