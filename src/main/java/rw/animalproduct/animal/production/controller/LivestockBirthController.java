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

    // =========================================================================
    // HELPER — pregnant-mothers dropdown
    // =========================================================================

    /**
     * Populates the pregnant-mothers drop-down.
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

        model.addAttribute("livestockList",    pregnantMothers);
        model.addAttribute("allLivestockList", Collections.emptyList());
    }

    // =========================================================================
    // REDIRECT
    // =========================================================================

    @GetMapping({"", "/"})
    public String redirectToList() {
        return "redirect:/livestock/births/list";
    }

    // =========================================================================
    // API — children filtered by mother category
    // =========================================================================

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

        LocalDate anchor           = mother.getLastBirthDate() != null
                ? mother.getLastBirthDate() : LocalDate.now();
        LocalDate earliestChild    = anchor.minusMonths(gestationMonths);
        LocalDate today            = LocalDate.now();

        // Animals already linked via ANY birth event
        Set<UUID> alreadyLinked = new HashSet<>();
        for (LivestockBirth b : birthService.getAll()) {
            if (b.getChildren() != null) {
                for (LivestockOffspring o : b.getChildren()) {
                    if (o.getChildLivestock() != null) {
                        alreadyLinked.add(o.getChildLivestock().getId());
                    }
                }
            }
        }

        return livestockRepository.findAll().stream()
                .filter(l -> categoryId == null
                        || (l.getLivestockCategory() != null
                        && categoryId.equals(l.getLivestockCategory().getId())))
                .filter(l -> !l.getId().equals(motherId))
                .filter(l -> !Boolean.TRUE.equals(l.getIsDraft()))
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .filter(l -> {
                    if (l.getMother() == null)                         return true;
                    if (motherId.equals(l.getMother().getId()))        return true;
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
                    return m;
                })
                .collect(Collectors.toList());
    }

    // =========================================================================
    // LIST
    // =========================================================================

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

        // Breeding-capable map — keyed by mother UUID
        LocalDate today = LocalDate.now();
        Map<UUID, Boolean> breedingCapableMap = new HashMap<>();
        for (LivestockBirth b : births) {
            // ── FIX: livestock is LAZY; use the raw FK to load the mother safely ──
            if (b.getLivestockId() == null) continue;
            Livestock mother = livestockRepository.findById(b.getLivestockId()).orElse(null);
            if (mother == null) continue;

            LocalDate bd = mother.getBirthDate();
            if (bd == null) continue;
            LivestockCategory cat = mother.getLivestockCategory();
            if (cat == null || cat.getMinBreedingAgeMonths() == null) continue;
            long ageMonths = ChronoUnit.MONTHS.between(bd, today);
            breedingCapableMap.put(mother.getId(), ageMonths >= cat.getMinBreedingAgeMonths());
        }
        model.addAttribute("breedingCapableMap", breedingCapableMap);

        // Pending drafts banner
        model.addAttribute("pendingDraftCount",
                livestockRepository.findAllPendingDrafts().size());

        // Draft-present map — keyed by birth UUID
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

    // =========================================================================
    // REGISTER
    // =========================================================================

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("birth", new LivestockBirth());
        addLivestockToModel(model);
        return "livestock-birth-register";
    }

    @PostMapping("/register/new")
    public String register(@Valid @ModelAttribute("birth") LivestockBirth birth,
                           BindingResult result,
                           @RequestParam(value = "linkedChildIds", required = false)
                           List<UUID> linkedChildIds,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        // ── FIX: entity has Boolean isExternalBirth — use setIsExternalBirth() ──
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
            LivestockBirth saved = birthService.addNew(birth, linkedChildIds);
            redirectAttributes.addFlashAttribute("success",
                    "Birth recorded successfully! "
                            + (linkedChildIds != null && !linkedChildIds.isEmpty()
                            ? linkedChildIds.size() + " child(ren) linked. " : "")
                            + "You can link or add more children below.");
            return "redirect:/livestock/births/" + saved.getId() + "/children";
        } catch (Exception e) {
            model.addAttribute("error", "Error recording birth: " + e.getMessage());
            addLivestockToModel(model);
            return "livestock-birth-register";
        }
    }

    // =========================================================================
    // EDIT
    // =========================================================================

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable UUID id, Model model) {
        Optional<LivestockBirth> opt = birthService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/births/list";

        LivestockBirth birth = opt.get();

        // ── FIX: load mother via FK, not via lazy association ──
        if (birth.getLivestockId() != null) {
            Livestock mother = livestockRepository.findById(birth.getLivestockId()).orElse(null);
            if (mother != null) {
                birth.setLivestock(mother);
                birth.setLivestockIdValue(mother.getId().toString());
            }
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
            birthService.update(id, birth);
            redirectAttributes.addFlashAttribute("success",
                    "Birth record updated successfully!");
            return "redirect:/livestock/births/list";
        } catch (Exception e) {
            model.addAttribute("error", "Error updating birth: " + e.getMessage());
            addLivestockToModel(model);
            return "livestock-birth-edit";
        }
    }

    // =========================================================================
    // DELETE — delegates to service which does soft-delete + audit log
    // =========================================================================

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

    // =========================================================================
    // VIEW
    //
    // FIX: birth.livestock is FetchType.LAZY — after the JPA session closes the
    //      proxy returns null in Thymeleaf.  Load the mother explicitly via the
    //      raw FK (livestock_id) and pass plain Strings to the template instead
    //      of relying on the proxy.
    // =========================================================================

    @GetMapping("/view/{id}")
    public String viewDetail(@PathVariable UUID id, Model model) {
        Optional<LivestockBirth> opt = birthService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/births/list";

        LivestockBirth birth = opt.get();

        // Resolve mother from FK — guaranteed in-session, no lazy issues
        String motherTag      = null;
        String motherCategory = null;
        if (birth.getLivestockId() != null) {
            Livestock mother = livestockRepository
                    .findById(birth.getLivestockId()).orElse(null);
            if (mother != null) {
                motherTag      = mother.getTagNumber();
                motherCategory = mother.getLivestockCategory() != null
                        ? mother.getLivestockCategory().getName() : null;
                // Re-attach so Thymeleaf can still use birth.livestock if needed
                birth.setLivestock(mother);
            }
        }

        // Unwrap children — List<LivestockOffspring> → List<Livestock>
        List<Livestock> linkedChildren = birth.getChildren() == null
                ? Collections.emptyList()
                : birth.getChildren().stream()
                .map(LivestockOffspring::getChildLivestock)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        model.addAttribute("birth",          birth);
        model.addAttribute("motherTag",      motherTag);      // plain String — no lazy proxy
        model.addAttribute("motherCategory", motherCategory); // plain String — no lazy proxy
        model.addAttribute("linkedChildren", linkedChildren);
        model.addAttribute("offspringLinks", birth.getChildren());
        return "livestock-birth-view";
    }

    // =========================================================================
    // CHILD LINKING
    // =========================================================================

    @GetMapping("/{birthId}/children")
    public String viewChildren(@PathVariable UUID birthId, Model model) {
        Optional<LivestockBirth> opt = birthService.getById(birthId);
        if (opt.isEmpty()) return "redirect:/livestock/births/list";

        LivestockBirth birth = opt.get();

        // ── FIX: resolve mother via FK, not lazy proxy ──
        Livestock mother = null;
        if (birth.getLivestockId() != null) {
            mother = livestockRepository.findById(birth.getLivestockId()).orElse(null);
            if (mother != null) birth.setLivestock(mother);
        }

        List<Livestock> linkedChildren = birth.getChildren().stream()
                .map(LivestockOffspring::getChildLivestock)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        UUID motherId   = mother != null ? mother.getId() : null;
        Set<UUID> linkedIds = linkedChildren.stream()
                .map(Livestock::getId)
                .collect(Collectors.toSet());

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
                : birth.getBirthDate() != null ? birth.getBirthDate() : LocalDate.now();
        LocalDate earliestChild = anchor.minusMonths(gestationMonths);

        Set<UUID> draftIdsForThisBirth = livestockRepository
                .findByDraftBirthEventIdAndIsDraftTrue(birthId)
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
                .filter(l -> !Boolean.TRUE.equals(l.getIsDeleted()))
                .sorted(Comparator.comparing(
                        l -> l.getBirthDate() != null ? l.getBirthDate() : LocalDate.MIN,
                        Comparator.reverseOrder()))
                .collect(Collectors.toList());

        long pendingDrafts = linkedChildren.stream()
                .filter(l -> Boolean.TRUE.equals(l.getIsDraft()))
                .count();

        model.addAttribute("birth",                    birth);
        model.addAttribute("linkedChildren",           linkedChildren);
        model.addAttribute("availableLivestock",       available);
        model.addAttribute("pendingDraftsForThisBirth", pendingDrafts);
        return "livestock-birth-children";
    }

    @PostMapping("/{birthId}/link-child")
    public String linkChild(@PathVariable UUID birthId,
                            @RequestParam("childLivestockId") UUID childLivestockId,
                            RedirectAttributes redirectAttributes) {
        try {
            birthService.linkChild(birthId, childLivestockId);
            redirectAttributes.addFlashAttribute("success",
                    "Animal linked successfully as child!");
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

    // =========================================================================
    // FAMILY TREE
    // =========================================================================

    @GetMapping("/family/{livestockId}")
    public String viewFamilyTree(@PathVariable UUID livestockId, Model model) {
        Optional<Livestock> opt = livestockRepository.findById(livestockId);
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

    // =========================================================================
    // BORN → SOLD REPORT
    // =========================================================================

    @GetMapping("/report/born-and-sold")
    public String bornAndSoldReport(Model model) {

        List<LivestockBirth> allBirths = birthService.getAll();
        List<Map<String, Object>> rows = new ArrayList<>();
        long totalSold = 0, totalOnFarm = 0, totalDead = 0;

        for (LivestockBirth birth : allBirths) {
            if (birth.getChildren() == null || birth.getChildren().isEmpty()) continue;

            // ── FIX: resolve mother via FK to avoid lazy issues in a loop ──
            Livestock birthMother = null;
            if (birth.getLivestockId() != null) {
                birthMother = livestockRepository.findById(birth.getLivestockId()).orElse(null);
            }

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
                // ── FIX: Boolean isExternalBirth — Lombok generates getIsExternalBirth() ──
                row.put("isExternal",     birth.getIsExternalBirth());
                row.put("sourceLocation", birth.getSourceLocation() != null
                        ? birth.getSourceLocation() : "—");
                row.put("birthDate",      birth.getBirthDate());
                row.put("birthId",        birth.getId());
                row.put("generation",     offspring.getGeneration());
                row.put("status",         child.getStatus() != null
                        ? child.getStatus() : Livestock.STATUS_ACTIVE);

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

    // =========================================================================
    // ADMIN — repair missing mother links
    // =========================================================================

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