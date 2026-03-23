package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockAbortion;
import rw.animalproduct.animal.production.entity.LivestockBirth;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.entity.LivestockOffspring;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.entity.LivestockTreatment;
import rw.animalproduct.animal.production.repository.AbaragizwaAmatungoRepository;
import rw.animalproduct.animal.production.repository.LivestockAbortionRepository;
import rw.animalproduct.animal.production.repository.LivestockBirthRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSaleRepository;
import rw.animalproduct.animal.production.repository.LivestockTreatmentRepository;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.services.LivestockCategoryService;
import rw.animalproduct.animal.production.services.LivestockService;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livestock")
public class LivestockController {

    private final LivestockService              livestockService;
    private final LivestockCategoryService      livestockCategoryService;
    private final AbaragizwaAmatungoRepository  abaragizwaAmatungoRepository;
    private final LocationRepository            locationRepository;
    private final LivestockRepository           livestockRepository;
    private final LivestockBirthRepository      birthRepository;
    private final LivestockSaleRepository       saleRepository;
    private final LivestockTreatmentRepository  treatmentRepository;  // ★ NEW
    private final LivestockAbortionRepository   abortionRepository;   // ★ NEW

    @Autowired
    public LivestockController(LivestockService livestockService,
                               LivestockCategoryService livestockCategoryService,
                               AbaragizwaAmatungoRepository abaragizwaAmatungoRepository,
                               LocationRepository locationRepository,
                               LivestockRepository livestockRepository,
                               LivestockBirthRepository birthRepository,
                               LivestockSaleRepository saleRepository,
                               LivestockTreatmentRepository treatmentRepository,  // ★ NEW
                               LivestockAbortionRepository abortionRepository) {   // ★ NEW
        this.livestockService             = livestockService;
        this.livestockCategoryService     = livestockCategoryService;
        this.abaragizwaAmatungoRepository = abaragizwaAmatungoRepository;
        this.locationRepository           = locationRepository;
        this.livestockRepository          = livestockRepository;
        this.birthRepository              = birthRepository;
        this.saleRepository               = saleRepository;
        this.treatmentRepository          = treatmentRepository;  // ★ NEW
        this.abortionRepository           = abortionRepository;   // ★ NEW
    }

    // ===================== LIVESTOCK CATEGORIES =====================

    @GetMapping("/categories")
    public String listCategories(Model model) {
        List<LivestockCategory> categories = livestockCategoryService.getAll();
        model.addAttribute("categories", categories);
        model.addAttribute("category", new LivestockCategory());
        return "livestock-categories-list";
    }

    @PostMapping("/categories/new")
    public String createCategory(@Valid @ModelAttribute("category") LivestockCategory category,
                                 BindingResult result,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {

        Optional<LivestockCategory> existingCode = livestockCategoryService.getByCode(category.getCode());
        if (existingCode.isPresent()) {
            result.rejectValue("code", "error.category", "Category code already exists");
        }

        Optional<LivestockCategory> existingName = livestockCategoryService.getByName(category.getName());
        if (existingName.isPresent()) {
            result.rejectValue("name", "error.category", "Category name already exists");
        }

        if (result.hasErrors()) {
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            model.addAttribute("error", errorMessages);
            model.addAttribute("categories", livestockCategoryService.getAll());
            return "livestock-categories-list";
        }

        livestockCategoryService.addNew(category);
        redirectAttributes.addFlashAttribute("success", "Livestock category created successfully!");
        return "redirect:/livestock/categories";
    }

    @GetMapping("/categories/edit/{id}")
    public String showEditCategoryForm(@PathVariable("id") UUID id, Model model) {
        Optional<LivestockCategory> categoryOpt = livestockCategoryService.getById(id);
        if (categoryOpt.isEmpty()) {
            return "redirect:/livestock/categories";
        }
        model.addAttribute("category", categoryOpt.get());
        return "livestock-category-edit";
    }

    @PostMapping("/categories/update/{id}")
    public String updateCategory(@PathVariable("id") UUID id,
                                 @Valid @ModelAttribute("category") LivestockCategory category,
                                 BindingResult result,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {

        Optional<LivestockCategory> existingCode = livestockCategoryService.getByCode(category.getCode());
        if (existingCode.isPresent() && !existingCode.get().getId().equals(id)) {
            result.rejectValue("code", "error.category", "Category code already exists");
        }

        Optional<LivestockCategory> existingName = livestockCategoryService.getByName(category.getName());
        if (existingName.isPresent() && !existingName.get().getId().equals(id)) {
            result.rejectValue("name", "error.category", "Category name already exists");
        }

        if (result.hasErrors()) {
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            model.addAttribute("error", errorMessages);
            return "livestock-category-edit";
        }

        livestockCategoryService.update(id, category);
        redirectAttributes.addFlashAttribute("success", "Livestock category updated successfully!");
        return "redirect:/livestock/categories";
    }

    @PostMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable("id") UUID id,
                                 RedirectAttributes redirectAttributes) {
        try {
            livestockCategoryService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Livestock category deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Cannot delete category: it may have livestock assigned to it.");
        }
        return "redirect:/livestock/categories";
    }

    // ===================== LIVESTOCK LIST =====================

    @GetMapping("/list")
    public String listAll(@RequestParam(value = "page", defaultValue = "0") int page,
                          @RequestParam(value = "size", defaultValue = "10") int size,
                          @RequestParam(value = "sort", defaultValue = "tagNumber") String sort,
                          Model model) {

        // ── Paginated livestock list ──────────────────────────────────
        List<Livestock> currentPageList;
        long totalItems = 0;
        int  totalPages = 1;

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.Direction.ASC, sort);
            Page<Livestock> pageContent = livestockRepository.findAll(pageable);
            currentPageList = pageContent.getContent();
            totalItems      = pageContent.getTotalElements();
            totalPages      = pageContent.getTotalPages();
        } catch (Exception e) {
            currentPageList = livestockService.getAll();
            totalItems      = currentPageList.size();
            totalPages      = 1;
            page            = 0;
        }

