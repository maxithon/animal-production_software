package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.dto.MaleReadyToBreedDTO;
import rw.animalproduct.animal.production.dto.FemaleReadyToBreedDTO;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.*;
import rw.animalproduct.animal.production.services.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livestock")
public class LivestockBreedingController {

    private final LivestockBreedingService   breedingService;
    private final VeterinarianService        veterinarianService;
    private final LivestockRepository        livestockRepository;
    private final VeterinarianRepository     veterinarianRepository;
    private final MalesReadyToBreedService   malesReadyToBreedService;
    private final FemalesReadyToBreedService femalesReadyToBreedService;

    public LivestockBreedingController(LivestockBreedingService breedingService,
                                       VeterinarianService veterinarianService,
                                       LivestockRepository livestockRepository,
                                       VeterinarianRepository veterinarianRepository,
                                       MalesReadyToBreedService malesReadyToBreedService,
                                       FemalesReadyToBreedService femalesReadyToBreedService
    ) {
        this.breedingService          = breedingService;
        this.veterinarianService      = veterinarianService;
        this.livestockRepository      = livestockRepository;
        this.veterinarianRepository   = veterinarianRepository;
        this.malesReadyToBreedService = malesReadyToBreedService;
        this.femalesReadyToBreedService = femalesReadyToBreedService;
    }
    // ── BREEDING MANAGEMENT PAGE ───────────────────────────────────────────────

    @GetMapping("/lifecycle/breeding-management")
    public String breedingManagement(Model model) {
        List<MaleReadyToBreedDTO> breedingMales = malesReadyToBreedService.getAllReadyToBreed()
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<FemaleReadyToBreedDTO> readyFemales = femalesReadyToBreedService.getAllReadyToBreed()
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, List<MaleReadyToBreedDTO>> groupedMales = new LinkedHashMap<>();
        for (MaleReadyToBreedDTO male : breedingMales) {
            if (male != null) {
                String category = male.getCategoryName() != null ? male.getCategoryName() : "Uncategorized";
                groupedMales.computeIfAbsent(category, k -> new ArrayList<>()).add(male);
            }
        }

        Map<String, List<FemaleReadyToBreedDTO>> groupedFemales = new LinkedHashMap<>();
        for (FemaleReadyToBreedDTO female : readyFemales) {
            if (female != null) {
                String category = female.getCategoryName() != null ? female.getCategoryName() : "Uncategorized";
                groupedFemales.computeIfAbsent(category, k -> new ArrayList<>()).add(female);
            }
        }

        int maleTotal = breedingMales.size();
        long maleNeverBred = breedingMales.stream()
                .filter(m -> m.getTotalBreedings() == null || m.getTotalBreedings() == 0)
                .count();
        double maleAvgSuccessRate = breedingMales.stream()
                .mapToDouble(MaleReadyToBreedDTO::getSuccessRate)
                .average()
                .orElse(0.0);

        List<PregnantAnimalDTO> pregnantAnimals = getPregnantAnimals();
        Map<String, List<PregnantAnimalDTO>> groupedPregnant = new LinkedHashMap<>();
        for (PregnantAnimalDTO animal : pregnantAnimals) {
            if (animal != null) {
                String category = animal.getCategoryName() != null ? animal.getCategoryName() : "Uncategorized";
                groupedPregnant.computeIfAbsent(category, k -> new ArrayList<>()).add(animal);
            }
        }

        List<LivestockBreeding> activeBreedings = breedingService.getAll().stream()
                .filter(b -> b != null && LivestockBreeding.STATUS_PENDING.equals(b.getStatus()))
                .collect(Collectors.toList());

        List<LivestockBreeding> pendingPregnancyCheck = breedingService.getDueForPregnancyCheck();

        List<LivestockBreeding> failedBreedings = breedingService.getAll().stream()
                .filter(b -> b != null && LivestockBreeding.STATUS_FAILED.equals(b.getStatus()))
                .collect(Collectors.toList());

        model.addAttribute("breedingMales",         breedingMales);
        model.addAttribute("groupedMales",           groupedMales);
        model.addAttribute("maleTotal",              maleTotal);
        model.addAttribute("maleNeverBred",          maleNeverBred);
        model.addAttribute("maleAvgSuccessRate",     maleAvgSuccessRate);
        model.addAttribute("readyFemales",           readyFemales);
        model.addAttribute("groupedFemales",         groupedFemales);
        model.addAttribute("pregnantAnimals",        pregnantAnimals);
        model.addAttribute("groupedPregnant",        groupedPregnant);
        model.addAttribute("activeBreedings",        activeBreedings);
        model.addAttribute("pendingPregnancyCheck",  pendingPregnancyCheck);
        model.addAttribute("failedBreedings",        failedBreedings);
        model.addAttribute("successRate",            String.format("%.1f", breedingService.getSuccessRate()));
        model.addAttribute("avgDaysToConception",    calculateAvgDaysToConception());
        model.addAttribute("today",                  LocalDate.now());

        return "livestock-breeding-management";
    }

