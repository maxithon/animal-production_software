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
import rw.animalproduct.animal.production.repository.BeneficiaryRepository;
import rw.animalproduct.animal.production.repository.LivestockAbortionRepository;
import rw.animalproduct.animal.production.repository.LivestockBirthRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSaleRepository;
import rw.animalproduct.animal.production.repository.LivestockTreatmentRepository;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.services.LivestockBreedingService;
import rw.animalproduct.animal.production.services.LivestockCategoryService;
import rw.animalproduct.animal.production.services.LivestockService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Comparator;

@Controller
@RequestMapping("/livestock")
public class LivestockController {

    private final LivestockService              livestockService;
    private final LivestockCategoryService      livestockCategoryService;
    private final BeneficiaryRepository         beneficiaryRepository;
    private final LocationRepository            locationRepository;
    private final LivestockRepository           livestockRepository;
    private final LivestockBirthRepository      birthRepository;
    private final LivestockSaleRepository       saleRepository;
    private final LivestockTreatmentRepository  treatmentRepository;
    private final LivestockAbortionRepository   abortionRepository;
    private final LivestockBreedingService      breedingService;

    @Autowired
    public LivestockController(LivestockService livestockService,
                               LivestockCategoryService livestockCategoryService,
                               BeneficiaryRepository beneficiaryRepository,
                               LocationRepository locationRepository,
                               LivestockRepository livestockRepository,
                               LivestockBirthRepository birthRepository,
                               LivestockSaleRepository saleRepository,
                               LivestockTreatmentRepository treatmentRepository,
                               LivestockAbortionRepository abortionRepository,
                               LivestockBreedingService breedingService) {
        this.livestockService             = livestockService;
        this.livestockCategoryService     = livestockCategoryService;
        this.beneficiaryRepository        = beneficiaryRepository;
        this.locationRepository           = locationRepository;
        this.livestockRepository          = livestockRepository;
        this.birthRepository              = birthRepository;
        this.saleRepository               = saleRepository;
        this.treatmentRepository          = treatmentRepository;
        this.abortionRepository           = abortionRepository;
        this.breedingService              = breedingService;
    }

    // =====================================================================
    // HELPER METHODS
    // =====================================================================

    private String suggestNextTag(String existingTag) {
        if (existingTag == null || existingTag.isEmpty()) return null;

        int i = existingTag.length() - 1;
        while (i >= 0 && Character.isDigit(existingTag.charAt(i))) i--;

        if (i == existingTag.length() - 1) return null;

        String prefix  = existingTag.substring(0, i + 1);
        String numPart = existingTag.substring(i + 1);

        try {
            long num     = Long.parseLong(numPart);
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

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private void populateFormModel(Model model) {
        model.addAttribute("categories",        livestockCategoryService.getAll());
        model.addAttribute("beneficiariesList", beneficiaryRepository.findAll());
        model.addAttribute("locationList",      locationRepository.findAll());
    }

    // =====================================================================
    // LIVESTOCK CATEGORIES
    // =====================================================================

    @GetMapping("/categories")
    public String listCategories(Model model) {
        model.addAttribute("categories", livestockCategoryService.getAll());
        model.addAttribute("category",   new LivestockCategory());
        return "livestock-categories-list";
    }

    @PostMapping("/categories/new")
    public String createCategory(@Valid @ModelAttribute("category") LivestockCategory category,
                                 BindingResult result,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {

        if (livestockCategoryService.getByCode(category.getCode()).isPresent()) {
            result.rejectValue("code", "error.category", "Category code already exists");
        }
        if (livestockCategoryService.getByName(category.getName()).isPresent()) {
            result.rejectValue("name", "error.category", "Category name already exists");
        }

        if (result.hasErrors()) {
            model.addAttribute("error", result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage).collect(Collectors.joining(", ")));
            model.addAttribute("categories", livestockCategoryService.getAll());
            return "livestock-categories-list";
        }

        livestockCategoryService.addNew(category);
        redirectAttributes.addFlashAttribute("success", "Livestock category created successfully!");
        return "redirect:/livestock/categories";
    }

    @GetMapping("/categories/edit/{id}")
    public String showEditCategoryForm(@PathVariable UUID id, Model model) {
        return livestockCategoryService.getById(id)
                .map(cat -> { model.addAttribute("category", cat); return "livestock-category-edit"; })
                .orElse("redirect:/livestock/categories");
    }

    @PostMapping("/categories/update/{id}")
    public String updateCategory(@PathVariable UUID id,
                                 @Valid @ModelAttribute("category") LivestockCategory category,
                                 BindingResult result, Model model,
                                 RedirectAttributes redirectAttributes) {

        livestockCategoryService.getByCode(category.getCode())
                .filter(c -> !c.getId().equals(id))
                .ifPresent(c -> result.rejectValue("code", "error.category", "Category code already exists"));

        livestockCategoryService.getByName(category.getName())
                .filter(c -> !c.getId().equals(id))
                .ifPresent(c -> result.rejectValue("name", "error.category", "Category name already exists"));

        if (result.hasErrors()) {
            model.addAttribute("error", result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage).collect(Collectors.joining(", ")));
            return "livestock-category-edit";
        }

        livestockCategoryService.update(id, category);
        redirectAttributes.addFlashAttribute("success", "Livestock category updated successfully!");
        return "redirect:/livestock/categories";
    }

    @PostMapping("/categories/delete/{id}")
    public String deleteCategory(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            livestockCategoryService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Livestock category deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Cannot delete category: it may have livestock assigned to it.");
        }
        return "redirect:/livestock/categories";
    }