        model.addAttribute("livestockList", currentPageList);
        model.addAttribute("currentPage",   page);
        model.addAttribute("totalPages",    totalPages);
        model.addAttribute("totalItems",    totalItems);
        model.addAttribute("pageSize",      size);

        // ── birthMap: child livestock_id → birth date ─────────────────
        Map<UUID, LocalDate> birthMap = new HashMap<>();
        try {
            List<LivestockBirth> allBirths = birthRepository.findAll();
            for (LivestockBirth birth : allBirths) {
                if (birth.getChildren() == null) continue;
                for (LivestockOffspring offspring : birth.getChildren()) {
                    if (offspring.getChildLivestock() != null) {
                        birthMap.put(
                                offspring.getChildLivestock().getId(),
                                birth.getBirthDate()
                        );
                    }
                }
            }
        } catch (Exception ignored) {}
        model.addAttribute("birthMap", birthMap);

        // ── motherMap: mother livestock_id → true ────────────────────
        Map<UUID, Boolean> motherMap = new HashMap<>();
        try {
            List<LivestockBirth> allBirths = birthRepository.findAll();
            for (LivestockBirth birth : allBirths) {
                if (birth.getLivestock() != null) {
                    motherMap.put(birth.getLivestock().getId(), Boolean.TRUE);
                }
            }
        } catch (Exception ignored) {}
        model.addAttribute("motherMap", motherMap);

        // ── saleMap: livestock_id → most recent LivestockSale ────────
        Map<UUID, LivestockSale> saleMap = new HashMap<>();
        try {
            List<LivestockSale> allSales = saleRepository.findAll();
            for (LivestockSale sale : allSales) {
                if (sale.getLivestock() == null) continue;
                UUID lid = sale.getLivestock().getId();
                if (!saleMap.containsKey(lid) ||
                        sale.getSaleDate().isAfter(saleMap.get(lid).getSaleDate())) {
                    saleMap.put(lid, sale);
                }
            }
        } catch (Exception ignored) {}
        model.addAttribute("saleMap", saleMap);

        // ── ★ treatmentMap: livestock_id → most recent treatment ─────
        Map<UUID, LivestockTreatment> treatmentMap      = new HashMap<>();
        Map<UUID, Long>               treatmentCountMap = new HashMap<>();
        try {
            List<LivestockTreatment> allTreatments = treatmentRepository.findAll();
            for (LivestockTreatment t : allTreatments) {
                if (t.getLivestock() == null) continue;
                UUID lid = t.getLivestock().getId();
                // count per animal
                treatmentCountMap.merge(lid, 1L, Long::sum);
                // keep most recent
                if (!treatmentMap.containsKey(lid) ||
                        t.getTreatmentDate().isAfter(treatmentMap.get(lid).getTreatmentDate())) {
                    treatmentMap.put(lid, t);
                }
            }
        } catch (Exception ignored) {}
        model.addAttribute("treatmentMap",      treatmentMap);
        model.addAttribute("treatmentCountMap", treatmentCountMap);
        // total treatments across ALL animals (sum of counts)
        long totalTreatments = treatmentCountMap.values().stream().mapToLong(Long::longValue).sum();
        model.addAttribute("totalTreatments", totalTreatments);

