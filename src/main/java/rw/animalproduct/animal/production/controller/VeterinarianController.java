package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Veterinarian;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.services.VeterinarianService;

import java.util.*;

@Controller
@RequestMapping("/veterinarians")
public class VeterinarianController {

    private final VeterinarianService veterinarianService;
    private final LocationRepository  locationRepository;

    public VeterinarianController(VeterinarianService veterinarianService,
                                  LocationRepository locationRepository) {
        this.veterinarianService = veterinarianService;
        this.locationRepository  = locationRepository;
    }

    // ── LIST ──────────────────────────────────────────────────────────────────

    @GetMapping
    public String list(Model model) {
        model.addAttribute("vets",        veterinarianService.getAll());
        model.addAttribute("vet",         new Veterinarian());
        model.addAttribute("locations",   locationRepository.findAll());
        model.addAttribute("activeCount", veterinarianService.getActive().size());
        return "veterinarians-list";
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @PostMapping("/new")
    public String save(@Valid @ModelAttribute("vet") Veterinarian vet,
                       BindingResult result,
                       Model model,
                       RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("vets",      veterinarianService.getAll());
            model.addAttribute("locations", locationRepository.findAll());
            return "veterinarians-list";
        }
        resolveLocation(vet);
        veterinarianService.addNew(vet);
        ra.addFlashAttribute("success", "Veterinarian registered successfully!");
        return "redirect:/veterinarians";
    }

    // ── EDIT FORM ─────────────────────────────────────────────────────────────

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable UUID id, Model model) {
        Optional<Veterinarian> opt = veterinarianService.getById(id);
        if (opt.isEmpty()) return "redirect:/veterinarians";

        Veterinarian vet = opt.get();
        if (vet.getLocation() != null) {
            vet.setLocationIdValue(vet.getLocation().getId().toString());
        }

        model.addAttribute("vet",       vet);
        model.addAttribute("locations", locationRepository.findAll());
        return "veterinarian-edit";
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @PostMapping("/update/{id}")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("vet") Veterinarian vet,
                         BindingResult result,
                         Model model,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("locations", locationRepository.findAll());
            return "veterinarian-edit";
        }
        resolveLocation(vet);
        veterinarianService.update(id, vet);
        ra.addFlashAttribute("success", "Veterinarian updated successfully!");
        return "redirect:/veterinarians";
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            veterinarianService.delete(id);
            ra.addFlashAttribute("success", "Veterinarian deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/veterinarians";
    }

    // ── TOGGLE ACTIVE ─────────────────────────────────────────────────────────

    @PostMapping("/toggle-active/{id}")
    public String toggleActive(@PathVariable UUID id, RedirectAttributes ra) {
        Optional<Veterinarian> opt = veterinarianService.getById(id);
        if (opt.isPresent()) {
            Veterinarian vet = opt.get();
            vet.setIsActive(!Boolean.TRUE.equals(vet.getIsActive()));
            veterinarianService.update(id, vet);
            ra.addFlashAttribute("success",
                    "Veterinarian " + (vet.getIsActive() ? "activated" : "deactivated") + ".");
        }
        return "redirect:/veterinarians";
    }

    // ── AJAX LIVE SEARCH ──────────────────────────────────────────────────────
    // Used by the treatment form's live-search widget.
    // Returns up to 20 active vets matching the query.

    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> search(
            @RequestParam(value = "q", required = false) String query) {

        List<Veterinarian> vets = veterinarianService.search(query);

        // Cap at 20 results for dropdown performance
        if (vets.size() > 20) {
            vets = vets.subList(0, 20);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Veterinarian v : vets) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id",             v.getId());
            map.put("fullName",       v.getFullName());
            map.put("displayName",    v.getDisplayName());
            map.put("phone",          v.getPhone());
            map.put("email",          v.getEmail());
            map.put("licenseNumber",  v.getLicenseNumber());
            map.put("specialization", v.getSpecialization());
            map.put("clinicName",     v.getClinicName());
            map.put("isActive",       v.getIsActive());
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    // ── AJAX QUICK-ADD ────────────────────────────────────────────────────────
    // Called from the "+" button on the treatment form when no vet is found.
    // Accepts { firstName, lastName, phone, licenseNumber } as JSON.

    @PostMapping("/quick-add")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> quickAdd(
            @RequestBody Map<String, String> payload) {

        Map<String, Object> response = new LinkedHashMap<>();

        String firstName = payload.getOrDefault("firstName", "").trim();
        String lastName  = payload.getOrDefault("lastName",  "").trim();

        if (firstName.isEmpty() || lastName.isEmpty()) {
            response.put("success", false);
            response.put("error", "First name and last name are required.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            Veterinarian vet = new Veterinarian();
            vet.setFirstName(firstName);
            vet.setLastName(lastName);
            vet.setPhone(payload.getOrDefault("phone", "").trim());
            vet.setLicenseNumber(payload.getOrDefault("licenseNumber", "").trim());
            vet.setIsActive(true);

            Veterinarian saved = veterinarianService.addNew(vet);

            Map<String, Object> vetMap = new LinkedHashMap<>();
            vetMap.put("id",            saved.getId());
            vetMap.put("fullName",      saved.getFullName());
            vetMap.put("licenseNumber", saved.getLicenseNumber());
            vetMap.put("phone",         saved.getPhone());

            response.put("success", true);
            response.put("vet", vetMap);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ── HELPER ────────────────────────────────────────────────────────────────

    private void resolveLocation(Veterinarian vet) {
        String locId = vet.getLocationIdValue();
        if (locId != null && !locId.trim().isEmpty()) {
            locationRepository.findById(UUID.fromString(locId))
                    .ifPresent(vet::setLocation);
        } else {
            vet.setLocation(null);
        }
    }
}
