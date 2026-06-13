package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.entity.LivestockBirth;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.entity.LivestockOffspring;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSaleRepository;
import rw.animalproduct.animal.production.services.LivestockBirthService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livestock/births")
public class LivestockBirthController {

    private final LivestockBirthService       birthService;
    private final LivestockRepository         livestockRepository;
    private final LivestockSaleRepository     saleRepository;
    private final LivestockBreedingRepository breedingRepository;

    @Autowired
    public LivestockBirthController(LivestockBirthService birthService,
                                    LivestockRepository livestockRepository,
                                    LivestockSaleRepository saleRepository,
                                    LivestockBreedingRepository breedingRepository) {
        this.birthService        = birthService;
        this.livestockRepository = livestockRepository;
        this.saleRepository      = saleRepository;
        this.breedingRepository  = breedingRepository;
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * Populates the pregnant-mothers drop-down.
     *
     * An animal qualifies as pregnant when ANY of these hold:
     *   1. status == "PREGNANT"
     *   2. isPregnant == true
     *   3. Has an active breeding record with status CONFIRMED_PREGNANT
     */
    private void addLivestockToModel(Model model) {
        Set<UUID> confirmedPregnantIds = breedingRepository
                .findByStatusAndIsDeletedFalse(LivestockBreeding.STATUS_CONFIRMED_PREGNANT)
                .stream()
                .filter(b -> b.getLivestock() != null)
                .map(b -> b.getLivestock().getId())
                .collect(Collectors.toSet());

        List<Livestock> pregnantMothers = livestockRepository.findAll().stream()
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .filter(l -> !Livestock.STATUS_DEAD.equals(l.getStatus()))
                .filter(l -> !Livestock.STATUS_SOLD.equals(l.getStatus()))
                .filter(l -> l.getGender() == null
                        || l.getGender().equalsIgnoreCase("FEMALE"))
                .filter(l -> Livestock.STATUS_PREGNANT.equals(l.getStatus())
                        || Boolean.TRUE.equals(l.getIsPregnant())
                        || confirmedPregnantIds.contains(l.getId()))
                .sorted(Comparator.comparing(Livestock::getTagNumber))
                .collect(Collectors.toList());

        model.addAttribute("livestockList", pregnantMothers);
        model.addAttribute("allLivestockList", Collections.emptyList());
    }

    // ── Redirect ──────────────────────────────────────────────────────────────

    @GetMapping({"", "/"})
    public String redirectToList() {
        return "redirect:/livestock/births/list";
    }

    // ═════════════════════════════════════════════════════════════════════════
    // API: Children filtered by mother category + young age
    //      FIXED: Now shows animals with NULL mother_id as eligible
    // ═════════════════════════════════════════════════════════════════════════
    @GetMapping("/api/children-by-mother")
    @ResponseBody
    public List<Map<String, Object>> getChildrenByMother(
            @RequestParam("motherId") UUID motherId) {

        Livestock mother = livestockRepository.findById(motherId).orElse(null);
        if (mother == null) return Collections.emptyList();

        UUID categoryId = (mother.getLivestockCategory() != null)
                ? mother.getLivestockCategory().getId()
                : null;

        int gestationMonths = 12;
        if (mother.getLivestockCategory() != null
                && mother.getLivestockCategory().getGestationPeriodMonths() != null
                && mother.getLivestockCategory().getGestationPeriodMonths() > 0) {
            gestationMonths = mother.getLivestockCategory().getGestationPeriodMonths();
        }

        LocalDate anchor = mother.getLastBirthDate() != null
                ? mother.getLastBirthDate()
                : LocalDate.now();
        LocalDate earliestChildBirth = anchor.minusMonths(gestationMonths);
        LocalDate today = LocalDate.now();

        // Get animals that are ALREADY linked to this mother via birth events
        Set<UUID> alreadyLinkedViaBirths = new HashSet<>();
        List<LivestockBirth> allBirths = birthService.getAll();
        for (LivestockBirth birth : allBirths) {
            if (birth.getChildren() != null) {
                for (LivestockOffspring offspring : birth.getChildren()) {
                    if (offspring.getChildLivestock() != null) {
                        alreadyLinkedViaBirths.add(offspring.getChildLivestock().getId());
                    }
                }
            }
        }

        List<Livestock> allLivestock = livestockRepository.findAll();

        // FIX: Eligible children are those that:
        // 1. Have NO mother OR mother matches the selected mother
        // 2. Are not already linked via a DIFFERENT birth event
        // 3. But allow linking if they already have THIS mother (for correction/re-linking)
        List<Livestock> eligible = allLivestock.stream()
                .filter(l -> {
                    if (categoryId == null) return true;
                    return l.getLivestockCategory() != null
                            && categoryId.equals(l.getLivestockCategory().getId());
                })
                .filter(l -> !l.getId().equals(motherId)) // not the mother itself
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft())) // not a draft
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted())) // not deleted
                // FIX: Allow animals with:
                // - No mother (NULL)
                // - OR mother matches the selected mother
                // - OR mother is different but not linked via any birth event (for admin correction)
                .filter(l -> {
                    if (l.getMother() == null) return true; // No mother yet
                    if (motherId.equals(l.getMother().getId())) return true; // Already has this mother
                    // If mother is different but not linked via any birth event, allow (for admin)
                    return !alreadyLinkedViaBirths.contains(l.getId());
                })
                // FIX: More permissive date range - allow up to 6 months before/after
                .filter(l -> {
                    if (l.getBirthDate() == null) return true;
                    LocalDate maxChildBirthDate = anchor.plusMonths(6); // Give 6 months buffer after birth
                    LocalDate minChildBirthDate = earliestChildBirth.minusMonths(6); // Give 6 months buffer before
                    return !l.getBirthDate().isBefore(minChildBirthDate)
                            && !l.getBirthDate().isAfter(maxChildBirthDate);
                })
                .sorted(Comparator.comparing(Livestock::getBirthDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        return eligible.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("tagNumber", l.getTagNumber());
            m.put("gender", l.getGender() != null ? l.getGender() : "Unknown");
            m.put("category", l.getLivestockCategory() != null
                    ? l.getLivestockCategory().getName() : "—");
            m.put("birthDate", l.getBirthDate() != null ? l.getBirthDate().toString() : null);
            long ageDays = l.getBirthDate() != null
                    ? ChronoUnit.DAYS.between(l.getBirthDate(), today) : 0;
            m.put("ageDays", ageDays);
            m.put("alreadyHasMother", l.getMother() != null);
            m.put("motherTag", l.getMother() != null ? l.getMother().getTagNumber() : null);
            return m;
        }).collect(Collectors.toList());
    }
    // ═════════════════════════════════════════════════════════════════════════
    // LIST
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/list")
    public String listAll(@RequestParam(value = "page", defaultValue = "0") int page,
                          @RequestParam(value = "size", defaultValue = "10") int size,
                          Model model) {

        List<LivestockBirth> births;

        try {
            Page<LivestockBirth> pageContent = birthService.getPaged(page, size);
            births = pageContent.getContent();
            model.addAttribute("births",      births);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages",  pageContent.getTotalPages());
            model.addAttribute("totalItems",  pageContent.getTotalElements());
            model.addAttribute("pageSize",    size);
        } catch (Exception e) {
            births = birthService.getAll();
            model.addAttribute("births",      births);
            model.addAttribute("totalItems",  births.size());
            model.addAttribute("totalPages",  1);
            model.addAttribute("currentPage", 0);
            model.addAttribute("pageSize",    10);
        }

        Map<UUID, Boolean> breedingCapableMap = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (LivestockBirth b : births) {
            Livestock mother = b.getLivestock();
            if (mother == null) continue;
            LocalDate bd = mother.getBirthDate();
            if (bd == null) continue;
            LivestockCategory cat = mother.getLivestockCategory();
            if (cat == null || cat.getMinBreedingAgeMonths() == null) continue;
            long ageMonths = ChronoUnit.MONTHS.between(bd, today);
            breedingCapableMap.put(mother.getId(), ageMonths >= cat.getMinBreedingAgeMonths());
        }
        model.addAttribute("breedingCapableMap", breedingCapableMap);

        long pendingDraftCount = livestockRepository.findAllPendingDrafts().size();
        model.addAttribute("pendingDraftCount", pendingDraftCount);

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

    // ═════════════════════════════════════════════════════════════════════════
    // REGISTER
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("birth", new LivestockBirth());
        addLivestockToModel(model);
        return "livestock-birth-register";
    }

    @PostMapping("/register/new")
    public String register(@Valid @ModelAttribute("birth") LivestockBirth birth,
                           BindingResult result,
                           @RequestParam(value = "linkedChildIds", required = false) List<UUID> linkedChildIds,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        birth.setIsExternalBirth(false);

        if (birth.getLivestockIdValue() == null || birth.getLivestockIdValue().trim().isEmpty()) {
            result.rejectValue("livestockIdValue", "error.birth",
                    "Mother animal is required — select the pregnant animal that gave birth");
        }

        if (result.hasErrors()) {
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            model.addAttribute("error", errorMessages);
            addLivestockToModel(model);
            return "livestock-birth-register";
        }

        try {
            LivestockBirth saved = birthService.addNew(birth, linkedChildIds);
            redirectAttributes.addFlashAttribute("success",
                    "Birth recorded successfully! " +
                            (linkedChildIds != null && !linkedChildIds.isEmpty()
                                    ? linkedChildIds.size() + " child(ren) linked. "
                                    : "") +
                            "You can link or add more children below.");
            return "redirect:/livestock/births/" + saved.getId() + "/children";
        } catch (Exception e) {
            model.addAttribute("error", "Error recording birth: " + e.getMessage());
            addLivestockToModel(model);
            return "livestock-birth-register";
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // EDIT
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable UUID id, Model model) {
        Optional<LivestockBirth> opt = birthService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/births/list";

        LivestockBirth birth = opt.get();
        if (birth.getLivestock() != null) {
            birth.setLivestockIdValue(birth.getLivestock().getId().toString());
        }
        model.addAttribute("birth", birth);
        addLivestockToModel(model);
        return "livestock-birth-edit";
    }

    @PostMapping("/update/{id}")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("birth") LivestockBirth birth,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        birth.setIsExternalBirth(false);

        if (birth.getLivestockIdValue() == null || birth.getLivestockIdValue().trim().isEmpty()) {
            result.rejectValue("livestockIdValue", "error.birth", "Mother animal is required");
        }

        if (result.hasErrors()) {
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
            model.addAttribute("error", errorMessages);
            addLivestockToModel(model);
            return "livestock-birth-edit";
        }

        try {
            birthService.update(id, birth);
            redirectAttributes.addFlashAttribute("success", "Birth record updated successfully!");
            return "redirect:/livestock/births/list";
        } catch (Exception e) {
            model.addAttribute("error", "Error updating birth: " + e.getMessage());
            addLivestockToModel(model);
            return "livestock-birth-edit";
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DELETE
    // ═════════════════════════════════════════════════════════════════════════

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            birthService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Birth record deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/livestock/births/list";
    }

    // ═════════════════════════════════════════════════════════════════════════
    // VIEW
    //
    // FIX: pass List<Livestock> as linkedChildren — the view iterates Livestock
    //      directly, not LivestockOffspring wrappers.
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/view/{id}")
    public String viewDetail(@PathVariable UUID id, Model model) {
        Optional<LivestockBirth> opt = birthService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/births/list";

        LivestockBirth birth = opt.get();

        // FIX: unwrap to Livestock so the template's th:each works without .childLivestock
        List<Livestock> linkedChildren = birth.getChildren() == null
                ? Collections.emptyList()
                : birth.getChildren().stream()
                .map(LivestockOffspring::getChildLivestock)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        model.addAttribute("birth",          birth);
        model.addAttribute("linkedChildren", linkedChildren);
        // Also pass the raw offspring list so the view can show generation etc. if needed
        model.addAttribute("offspringLinks", birth.getChildren());
        return "livestock-birth-view";
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CHILD LINKING
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/{birthId}/children")
    public String viewChildren(@PathVariable UUID birthId, Model model) {
        Optional<LivestockBirth> opt = birthService.getById(birthId);
        if (opt.isEmpty()) return "redirect:/livestock/births/list";

        LivestockBirth birth = opt.get();

        List<Livestock> linkedChildren = birth.getChildren().stream()
                .map(LivestockOffspring::getChildLivestock)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        UUID motherId  = birth.getLivestock() != null ? birth.getLivestock().getId() : null;
        Livestock mother = birth.getLivestock();

        Set<UUID> linkedIds = linkedChildren.stream()
                .map(Livestock::getId)
                .collect(Collectors.toSet());

        UUID categoryId = (mother != null && mother.getLivestockCategory() != null)
                ? mother.getLivestockCategory().getId()
                : null;

        int gestationMonths = 12;
        if (mother != null && mother.getLivestockCategory() != null
                && mother.getLivestockCategory().getGestationPeriodMonths() != null
                && mother.getLivestockCategory().getGestationPeriodMonths() > 0) {
            gestationMonths = mother.getLivestockCategory().getGestationPeriodMonths();
        }

        LocalDate anchor = (mother != null && mother.getLastBirthDate() != null)
                ? mother.getLastBirthDate()
                : birth.getBirthDate() != null ? birth.getBirthDate() : LocalDate.now();

        LocalDate earliestChildBirth = anchor.minusMonths(gestationMonths);
        LocalDate today = LocalDate.now();

        Set<UUID> draftIdsForThisBirth = livestockRepository
                .findByDraftBirthEventIdAndIsDraftTrue(birthId)
                .stream()
                .map(Livestock::getId)
                .collect(Collectors.toSet());

        // FIX: Also get animals that have mother_id = this mother (already linked via Livestock.mother)
        List<Livestock> available = livestockRepository.findAll().stream()
                .filter(l -> !linkedIds.contains(l.getId()))
                .filter(l -> motherId == null || !l.getId().equals(motherId))
                .filter(l -> {
                    if (categoryId == null) return true;
                    return l.getLivestockCategory() != null
                            && categoryId.equals(l.getLivestockCategory().getId());
                })
                .filter(l -> {
                    if (Boolean.TRUE.equals(l.getIsDraft())) {
                        return draftIdsForThisBirth.contains(l.getId());
                    }
                    // FIX: Allow animals with NULL mother OR mother matches this mother
                    if (l.getMother() != null) {
                        return motherId != null && l.getMother().getId().equals(motherId);
                    }
                    // If no mother set, check birth date within reasonable range
                    if (l.getBirthDate() == null) return true;
                    LocalDate maxChildBirthDate = anchor.plusMonths(2);
                    LocalDate minChildBirthDate = earliestChildBirth.minusMonths(3);
                    return !l.getBirthDate().isBefore(minChildBirthDate)
                            && !l.getBirthDate().isAfter(maxChildBirthDate);
                })
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .sorted(Comparator.comparing(
                        l -> l.getBirthDate() != null ? l.getBirthDate() : LocalDate.MIN,
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());

        model.addAttribute("birth",              birth);
        model.addAttribute("linkedChildren",     linkedChildren);
        model.addAttribute("availableLivestock", available);

        long pendingDraftsForThisBirth = linkedChildren.stream()
                .filter(l -> Boolean.TRUE.equals(l.getIsDraft()))
                .count();
        model.addAttribute("pendingDraftsForThisBirth", pendingDraftsForThisBirth);

        return "livestock-birth-children";
    }

    @PostMapping("/{birthId}/link-child")
    public String linkChild(@PathVariable UUID birthId,
                            @RequestParam("childLivestockId") UUID childLivestockId,
                            RedirectAttributes redirectAttributes) {
        try {
            birthService.linkChild(birthId, childLivestockId);
            redirectAttributes.addFlashAttribute("success", "Animal linked successfully as child!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error linking animal: " + e.getMessage());
        }
        return "redirect:/livestock/births/" + birthId + "/children";
    }

    @PostMapping("/unlink-child/{childLivestockId}")
    public String unlinkChild(@PathVariable UUID childLivestockId,
                              @RequestParam("birthId") UUID birthId,
                              RedirectAttributes redirectAttributes) {
        try {
            birthService.unlinkChild(childLivestockId);
            redirectAttributes.addFlashAttribute("success", "Animal unlinked successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/livestock/births/" + birthId + "/children";
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FAMILY TREE
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/family/{livestockId}")
    public String viewFamilyTree(@PathVariable UUID livestockId, Model model) {
        Optional<Livestock> opt = livestockRepository.findById(livestockId);
        if (opt.isEmpty()) return "redirect:/livestock/list";

        Livestock animal = opt.get();
        List<Livestock>      directChildren  = birthService.getDirectChildren(livestockId);
        List<LivestockBirth> birthsAsMother  = birthService.getByLivestockId(livestockId);

        model.addAttribute("animal",         animal);
        model.addAttribute("mother",         animal.getMother());
        model.addAttribute("directChildren", directChildren);
        model.addAttribute("birthsAsMother", birthsAsMother);
        model.addAttribute("hasChildren",    birthService.hasChildren(livestockId));

        Livestock grandmother = null;
        if (animal.getMother() != null && animal.getMother().getMother() != null) {
            grandmother = animal.getMother().getMother();
        }
        model.addAttribute("grandmother", grandmother);

        return "livestock-family";
    }

    // ═════════════════════════════════════════════════════════════════════════
    // BORN → SOLD REPORT
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/report/born-and-sold")
    public String bornAndSoldReport(Model model) {

        List<LivestockBirth> allBirths = birthService.getAll();

        List<Map<String, Object>> rows = new ArrayList<>();
        long totalSold   = 0;
        long totalOnFarm = 0;
        long totalDead   = 0;

        for (LivestockBirth birth : allBirths) {
            if (birth.getChildren() == null || birth.getChildren().isEmpty()) continue;

            for (LivestockOffspring offspring : birth.getChildren()) {
                Livestock child = offspring.getChildLivestock();
                if (child == null) continue;
                if (Boolean.TRUE.equals(child.getIsDraft())) continue;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("childId",     child.getId());
                row.put("childTag",    child.getTagNumber());
                row.put("childGender", child.getGender());
                row.put("category",    child.getLivestockCategory() != null
                        ? child.getLivestockCategory().getName() : "—");
                row.put("motherTag",      birth.getLivestock() != null
                        ? birth.getLivestock().getTagNumber() : "Unknown");
                row.put("motherId",       birth.getLivestock() != null
                        ? birth.getLivestock().getId() : null);
                row.put("isExternal",     birth.getIsExternalBirth());
                row.put("sourceLocation", birth.getSourceLocation() != null
                        ? birth.getSourceLocation() : "—");
                row.put("birthDate", birth.getBirthDate());
                row.put("birthId",   birth.getId());
                row.put("generation", offspring.getGeneration());
                row.put("status",    child.getStatus() != null
                        ? child.getStatus() : Livestock.STATUS_ACTIVE);

                List<LivestockSale> sales = saleRepository.findByLivestockId(child.getId());
                if (!sales.isEmpty()) {
                    LivestockSale latestSale = sales.stream()
                            .max(Comparator.comparing(LivestockSale::getSaleDate))
                            .orElse(sales.get(0));
                    row.put("saleId",       latestSale.getId());
                    row.put("saleDate",     latestSale.getSaleDate());
                    row.put("salePrice",    latestSale.getSalePrice());
                    row.put("saleLocation", latestSale.getSaleLocation() != null
                            ? latestSale.getSaleLocation() : "—");
                    row.put("saleReason",   latestSale.getSaleReason() != null
                            ? latestSale.getSaleReason() : "—");
                    if (birth.getBirthDate() != null && latestSale.getSaleDate() != null) {
                        long days = ChronoUnit.DAYS.between(birth.getBirthDate(), latestSale.getSaleDate());
                        row.put("daysToSale", days);
                    } else {
                        row.put("daysToSale", null);
                    }
                } else {
                    row.put("saleId", null); row.put("saleDate", null);
                    row.put("salePrice", null); row.put("saleLocation", "—");
                    row.put("saleReason", "—"); row.put("daysToSale", null);
                }

                String status = (String) row.get("status");
                if (Livestock.STATUS_SOLD.equals(status))      totalSold++;
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

    // ═════════════════════════════════════════════════════════════════════════
    // ADMIN: Repair missing mother links
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/admin/repair-mother-links")
    public String repairMotherLinks(RedirectAttributes redirectAttributes) {
        try {
            int fixed = birthService.repairMissingMotherLinks();
            redirectAttributes.addFlashAttribute("success",
                    "Mother link repair complete. Fixed " + fixed + " animal(s).");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Repair failed: " + e.getMessage());
        }
        return "redirect:/livestock/births/list";
    }
}