        // ── ★ abortionMap: livestock_id → most recent abortion ───────
        Map<UUID, LivestockAbortion> abortionMap      = new HashMap<>();
        Map<UUID, Long>              abortionCountMap = new HashMap<>();
        try {
            List<LivestockAbortion> allAbortions = abortionRepository.findAll();
            for (LivestockAbortion a : allAbortions) {
                if (a.getLivestock() == null) continue;
                UUID lid = a.getLivestock().getId();
                // count per animal
                abortionCountMap.merge(lid, 1L, Long::sum);
                // keep most recent
                if (!abortionMap.containsKey(lid) ||
                        a.getAbortionDate().isAfter(abortionMap.get(lid).getAbortionDate())) {
                    abortionMap.put(lid, a);
                }
            }
        } catch (Exception ignored) {}
        model.addAttribute("abortionMap",      abortionMap);
        model.addAttribute("abortionCountMap", abortionCountMap);
        // total abortion events across ALL animals
        long totalAbortions = abortionCountMap.values().stream().mapToLong(Long::longValue).sum();
        model.addAttribute("totalAbortions", totalAbortions);

        // ── sickMap / sickCountMap: wire up your sick repository here ─
        // Currently set empty so the template does not crash.
        model.addAttribute("sickMap",      new HashMap<>());
        model.addAttribute("sickCountMap", new HashMap<>());

        // ── Status counts for the summary strip ───────────────────────
        try {
            List<Livestock> all = livestockRepository.findAll();
            model.addAttribute("totalActive",
                    all.stream().filter(l -> Livestock.STATUS_ACTIVE.equals(l.getStatus())).count());
            model.addAttribute("totalSold",
                    all.stream().filter(l -> Livestock.STATUS_SOLD.equals(l.getStatus())).count());
            model.addAttribute("totalDead",
                    all.stream().filter(l -> Livestock.STATUS_DEAD.equals(l.getStatus())).count());
            model.addAttribute("totalSick",
                    all.stream().filter(l -> Livestock.STATUS_SICK.equals(l.getStatus())).count());
            model.addAttribute("totalBornOnFarm", (long) birthMap.size());
        } catch (Exception ignored) {
            model.addAttribute("totalActive",     0L);
            model.addAttribute("totalSold",       0L);
            model.addAttribute("totalDead",       0L);
            model.addAttribute("totalSick",       0L);
            model.addAttribute("totalBornOnFarm", 0L);
        }