    // =====================================================================
    // LIVESTOCK LIST
    // =====================================================================

    @GetMapping("/list")
    public String listAll(@RequestParam(value = "page",  defaultValue = "0")         int page,
                          @RequestParam(value = "size",  defaultValue = "10")        int size,
                          @RequestParam(value = "sort",  defaultValue = "tagNumber") String sort,
                          Model model) {

        List<Livestock> allLivestock = livestockService.getAll();

        long totalAllItems  = allLivestock.size();
        long totalActive    = allLivestock.stream().filter(l -> "ACTIVE".equals(l.getStatus())).count();
        long totalSold      = allLivestock.stream().filter(l -> "SOLD".equals(l.getStatus())).count();
        long totalSick      = allLivestock.stream().filter(l -> "SICK".equals(l.getStatus())).count();
        long totalDead      = allLivestock.stream().filter(l -> "DEAD".equals(l.getStatus())).count();
        long totalPregnant  = allLivestock.stream().filter(l -> "PREGNANT".equals(l.getStatus())).count();
        long totalBornOnFarm = allLivestock.stream()
                .filter(l -> birthRepository.findByChildAnimalId(l.getId()).isPresent())
                .count();

        long totalTreatments = treatmentRepository.countByIsDeletedFalse();
        long totalAbortions  = abortionRepository.findAllActive().size();

        List<Livestock> currentPageList;
        int totalPages;
        int currentPage;
        int pageSize;

        try {
            Pageable pageable  = PageRequest.of(page, size, Sort.Direction.ASC, sort);
            Page<Livestock> pg = livestockRepository.findAll(pageable);
            currentPageList    = pg.getContent();
            totalPages         = pg.getTotalPages();
            currentPage        = page;
            pageSize           = size;
        } catch (Exception e) {
            currentPageList = allLivestock;
            totalPages      = 1;
            currentPage     = 0;
            pageSize        = size;
        }

        model.addAttribute("livestockList", currentPageList);
        model.addAttribute("currentPage",   currentPage);
        model.addAttribute("totalPages",    totalPages);
        model.addAttribute("totalItems",    totalAllItems);
        model.addAttribute("pageSize",      pageSize);

        // Birth map
        Map<UUID, LivestockBirth> birthMap = new HashMap<>();
        for (Livestock ls : currentPageList) {
            birthRepository.findByChildAnimalId(ls.getId()).ifPresent(b -> birthMap.put(ls.getId(), b));
        }
        model.addAttribute("birthMap", birthMap);

        // Mother map
        Map<UUID, Boolean> motherMap = new HashMap<>();
        for (Livestock ls : currentPageList) {
            List<LivestockBirth> births = birthRepository.findByLivestockId(ls.getId());
            if (births != null && !births.isEmpty()) motherMap.put(ls.getId(), true);
        }
        model.addAttribute("motherMap", motherMap);

        // Sale map
        Map<UUID, LivestockSale> saleMap = new HashMap<>();
        for (Livestock ls : currentPageList) {
            List<LivestockSale> sales = saleRepository.findByLivestockId(ls.getId());
            if (sales != null && !sales.isEmpty()) {
                sales.stream().max(Comparator.comparing(LivestockSale::getSaleDate))
                        .ifPresent(s -> saleMap.put(ls.getId(), s));
            }
        }
        model.addAttribute("saleMap", saleMap);

        // Treatment maps
        Map<UUID, LivestockTreatment> treatmentMap      = new HashMap<>();
        Map<UUID, Long>               treatmentCountMap = new HashMap<>();
        for (Livestock ls : currentPageList) {
            List<LivestockTreatment> treatments = treatmentRepository.findByLivestock_Id(ls.getId());
            if (treatments != null && !treatments.isEmpty()) {
                treatments.stream().max(Comparator.comparing(LivestockTreatment::getTreatmentDate))
                        .ifPresent(t -> treatmentMap.put(ls.getId(), t));
                treatmentCountMap.put(ls.getId(), (long) treatments.size());
            }
        }
        model.addAttribute("treatmentMap",      treatmentMap);
        model.addAttribute("treatmentCountMap", treatmentCountMap);

        // Abortion maps
        Map<UUID, LivestockAbortion> abortionMap      = new HashMap<>();
        Map<UUID, Long>              abortionCountMap = new HashMap<>();
        for (Livestock ls : currentPageList) {
            List<LivestockAbortion> abortions = abortionRepository.findByLivestockId(ls.getId());
            if (abortions != null && !abortions.isEmpty()) {
                abortions.stream().max(Comparator.comparing(LivestockAbortion::getAbortionDate))
                        .ifPresent(a -> abortionMap.put(ls.getId(), a));
                abortionCountMap.put(ls.getId(), (long) abortions.size());
            }
        }
        model.addAttribute("abortionMap",      abortionMap);
        model.addAttribute("abortionCountMap", abortionCountMap);
        model.addAttribute("sickMap",          new HashMap<>());
        model.addAttribute("sickCountMap",     new HashMap<>());

        // Breeding capability map
        Map<UUID, Boolean> breedingCapableMap = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (Livestock ls : currentPageList) {
            LocalDate bd = ls.getBirthDate();
            if (bd == null) continue;
            LivestockCategory cat = ls.getLivestockCategory();
            if (cat == null || cat.getMinBreedingAgeMonths() == null) continue;
            long ageMonths = ChronoUnit.MONTHS.between(bd, today);
            breedingCapableMap.put(ls.getId(), ageMonths >= cat.getMinBreedingAgeMonths());
        }
        model.addAttribute("breedingCapableMap", breedingCapableMap);

        model.addAttribute("totalActive",     totalActive);
        model.addAttribute("totalSold",       totalSold);
        model.addAttribute("totalSick",       totalSick);
        model.addAttribute("totalDead",       totalDead);
        model.addAttribute("totalPregnant",   totalPregnant);
        model.addAttribute("totalBornOnFarm", totalBornOnFarm);
        model.addAttribute("totalTreatments", totalTreatments);
        model.addAttribute("totalAbortions",  totalAbortions);

        return "livestock-list";
    }

