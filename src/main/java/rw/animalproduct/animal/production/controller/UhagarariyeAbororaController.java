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
import rw.animalproduct.animal.production.entity.UhagarariyeAborora;
import rw.animalproduct.animal.production.services.LocationService;
import rw.animalproduct.animal.production.services.UhagarariyeAbororaService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/uhagarariye")
public class UhagarariyeAbororaController {

    private final UhagarariyeAbororaService uhagarariyeAbororaService;
    private final LocationService locationService;

    @Autowired
    public UhagarariyeAbororaController(UhagarariyeAbororaService uhagarariyeAbororaService,
                                        LocationService locationService) {
        this.uhagarariyeAbororaService = uhagarariyeAbororaService;
        this.locationService = locationService;
    }

    @GetMapping("/list")
    public String listAll(Model model) {
        List<UhagarariyeAborora> list = uhagarariyeAbororaService.getAll();
        model.addAttribute("uhagarariyeList", list);
        return "uhagarariye-list";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        List<Location> locations = locationService.getAllLocations();
        model.addAttribute("locations", locations);
        model.addAttribute("uhagarariye", new UhagarariyeAborora());
        return "uhagarariye-register";
    }

    @PostMapping("/register/new")
    public String register(@Valid @ModelAttribute("uhagarariye") UhagarariyeAborora uhagarariye,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        // Check for duplicate NID
        Optional<UhagarariyeAborora> existingOpt = uhagarariyeAbororaService.getByNid(uhagarariye.getNid());
        if (existingOpt.isPresent()) {
            result.rejectValue("nid", "error.uhagarariye", "NID already exists");
        }

        // Additional validation for NID format (16 digits)
        if (uhagarariye.getNid() != null && !uhagarariye.getNid().matches("^[0-9]{16}$")) {
            result.rejectValue("nid", "error.uhagarariye", "National ID must be exactly 16 digits");
        }

        // Additional validation for phone format (10 digits)
        if (uhagarariye.getPhone() != null && !uhagarariye.getPhone().matches("^(078|079|072|073)[0-9]{7}$")) {
            result.rejectValue("phone", "error.uhagarariye", "Phone number must be 10 digits starting with 078, 079, 072, or 073");
        }

        // Email validation
        if (uhagarariye.getEmail() != null && !uhagarariye.getEmail().isEmpty()) {
            if (!uhagarariye.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                result.rejectValue("email", "error.uhagarariye", "Please provide a valid email address");
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
            return "uhagarariye-register";
        }

        uhagarariyeAbororaService.addNew(uhagarariye);
        redirectAttributes.addFlashAttribute("success", "Uhagarariye aborora registered successfully!");
        return "redirect:/uhagarariye/list";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") UUID id, Model model) {
        Optional<UhagarariyeAborora> uhagarariyeOpt = uhagarariyeAbororaService.getById(id);
        if (uhagarariyeOpt.isEmpty()) {
            return "redirect:/uhagarariye/list";
        }

        UhagarariyeAborora uhagarariye = uhagarariyeOpt.get();
        List<Location> locations = locationService.getAllLocations();

        model.addAttribute("uhagarariye", uhagarariye);
        model.addAttribute("locations", locations);

        // Declare variables OUTSIDE the if block
        Location parent = null;
        Location grandParent = null;
        Location greatGrandParent = null;
        Location greatGreatGrandParent = null;

        // FIXED: Load location hierarchy if location exists
        if (uhagarariye.getLocation() != null) {
            Location currentLocation = uhagarariye.getLocation();
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

        return "uhagarariye-edit";
    }

    @PostMapping("/update/{id}")
    public String update(@PathVariable("id") UUID id,
                         @Valid @ModelAttribute("uhagarariye") UhagarariyeAborora uhagarariye,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        // Check for duplicate NID (excluding current record)
        Optional<UhagarariyeAborora> existingOpt = uhagarariyeAbororaService.getByNid(uhagarariye.getNid());
        if (existingOpt.isPresent() && !existingOpt.get().getId().equals(id)) {
            result.rejectValue("nid", "error.uhagarariye", "NID already exists");
        }

        // Additional validation for NID format (16 digits)
        if (uhagarariye.getNid() != null && !uhagarariye.getNid().matches("^[0-9]{16}$")) {
            result.rejectValue("nid", "error.uhagarariye", "National ID must be exactly 16 digits");
        }

        // Additional validation for phone format (10 digits)
        if (uhagarariye.getPhone() != null && !uhagarariye.getPhone().matches("^(078|079|072|073)[0-9]{7}$")) {
            result.rejectValue("phone", "error.uhagarariye", "Phone number must be 10 digits starting with 078, 079, 072, or 073");
        }

        // Email validation
        if (uhagarariye.getEmail() != null && !uhagarariye.getEmail().isEmpty()) {
            if (!uhagarariye.getEmail().matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                result.rejectValue("email", "error.uhagarariye", "Please provide a valid email address");
            }
        }

        if (result.hasErrors()) {
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));

            model.addAttribute("error", errorMessages);
            List<Location> locations = locationService.getAllLocations();
            model.addAttribute("locations", locations);
            return "uhagarariye-edit";
        }

        uhagarariyeAbororaService.update(id, uhagarariye);
        redirectAttributes.addFlashAttribute("success", "Uhagarariye aborora updated successfully!");
        return "redirect:/uhagarariye/list";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") UUID id,
                         RedirectAttributes redirectAttributes) {
        uhagarariyeAbororaService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Uhagarariye aborora deleted successfully!");
        return "redirect:/uhagarariye/list";
    }

    @GetMapping("/view/{id}")
    public String viewDetails(@PathVariable("id") UUID id, Model model) {
        Optional<UhagarariyeAborora> uhagarariyeOpt = uhagarariyeAbororaService.getById(id);
        if (uhagarariyeOpt.isEmpty()) {
            return "redirect:/uhagarariye/list";
        }

        model.addAttribute("uhagarariye", uhagarariyeOpt.get());
        return "uhagarariye-view";
    }
}