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
import rw.animalproduct.animal.production.entity.LivestockBirth;
import rw.animalproduct.animal.production.entity.LivestockOffspring;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSaleRepository;
import rw.animalproduct.animal.production.services.LivestockBirthService;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livestock/births")
public class LivestockBirthController {

    private final LivestockBirthService   birthService;
    private final LivestockRepository     livestockRepository;
    private final LivestockSaleRepository saleRepository;

    @Autowired
    public LivestockBirthController(LivestockBirthService birthService,
                                    LivestockRepository livestockRepository,
                                    LivestockSaleRepository saleRepository) {
        this.birthService        = birthService;
        this.livestockRepository = livestockRepository;
        this.saleRepository      = saleRepository;
    }

    // ── Helper ───────────────────────────────────────────────────────

    private void addLivestockToModel(Model model) {
        List<Livestock> females = livestockRepository.findByGenderIgnoreCase("FEMALE");
        if (females.isEmpty()) {
            model.addAttribute("livestockList", livestockRepository.findAll());
        } else {
            model.addAttribute("livestockList", females);
        }
        model.addAttribute("allLivestockList", livestockRepository.findAll());
    }

    // ── Redirect ─────────────────────────────────────────────────────

    @GetMapping({"", "/"})
    public String redirectToList() {
        return "redirect:/livestock/births/list";
    }

    // ===================== LIST =====================

    @GetMapping("/list")
    public String listAll(@RequestParam(value = "page", defaultValue = "0") int page,
                          @RequestParam(value = "size", defaultValue = "10") int size,
                          Model model) {
        try {
            Page<LivestockBirth> pageContent = birthService.getPaged(page, size);
            model.addAttribute("births",      pageContent.getContent());
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages",  pageContent.getTotalPages());
            model.addAttribute("totalItems",  pageContent.getTotalElements());
            model.addAttribute("pageSize",    size);
        } catch (Exception e) {
            List<LivestockBirth> all = birthService.getAll();
            model.addAttribute("births",      all);
            model.addAttribute("totalItems",  all.size());
            model.addAttribute("totalPages",  1);
            model.addAttribute("currentPage", 0);
            model.addAttribute("pageSize",    10);
        }
        return "livestock-births-list";
    }

    // ===================== REGISTER =====================

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("birth", new LivestockBirth());
        addLivestockToModel(model);
        return "livestock-birth-register";
    }

    @PostMapping("/register/new")
    public String register(@Valid @ModelAttribute("birth") LivestockBirth birth,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        boolean isExternal = Boolean.TRUE.equals(birth.getIsExternalBirth());

        // Mother is required ONLY for on-farm births
        if (!isExternal
                && (birth.getLivestockIdValue() == null
                || birth.getLivestockIdValue().trim().isEmpty())) {
            result.rejectValue("livestockIdValue", "error.birth",
                    "Mother animal is required for on-farm births");
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
            LivestockBirth saved = birthService.addNew(birth);
            String msg = isExternal
                    ? "External birth recorded! Now link the purchased animal below."
                    : "Birth recorded successfully! Now link the child animals below.";
            redirectAttributes.addFlashAttribute("success", msg);
            return "redirect:/livestock/births/" + saved.getId() + "/children";
        } catch (Exception e) {
            model.addAttribute("error", "Error recording birth: " + e.getMessage());
            addLivestockToModel(model);
            return "livestock-birth-register";
        }
    }

    // ===================== EDIT =====================

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

        boolean isExternal = Boolean.TRUE.equals(birth.getIsExternalBirth());

        if (!isExternal
                && (birth.getLivestockIdValue() == null
                || birth.getLivestockIdValue().trim().isEmpty())) {
            result.rejectValue("livestockIdValue", "error.birth",
                    "Mother animal is required for on-farm births");
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

    // ===================== DELETE =====================

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

    // ===================== VIEW =====================

    @GetMapping("/view/{id}")
    public String viewDetail(@PathVariable UUID id, Model model) {
        Optional<LivestockBirth> opt = birthService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/births/list";

        LivestockBirth birth = opt.get();
        model.addAttribute("birth", birth);
        model.addAttribute("linkedChildren", birth.getChildren());
        return "livestock-birth-view";
    }

    // ===================== CHILD LINKING =====================

    @GetMapping("/{birthId}/children")
    public String viewChildren(@PathVariable UUID birthId, Model model) {
        Optional<LivestockBirth> opt = birthService.getById(birthId);
        if (opt.isEmpty()) return "redirect:/livestock/births/list";

        LivestockBirth birth = opt.get();

        List<Livestock> linkedChildren = birth.getChildren().stream()
                .map(LivestockOffspring::getChildLivestock)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        UUID motherId = birth.getLivestock() != null ? birth.getLivestock().getId() : null;

        List<Livestock> available = livestockRepository.findAll().stream()
                .filter(l -> !linkedChildren.contains(l))
                .filter(l -> motherId == null || !l.getId().equals(motherId))
                .collect(Collectors.toList());

        model.addAttribute("birth",              birth);
        model.addAttribute("linkedChildren",     linkedChildren);
        model.addAttribute("availableLivestock", available);
        return "livestock-birth-children";
    }

    @PostMapping("/{birthId}/link-child")
    public String linkChild(@PathVariable UUID birthId,
                            @RequestParam("childLivestockId") UUID childLivestockId,
                            RedirectAttributes redirectAttributes) {
        try {
            birthService.linkChild(birthId, childLivestockId);
            redirectAttributes.addFlashAttribute("success", "Animal linked successfully!");
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

    // ===================== FAMILY TREE =====================

    @GetMapping("/family/{livestockId}")
    public String viewFamilyTree(@PathVariable UUID livestockId, Model model) {
        Optional<Livestock> opt = livestockRepository.findById(livestockId);
        if (opt.isEmpty()) return "redirect:/livestock/list";

        Livestock animal = opt.get();
        List<Livestock> directChildren      = birthService.getDirectChildren(livestockId);
        List<LivestockBirth> birthsAsMother = birthService.getByLivestockId(livestockId);

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

    // ===================== BORN → SOLD REPORT =====================

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

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("childId",        child.getId());
                row.put("childTag",       child.getTagNumber());
                row.put("childGender",    child.getGender());
                row.put("category",       child.getLivestockCategory() != null
                        ? child.getLivestockCategory().getName() : "—");

                // Mother tag — null for external/purchased animals
                row.put("motherTag",      birth.getLivestock() != null
                        ? birth.getLivestock().getTagNumber() : "Unknown (purchased)");
                row.put("motherId",       birth.getLivestock() != null
                        ? birth.getLivestock().getId() : null);
                row.put("isExternal",     birth.getIsExternalBirth());
                row.put("sourceLocation", birth.getSourceLocation() != null
                        ? birth.getSourceLocation() : "—");

                // birth_date comes from livestock_births — same for both farm and purchased animals
                row.put("birthDate",      birth.getBirthDate());
                row.put("birthId",        birth.getId());
                row.put("generation",     offspring.getGeneration());
                row.put("status",         child.getStatus() != null
                        ? child.getStatus() : Livestock.STATUS_ACTIVE);

                // Sale record
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
                    row.put("saleId",       null);
                    row.put("saleDate",     null);
                    row.put("salePrice",    null);
                    row.put("saleLocation", "—");
                    row.put("saleReason",   "—");
                    row.put("daysToSale",   null);
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
}