    // =====================================================================
    // REGISTER
    // =====================================================================

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("livestock", new Livestock());
        populateFormModel(model);

        String lastTag = findLastTagNumber();
        model.addAttribute("lastTag",      lastTag);
        model.addAttribute("suggestedTag", suggestNextTag(lastTag));

        return "livestock-register";
    }

    /**
     * Registers a new animal.
     *
     * KEY LOGIC:
     *
     * 1. BIRTH animals:
     *    - birthDate is cleared (managed via livestock_births table)
     *    - isPregnant is forced to false (a newborn cannot be pregnant)
     *    - No breeding record is created at registration time
     *
     * 2. PURCHASE / DONATION / TRANSFER / OTHER animals:
     *    - birthDate is kept as provided (the animal's real birthday)
     *    - If female + isPregnant = true:
     *        → status set to PREGNANT
     *        → pregnancyStatus set to "PREGNANT"
     *        → A CONFIRMED_PREGNANT breeding record is auto-created (PURCHASE_PREGNANT method)
     *          so the animal appears in all pregnancy tracking dashboards
     *
     * Form parameters (beyond the Livestock object):
     *   locationId       – UUID of the selected location
     *   conceptionDate   – yyyy-MM-dd string, may be blank
     *   expectedDueDate  – yyyy-MM-dd string, may be blank
     */
    @PostMapping("/register/new")
    public String register(@Valid @ModelAttribute("livestock") Livestock livestock,
                           @RequestParam(value = "locationId",      required = false) UUID locationId,
                           @RequestParam(value = "conceptionDate",  required = false) String conceptionDateStr,
                           @RequestParam(value = "expectedDueDate", required = false) String expectedDueDateStr,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        // Duplicate tag check
        if (livestock.getTagNumber() != null && !livestock.getTagNumber().isBlank()) {
            livestockService.getByTagNumber(livestock.getTagNumber()).ifPresent(existing -> {
                result.rejectValue("tagNumber", "error.livestock", "Tag number already exists");
                model.addAttribute("lastTag",      livestock.getTagNumber());
                model.addAttribute("suggestedTag", suggestNextTag(livestock.getTagNumber()));
            });
        }

        // ── BIRTH animals: clear birth date + force not-pregnant ──────────────
        boolean isBirth = Livestock.ACQ_BIRTH.equals(livestock.getAcquisitionMethod());
        if (isBirth) {
            livestock.setBirthDate(null);
            livestock.setIsPregnant(false);
            livestock.setPregnancyStatus("NOT_PREGNANT");
            livestock.setStatus(Livestock.STATUS_ACTIVE);
        } else {
            // ── Non-BIRTH: apply pregnancy status ─────────────────────────────
            if (Boolean.TRUE.equals(livestock.getIsPregnant())
                    && "FEMALE".equalsIgnoreCase(livestock.getGender())) {
                livestock.setStatus(Livestock.STATUS_PREGNANT);
                livestock.setPregnancyStatus("PREGNANT");
            } else {
                livestock.setIsPregnant(false);
                livestock.setPregnancyStatus("NOT_PREGNANT");
            }
        }

        livestock.setPregnancyMonths(null);

        if (result.hasErrors()) {
            // Rebuild model for re-render
            if (!model.containsAttribute("suggestedTag")) {
                String lastTag = findLastTagNumber();
                model.addAttribute("lastTag",      lastTag);
                model.addAttribute("suggestedTag", suggestNextTag(lastTag));
            }
            model.addAttribute("error", result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage).collect(Collectors.joining(", ")));
            populateFormModel(model);
            return "livestock-register";
        }

        try {
            if (locationId != null) {
                locationRepository.findById(locationId).ifPresent(livestock::setLocation);
            }

            // Parse pregnancy dates from form (only meaningful for non-BIRTH)
            LocalDate conceptionDate  = isBirth ? null : parseDate(conceptionDateStr);
            LocalDate expectedDueDate = isBirth ? null : parseDate(expectedDueDateStr);

            // Store conception / due date on the livestock record itself
            if (conceptionDate != null) {
                livestock.setConceptionDate(conceptionDate);
                livestock.setLastBreedingDate(conceptionDate);
            }
            if (expectedDueDate != null) {
                livestock.setExpectedDueDate(expectedDueDate);
            }

            Livestock saved = livestockService.addNew(livestock);

            // ── AUTO-CREATE BREEDING RECORD FOR PURCHASED PREGNANT ANIMALS ────
            //
            // Condition: female + non-BIRTH acquisition + isPregnant = true
            //
            // This creates a CONFIRMED_PREGNANT breeding record so the animal
            // appears in:
            //   • Pregnancy tracking dashboard
            //   • Due-date alerts
            //   • Breeding management page (Pregnant Animals section)
            //
            // REQUIREMENT: DB constraint must allow 'PURCHASE_PREGNANT' method.
            // Run the migration SQL in LivestockBreedingService class javadoc.
            //
            boolean isPurchasedPregnant =
                    "FEMALE".equalsIgnoreCase(saved.getGender())
                            && Boolean.TRUE.equals(saved.getIsPregnant())
                            && !Livestock.ACQ_BIRTH.equals(saved.getAcquisitionMethod());

            if (isPurchasedPregnant) {
                breedingService.createForPurchasedPregnantAnimal(saved, conceptionDate, expectedDueDate);
            }

            redirectAttributes.addFlashAttribute("success", "Livestock registered successfully!");
            return "redirect:/livestock/list";

        } catch (Exception e) {
            model.addAttribute("error", "Error registering livestock: " + e.getMessage());
            populateFormModel(model);
            return "livestock-register";
        }
    }

    // =====================================================================
    // EDIT
    // =====================================================================

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable UUID id, Model model) {
        return livestockService.getById(id).map(livestock -> {
            if (livestock.getLivestockCategory() != null) {
                livestock.setLivestockCategoryIdValue(livestock.getLivestockCategory().getId().toString());
            }
            if (livestock.getBeneficiary() != null) {
                livestock.setBeneficiaryIdValue(livestock.getBeneficiary().getId().toString());
            }
            model.addAttribute("livestock", livestock);
            populateFormModel(model);
            return "livestock-edit";
        }).orElse("redirect:/livestock/list");
    }

    @PostMapping("/update/{id}")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("livestock") Livestock livestock,
                         @RequestParam(value = "locationId",      required = false) UUID locationId,
                         @RequestParam(value = "conceptionDate",  required = false) String conceptionDateStr,
                         @RequestParam(value = "expectedDueDate", required = false) String expectedDueDateStr,
                         BindingResult result, Model model,
                         RedirectAttributes redirectAttributes) {

        // Fetch existing to detect pregnancy state change
        boolean wasPregnant = livestockService.getById(id)
                .map(e -> Boolean.TRUE.equals(e.getIsPregnant()))
                .orElse(false);

        boolean isBirth = Livestock.ACQ_BIRTH.equals(livestock.getAcquisitionMethod());
        if (isBirth) {
            livestock.setBirthDate(null);
            livestock.setIsPregnant(false);
            livestock.setPregnancyStatus("NOT_PREGNANT");
        } else {
            if (Boolean.TRUE.equals(livestock.getIsPregnant())
                    && "FEMALE".equalsIgnoreCase(livestock.getGender())) {
                livestock.setStatus(Livestock.STATUS_PREGNANT);
                livestock.setPregnancyStatus("PREGNANT");
            } else {
                livestock.setIsPregnant(false);
                if ("PREGNANT".equals(livestock.getPregnancyStatus())) {
                    livestock.setPregnancyStatus("NOT_PREGNANT");
                }
            }
        }

        livestock.setPregnancyMonths(null);

        if (result.hasErrors()) {
            model.addAttribute("error", result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage).collect(Collectors.joining(", ")));
            populateFormModel(model);
            return "livestock-edit";
        }

        try {
            if (locationId != null) {
                locationRepository.findById(locationId).ifPresent(livestock::setLocation);
            }

            LocalDate conceptionDate  = isBirth ? null : parseDate(conceptionDateStr);
            LocalDate expectedDueDate = isBirth ? null : parseDate(expectedDueDateStr);

            if (conceptionDate != null) {
                livestock.setConceptionDate(conceptionDate);
                livestock.setLastBreedingDate(conceptionDate);
            }
            if (expectedDueDate != null) {
                livestock.setExpectedDueDate(expectedDueDate);
            }

            Livestock saved = livestockService.update(id, livestock);

            // If animal was just marked pregnant during edit AND it's a purchased animal
            // AND it had no breeding record before → create one
            boolean nowPregnant = Boolean.TRUE.equals(saved.getIsPregnant());
            boolean isPurchased = !Livestock.ACQ_BIRTH.equals(saved.getAcquisitionMethod());
            boolean justBecamePregnant = nowPregnant && !wasPregnant;

            if (justBecamePregnant && isPurchased && "FEMALE".equalsIgnoreCase(saved.getGender())) {
                // Only create if no active breeding record already exists
                boolean hasActive = !breedingService.getAll().stream()
                        .filter(b -> b.getLivestock() != null
                                && b.getLivestock().getId().equals(saved.getId()))
                        .filter(b -> LivestockBreeding.STATUS_CONFIRMED_PREGNANT.equals(b.getStatus())
                                || LivestockBreeding.STATUS_PENDING.equals(b.getStatus()))
                        .toList().isEmpty();

                if (!hasActive) {
                    breedingService.createForPurchasedPregnantAnimal(saved, conceptionDate, expectedDueDate);
                }
            }

            redirectAttributes.addFlashAttribute("success", "Livestock updated successfully!");
            return "redirect:/livestock/list";

        } catch (Exception e) {
            model.addAttribute("error", "Error updating livestock: " + e.getMessage());
            populateFormModel(model);
            return "livestock-edit";
        }
    }

    // =====================================================================
    // DELETE
    // =====================================================================

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
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
    public String viewDetail(@PathVariable UUID id, Model model) {
        return livestockService.getById(id).map(livestock -> {
            model.addAttribute("livestock", livestock);
            return "livestock-detail";
        }).orElse("redirect:/livestock/list");
    }

    // =====================================================================
    // API ENDPOINTS
    // =====================================================================

    @GetMapping("/api/suggest-tag")
    @ResponseBody
    public Map<String, String> suggestTagForCategory(@RequestParam("categoryId") String categoryId) {
        String lastTag = livestockRepository.findAll().stream()
                .filter(l -> l.getLivestockCategory() != null
                        && l.getLivestockCategory().getId().toString().equals(categoryId))
                .map(Livestock::getTagNumber)
                .filter(t -> t != null && !t.isEmpty())
                .max(Comparator.naturalOrder())
                .orElse(null);

        Map<String, String> response = new HashMap<>();
        response.put("lastTag",      lastTag != null ? lastTag : "None yet");
        response.put("suggestedTag", lastTag != null ? suggestNextTag(lastTag) : null);
        return response;
    }
}