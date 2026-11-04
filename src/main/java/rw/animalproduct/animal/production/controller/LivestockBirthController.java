package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.dto.OffspringView;
import rw.animalproduct.animal.production.entity.Beneficiary;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.entity.LivestockBirth;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.entity.LivestockOffspring;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.entity.Veterinarian;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSaleRepository;
import rw.animalproduct.animal.production.repository.VeterinarianRepository;
import rw.animalproduct.animal.production.services.LivestockBirthService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livestock/births")
public class LivestockBirthController {

    private static final ZoneId APP_ZONE = ZoneId.of("Africa/Kigali");
    private static final String ACQUISITION_PURCHASE_PREGNANT = "PURCHASE_PREGNANT";
    private static final List<Integer> ALLOWED_PAGE_SIZES = List.of(10, 20, 50, 100);

    private final LivestockBirthService       birthService;
    private final LivestockRepository         livestockRepository;
    private final LivestockSaleRepository     saleRepository;
    private final LivestockBreedingRepository breedingRepository;
    private final VeterinarianRepository      veterinarianRepository;
    private final LivestockCategoryRepository categoryRepository;

    @Autowired
    public LivestockBirthController(LivestockBirthService birthService,
                                    LivestockRepository livestockRepository,
                                    LivestockSaleRepository saleRepository,
                                    LivestockBreedingRepository breedingRepository,
                                    VeterinarianRepository veterinarianRepository,
                                    LivestockCategoryRepository categoryRepository) {
        this.birthService        = birthService;
        this.livestockRepository = livestockRepository;
        this.saleRepository      = saleRepository;
        this.breedingRepository  = breedingRepository;
        this.veterinarianRepository = veterinarianRepository;
        this.categoryRepository  = categoryRepository;
    }

