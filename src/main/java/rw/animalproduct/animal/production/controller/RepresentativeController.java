package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.entity.Representative;
import rw.animalproduct.animal.production.services.LocationService;
import rw.animalproduct.animal.production.services.RepresentativeService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/representatives")
public class RepresentativeController {

    private final RepresentativeService representativesAbororaService;
    private final LocationService locationService;

    @Autowired
    public RepresentativeController(RepresentativeService representativesAbororaService,
                                    LocationService locationService) {
        this.representativesAbororaService = representativesAbororaService;
        this.locationService = locationService;
    }

    @GetMapping("/list")
    public String listAll(Model model) {
        List<Representative> list = representativesAbororaService.getAll();
        model.addAttribute("representativesList", list);
        return "representatives-list";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        List<Location> locations = locationService.getAllLocations();
        model.addAttribute("locations", locations);
        model.addAttribute("representatives", new Representative());
        return "representatives-register";
    }

    @PostMapping("/register/new")
    public String register(@Valid @ModelAttribute("representatives") Representative representatives,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        // Check for duplicate NID
        Optional<Representative> existingOpt = representativesAbororaService.getByNid(representatives.getNid());
        if (existingOpt.isPresent()) {
            result.rejectValue("nid", "error.representatives", "NID already exists");
        }

        // Additional validation for NID format (16 digits)
        if (representatives.getNid() != null && !representatives.getNid().matches("^[0-9]{16}$")) {
            result.rejectValue("nid", "error.representatives", "National ID must be exactly 16 digits");
        }

        // Additional validation for phone format (10 digits)
        if (representatives.getPhone() != null && !representatives.getPhone().matches("^(078|079|072|073)[0-9]{7}$")) {
            result.rejectValue("phone", "error.representatives", "Phone number must be 10 digits starting with 078, 079, 072, or 073");
        }

        // Email validation
        if (representatives.getEmail() != null && !representatives.getEmail().isEmpty()) {
            if (!representatives.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                result.rejectValue("email", "error.representatives", "Please provide a valid email address");
            }
        }

        // Check if there are validation errors
        if (result.hasErrors()) {
            // Collect all error messages
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));

            model.addAttribute("error", errorMessages);
            List<Location> locations = locationService.getAllLocations();
            model.addAttribute("locations", locations);
            return "representatives-register";
        }

        representativesAbororaService.addNew(representatives);
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

        // Declare variables OUTSIDE the if block
        Location parent = null;
        Location grandParent = null;
        Location greatGrandParent = null;
        Location greatGreatGrandParent = null;

        // FIXED: Load location hierarchy if location exists
        if (representatives.getLocation() != null) {
            Location currentLocation = representatives.getLocation();
            model.addAttribute("selectedVillage", currentLocation.getId());

            // Load parent locations hierarchically
            parent = currentLocation.getParent();
            if (parent != null) {
                // Parent is Cell
                model.addAttribute("selectedCell", parent.getId());

                grandParent = parent.getParent();
                if (grandParent != null) {
                    // GrandParent is Sector
                    model.addAttribute("selectedSector", grandParent.getId());

                    greatGrandParent = grandParent.getParent();
                    if (greatGrandParent != null) {
                        // GreatGrandParent is District
                        model.addAttribute("selectedDistrict", greatGrandParent.getId());

                        greatGreatGrandParent = greatGrandParent.getParent();
                        if (greatGreatGrandParent != null) {
                            // GreatGreatGrandParent is Province
                            model.addAttribute("selectedProvince", greatGreatGrandParent.getId());
                        }
                    }
                }
            }
        }

        // Pre-load the hierarchy levels - Now these variables exist!
        model.addAttribute("provinces", locationService.getLocationsByType("PROVINCE"));

        // Load districts for selected province
        if (greatGrandParent != null) {
            model.addAttribute("districts", locationService.getChildLocations(greatGrandParent.getId()));
        }

        // Load sectors for selected district
        if (grandParent != null) {
            model.addAttribute("sectors", locationService.getChildLocations(grandParent.getId()));
        }

        // Load cells for selected sector
        if (parent != null) {
            model.addAttribute("cells", locationService.getChildLocations(parent.getId()));
        }

        // Load villages for selected cell
        if (parent != null) {
            model.addAttribute("villages", locationService.getChildLocations(parent.getId()));
        }

        return "representatives-edit";
    }

    @PostMapping("/update/{id}")
    public String update(@PathVariable("id") UUID id,
                         @Valid @ModelAttribute("representatives") Representative representatives,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        // Check for duplicate NID (excluding current record)
        Optional<Representative> existingOpt = representativesAbororaService.getByNid(representatives.getNid());
        if (existingOpt.isPresent() && !existingOpt.get().getId().equals(id)) {
            result.rejectValue("nid", "error.representatives", "NID already exists");
        }

        // Additional validation for NID format (16 digits)
        if (representatives.getNid() != null && !representatives.getNid().matches("^[0-9]{16}$")) {
            result.rejectValue("nid", "error.representatives", "National ID must be exactly 16 digits");
        }

        // Additional validation for phone format (10 digits)
        if (representatives.getPhone() != null && !representatives.getPhone().matches("^(078|079|072|073)[0-9]{7}$")) {
            result.rejectValue("phone", "error.representatives", "Phone number must be 10 digits starting with 078, 079, 072, or 073");
        }

        // Email validation
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

        representativesAbororaService.update(id, representatives);
        redirectAttributes.addFlashAttribute("success", "Uhagarariye aborora updated successfully!");
        return "redirect:/representatives/list";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") UUID id,
                         RedirectAttributes redirectAttributes) {
        representativesAbororaService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Uhagarariye aborora deleted successfully!");
        return "redirect:/representatives/list";
    }

    @GetMapping("/view/{id}")
    public String viewDetails(@PathVariable("id") UUID id, Model model) {
        Optional<Representative> representativesOpt = representativesAbororaService.getById(id);
        if (representativesOpt.isEmpty()) {
            return "redirect:/representatives/list";
        }

        model.addAttribute("representatives", representativesOpt.get());
        return "representatives-view";
    }
}