package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.dto.MaleReadyToBreedDTO;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.VeterinarianRepository;
import rw.animalproduct.animal.production.services.LivestockBreedingService;
import rw.animalproduct.animal.production.services.MalesReadyToBreedService;
import rw.animalproduct.animal.production.services.VeterinarianService;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/livestock/breeding")
public class LivestockBreedingController {

    private final LivestockBreedingService  breedingService;
    private final VeterinarianService       veterinarianService;
    private final LivestockRepository       livestockRepository;
    private final VeterinarianRepository    veterinarianRepository;
    private final MalesReadyToBreedService  malesReadyToBreedService;

    public LivestockBreedingController(LivestockBreedingService breedingService,
                                       VeterinarianService veterinarianService,
                                       LivestockRepository livestockRepository,
                                       VeterinarianRepository veterinarianRepository,
                                       MalesReadyToBreedService malesReadyToBreedService) {
        this.breedingService          = breedingService;
        this.veterinarianService      = veterinarianService;
        this.livestockRepository      = livestockRepository;
        this.veterinarianRepository   = veterinarianRepository;
        this.malesReadyToBreedService = malesReadyToBreedService;
    }

    // ── DASHBOARD ─────────────────────────────────────────────────────────────

    @GetMapping
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

    @GetMapping("/list")
    public String list(Model model) {
        model.addAttribute("breedings", breedingService.getAll());
        return "livestock-breeding-list";
    }