    private void addLivestockToModel(Model model) {
        Set<UUID> activePregnancyIds = breedingRepository
                .findByStatusInAndIsDeletedFalse(
                        List.of(LivestockBreeding.STATUS_CONFIRMED_PREGNANT, LivestockBreeding.STATUS_PREGNANT))
                .stream()
                .filter(b -> b.getLivestock() != null)
                .map(b -> b.getLivestock().getId())
                .collect(Collectors.toSet());

        List<Livestock> pregnantMothers = livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus()))
                .filter(l -> !Livestock.STATUS_SOLD.equals(l.getStatus()))
                .filter(l -> l.getGender() == null || l.getGender().equalsIgnoreCase("FEMALE"))
                .filter(l -> Livestock.REPRO_PREGNANT.equals(l.getReproductiveStatus())
                        || Livestock.STATUS_PREGNANT.equals(l.getStatus())
                        || Boolean.TRUE.equals(l.getIsPregnant())
                        || activePregnancyIds.contains(l.getId()))
                .sorted(Comparator.comparing(Livestock::getTagNumber))
                .collect(Collectors.toList());

        List<Livestock> availableFathers = livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus()))
                .filter(l -> !Livestock.STATUS_SOLD.equals(l.getStatus()))
                .filter(l -> "MALE".equalsIgnoreCase(l.getGender()))
                .sorted(Comparator.comparing(Livestock::getTagNumber))
                .collect(Collectors.toList());

        model.addAttribute("livestockList",    pregnantMothers);
        model.addAttribute("allLivestockList", Collections.emptyList());
        model.addAttribute("fathersList",      availableFathers);
    }

    private Veterinarian attachVeterinarian(LivestockBirth birth) {
        if (birth.getVeterinarianId() == null) return null;
        Veterinarian vet = veterinarianRepository.findById(birth.getVeterinarianId()).orElse(null);
        if (vet != null) {
            birth.setVeterinarian(vet);
        }
        return vet;
    }

    private Map<String, String> resolveBirthSource(LivestockBirth birth, Livestock mother) {
        Map<String, String> info = new HashMap<>();
        boolean purchasedPregnant = mother != null
                && ACQUISITION_PURCHASE_PREGNANT.equals(mother.getAcquisitionMethod());

        if (purchasedPregnant) {
            info.put("label", "Purchased Pregnant");
            info.put("badgeClass", "bg-warning text-dark");
            info.put("icon", "bi-box-arrow-in-down");
        } else if (mother == null) {
            info.put("label", "Other / Unrecorded");
            info.put("badgeClass", "bg-secondary");
            info.put("icon", "bi-question-circle");
        } else if (Boolean.TRUE.equals(birth.getIsExternalBirth())) {
            info.put("label", "External Birth");
            info.put("badgeClass", "bg-info text-dark");
            info.put("icon", "bi-geo-alt-fill");
        } else {
            info.put("label", "Born on Farm");
            info.put("badgeClass", "bg-success");
            info.put("icon", "bi-house-heart-fill");
        }
        return info;
    }

    private List<Location> buildLocationBreadcrumb(Location location) {
        List<Location> chain = new ArrayList<>();
        Location current = location;
        while (current != null) {
            chain.add(0, current);
            current = current.getParent();
        }
        return chain;
    }

    public static class AnimalOwnerInfo {
        private final String beneficiaryName;
        private final String beneficiaryPhone;
        private final List<Location> locationBreadcrumb;
        private final String locationPath;
        private final String locationShort;

        public AnimalOwnerInfo(String beneficiaryName, String beneficiaryPhone,
                               List<Location> locationBreadcrumb) {
            this.beneficiaryName = beneficiaryName;
            this.beneficiaryPhone = beneficiaryPhone;
            this.locationBreadcrumb = locationBreadcrumb;

            List<Location> safeBreadcrumb = locationBreadcrumb != null
                    ? locationBreadcrumb : Collections.emptyList();

            this.locationPath = safeBreadcrumb.stream()
                    .map(Location::getName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" › "));

            this.locationShort = safeBreadcrumb.size() <= 2
                    ? this.locationPath
                    : safeBreadcrumb.stream()
                    .skip(safeBreadcrumb.size() - 2)
                    .map(Location::getName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" › "));
        }

        public String getBeneficiaryName() { return beneficiaryName; }
        public String getBeneficiaryPhone() { return beneficiaryPhone; }
        public List<Location> getLocationBreadcrumb() { return locationBreadcrumb; }
        public String getLocationPath() { return locationPath; }
        public String getLocationShort() { return locationShort; }
    }

    @GetMapping({"", "/"})
    public String redirectToList() {
        return "redirect:/livestock/births/list";
    }

    @GetMapping("/api/children-by-mother")
    @ResponseBody
    public List<Map<String, Object>> getChildrenByMother(
            @RequestParam("motherId") UUID motherId) {

        Livestock mother = livestockRepository.findById(motherId).orElse(null);
        if (mother == null) return Collections.emptyList();

        UUID categoryId = (mother.getLivestockCategory() != null)
                ? mother.getLivestockCategory().getId() : null;

        int gestationMonths = 12;
        if (mother.getLivestockCategory() != null
                && mother.getLivestockCategory().getGestationPeriodMonths() != null
                && mother.getLivestockCategory().getGestationPeriodMonths() > 0) {
            gestationMonths = mother.getLivestockCategory().getGestationPeriodMonths();
        }

        LocalDate anchor        = mother.getLastBirthDate() != null
                ? mother.getLastBirthDate() : LocalDate.now(APP_ZONE);
        LocalDate earliestChild = anchor.minusMonths(gestationMonths);
        LocalDate today         = LocalDate.now(APP_ZONE);

        Set<UUID> alreadyLinked = birthService.getLinkedChildIds();

        return livestockRepository.findAll().stream()
                .filter(l -> categoryId == null
                        || (l.getLivestockCategory() != null
                        && categoryId.equals(l.getLivestockCategory().getId())))
                .filter(l -> !l.getId().equals(motherId))
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> {
                    if (l.getMother() == null)                        return true;
                    if (motherId.equals(l.getMother().getId()))       return true;
                    return !alreadyLinked.contains(l.getId());
                })
                .filter(l -> {
                    if (l.getBirthDate() == null) return true;
                    return !l.getBirthDate().isBefore(earliestChild.minusMonths(6))
                            && !l.getBirthDate().isAfter(anchor.plusMonths(6));
                })
                .sorted(Comparator.comparing(Livestock::getBirthDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(l -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",             l.getId());
                    m.put("tagNumber",      l.getTagNumber());
                    m.put("gender",         l.getGender() != null ? l.getGender() : "Unknown");
                    m.put("category",       l.getLivestockCategory() != null
                            ? l.getLivestockCategory().getName() : "—");
                    m.put("birthDate",      l.getBirthDate() != null
                            ? l.getBirthDate().toString() : null);
                    m.put("ageDays",        l.getBirthDate() != null
                            ? ChronoUnit.DAYS.between(l.getBirthDate(), today) : 0);
                    m.put("alreadyHasMother", l.getMother() != null);
                    m.put("motherTag",      l.getMother() != null
                            ? l.getMother().getTagNumber() : null);
                    m.put("status",         l.getStatus() != null
                            ? l.getStatus() : Livestock.STATUS_ACTIVE);
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/api/auto-dates")
    @ResponseBody
    public Map<String, Object> getAutoDates(@RequestParam("motherId") UUID motherId) {
        return birthService.getAutoDatesForMother(motherId);
    }

    @GetMapping("/api/date-suggestions")
    @ResponseBody
    public Map<String, Object> getDateSuggestions(
            @RequestParam("motherId") UUID motherId,
            @RequestParam("birthDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthDate) {

        Map<String, Object> response = new LinkedHashMap<>();

        Livestock mother = livestockRepository.findById(motherId).orElse(null);
        if (mother == null || birthDate == null) {
            response.put("error", "Mother not found or birth date missing");
            return response;
        }

        LivestockCategory category = mother.getLivestockCategory();
        if (category == null) {
            response.put("error", "Mother has no livestock category");
            return response;
        }

        Map<String, Object> suggestions = birthService.getDateSuggestionExplanation(birthDate, category);

        response.put("weaningDate",
                suggestions.get("weaningDate") != null
                        ? suggestions.get("weaningDate").toString() : null);
        response.put("nextBreedingDate",
                suggestions.get("nextBreedingDate") != null
                        ? suggestions.get("nextBreedingDate").toString() : null);
        response.put("weaningExplanation",  suggestions.get("weaningExplanation"));
        response.put("breedingExplanation", suggestions.get("breedingExplanation"));

        return response;
    }

    @GetMapping("/api/veterinarians/search")
    @ResponseBody
    public List<Map<String, Object>> searchVeterinarians(
            @RequestParam(value = "q", defaultValue = "") String q) {

        String query = q == null ? "" : q.trim();

        List<Veterinarian> results = query.isEmpty()
                ? veterinarianRepository.findAllActive()
                : veterinarianRepository.searchActive(query);

        return results.stream().limit(20).map(v -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",          v.getId());
            m.put("displayName", v.getFullName());
            m.put("clinicName",  v.getClinicName() != null ? v.getClinicName() : "");
            m.put("phone",       v.getPhone() != null ? v.getPhone() : "");
            m.put("license",     v.getLicenseNumber() != null ? v.getLicenseNumber() : "");
            return m;
        }).collect(Collectors.toList());
    }

    @PostMapping("/api/veterinarians/quick-add")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> quickAddVeterinarian(
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam(value = "phone", required = false) String phone) {

        try {
            Veterinarian saved = birthService.quickAddVeterinarian(firstName, lastName, phone);
            List<Veterinarian> dupes =
                    birthService.findPossibleDuplicateVeterinarians(firstName, lastName);
            boolean duplicateWarning = dupes.size() > 1;

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id",               saved.getId());
            response.put("displayName",      saved.getFullName());
            response.put("licenseNumber",    saved.getLicenseNumber());
            response.put("phone",            saved.getPhone());
            response.put("duplicateWarning", duplicateWarning);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "Could not create veterinarian: " + e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    @GetMapping("/list")
    public String listAll(@RequestParam(value = "page", defaultValue = "0") int page,
                          @RequestParam(value = "size", defaultValue = "10") int size,
                          Model model) {

        List<LivestockBirth> births;
        try {
            Page<LivestockBirth> pageContent = birthService.getPaged(page, size);
            births = pageContent.getContent();
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages",  pageContent.getTotalPages());
            model.addAttribute("totalItems",  pageContent.getTotalElements());
            model.addAttribute("pageSize",    size);
        } catch (Exception e) {
            births = birthService.getAll();
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages",  1);
            model.addAttribute("totalItems",  births.size());
            model.addAttribute("pageSize",    10);
        }
        model.addAttribute("births", births);

        Map<UUID, Livestock> motherById = birthService.getMothersForBirths(births);

        model.addAttribute("totalLocked", birthService.countLocked());
        model.addAttribute("categoryBirthStats", birthService.getCategoryBirthStats());
        model.addAttribute("allowedPageSizes", ALLOWED_PAGE_SIZES);

        LocalDate today = LocalDate.now(APP_ZONE);
        Map<UUID, Boolean> breedingCapableMap = new HashMap<>();
        Map<UUID, Map<String, String>> birthSourceMap = new HashMap<>();
        Map<UUID, AnimalOwnerInfo> birthOwnerInfoMap = new HashMap<>();
        Map<String, Integer> offspringThisPageByCategory = new TreeMap<>();

        for (LivestockBirth b : births) {
            Livestock mother = b.getLivestockId() != null ? motherById.get(b.getLivestockId()) : null;
            birthSourceMap.put(b.getId(), resolveBirthSource(b, mother));

            String categoryName = "Unknown";

            if (mother != null) {
                LocalDate bd = mother.getBirthDate();
                LivestockCategory cat = mother.getLivestockCategory();
                if (cat != null) categoryName = cat.getName();
                if (bd != null && cat != null && cat.getMinBreedingAgeMonths() != null) {
                    long ageMonths = ChronoUnit.MONTHS.between(bd, today);
                    breedingCapableMap.put(mother.getId(), ageMonths >= cat.getMinBreedingAgeMonths());
                }

                String beneficiaryName  = mother.getBeneficiary() != null ? mother.getBeneficiary().getFullName() : null;
                String beneficiaryPhone = mother.getBeneficiary() != null ? mother.getBeneficiary().getPhone() : null;
                List<Location> breadcrumb = mother.getLocation() != null
                        ? buildLocationBreadcrumb(mother.getLocation())
                        : Collections.emptyList();

                birthOwnerInfoMap.put(b.getId(),
                        new AnimalOwnerInfo(beneficiaryName, beneficiaryPhone, breadcrumb));
            } else {
                birthOwnerInfoMap.put(b.getId(),
                        new AnimalOwnerInfo(null, null, Collections.emptyList()));
            }

            int offspringOnThisRow = b.getOffspringCount() != null ? b.getOffspringCount() : 0;
            offspringThisPageByCategory.merge(categoryName, offspringOnThisRow, Integer::sum);
        }
        model.addAttribute("breedingCapableMap", breedingCapableMap);
        model.addAttribute("birthSourceMap", birthSourceMap);
        model.addAttribute("birthOwnerInfoMap", birthOwnerInfoMap);
        model.addAttribute("offspringThisPageByCategory", offspringThisPageByCategory);

        List<Livestock> pendingDrafts = livestockRepository.findAllPendingDrafts();
        model.addAttribute("pendingDraftCount", pendingDrafts.size());
        Map<String, Long> pendingDraftsByCategory = pendingDrafts.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getLivestockCategory() != null ? l.getLivestockCategory().getName() : "Unknown",
                        TreeMap::new,
                        Collectors.counting()));
        model.addAttribute("pendingDraftsByCategory", pendingDraftsByCategory);

        Map<UUID, Boolean> hasDraftMap = new HashMap<>();
        for (LivestockBirth b : births) {
            boolean anyDraft = b.getChildren() != null && b.getChildren().stream()
                    .map(LivestockOffspring::getChildLivestock)
                    .anyMatch(child -> child != null && Boolean.TRUE.equals(child.getIsDraft()));
            hasDraftMap.put(b.getId(), anyDraft);
        }
        model.addAttribute("hasDraftMap", hasDraftMap);

        return "livestock-births-list";
    }

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("birth", new LivestockBirth());
        addLivestockToModel(model);
        model.addAttribute("today", LocalDate.now(APP_ZONE).toString());
        return "livestock-birth-register";
    }

    @PostMapping("/register/new")
    public String register(@Valid @ModelAttribute("birth") LivestockBirth birth,
                           BindingResult result,
                           @RequestParam(value = "linkedChildIds", required = false)
                           List<UUID> linkedChildIds,
                           @RequestParam(value = "fatherId", required = false) UUID fatherId,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        birth.setIsExternalBirth(false);

        if (birth.getLivestockIdValue() == null
                || birth.getLivestockIdValue().trim().isEmpty()) {
            result.rejectValue("livestockIdValue", "error.birth",
                    "Mother animal is required — select the pregnant animal that gave birth");
        }

        if (result.hasErrors()) {
            model.addAttribute("error",
                    result.getFieldErrors().stream()
                            .map(FieldError::getDefaultMessage)
                            .collect(Collectors.joining(", ")));
            addLivestockToModel(model);
            return "livestock-birth-register";
        }

        try {
            LivestockBirth saved = birthService.addNew(birth, linkedChildIds, fatherId);
            redirectAttributes.addFlashAttribute("success",
                    "Birth recorded successfully! "
                            + (linkedChildIds != null && !linkedChildIds.isEmpty()
                            ? linkedChildIds.size() + " offspring linked. " : "")
                            + "Offspring can also be linked after the birth record is saved.");
            return "redirect:/livestock/births/" + saved.getId() + "/children";
        } catch (IllegalStateException | IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            addLivestockToModel(model);
            return "livestock-birth-register";
        } catch (Exception e) {
            model.addAttribute("error", "Error recording birth: " + e.getMessage());
            addLivestockToModel(model);
            return "livestock-birth-register";
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        Optional<LivestockBirth> opt = birthService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/births/list";

        LivestockBirth birth = opt.get();

        if (birth.isLocked()) {
            redirectAttributes.addFlashAttribute("error",
                    "This birth record is locked (verified/completed). Amend (reopen) it first to make changes.");
            return "redirect:/livestock/births/view/" + id;
        }

        if (birth.getLivestockId() != null) {
            Livestock mother = livestockRepository.findById(birth.getLivestockId()).orElse(null);
            if (mother != null) {
                birth.setLivestock(mother);
                birth.setLivestockIdValue(mother.getId().toString());
            }
        }

        Veterinarian selectedVet = attachVeterinarian(birth);
        if (selectedVet != null) {
            birth.setVeterinarianIdValue(selectedVet.getId().toString());
            model.addAttribute("selectedVeterinarianLabel", selectedVet.getFullName());
        } else {
            model.addAttribute("selectedVeterinarianLabel", "");
        }

        Livestock commonFather = birthService.getCommonFatherForBirth(id);
        model.addAttribute("selectedFatherId", commonFather != null ? commonFather.getId().toString() : "");

        model.addAttribute("birth", birth);
        addLivestockToModel(model);
        model.addAttribute("today", LocalDate.now(APP_ZONE).toString());
        return "livestock-birth-edit";
    }

    @PostMapping("/update/{id}")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("birth") LivestockBirth birth,
                         BindingResult result,
                         @RequestParam(value = "fatherId", required = false) UUID fatherId,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        birth.setIsExternalBirth(false);

        if (birth.getLivestockIdValue() == null
                || birth.getLivestockIdValue().trim().isEmpty()) {
            result.rejectValue("livestockIdValue", "error.birth",
                    "Mother animal is required");
        }

        if (result.hasErrors()) {
            model.addAttribute("error",
                    result.getFieldErrors().stream()
                            .map(FieldError::getDefaultMessage)
                            .collect(Collectors.joining(", ")));
            addLivestockToModel(model);
            return "livestock-birth-edit";
        }

        try {
            birthService.update(id, birth, fatherId);
            redirectAttributes.addFlashAttribute("success",
                    "Birth record updated successfully!");
            return "redirect:/livestock/births/list";
        } catch (IllegalStateException | IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            addLivestockToModel(model);
            return "livestock-birth-edit";
        } catch (Exception e) {
            model.addAttribute("error", "Error updating birth: " + e.getMessage());
            addLivestockToModel(model);
            return "livestock-birth-edit";
        }
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            birthService.delete(id);
            redirectAttributes.addFlashAttribute("success",
                    "Birth record deleted and logged in the audit trail.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Cannot delete: " + e.getMessage());
        }
        return "redirect:/livestock/births/list";
    }

    @PostMapping("/complete/{id}")
    public String complete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            birthService.complete(id);
            redirectAttributes.addFlashAttribute("success",
                    "Birth record verified and locked. It can no longer be edited or deleted until amended.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not complete: " + e.getMessage());
        }
        return "redirect:/livestock/births/list";
    }

    @PostMapping("/reopen/{id}")
    public String reopen(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            birthService.reopen(id);
            redirectAttributes.addFlashAttribute("success",
                    "Birth record reopened for amendment. Remember to complete it again when you're done.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Could not reopen: " + e.getMessage());
        }
        return "redirect:/livestock/births/list";
    }

    @GetMapping("/view/{id}")
    public String viewDetail(@PathVariable UUID id, Model model) {
        Optional<LivestockBirth> opt = birthService.getWithChildren(id);
        if (opt.isEmpty()) return "redirect:/livestock/births/list";

        LivestockBirth birth = opt.get();
        Livestock mother = birth.getLivestock();
        String motherTag      = mother != null ? mother.getTagNumber() : null;
        String motherCategory = mother != null && mother.getLivestockCategory() != null
                ? mother.getLivestockCategory().getName() : null;

        attachVeterinarian(birth);

        Livestock father = birthService.getCommonFatherForBirth(id);
        List<OffspringView> offspringViews = birthService.getOffspringViews(id);

        model.addAttribute("birth",          birth);
        model.addAttribute("motherTag",      motherTag);
        model.addAttribute("motherCategory", motherCategory);
        model.addAttribute("fatherTag",      father != null ? father.getTagNumber() : null);
        model.addAttribute("offspringViews", offspringViews);
        model.addAttribute("birthSource",    resolveBirthSource(birth, mother));
        model.addAttribute("draftCount",     offspringViews.stream().filter(OffspringView::isDraft).count());
        return "livestock-birth-view";
    }

    @GetMapping("/{birthId}/children")
    public String viewChildren(@PathVariable UUID birthId, Model model) {
        Optional<LivestockBirth> opt = birthService.getWithChildren(birthId);
        if (opt.isEmpty()) return "redirect:/livestock/births/list";

        LivestockBirth birth = opt.get();
        Livestock mother = birth.getLivestock();

        attachVeterinarian(birth);

        List<OffspringView> offspringViews = birthService.getOffspringViews(birthId);
        Set<UUID> linkedIds = offspringViews.stream()
                .map(OffspringView::getChildId)
                .collect(Collectors.toSet());

        Livestock commonFather = birthService.getCommonFatherForBirth(birthId);
        model.addAttribute("selectedFatherId", commonFather != null ? commonFather.getId().toString() : "");
        model.addAttribute("fathersList", livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus()))
                .filter(l -> !Livestock.STATUS_SOLD.equals(l.getStatus()))
                .filter(l -> "MALE".equalsIgnoreCase(l.getGender()))
                .sorted(Comparator.comparing(Livestock::getTagNumber))
                .collect(Collectors.toList()));

        UUID motherId   = mother != null ? mother.getId() : null;
        UUID categoryId = (mother != null && mother.getLivestockCategory() != null)
                ? mother.getLivestockCategory().getId() : null;

        int gestationMonths = 12;
        if (mother != null && mother.getLivestockCategory() != null
                && mother.getLivestockCategory().getGestationPeriodMonths() != null
                && mother.getLivestockCategory().getGestationPeriodMonths() > 0) {
            gestationMonths = mother.getLivestockCategory().getGestationPeriodMonths();
        }

        LocalDate anchor = (mother != null && mother.getLastBirthDate() != null)
                ? mother.getLastBirthDate()
                : (birth.getBirthDate() != null ? birth.getBirthDate() : LocalDate.now(APP_ZONE));
        LocalDate earliestChild = anchor.minusMonths(gestationMonths);

        // ✅ FIXED: renamed with underscore — Spring Data derives from `draftBirthEvent` property.
        Set<UUID> draftIdsForThisBirth = livestockRepository
                .findByDraftBirthEvent_IdAndIsDraftTrue(birthId)
                .stream()
                .map(Livestock::getId)
                .collect(Collectors.toSet());

        final UUID finalMotherId = motherId;
        List<Livestock> available = livestockRepository.findAll().stream()
                .filter(l -> !linkedIds.contains(l.getId()))
                .filter(l -> finalMotherId == null || !l.getId().equals(finalMotherId))
                .filter(l -> {
                    if (categoryId == null) return true;
                    return l.getLivestockCategory() != null
                            && categoryId.equals(l.getLivestockCategory().getId());
                })
                .filter(l -> {
                    if (Boolean.TRUE.equals(l.getIsDraft())) {
                        return draftIdsForThisBirth.contains(l.getId());
                    }
                    if (l.getMother() != null) {
                        return finalMotherId != null
                                && l.getMother().getId().equals(finalMotherId);
                    }
                    if (l.getBirthDate() == null) return true;
                    return !l.getBirthDate().isBefore(earliestChild.minusMonths(3))
                            && !l.getBirthDate().isAfter(anchor.plusMonths(2));
                })
                .sorted(Comparator.comparing(
                        l -> l.getBirthDate() != null ? l.getBirthDate() : LocalDate.MIN,
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());

        long pendingDrafts = offspringViews.stream().filter(OffspringView::isDraft).count();

        model.addAttribute("birth",                    birth);
        model.addAttribute("offspringViews",           offspringViews);
        model.addAttribute("availableLivestock",       available);
        model.addAttribute("pendingDraftsForThisBirth", pendingDrafts);
        return "livestock-birth-children";
    }

    @PostMapping("/{birthId}/link-child")
    public String linkChild(@PathVariable UUID birthId,
                            @RequestParam("childLivestockId") UUID childLivestockId,
                            @RequestParam(value = "outcome", required = false, defaultValue = "ALIVE") String outcome,
                            @RequestParam(value = "birthWeightKg", required = false) BigDecimal birthWeightKg,
                            @RequestParam(value = "fatherId", required = false) UUID fatherId,
                            RedirectAttributes redirectAttributes) {
        try {
            birthService.linkChild(birthId, childLivestockId, outcome, birthWeightKg, fatherId);
            redirectAttributes.addFlashAttribute("success",
                    "Animal linked successfully as offspring!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error linking animal: " + e.getMessage());
        }
        return "redirect:/livestock/births/" + birthId + "/children";
    }

    @PostMapping("/unlink-child/{childLivestockId}")
    public String unlinkChild(@PathVariable UUID childLivestockId,
                              @RequestParam("birthId") UUID birthId,
                              RedirectAttributes redirectAttributes) {
        try {
            birthService.unlinkChild(childLivestockId);
            redirectAttributes.addFlashAttribute("success",
                    "Animal unlinked successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Error: " + e.getMessage());
        }
        return "redirect:/livestock/births/" + birthId + "/children";
    }

    @GetMapping("/family/{livestockId}")
    public String viewFamilyTree(@PathVariable UUID livestockId, Model model) {
        Optional<Livestock> opt = livestockRepository.findByIdWithDetails(livestockId);
        if (opt.isEmpty()) return "redirect:/livestock/list";

        Livestock animal         = opt.get();
        List<Livestock>      directChildren = birthService.getDirectChildren(livestockId);
        List<LivestockBirth> birthsAsMother = birthService.getByLivestockId(livestockId);

        Livestock grandmother = null;
        if (animal.getMother() != null && animal.getMother().getMother() != null) {
            grandmother = animal.getMother().getMother();
        }

        model.addAttribute("animal",         animal);
        model.addAttribute("mother",         animal.getMother());
        model.addAttribute("grandmother",    grandmother);
        model.addAttribute("directChildren", directChildren);
        model.addAttribute("birthsAsMother", birthsAsMother);
        model.addAttribute("hasChildren",    birthService.hasChildren(livestockId));
        return "livestock-family";
    }

    @GetMapping("/report/born-and-sold")
    public String bornAndSoldReport(Model model) {

        List<LivestockBirth> allBirths = birthService.getAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        long totalSold = 0, totalOnFarm = 0, totalDead = 0;

        Map<UUID, Livestock> motherById = birthService.getMothersForBirths(allBirths);

        for (LivestockBirth birth : allBirths) {
            if (birth.getChildren() == null || birth.getChildren().isEmpty()) continue;

            Livestock birthMother = birth.getLivestockId() != null
                    ? motherById.get(birth.getLivestockId()) : null;

            for (LivestockOffspring offspring : birth.getChildren()) {
                Livestock child = offspring.getChildLivestock();
                if (child == null || Boolean.TRUE.equals(child.getIsDraft())) continue;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("childId",        child.getId());
                row.put("childTag",       child.getTagNumber());
                row.put("childGender",    child.getGender());
                row.put("category",       child.getLivestockCategory() != null
                        ? child.getLivestockCategory().getName() : "—");
                row.put("motherTag",      birthMother != null
                        ? birthMother.getTagNumber() : "Unknown");
                row.put("motherId",       birthMother != null ? birthMother.getId() : null);
                row.put("isExternal",     birth.getIsExternalBirth());
                row.put("sourceLocation", birth.getSourceLocation() != null
                        ? birth.getSourceLocation() : "—");
                row.put("birthDate",      birth.getBirthDate());
                row.put("birthId",        birth.getId());
                row.put("generation",     offspring.getGeneration());
                row.put("status",         child.getStatus() != null
                        ? child.getStatus() : Livestock.STATUS_ACTIVE);

                Beneficiary beneficiary = child.getBeneficiary();
                row.put("beneficiaryName", beneficiary != null ? beneficiary.getFullName() : null);
                row.put("beneficiaryPhone", beneficiary != null ? beneficiary.getPhone() : null);

                List<LivestockSale> sales = saleRepository.findByLivestockId(child.getId());
                if (!sales.isEmpty()) {
                    LivestockSale latest = sales.stream()
                            .max(Comparator.comparing(LivestockSale::getSaleDate))
                            .orElse(sales.get(0));
                    row.put("saleId",       latest.getId());
                    row.put("saleDate",     latest.getSaleDate());
                    row.put("salePrice",    latest.getSalePrice());
                    row.put("saleLocation", latest.getSaleLocation() != null
                            ? latest.getSaleLocation() : "—");
                    row.put("saleReason",   latest.getSaleReason() != null
                            ? latest.getSaleReason() : "—");
                    if (birth.getBirthDate() != null && latest.getSaleDate() != null) {
                        row.put("daysToSale",
                                ChronoUnit.DAYS.between(birth.getBirthDate(), latest.getSaleDate()));
                    } else {
                        row.put("daysToSale", null);
                    }
                } else {
                    row.put("saleId", null); row.put("saleDate", null);
                    row.put("salePrice", null); row.put("saleLocation", "—");
                    row.put("saleReason", "—"); row.put("daysToSale", null);
                }

                String status = (String) row.get("status");
                if      (Livestock.STATUS_SOLD.equals(status)) totalSold++;
                else if (Livestock.STATUS_DEAD.equals(status)) totalDead++;
                else                                           totalOnFarm++;

                rows.add(row);
            }
        }

        rows.sort(Comparator.comparing(r -> {
            String s = (String) r.get("status");
            if (Livestock.STATUS_SOLD.equals(s))   return 0;
            if (Livestock.STATUS_ACTIVE.equals(s)) return 1;
            return 2;
        }));

        model.addAttribute("rows",        rows);
        model.addAttribute("totalRows",   rows.size());
        model.addAttribute("totalSold",   totalSold);
        model.addAttribute("totalOnFarm", totalOnFarm);
        model.addAttribute("totalDead",   totalDead);
        return "livestock-born-sold-report";
    }

    @GetMapping("/report/birth-summary")
    public String birthSummaryReport(
            @RequestParam(value = "fromDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "motherId", required = false) UUID motherId,
            @RequestParam(value = "birthType", required = false) String birthType,
            @RequestParam(value = "location", required = false) String location,
            Model model) {

        LivestockBirthService.BirthReportResult report =
                birthService.generateBirthReport(fromDate, toDate, categoryId, motherId, birthType, location);

        model.addAttribute("report", report);

        model.addAttribute("categories", categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(LivestockCategory::getName))
                .collect(Collectors.toList()));

        model.addAttribute("mothers", livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> l.getOffspringCount() != null && l.getOffspringCount() > 0)
                .sorted(Comparator.comparing(Livestock::getTagNumber))
                .collect(Collectors.toList()));

        model.addAttribute("birthTypes", List.of(
                LivestockBirth.BIRTH_TYPE_NORMAL, LivestockBirth.BIRTH_TYPE_DIFFICULT));

        model.addAttribute("selectedFromDate", fromDate);
        model.addAttribute("selectedToDate", toDate);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedMotherId", motherId);
        model.addAttribute("selectedBirthType", birthType);
        model.addAttribute("selectedLocation", location);

        return "livestock-birth-report";
    }

    @GetMapping("/admin/repair-mother-links")
    public String repairMotherLinks(RedirectAttributes redirectAttributes) {
        try {
            int fixed = birthService.repairMissingMotherLinks();
            redirectAttributes.addFlashAttribute("success",
                    "Mother link repair complete. Fixed " + fixed + " animal(s).");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Repair failed: " + e.getMessage());
        }
        return "redirect:/livestock/births/list";
    }
}