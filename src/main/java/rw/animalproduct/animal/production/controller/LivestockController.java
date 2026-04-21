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
import rw.animalproduct.animal.production.entity.*;
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
import java.util.Comparator;

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
    private final LivestockTreatmentRepository  treatmentRepository;
    private final LivestockAbortionRepository   abortionRepository;

    @Autowired
    public LivestockController(LivestockService livestockService,
                               LivestockCategoryService livestockCategoryService,
                               AbaragizwaAmatungoRepository abaragizwaAmatungoRepository,
                               LocationRepository locationRepository,
                               LivestockRepository livestockRepository,
                               LivestockBirthRepository birthRepository,
                               LivestockSaleRepository saleRepository,
                               LivestockTreatmentRepository treatmentRepository,
                               LivestockAbortionRepository abortionRepository) {
        this.livestockService             = livestockService;
        this.livestockCategoryService     = livestockCategoryService;
        this.abaragizwaAmatungoRepository = abaragizwaAmatungoRepository;
        this.locationRepository           = locationRepository;
        this.livestockRepository          = livestockRepository;
        this.birthRepository              = birthRepository;
        this.saleRepository               = saleRepository;
        this.treatmentRepository          = treatmentRepository;
        this.abortionRepository           = abortionRepository;
    }

    // =====================================================================
    // HELPER METHODS
    // =====================================================================

    private String suggestNextTag(String existingTag) {
        if (existingTag == null || existingTag.isEmpty()) return null;

        int i = existingTag.length() - 1;
        while (i >= 0 && Character.isDigit(existingTag.charAt(i))) i--;

        if (i == existingTag.length() - 1) return null;

        String prefix = existingTag.substring(0, i + 1);
        String numPart = existingTag.substring(i + 1);

        try {
            long num = Long.parseLong(numPart);
            String nextNum = String.format("%0" + numPart.length() + "d", num + 1);
            return prefix + nextNum;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String findLastTagNumber() {
        return livestockRepository.findAll().stream()
                .map(Livestock::getTagNumber)
                .filter(t -> t != null && !t.isEmpty())
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    // =====================================================================
    // LIVESTOCK CATEGORIES
    // =====================================================================

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

    // =====================================================================
    // LIVESTOCK LIST
    // =====================================================================

    @GetMapping("/list")
    public String listAll(@RequestParam(value = "page", defaultValue = "0") int page,
                          @RequestParam(value = "size", defaultValue = "10") int size,
                          @RequestParam(value = "sort", defaultValue = "tagNumber") String sort,
                          Model model) {

        List<Livestock> currentPageList;
        long totalItems = 0;
        int totalPages = 1;

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.Direction.ASC, sort);
            Page<Livestock> pageContent = livestockRepository.findAll(pageable);
            currentPageList = pageContent.getContent();
            totalItems = pageContent.getTotalElements();
            totalPages = pageContent.getTotalPages();
        } catch (Exception e) {
            currentPageList = livestockService.getAll();
            totalItems = currentPageList.size();
            totalPages = 1;
            page = 0;
        }

        model.addAttribute("livestockList", currentPageList);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("pageSize", size);

        model.addAttribute("birthMap", new HashMap<>());
        model.addAttribute("motherMap", new HashMap<>());
        model.addAttribute("saleMap", new HashMap<>());
        model.addAttribute("treatmentMap", new HashMap<>());
        model.addAttribute("treatmentCountMap", new HashMap<>());
        model.addAttribute("abortionMap", new HashMap<>());
        model.addAttribute("abortionCountMap", new HashMap<>());
        model.addAttribute("sickMap", new HashMap<>());
        model.addAttribute("sickCountMap", new HashMap<>());

        return "livestock-list";
    }

    // =====================================================================
    // REGISTER
    // =====================================================================

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("livestock", new Livestock());
        model.addAttribute("categories", livestockCategoryService.getAll());
        model.addAttribute("abaragizwaList", abaragizwaAmatungoRepository.findAll());
        model.addAttribute("locationList", locationRepository.findAll());

        String lastTag = findLastTagNumber();
        model.addAttribute("lastTag", lastTag);
        model.addAttribute("suggestedTag", suggestNextTag(lastTag));

        return "livestock-register";
    }

    @PostMapping("/register/new")
    public String register(@Valid @ModelAttribute("livestock") Livestock livestock,
                           @RequestParam(value = "locationId", required = false) UUID locationId,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        // Check for duplicate tag number
        Optional<Livestock> existingTag = livestockService.getByTagNumber(livestock.getTagNumber());
        if (existingTag.isPresent()) {
            result.rejectValue("tagNumber", "error.livestock", "Tag number already exists");
            model.addAttribute("lastTag", livestock.getTagNumber());
            model.addAttribute("suggestedTag", suggestNextTag(livestock.getTagNumber()));
        }

        // Handle pregnancy logic using plain String values
        if (Boolean.TRUE.equals(livestock.getIsPregnant())) {
            livestock.setStatus(Livestock.STATUS_PREGNANT);
            livestock.setPregnancyStatus("PREGNANT");
        } else {
            livestock.setPregnancyStatus("NOT_PREGNANT");
            livestock.setPregnancyMonths(null);
        }

        if (result.hasErrors()) {
            if (!model.containsAttribute("suggestedTag")) {
                String lastTag = findLastTagNumber();
                model.addAttribute("lastTag", lastTag);
                model.addAttribute("suggestedTag", suggestNextTag(lastTag));
            }

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

    // =====================================================================
    // EDIT
    // =====================================================================

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") UUID id, Model model) {
        Optional<Livestock> livestockOpt = livestockService.getById(id);
        if (livestockOpt.isEmpty()) {
            return "redirect:/livestock/list";
        }

        Livestock livestock = livestockOpt.get();

        // Pre-populate transient fields for the form
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

        // Handle pregnancy logic using plain String values
        if (Boolean.TRUE.equals(livestock.getIsPregnant())) {
            livestock.setStatus(Livestock.STATUS_PREGNANT);
            livestock.setPregnancyStatus("PREGNANT");
        } else {
            if ("PREGNANT".equals(livestock.getPregnancyStatus())) {
                livestock.setPregnancyStatus("NOT_PREGNANT");
            }
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

    // =====================================================================
    // DELETE
    // =====================================================================

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") UUID id, RedirectAttributes redirectAttributes) {
        try {
            livestockService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Livestock deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Cannot delete livestock: " + e.getMessage());
        }
        return "redirect:/livestock/list";
    }

    // =====================================================================
    // VIEW DETAIL
    // =====================================================================

    @GetMapping("/view/{id}")
    public String viewDetail(@PathVariable("id") UUID id, Model model) {
        Optional<Livestock> livestockOpt = livestockService.getById(id);
        if (livestockOpt.isEmpty()) {
            return "redirect:/livestock/list";
        }
        model.addAttribute("livestock", livestockOpt.get());
        return "livestock-detail";
    }

    // =====================================================================
    // API ENDPOINTS
    // =====================================================================

    @GetMapping("/api/suggest-tag")
    @ResponseBody
    public Map<String, String> suggestTagForCategory(@RequestParam("categoryId") String categoryId) {
        Map<String, String> response = new HashMap<>();

        String lastTag = livestockRepository.findAll().stream()
                .filter(l -> l.getLivestockCategory() != null
                        && l.getLivestockCategory().getId().toString().equals(categoryId))
                .map(Livestock::getTagNumber)
                .filter(t -> t != null && !t.isEmpty())
                .max(Comparator.naturalOrder())
                .orElse(null);

        response.put("lastTag", lastTag != null ? lastTag : "None yet");
        response.put("suggestedTag", lastTag != null ? suggestNextTag(lastTag) : null);

        return response;
    }
}