    // ── REGISTER FORM ─────────────────────────────────────────────────────────

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("breeding", new LivestockBreeding());
        addLivestockAndVetsToModel(model);
        return "livestock-breeding-form";
    }

    @PostMapping("/register")
    public String save(@Valid @ModelAttribute("breeding") LivestockBreeding breeding,
                       BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            addLivestockAndVetsToModel(model);
            return "livestock-breeding-form";
        }
        resolveRelations(breeding);

        // Validate same category if both animals selected
        if (breeding.getLivestock() != null && breeding.getMaleLivestock() != null) {
            String femaleCat = getCategoryName(breeding.getLivestock());
            String maleCat   = getCategoryName(breeding.getMaleLivestock());
            if (!femaleCat.equalsIgnoreCase(maleCat)) {
                result.rejectValue("maleLivestockIdValue", "category.mismatch",
                        "Male and female must be the same animal category (" + femaleCat + ")");
                addLivestockAndVetsToModel(model);
                return "livestock-breeding-form";
            }
        }

        breedingService.addNew(breeding);
        ra.addFlashAttribute("success", "Breeding record saved successfully!");
        return "redirect:/livestock/breeding";
    }

    // ── AJAX: Males by Category ────────────────────────────────────────────────
    /**
     * Returns males filtered by the same category as the selected female.
     * Called from JavaScript when the user picks a female animal.
     *
     * GET /livestock/breeding/males-by-category?categoryId=UUID
     */
    @GetMapping("/males-by-category")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getMalesByCategory(
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "femaleId",   required = false) UUID femaleId) {

        List<Livestock> activeMales = livestockRepository.findAll().stream()
                .filter(ls -> !Livestock.STATUS_DEAD.equals(ls.getStatus())
                        && !Livestock.STATUS_SOLD.equals(ls.getStatus()))
                .filter(ls -> "MALE".equalsIgnoreCase(ls.getGender()))
                .collect(Collectors.toList());

        // If a categoryId is provided, filter by it
        if (categoryId != null) {
            final UUID catId = categoryId;
            activeMales = activeMales.stream()
                    .filter(ls -> ls.getLivestockCategory() != null
                            && catId.equals(ls.getLivestockCategory().getId()))
                    .collect(Collectors.toList());
        }

        // If a specific female was picked, also exclude her from the male list (safety)
        if (femaleId != null) {
            final UUID fId = femaleId;
            activeMales = activeMales.stream()
                    .filter(ls -> !ls.getId().equals(fId))
                    .collect(Collectors.toList());
        }

        // Get eligible male IDs (12+ months) for UI hint
        Set<UUID> eligibleIds = malesReadyToBreedService.getAllReadyToBreed()
                .stream()
                .map(MaleReadyToBreedDTO::getId)
                .collect(Collectors.toSet());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Livestock male : activeMales) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",       male.getId().toString());
            m.put("tagNumber", male.getTagNumber());
            m.put("category", male.getLivestockCategory() != null ? male.getLivestockCategory().getName() : "");
            m.put("eligible", eligibleIds.contains(male.getId())); // true = 12+ months
            result.add(m);
        }

        return ResponseEntity.ok(result);
    }

    // ── EDIT ──────────────────────────────────────────────────────────────────

    @GetMapping("/edit/{id}")
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

    @PostMapping("/update/{id}")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("breeding") LivestockBreeding breeding,
                         BindingResult result, Model model, RedirectAttributes ra) {
        if (result.hasErrors()) {
            addLivestockAndVetsToModel(model);
            return "livestock-breeding-edit";
        }
        resolveRelations(breeding);

        // Validate same category
        if (breeding.getLivestock() != null && breeding.getMaleLivestock() != null) {
            String femaleCat = getCategoryName(breeding.getLivestock());
            String maleCat   = getCategoryName(breeding.getMaleLivestock());
            if (!femaleCat.equalsIgnoreCase(maleCat)) {
                result.rejectValue("maleLivestockIdValue", "category.mismatch",
                        "Male and female must be the same animal category (" + femaleCat + ")");
                addLivestockAndVetsToModel(model);
                return "livestock-breeding-edit";
            }
        }

        breedingService.update(id, breeding);
        ra.addFlashAttribute("success", "Breeding record updated successfully!");
        return "redirect:/livestock/breeding";
    }

    // ── VIEW ──────────────────────────────────────────────────────────────────

    @GetMapping("/view/{id}")
    public String view(@PathVariable UUID id, Model model) {
        Optional<LivestockBreeding> opt = breedingService.getById(id);
        if (opt.isEmpty()) return "redirect:/livestock/breeding";
        model.addAttribute("breeding", opt.get());
        return "livestock-breeding-view";
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            breedingService.delete(id);
            ra.addFlashAttribute("success", "Breeding record deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/livestock/breeding";
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private void addLivestockAndVetsToModel(Model model) {
        List<Livestock> all = livestockRepository.findAll().stream()
                .filter(ls -> !Livestock.STATUS_DEAD.equals(ls.getStatus())
                        && !Livestock.STATUS_SOLD.equals(ls.getStatus()))
                .collect(Collectors.toList());

        // Female animals
        List<Livestock> females = all.stream()
                .filter(ls -> "FEMALE".equalsIgnoreCase(ls.getGender()))
                .collect(Collectors.toList());
        if (females.isEmpty()) females = all;

        // ALL active male livestock — used as a fallback / initial load
        List<Livestock> allMaleLivestock = all.stream()
                .filter(ls -> "MALE".equalsIgnoreCase(ls.getGender()))
                .collect(Collectors.toList());

        // Eligible males (12+ months) for recommended UI hint
        List<MaleReadyToBreedDTO> eligibleMales = malesReadyToBreedService.getAllReadyToBreed();

        // Build a JSON-friendly map of categoryId -> [males] for client-side filtering
        // This avoids extra AJAX round-trips on initial page load
        Map<String, List<Map<String, Object>>> malesByCategory = new LinkedHashMap<>();
        for (Livestock male : allMaleLivestock) {
            String catKey = male.getLivestockCategory() != null
                    ? male.getLivestockCategory().getId().toString()
                    : "UNKNOWN";
            malesByCategory.computeIfAbsent(catKey, k -> new ArrayList<>())
                    .add(Map.of(
                            "id",       male.getId().toString(),
                            "tagNumber", male.getTagNumber(),
                            "category", male.getLivestockCategory() != null
                                    ? male.getLivestockCategory().getName() : ""
                    ));
        }

        model.addAttribute("femaleLivestock",   females);
        model.addAttribute("allMaleLivestock",  allMaleLivestock);   // ← FIXED: was "maleLivestock" mismatch
        model.addAttribute("eligibleMales",     eligibleMales);
        model.addAttribute("malesByCategoryJson", toJson(malesByCategory));
        model.addAttribute("vets",              veterinarianService.getActive());

        // Pass gestation days map for auto-calculating due dates per species
        model.addAttribute("gestationDaysJson", buildGestationJson());
    }

    private void resolveRelations(LivestockBreeding breeding) {
        String lsId = breeding.getLivestockIdValue();
        if (lsId != null && !lsId.trim().isEmpty()) {
            livestockRepository.findById(UUID.fromString(lsId))
                    .ifPresent(breeding::setLivestock);
        }

        String maleId = breeding.getMaleLivestockIdValue();
        if (maleId != null && !maleId.trim().isEmpty()) {
            livestockRepository.findById(UUID.fromString(maleId))
                    .ifPresent(breeding::setMaleLivestock);
        } else {
            breeding.setMaleLivestock(null);
        }

        String vetId = breeding.getVeterinarianIdValue();
        if (vetId != null && !vetId.trim().isEmpty()) {
            veterinarianRepository.findById(UUID.fromString(vetId))
                    .ifPresent(breeding::setVeterinarian);
        } else {
            breeding.setVeterinarian(null);
        }
    }

    private String getCategoryName(Livestock ls) {
        if (ls.getLivestockCategory() == null) return "UNKNOWN";
        return ls.getLivestockCategory().getName();
    }

    /** Minimal JSON serialiser (no external dependency needed) */
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

    /**
     * Typical gestation periods in days by animal category name (case-insensitive).
     * The frontend uses this to auto-populate the Expected Due Date field.
     */
    private String buildGestationJson() {
        return "{" +
                "\"COW\":283,\"CATTLE\":283,\"DAIRY COW\":283," +
                "\"GOAT\":150,\"SHEEP\":147," +
                "\"PIG\":114,\"SOW\":114," +
                "\"RABBIT\":31," +
                "\"HORSE\":340,\"MARE\":340," +
                "\"DONKEY\":365," +
                "\"DOG\":63,\"CAT\":65," +
                "\"DEFAULT\":283" +
                "}";
    }
}
