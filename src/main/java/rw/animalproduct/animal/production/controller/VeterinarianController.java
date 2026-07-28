package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Veterinarian;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.services.AuditLogService;
import rw.animalproduct.animal.production.services.VeterinarianService;

import java.util.*;

@Controller
@RequestMapping("/veterinarians")
public class VeterinarianController {

    private final VeterinarianService veterinarianService;
    private final LocationRepository  locationRepository;
    private final AuditLogService     auditLogService;

    public VeterinarianController(VeterinarianService veterinarianService,
                                  LocationRepository locationRepository,
                                  AuditLogService auditLogService) {
        this.veterinarianService = veterinarianService;
        this.locationRepository  = locationRepository;
        this.auditLogService     = auditLogService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("vets",        veterinarianService.getAll());
        model.addAttribute("vet",         new Veterinarian());
        model.addAttribute("locations",   locationRepository.findAll());
        model.addAttribute("activeCount", veterinarianService.getActive().size());
        return "veterinarians-list";
    }

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
        Veterinarian saved = veterinarianService.addNew(vet);

        auditLogService.log(
                "veterinarian",
                saved.getId(),
                "CREATE",
                getCurrentUsername(),
                null,
                saved,
                "Registered veterinarian: " + saved.getFullName()
        );

        ra.addFlashAttribute("success", "Veterinarian registered successfully!");
        return "redirect:/veterinarians";
    }

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

        String beforeSnapshot = veterinarianService.getById(id)
                .map(auditLogService::snapshot)
                .orElse(null);

        resolveLocation(vet);
        veterinarianService.update(id, vet);

        Veterinarian after = veterinarianService.getById(id).orElse(null);

        auditLogService.log(
                "veterinarian",
                id,
                "UPDATE",
                getCurrentUsername(),
                beforeSnapshot,
                after,
                "Updated veterinarian: " + vet.getFirstName() + " " + vet.getLastName()
        );

        ra.addFlashAttribute("success", "Veterinarian updated successfully!");
        return "redirect:/veterinarians";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            Optional<Veterinarian> vetOpt = veterinarianService.getById(id);

            if (vetOpt.isPresent()) {
                Veterinarian vet = vetOpt.get();
                auditLogService.log(
                        "veterinarian",
                        id,
                        "DELETE",
                        getCurrentUsername(),
                        vet,
                        null,
                        "Deleted veterinarian: " + vet.getFullName()
                );
            }

            veterinarianService.delete(id);
            ra.addFlashAttribute("success", "Veterinarian deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/veterinarians";
    }

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

    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> search(
            @RequestParam(value = "q", required = false) String query) {

        List<Veterinarian> vets = veterinarianService.search(query);

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

            auditLogService.log(
                    "veterinarian",
                    saved.getId(),
                    "CREATE",
                    getCurrentUsername(),
                    null,
                    saved,
                    "Quick-added veterinarian: " + saved.getFullName()
            );

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

    private void resolveLocation(Veterinarian vet) {
        String locId = vet.getLocationIdValue();
        if (locId != null && !locId.trim().isEmpty()) {
            locationRepository.findById(UUID.fromString(locId))
                    .ifPresent(vet::setLocation);
        } else {
            vet.setLocation(null);
        }
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "system";
    }
}