        return "livestock-list";
    }

    // ===================== REGISTER =====================

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("livestock", new Livestock());
        model.addAttribute("categories", livestockCategoryService.getAll());
        model.addAttribute("abaragizwaList", abaragizwaAmatungoRepository.findAll());
        model.addAttribute("locationList", locationRepository.findAll());
        return "livestock-register";
    }

    @PostMapping("/register/new")
    public String register(@Valid @ModelAttribute("livestock") Livestock livestock,
                           @RequestParam(value = "locationId", required = false) UUID locationId,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        Optional<Livestock> existingTag = livestockService.getByTagNumber(livestock.getTagNumber());
        if (existingTag.isPresent()) {
            result.rejectValue("tagNumber", "error.livestock", "Tag number already exists");
        }

        if (livestock.getLivestockCategoryIdValue() == null || livestock.getLivestockCategoryIdValue().trim().isEmpty()) {
            result.rejectValue("livestockCategoryIdValue", "error.livestock", "Livestock category is required");
        }

        if (livestock.getAbaragizwaAmatungoIdValue() == null || livestock.getAbaragizwaAmatungoIdValue().trim().isEmpty()) {
            result.rejectValue("abaragizwaAmatungoIdValue", "error.livestock", "Beneficiary is required");
        }

        if (result.hasErrors()) {
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            model.addAttribute("error", errorMessages);
            model.addAttribute("categories", livestockCategoryService.getAll());
            model.addAttribute("abaragizwaList", abaragizwaAmatungoRepository.findAll());
            model.addAttribute("locationList", locationRepository.findAll());
            return "livestock-register";
        }

        try {
            if (locationId != null) {
                locationRepository.findById(locationId).ifPresent(livestock::setLocation);
            }
            livestockService.addNew(livestock);
            redirectAttributes.addFlashAttribute("success", "Livestock registered successfully!");
            return "redirect:/livestock/list";
        } catch (Exception e) {
            model.addAttribute("error", "Error registering livestock: " + e.getMessage());
            model.addAttribute("categories", livestockCategoryService.getAll());
            model.addAttribute("abaragizwaList", abaragizwaAmatungoRepository.findAll());
            model.addAttribute("locationList", locationRepository.findAll());
            return "livestock-register";
        }
    }

    // ===================== EDIT =====================

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") UUID id, Model model) {
        Optional<Livestock> livestockOpt = livestockService.getById(id);
        if (livestockOpt.isEmpty()) {
            return "redirect:/livestock/list";
        }

        Livestock livestock = livestockOpt.get();
        if (livestock.getLivestockCategory() != null) {
            livestock.setLivestockCategoryIdValue(livestock.getLivestockCategory().getId().toString());
        }
        if (livestock.getAbaragizwaAmatungo() != null) {
            livestock.setAbaragizwaAmatungoIdValue(livestock.getAbaragizwaAmatungo().getId().toString());
        }

        model.addAttribute("livestock", livestock);
        model.addAttribute("categories", livestockCategoryService.getAll());
        model.addAttribute("abaragizwaList", abaragizwaAmatungoRepository.findAll());
        model.addAttribute("locationList", locationRepository.findAll());
        return "livestock-edit";
    }

    @PostMapping("/update/{id}")
    public String update(@PathVariable("id") UUID id,
                         @Valid @ModelAttribute("livestock") Livestock livestock,
                         @RequestParam(value = "locationId", required = false) UUID locationId,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        Optional<Livestock> existingTag = livestockService.getByTagNumber(livestock.getTagNumber());
        if (existingTag.isPresent() && !existingTag.get().getId().equals(id)) {
            result.rejectValue("tagNumber", "error.livestock", "Tag number already exists");
        }

        if (livestock.getLivestockCategoryIdValue() == null || livestock.getLivestockCategoryIdValue().trim().isEmpty()) {
            result.rejectValue("livestockCategoryIdValue", "error.livestock", "Livestock category is required");
        }

        if (livestock.getAbaragizwaAmatungoIdValue() == null || livestock.getAbaragizwaAmatungoIdValue().trim().isEmpty()) {
            result.rejectValue("abaragizwaAmatungoIdValue", "error.livestock", "Beneficiary is required");
        }

        if (result.hasErrors()) {
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            model.addAttribute("error", errorMessages);
            model.addAttribute("categories", livestockCategoryService.getAll());
            model.addAttribute("abaragizwaList", abaragizwaAmatungoRepository.findAll());
            model.addAttribute("locationList", locationRepository.findAll());
            return "livestock-edit";
        }

        try {
            if (locationId != null) {
                locationRepository.findById(locationId).ifPresent(livestock::setLocation);
            }
            livestockService.update(id, livestock);
            redirectAttributes.addFlashAttribute("success", "Livestock updated successfully!");
            return "redirect:/livestock/list";
        } catch (Exception e) {
            model.addAttribute("error", "Error updating livestock: " + e.getMessage());
            model.addAttribute("categories", livestockCategoryService.getAll());
            model.addAttribute("abaragizwaList", abaragizwaAmatungoRepository.findAll());
            model.addAttribute("locationList", locationRepository.findAll());
            return "livestock-edit";
        }
    }

    // ===================== DELETE =====================

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") UUID id,
                         RedirectAttributes redirectAttributes) {
        livestockService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Livestock deleted successfully!");
        return "redirect:/livestock/list";
    }

    // ===================== VIEW =====================

    @GetMapping("/view/{id}")
    public String viewDetails(@PathVariable("id") UUID id, Model model) {
        Optional<Livestock> livestockOpt = livestockService.getById(id);
        if (livestockOpt.isEmpty()) {
            return "redirect:/livestock/list";
        }
        model.addAttribute("livestock", livestockOpt.get());
        return "livestock-view";
    }
}