    private double calculateAvgDaysToConception() {
        List<LivestockBreeding> confirmed = breedingService.getAll().stream()
                .filter(b -> b != null && LivestockBreeding.STATUS_CONFIRMED.equals(b.getStatus()))
                .collect(Collectors.toList());
        if (confirmed.isEmpty()) return 0.0;
        return 45.0;
    }

    private List<PregnantAnimalDTO> getPregnantAnimals() {
        List<LivestockBreeding> confirmedBreedings = breedingService.getAll().stream()
                .filter(b -> b != null && LivestockBreeding.STATUS_CONFIRMED.equals(b.getStatus()))
                .filter(b -> b.getLivestock() != null)
                .collect(Collectors.toList());

        List<PregnantAnimalDTO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (LivestockBreeding breeding : confirmedBreedings) {
            PregnantAnimalDTO dto = new PregnantAnimalDTO();
            dto.setId(breeding.getLivestock().getId());
            dto.setTagNumber(breeding.getLivestock().getTagNumber());
            if (breeding.getLivestock().getLivestockCategory() != null) {
                dto.setCategoryName(breeding.getLivestock().getLivestockCategory().getName());
            }
            dto.setExpectedDueDate(breeding.getExpectedDueDate());
            if (breeding.getExpectedDueDate() != null) {
                long daysUntilDue = ChronoUnit.DAYS.between(today, breeding.getExpectedDueDate());
                dto.setDaysUntilDue((int) daysUntilDue);
            }
            result.add(dto);
        }
        return result;
    }

    // ── DASHBOARD ─────────────────────────────────────────────────────────────

