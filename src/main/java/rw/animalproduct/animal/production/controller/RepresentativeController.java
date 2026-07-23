package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Beneficiary;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.entity.Representative;
import rw.animalproduct.animal.production.services.AuditLogService;
import rw.animalproduct.animal.production.services.BeneficiaryService;
import rw.animalproduct.animal.production.services.LocationService;
import rw.animalproduct.animal.production.services.RepresentativeService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/representatives")
public class RepresentativeController {

    private final RepresentativeService representativesAbororaService;
    private final LocationService locationService;
    private final BeneficiaryService beneficiaryService;
    private final AuditLogService auditLogService;

    @Autowired
    public RepresentativeController(RepresentativeService representativesAbororaService,
                                    LocationService locationService,
                                    BeneficiaryService beneficiaryService,
                                    AuditLogService auditLogService) {
        this.representativesAbororaService = representativesAbororaService;
        this.locationService = locationService;
        this.beneficiaryService = beneficiaryService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/list")
    public String listAll(Model model) {
        List<Representative> list = representativesAbororaService.getAll();
        Map<UUID, Long> beneficiaryCounts = beneficiaryService.getBeneficiaryCountsByRepresentative();

        model.addAttribute("representativesList", list);
        model.addAttribute("beneficiaryCounts", beneficiaryCounts);
        return "representatives-list";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        List<Location> locations = locationService.getAllLocations();
        model.addAttribute("locations", locations);
        model.addAttribute("representatives", new Representative());
        return "representatives-register";
    }

    // ── CREATE (now logged) ─────────────────────────────────────────────────
    @PostMapping("/register/new")
    public String register(@Valid @ModelAttribute("representatives") Representative representatives,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        Optional<Representative> existingOpt = representativesAbororaService.getByNid(representatives.getNid());
        if (existingOpt.isPresent()) {
            result.rejectValue("nid", "error.representatives", "NID already exists");
        }

        if (representatives.getNid() != null && !representatives.getNid().matches("^[0-9]{16}$")) {
            result.rejectValue("nid", "error.representatives", "National ID must be exactly 16 digits");
        }

        if (representatives.getPhone() != null && !representatives.getPhone().matches("^(078|079|072|073)[0-9]{7}$")) {
            result.rejectValue("phone", "error.representatives", "Phone number must be 10 digits starting with 078, 079, 072, or 073");
        }

        if (representatives.getEmail() != null && !representatives.getEmail().isEmpty()) {
            if (!representatives.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                result.rejectValue("email", "error.representatives", "Please provide a valid email address");
            }
        }

        if (result.hasErrors()) {
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));

            model.addAttribute("error", errorMessages);
            List<Location> locations = locationService.getAllLocations();
            model.addAttribute("locations", locations);
            return "representatives-register";
        }

        Representative saved = representativesAbororaService.addNew(representatives);

        auditLogService.log(
                "representative",
                saved.getId(),
                "CREATE",
                getCurrentUsername(),
                null,
                saved,
                "Registered representative: " + saved.getFirstName() + " " + saved.getLastName()
        );

        redirectAttributes.addFlashAttribute("success", "Uhagarariye aborora registered successfully!");
        return "redirect:/representatives/list";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") UUID id, Model model) {
        Optional<Representative> representativesOpt = representativesAbororaService.getById(id);
        if (representativesOpt.isEmpty()) {
            return "redirect:/representatives/list";
        }

        Representative representatives = representativesOpt.get();
        List<Location> locations = locationService.getAllLocations();

        model.addAttribute("representatives", representatives);
        model.addAttribute("locations", locations);

        Location parent = null;
        Location grandParent = null;
        Location greatGrandParent = null;
        Location greatGreatGrandParent = null;

        if (representatives.getLocation() != null) {
            Location currentLocation = representatives.getLocation();
            model.addAttribute("selectedVillage", currentLocation.getId());

            parent = currentLocation.getParent();
            if (parent != null) {
                model.addAttribute("selectedCell", parent.getId());

                grandParent = parent.getParent();
                if (grandParent != null) {
                    model.addAttribute("selectedSector", grandParent.getId());

                    greatGrandParent = grandParent.getParent();
                    if (greatGrandParent != null) {
                        model.addAttribute("selectedDistrict", greatGrandParent.getId());

                        greatGreatGrandParent = greatGrandParent.getParent();
                        if (greatGreatGrandParent != null) {
                            model.addAttribute("selectedProvince", greatGreatGrandParent.getId());
                        }
                    }
                }
            }
        }

