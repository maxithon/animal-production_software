package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.AbaragizwaAmatungo;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.entity.UhagarariyeAborora;
import rw.animalproduct.animal.production.repository.AbaragizwaAmatungoRepository;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.services.AbaragizwaAmatungoService;
import rw.animalproduct.animal.production.services.UhagarariyeAbororaService;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/abaragizwa")
public class AbaragizwaAmatungoController {

    private final AbaragizwaAmatungoService abaragizwaAmatungoService;
    private final UhagarariyeAbororaService uhagarariyeAbororaService;
    private final LocationRepository locationRepository;
    private final AbaragizwaAmatungoRepository abaragizwaAmatungoRepository;

    @Autowired
    public AbaragizwaAmatungoController(AbaragizwaAmatungoService abaragizwaAmatungoService,
                                        UhagarariyeAbororaService uhagarariyeAbororaService,
                                        LocationRepository locationRepository,
                                        AbaragizwaAmatungoRepository abaragizwaAmatungoRepository) {
        this.abaragizwaAmatungoService = abaragizwaAmatungoService;
        this.uhagarariyeAbororaService = uhagarariyeAbororaService;
        this.locationRepository = locationRepository;
        this.abaragizwaAmatungoRepository = abaragizwaAmatungoRepository;
    }

    @GetMapping("/list")
    public String listAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sort", defaultValue = "createdDate") String sort,
            Model model) {

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, sort);
            Page<AbaragizwaAmatungo> pageContent = abaragizwaAmatungoRepository.findAll(pageable);

            model.addAttribute("abaragizwaList", pageContent.getContent());
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", pageContent.getTotalPages());
            model.addAttribute("totalItems", pageContent.getTotalElements());
            model.addAttribute("pageSize", size);
        } catch (Exception e) {
            // Fallback to non-paginated list if error
            List<AbaragizwaAmatungo> list = abaragizwaAmatungoService.getAll();
            model.addAttribute("abaragizwaList", list);
            model.addAttribute("totalItems", list.size());
            model.addAttribute("totalPages", 1);
            model.addAttribute("currentPage", 0);
            model.addAttribute("pageSize", 10);
        }

        return "abaragizwa-list";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        List<UhagarariyeAborora> uhagarariyeList = uhagarariyeAbororaService.getAll();
        List<Location> locationList = locationRepository.findAll();

        model.addAttribute("uhagarariyeList", uhagarariyeList);
        model.addAttribute("locationList", locationList);
        model.addAttribute("abaragizwa", new AbaragizwaAmatungo());
        return "abaragizwa-register";
    }

    @PostMapping("/register/new")
    public String register(@Valid @ModelAttribute("abaragizwa") AbaragizwaAmatungo abaragizwa,
                           @RequestParam(value = "locationId", required = false) UUID locationId,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        // Check for duplicate NID
        Optional<AbaragizwaAmatungo> existingOpt = abaragizwaAmatungoService.getByNid(abaragizwa.getNid());
        if (existingOpt.isPresent()) {
            result.rejectValue("nid", "error.abaragizwa", "NID already exists");
        }

        // Additional validation for NID format
        if (abaragizwa.getNid() != null && !abaragizwa.getNid().matches("^[0-9]{16}$")) {
            result.rejectValue("nid", "error.abaragizwa", "National ID must be exactly 16 digits");
        }

        // Additional validation for phone format
        if (abaragizwa.getPhone() != null && !abaragizwa.getPhone().matches("^(078|079|072|073)[0-9]{7}$")) {
            result.rejectValue("phone", "error.abaragizwa", "Phone number must be 10 digits starting with 078, 079, 072, or 073");
        }

        // Validate location is selected
        if (locationId == null) {
            result.reject("error.location", "Location is required");
        }

        // Validate representative is selected
        if (abaragizwa.getUhagarariyeAbororaIdValue() == null || abaragizwa.getUhagarariyeAbororaIdValue().trim().isEmpty()) {
            result.rejectValue("uhagarariyeAbororaIdValue", "error.abaragizwa", "Representative is required");
        }

        // FIXED: Validate PDF is provided
        if (abaragizwa.getAmasezerano() == null || abaragizwa.getAmasezerano().trim().isEmpty()) {
            result.reject("error.amasezerano", "Contract PDF is required");
        }

        // Check if there are validation errors
        if (result.hasErrors()) {
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));

            model.addAttribute("error", errorMessages);
            List<UhagarariyeAborora> uhagarariyeList = uhagarariyeAbororaService.getAll();
            List<Location> locationList = locationRepository.findAll();
            model.addAttribute("uhagarariyeList", uhagarariyeList);
            model.addAttribute("locationList", locationList);
            return "abaragizwa-register";
        }

        try {
            // Set the representative
            if (abaragizwa.getUhagarariyeAbororaIdValue() != null && !abaragizwa.getUhagarariyeAbororaIdValue().trim().isEmpty()) {
                UhagarariyeAborora representative = uhagarariyeAbororaService.getById(UUID.fromString(abaragizwa.getUhagarariyeAbororaIdValue()))
                        .orElseThrow(() -> new IllegalArgumentException("Representative not found"));
                abaragizwa.setUhagarariyeAborora(representative);
            }

            // Set the location
            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new IllegalArgumentException("Location not found"));
            abaragizwa.setLocation(location);

            // Set audit fields
            abaragizwa.setCreatedDate(new Date());

            abaragizwaAmatungoService.addNew(abaragizwa);
            redirectAttributes.addFlashAttribute("success", "Abaragizwa amatungo registered successfully!");
            return "redirect:/abaragizwa/list";
        } catch (Exception e) {
            model.addAttribute("error", "Error registering beneficiary: " + e.getMessage());
            List<UhagarariyeAborora> uhagarariyeList = uhagarariyeAbororaService.getAll();
            List<Location> locationList = locationRepository.findAll();
            model.addAttribute("uhagarariyeList", uhagarariyeList);
            model.addAttribute("locationList", locationList);
            return "abaragizwa-register";
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") UUID id, Model model) {
        Optional<AbaragizwaAmatungo> abaragizwaOpt = abaragizwaAmatungoService.getById(id);
        if (abaragizwaOpt.isEmpty()) {
            return "redirect:/abaragizwa/list";
        }

        List<UhagarariyeAborora> uhagarariyeList = uhagarariyeAbororaService.getAll();
        List<Location> locationList = locationRepository.findAll();

        AbaragizwaAmatungo abaragizwa = abaragizwaOpt.get();

        // FIXED: Ensure photo and amasezerano are properly set
        if (abaragizwa.getUhagarariyeAborora() != null) {
            abaragizwa.setUhagarariyeAbororaIdValue(abaragizwa.getUhagarariyeAborora().getId().toString());
        }

        // Log file URLs for debugging
        if (abaragizwa.getPhoto() != null && !abaragizwa.getPhoto().isEmpty()) {
            System.out.println("DEBUG: Photo URL: " + abaragizwa.getPhoto());
        }

        if (abaragizwa.getAmasezerano() != null && !abaragizwa.getAmasezerano().isEmpty()) {
            System.out.println("DEBUG: PDF URL: " + abaragizwa.getAmasezerano());
        }

        model.addAttribute("abaragizwa", abaragizwa);
        model.addAttribute("uhagarariyeList", uhagarariyeList);
        model.addAttribute("locationList", locationList);
        return "abaragizwa-edit";
    }

    @PostMapping("/update/{id}")
    public String update(@PathVariable("id") UUID id,
                         @Valid @ModelAttribute("abaragizwa") AbaragizwaAmatungo abaragizwa,
                         @RequestParam(value = "locationId", required = false) UUID locationId,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        // Check for duplicate NID (excluding current record)
        Optional<AbaragizwaAmatungo> existingOpt = abaragizwaAmatungoService.getByNid(abaragizwa.getNid());
        if (existingOpt.isPresent() && !existingOpt.get().getId().equals(id)) {
            result.rejectValue("nid", "error.abaragizwa", "NID already exists");
        }

        // Additional validation for NID format
        if (abaragizwa.getNid() != null && !abaragizwa.getNid().matches("^[0-9]{16}$")) {
            result.rejectValue("nid", "error.abaragizwa", "National ID must be exactly 16 digits");
        }

        // Additional validation for phone format
        if (abaragizwa.getPhone() != null && !abaragizwa.getPhone().matches("^(078|079|072|073)[0-9]{7}$")) {
            result.rejectValue("phone", "error.abaragizwa", "Phone number must be 10 digits starting with 078, 079, 072, or 073");
        }

        // Validate location is selected
        if (locationId == null) {
            result.reject("error.location", "Location is required");
        }

        // Validate representative is selected
        if (abaragizwa.getUhagarariyeAbororaIdValue() == null || abaragizwa.getUhagarariyeAbororaIdValue().trim().isEmpty()) {
            result.rejectValue("uhagarariyeAbororaIdValue", "error.abaragizwa", "Representative is required");
        }

        if (result.hasErrors()) {
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));

            model.addAttribute("error", errorMessages);
            List<UhagarariyeAborora> uhagarariyeList = uhagarariyeAbororaService.getAll();
            List<Location> locationList = locationRepository.findAll();
            model.addAttribute("uhagarariyeList", uhagarariyeList);
            model.addAttribute("locationList", locationList);
            return "abaragizwa-edit";
        }

        try {
            // Set the representative
            if (abaragizwa.getUhagarariyeAbororaIdValue() != null && !abaragizwa.getUhagarariyeAbororaIdValue().trim().isEmpty()) {
                UhagarariyeAborora representative = uhagarariyeAbororaService.getById(UUID.fromString(abaragizwa.getUhagarariyeAbororaIdValue()))
                        .orElseThrow(() -> new IllegalArgumentException("Representative not found"));
                abaragizwa.setUhagarariyeAborora(representative);
            }

            // Set the location
            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new IllegalArgumentException("Location not found"));
            abaragizwa.setLocation(location);

            abaragizwaAmatungoService.update(id, abaragizwa);
            redirectAttributes.addFlashAttribute("success", "Abaragizwa amatungo updated successfully!");
            return "redirect:/abaragizwa/list";
        } catch (Exception e) {
            model.addAttribute("error", "Error updating beneficiary: " + e.getMessage());
            List<UhagarariyeAborora> uhagarariyeList = uhagarariyeAbororaService.getAll();
            List<Location> locationList = locationRepository.findAll();
            model.addAttribute("uhagarariyeList", uhagarariyeList);
            model.addAttribute("locationList", locationList);
            return "abaragizwa-edit";
        }
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") UUID id,
                         RedirectAttributes redirectAttributes) {
        abaragizwaAmatungoService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Abaragizwa amatungo deleted successfully!");
        return "redirect:/abaragizwa/list";
    }

    @GetMapping("/view/{id}")
    public String viewDetails(@PathVariable("id") UUID id, Model model) {
        Optional<AbaragizwaAmatungo> abaragizwaOpt = abaragizwaAmatungoService.getById(id);
        if (abaragizwaOpt.isEmpty()) {
            return "redirect:/abaragizwa/list";
        }

        model.addAttribute("abaragizwa", abaragizwaOpt.get());
        return "abaragizwa-view";
    }
}