    @GetMapping("/breeding")
    public String dashboard(Model model) {
        model.addAttribute("totalBreedings",     breedingService.getAll().size());
        model.addAttribute("pendingCount",       breedingService.countByStatus("PENDING"));
        model.addAttribute("confirmedCount",     breedingService.countByStatus("CONFIRMED_PREGNANT"));
        model.addAttribute("successRate",        breedingService.getSuccessRate());
        model.addAttribute("dueForCheck",        breedingService.getDueForPregnancyCheck());
        model.addAttribute("approachingDueDate", breedingService.getApproachingDueDate());
        model.addAttribute("recentBreedings",    breedingService.getRecentBreedings());
        return "livestock-breeding-dashboard";
    }
    // ── LIST ──────────────────────────────────────────────────────────────────
    @GetMapping("/breeding/list")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size,
                       Model model) {

        Page<LivestockBreeding> breedingPage = breedingService.getPaginated(page, size);
        model.addAttribute("breedings",    breedingPage.getContent());
        model.addAttribute("currentPage",  breedingPage.getNumber());
        model.addAttribute("totalPages",   breedingPage.getTotalPages());
        model.addAttribute("totalItems",   breedingPage.getTotalElements());
        model.addAttribute("pageSize",     breedingPage.getSize());
        return "livestock-breeding-list";
    }

    // ── REGISTER FORM ─────────────────────────────────────────────────────────
    @GetMapping("/breeding/register")
    public String registerForm(@RequestParam(required = false) UUID livestockId, Model model) {
        LivestockBreeding breeding = new LivestockBreeding();
        breeding.setStatus(LivestockBreeding.STATUS_PENDING);

        if (livestockId != null) {
            livestockRepository.findById(livestockId).ifPresent(animal -> {
                breeding.setLivestockIdValue(animal.getId().toString());
                breeding.setLivestock(animal); // lets the form pre-render category/context if needed
            });
        }

        model.addAttribute("breeding", breeding);
        addLivestockAndVetsToModel(model);
        return "livestock-breeding-form";
    }

    @PostMapping("/breeding/register")
    public String save(@Valid @ModelAttribute("breeding") LivestockBreeding breeding,
                       BindingResult result, Model model, RedirectAttributes ra) {

        // 1. Female animal is required
        if (breeding.getLivestockIdValue() == null || breeding.getLivestockIdValue().trim().isEmpty()) {
            result.rejectValue("livestockIdValue", "required", "Female livestock is required");
        }

        // 1b. Breeding date can't be in the future (defense in depth — the form
        // already caps the date picker at today, but this guards direct posts).
        if (breeding.getBreedingDate() != null && breeding.getBreedingDate().isAfter(LocalDate.now())) {
            result.rejectValue("breedingDate", "date.future", "Breeding date cannot be in the future");
        }

        // 1c. Expected Pregnancy Check Date / Expected Due Date order validation
        // (backs up the client-side checks on the form — both dates are optional,
        // so these only fire when the relevant fields are actually filled in).
        validateExpectedDates(breeding, result);

        if (result.hasErrors()) {
            long realErrors = result.getAllErrors().stream()
                    .filter(e -> !(e instanceof org.springframework.validation.FieldError fe
                            && "livestock".equals(fe.getField())))
                    .count();
            if (realErrors > 0) {
                addLivestockAndVetsToModel(model);
                return "livestock-breeding-form";
            }
        }

        // 2. Resolve the Livestock entity from the transient ID value
        resolveRelations(breeding);

        if (breeding.getLivestock() == null) {
            result.rejectValue("livestockIdValue", "required",
                    "Female livestock is required — please select a valid animal");
            addLivestockAndVetsToModel(model);
            return "livestock-breeding-form";
        }

        // 3. Must be female
        if (!"FEMALE".equalsIgnoreCase(breeding.getLivestock().getGender())) {
            result.rejectValue("livestockIdValue", "gender.invalid",
                    "Selected animal is not female");
            addLivestockAndVetsToModel(model);
            return "livestock-breeding-form";
        }

        // 4. Check breeding eligibility based on gestation period
        String eligibilityError = checkBreedingEligibility(
                breeding.getLivestock(), breeding.getBreedingDate());
        if (eligibilityError != null) {
            model.addAttribute("error", eligibilityError);
            addLivestockAndVetsToModel(model);
            return "livestock-breeding-form";
        }

        // 5. Male must be same category for natural breeding
        String method = breeding.getBreedingMethod();
        boolean requiresMale = method == null
                || LivestockBreeding.METHOD_NATURAL.equalsIgnoreCase(method);

        if (breeding.getMaleLivestock() != null && requiresMale) {
            String femaleCat = getCategoryName(breeding.getLivestock());
            String maleCat   = getCategoryName(breeding.getMaleLivestock());
            if (!femaleCat.equalsIgnoreCase(maleCat)) {
                result.rejectValue("maleLivestockIdValue", "category.mismatch",
                        "Male and female must be the same animal category (" + femaleCat + ")");
                addLivestockAndVetsToModel(model);
                return "livestock-breeding-form";
            }
        }

        if (!requiresMale) {
            breeding.setMaleLivestock(null);
        }

        breedingService.addNew(breeding);
        ra.addFlashAttribute("success", "Breeding record saved successfully!");
        return "redirect:/livestock/breeding";
    }

    // ── AJAX: Males by Category ────────────────────────────────────────────────
    @GetMapping("/breeding/males-by-category")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getMalesByCategory(
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "femaleId",   required = false) UUID femaleId) {

        Set<UUID> eligibleIds = malesReadyToBreedService.getAllReadyToBreed()
                .stream()
                .filter(Objects::nonNull)
                .map(MaleReadyToBreedDTO::getId)
                .collect(Collectors.toSet());

        List<Livestock> eligibleMales = livestockRepository.findAll().stream()
                .filter(ls -> !Livestock.STATUS_DEAD.equals(ls.getStatus())
                        && !Livestock.STATUS_SOLD.equals(ls.getStatus()))
                .filter(ls -> "MALE".equalsIgnoreCase(ls.getGender()))
                .filter(ls -> eligibleIds.contains(ls.getId()))
                .collect(Collectors.toList());

        if (categoryId != null) {
            final UUID catId = categoryId;
            eligibleMales = eligibleMales.stream()
                    .filter(ls -> ls.getLivestockCategory() != null
                            && catId.equals(ls.getLivestockCategory().getId()))
                    .collect(Collectors.toList());
        }

        if (femaleId != null) {
            final UUID fId = femaleId;
            eligibleMales = eligibleMales.stream()
                    .filter(ls -> !ls.getId().equals(fId))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Livestock male : eligibleMales) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",        male.getId().toString());
            m.put("tagNumber", male.getTagNumber());
            m.put("category",  male.getLivestockCategory() != null
                    ? male.getLivestockCategory().getName() : "");
            m.put("eligible",  true);
            result.add(m);
        }

        return ResponseEntity.ok(result);
    }

    // ── EDIT ──────────────────────────────────────────────────────────────────

    @GetMapping("/breeding/edit/{id}")
    public String editForm(@PathVariable UUID id, Model model) {
        Optional<LivestockBreeding> opt = breedingService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/breeding";
        LivestockBreeding b = opt.get();
        if (b.getLivestock()     != null) b.setLivestockIdValue(b.getLivestock().getId().toString());
        if (b.getMaleLivestock() != null) b.setMaleLivestockIdValue(b.getMaleLivestock().getId().toString());
        if (b.getVeterinarian()  != null) b.setVeterinarianIdValue(b.getVeterinarian().getId().toString());
        model.addAttribute("breeding", b);
        addLivestockAndVetsToModel(model);
        return "livestock-breeding-edit";
    }

    @PostMapping("/breeding/update/{id}")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("breeding") LivestockBreeding breeding,
                         BindingResult result, Model model, RedirectAttributes ra) {

        if (breeding.getLivestockIdValue() == null || breeding.getLivestockIdValue().trim().isEmpty()) {
            result.rejectValue("livestockIdValue", "required", "Female livestock is required");
        }

        if (breeding.getBreedingDate() != null && breeding.getBreedingDate().isAfter(LocalDate.now())) {
            result.rejectValue("breedingDate", "date.future", "Breeding date cannot be in the future");
        }

        validateExpectedDates(breeding, result);

        long realErrors = result.getAllErrors().stream()
                .filter(e -> !(e instanceof org.springframework.validation.FieldError fe
                        && "livestock".equals(fe.getField())))
                .count();
        if (realErrors > 0) {
            addLivestockAndVetsToModel(model);
            return "livestock-breeding-edit";
        }

        resolveRelations(breeding);

        if (breeding.getLivestock() == null) {
            result.rejectValue("livestockIdValue", "required", "Female livestock is required");
            addLivestockAndVetsToModel(model);
            return "livestock-breeding-edit";
        }

        String method = breeding.getBreedingMethod();
        boolean requiresMale = method == null
                || LivestockBreeding.METHOD_NATURAL.equalsIgnoreCase(method);

        if (breeding.getMaleLivestock() != null && requiresMale) {
            String femaleCat = getCategoryName(breeding.getLivestock());
            String maleCat   = getCategoryName(breeding.getMaleLivestock());
            if (!femaleCat.equalsIgnoreCase(maleCat)) {
                result.rejectValue("maleLivestockIdValue", "category.mismatch",
                        "Male and female must be the same animal category (" + femaleCat + ")");
                addLivestockAndVetsToModel(model);
                return "livestock-breeding-edit";
            }
        }

        if (!requiresMale) {
            breeding.setMaleLivestock(null);
        }

        breedingService.update(id, breeding);
        ra.addFlashAttribute("success", "Breeding record updated successfully!");
        return "redirect:/livestock/breeding";
    }

    // ── VIEW ──────────────────────────────────────────────────────────────────

    @GetMapping("/breeding/view/{id}")
    public String view(@PathVariable UUID id, Model model) {
        Optional<LivestockBreeding> opt = breedingService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/breeding";
        model.addAttribute("breeding", opt.get());
        return "livestock-breeding-view";
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @PostMapping("/breeding/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            breedingService.delete(id);
            ra.addFlashAttribute("success", "Breeding record deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/livestock/breeding";
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────
    private void validateExpectedDates(LivestockBreeding breeding, BindingResult result) {
        LocalDate bDate    = breeding.getBreedingDate();
        LocalDate checkDate = breeding.getExpectedPregnancyCheckDate();
        LocalDate dueDate   = breeding.getExpectedDueDate();

        if (bDate != null && checkDate != null && checkDate.isBefore(bDate)) {
            result.rejectValue("expectedPregnancyCheckDate", "date.beforeBreeding",
                    "Pregnancy check date cannot be before the breeding date");
        }

        if (bDate != null && dueDate != null && dueDate.isBefore(bDate)) {
            result.rejectValue("expectedDueDate", "date.beforeBreeding",
                    "Expected due date cannot be before the breeding date");
        }

        if (checkDate != null && dueDate != null && checkDate.isAfter(dueDate)) {
            result.rejectValue("expectedPregnancyCheckDate", "date.afterDue",
                    "Pregnancy check date must be on or before the expected due date");
        }
    }
    private String checkBreedingEligibility(Livestock female, LocalDate breedingDate) {
        // Never given birth before — always eligible
        if (female.getLastBirthDate() == null) return null;

        // Get gestation months from the animal's category
        int gestationMonths = 0;
        if (female.getLivestockCategory() != null
                && female.getLivestockCategory().getGestationPeriodMonths() != null) {
            gestationMonths = female.getLivestockCategory().getGestationPeriodMonths();
        }

        // No rule configured for this category — allow
        if (gestationMonths <= 0) return null;

        LocalDate effectiveBreedingDate = (breedingDate != null) ? breedingDate : LocalDate.now();
        LocalDate earliestNextBreeding  = female.getLastBirthDate().plusMonths(gestationMonths);

        if (effectiveBreedingDate.isBefore(earliestNextBreeding)) {
            long daysRemaining = ChronoUnit.DAYS.between(effectiveBreedingDate, earliestNextBreeding);
            return "Animal " + female.getTagNumber()
                    + " is not yet eligible for breeding."
                    + " Last birth: " + female.getLastBirthDate()
                    + ". Earliest next breeding date: " + earliestNextBreeding
                    + " (" + daysRemaining + " day(s) remaining)."
                    + " Category: " + female.getLivestockCategory().getName()
                    + " — gestation period: " + gestationMonths + " months.";
        }

        return null;
    }
    private void addLivestockAndVetsToModel(Model model) {
        List<Livestock> all = livestockRepository.findAll().stream()
                .filter(ls -> !Livestock.STATUS_DEAD.equals(ls.getStatus())
                        && !Livestock.STATUS_SOLD.equals(ls.getStatus()))
                .collect(Collectors.toList());

        Set<UUID> eligibleFemaleIds = femalesReadyToBreedService.getAllReadyToBreed()
                .stream()
                .filter(Objects::nonNull)
                .map(FemaleReadyToBreedDTO::getId)
                .collect(Collectors.toSet());

        List<Livestock> females = all.stream()
                .filter(ls -> "FEMALE".equalsIgnoreCase(ls.getGender()))
                .filter(ls -> eligibleFemaleIds.contains(ls.getId()))
                .sorted(Comparator
                        .comparing((Livestock ls) -> ls.getLivestockCategory() != null
                                        ? ls.getLivestockCategory().getName() : "Uncategorized",
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ls -> ls.getTagNumber() != null ? ls.getTagNumber() : "",
                                String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        Map<String, List<Livestock>> femalesByCategory = new LinkedHashMap<>();
        for (Livestock f : females) {
            String cat = f.getLivestockCategory() != null
                    ? f.getLivestockCategory().getName() : "Uncategorized";
            femalesByCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(f);
        }

        List<Livestock> allMaleLivestock = all.stream()
                .filter(ls -> "MALE".equalsIgnoreCase(ls.getGender()))
                .sorted(Comparator.comparing(
                        ls -> ls.getTagNumber() != null ? ls.getTagNumber() : "",
                        String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        List<MaleReadyToBreedDTO> eligibleMales = malesReadyToBreedService.getAllReadyToBreed();

        Map<String, List<Map<String, Object>>> malesByCategory = new LinkedHashMap<>();
        for (Livestock male : allMaleLivestock) {
            String catKey = male.getLivestockCategory() != null
                    ? male.getLivestockCategory().getId().toString()
                    : "UNKNOWN";
            malesByCategory.computeIfAbsent(catKey, k -> new ArrayList<>())
                    .add(Map.of(
                            "id",        male.getId().toString(),
                            "tagNumber", male.getTagNumber(),
                            "category",  male.getLivestockCategory() != null
                                    ? male.getLivestockCategory().getName() : ""
                    ));
        }

        model.addAttribute("femaleLivestock",     females);
        model.addAttribute("femalesByCategory",   femalesByCategory);
        model.addAttribute("hasEligibleFemales",  !females.isEmpty());
        model.addAttribute("allMaleLivestock",    allMaleLivestock);
        model.addAttribute("eligibleMales",       eligibleMales);
        model.addAttribute("malesByCategoryJson", toJson(malesByCategory));
        model.addAttribute("vets",                veterinarianService.getActive());
        model.addAttribute("gestationDaysJson",   buildGestationJson());
    }

    private void resolveRelations(LivestockBreeding breeding) {
        String lsId = breeding.getLivestockIdValue();
        if (lsId != null && !lsId.trim().isEmpty()) {
            livestockRepository.findById(UUID.fromString(lsId.trim()))
                    .ifPresent(breeding::setLivestock);
        }

        String maleId = breeding.getMaleLivestockIdValue();
        if (maleId != null && !maleId.trim().isEmpty()) {
            livestockRepository.findById(UUID.fromString(maleId.trim()))
                    .ifPresent(breeding::setMaleLivestock);
        } else {
            breeding.setMaleLivestock(null);
        }

        String vetId = breeding.getVeterinarianIdValue();
        if (vetId != null && !vetId.trim().isEmpty()) {
            veterinarianRepository.findById(UUID.fromString(vetId.trim()))
                    .ifPresent(breeding::setVeterinarian);
        } else {
            breeding.setVeterinarian(null);
        }
    }

    private String getCategoryName(Livestock ls) {
        if (ls.getLivestockCategory() == null) return "UNKNOWN";
        return ls.getLivestockCategory().getName();
    }

    private String toJson(Map<String, List<Map<String, Object>>> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean firstCat = true;
        for (Map.Entry<String, List<Map<String, Object>>> e : map.entrySet()) {
            if (!firstCat) sb.append(",");
            firstCat = false;
            sb.append("\"").append(e.getKey()).append("\":[");
            boolean firstItem = true;
            for (Map<String, Object> item : e.getValue()) {
                if (!firstItem) sb.append(",");
                firstItem = false;
                sb.append("{");
                sb.append("\"id\":\"").append(item.get("id")).append("\",");
                sb.append("\"tagNumber\":\"").append(item.get("tagNumber")).append("\",");
                sb.append("\"category\":\"").append(item.get("category")).append("\"");
                sb.append("}");
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    private String buildGestationJson() {
        return "{"
                + "\"COW\":283,\"CATTLE\":283,\"DAIRY COW\":283,"
                + "\"GOAT\":150,\"SHEEP\":147,"
                + "\"PIG\":114,\"SOW\":114,"
                + "\"RABBIT\":31,"
                + "\"HORSE\":340,\"MARE\":340,"
                + "\"DONKEY\":365,"
                + "\"DOG\":63,\"CAT\":65,"
                + "\"DEFAULT\":283"
                + "}";
    }

    // ── INNER DTO ─────────────────────────────────────────────────────────────

    public static class PregnantAnimalDTO {
        private UUID      id;
        private String    tagNumber;
        private String    categoryName;
        private LocalDate expectedDueDate;
        private Integer   daysUntilDue;

        public UUID      getId()              { return id; }
        public void      setId(UUID id)       { this.id = id; }

        public String    getTagNumber()                   { return tagNumber; }
        public void      setTagNumber(String tagNumber)   { this.tagNumber = tagNumber; }

        public String    getCategoryName()                    { return categoryName; }
        public void      setCategoryName(String categoryName) { this.categoryName = categoryName; }

        public LocalDate getExpectedDueDate()                       { return expectedDueDate; }
        public void      setExpectedDueDate(LocalDate expectedDueDate) { this.expectedDueDate = expectedDueDate; }

        public Integer   getDaysUntilDue()                    { return daysUntilDue; }
        public void      setDaysUntilDue(Integer daysUntilDue) { this.daysUntilDue = daysUntilDue; }
    }
}