        model.addAttribute("provinces", locationService.getLocationsByType("PROVINCE"));

        if (greatGrandParent != null) {
            model.addAttribute("districts", locationService.getChildLocations(greatGrandParent.getId()));
        }
        if (grandParent != null) {
            model.addAttribute("sectors", locationService.getChildLocations(grandParent.getId()));
        }
        if (parent != null) {
            model.addAttribute("cells", locationService.getChildLocations(parent.getId()));
        }
        if (parent != null) {
            model.addAttribute("villages", locationService.getChildLocations(parent.getId()));
        }

        return "representatives-edit";
    }

    // ── UPDATE (now logged) ─────────────────────────────────────────────────
    @PostMapping("/update/{id}")
    public String update(@PathVariable("id") UUID id,
                         @Valid @ModelAttribute("representatives") Representative representatives,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        Optional<Representative> existingOpt = representativesAbororaService.getByNid(representatives.getNid());
        if (existingOpt.isPresent() && !existingOpt.get().getId().equals(id)) {
            result.rejectValue("nid", "error.representatives", "NID already exists");
        }

        if (representatives.getNid() != null && !representatives.getNid().matches("^[0-9]{16}$")) {
            result.rejectValue("nid", "error.representatives", "National ID must be exactly 16 digits");
        }

        if (representatives.getPhone() != null && !representatives.getPhone().matches("^(078|079|072|073)[0-9]{7}$")) {
            result.rejectValue("phone", "error.representatives", "Phone number must be 10 digits starting with 078, 079, 072, or 073");
        }

        if (representatives.getEmail() != null && !representatives.getEmail().isEmpty()) {
            if (!representatives.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                result.rejectValue("email", "error.representatives", "Please provide a valid email address");
            }
        }

        if (result.hasErrors()) {
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));

            model.addAttribute("error", errorMessages);
            List<Location> locations = locationService.getAllLocations();
            model.addAttribute("locations", locations);
            return "representatives-edit";
        }

        // Capture "before" as a JSON string NOW, before update() mutates the managed entity
        String beforeSnapshot = representativesAbororaService.getById(id)
                .map(auditLogService::snapshot)
                .orElse(null);

        Representative updated = representativesAbororaService.update(id, representatives);

        auditLogService.log(
                "representative",
                id,
                "UPDATE",
                getCurrentUsername(),
                beforeSnapshot,
                updated,
                "Updated representative: " + representatives.getFirstName() + " " + representatives.getLastName()
        );

        redirectAttributes.addFlashAttribute("success", "Uhagarariye aborora updated successfully!");
        return "redirect:/representatives/list";
    }

    // ── DELETE ────────────────────────────────────────────────────────────
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") UUID id,
                         RedirectAttributes redirectAttributes) {

        Optional<Representative> representativeOpt = representativesAbororaService.getById(id);

        if (representativeOpt.isPresent()) {
            Representative representative = representativeOpt.get();

            auditLogService.log(
                    "representative",
                    id,
                    "DELETE",
                    getCurrentUsername(),
                    representative,
                    null,
                    "Deleted representative: " + representative.getFirstName() + " " + representative.getLastName()
            );
        }

        representativesAbororaService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Uhagarariye aborora deleted successfully!");
        return "redirect:/representatives/list";
    }

    @GetMapping("/view/{id}")
    public String viewDetails(@PathVariable("id") UUID id,
                              @RequestParam(value = "page", defaultValue = "0") int page,
                              @RequestParam(value = "size", defaultValue = "5") int size,
                              Model model) {
        Optional<Representative> representativesOpt = representativesAbororaService.getById(id);
        if (representativesOpt.isEmpty()) {
            return "redirect:/representatives/list";
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("firstName").ascending());
        Page<Beneficiary> beneficiariesPage = beneficiaryService.getByUhagarariyePaginated(id, pageable);

        model.addAttribute("representatives", representativesOpt.get());
        model.addAttribute("beneficiariesList", beneficiariesPage.getContent());
        model.addAttribute("beneficiariesCount", beneficiariesPage.getTotalElements());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", beneficiariesPage.getTotalPages());
        model.addAttribute("pageSize", size);
        return "representatives-view";
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "system";
    }
}