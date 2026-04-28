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
import rw.animalproduct.animal.production.entity.Beneficiary;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.entity.Representative;
import rw.animalproduct.animal.production.repository.BeneficiaryRepository;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.services.BeneficiaryService;
import rw.animalproduct.animal.production.services.RepresentativeService;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService beneficiariesAmatungoService;
    private final RepresentativeService representativesAbororaService;
    private final LocationRepository locationRepository;
    private final BeneficiaryRepository beneficiaryRepository;

    @Autowired
    public BeneficiaryController(BeneficiaryService beneficiariesAmatungoService,
                                 RepresentativeService representativesAbororaService,
                                 LocationRepository locationRepository,
                                 BeneficiaryRepository beneficiaryRepository) {
        this.beneficiariesAmatungoService = beneficiariesAmatungoService;
        this.representativesAbororaService = representativesAbororaService;
        this.locationRepository = locationRepository;
        this.beneficiaryRepository = beneficiaryRepository;
    }

    @GetMapping("/list")
    public String listAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sort", defaultValue = "createdDate") String sort,
            Model model) {

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, sort);
            Page<Beneficiary> pageContent = beneficiaryRepository.findAll(pageable);

            model.addAttribute("beneficiariesList", pageContent.getContent());
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", pageContent.getTotalPages());
            model.addAttribute("totalItems", pageContent.getTotalElements());
            model.addAttribute("pageSize", size);
        } catch (Exception e) {
            // Fallback to non-paginated list if error
            List<Beneficiary> list = beneficiariesAmatungoService.getAll();
            model.addAttribute("beneficiariesList", list);
            model.addAttribute("totalItems", list.size());
            model.addAttribute("totalPages", 1);
            model.addAttribute("currentPage", 0);
            model.addAttribute("pageSize", 10);
        }

        return "beneficiaries-list";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        List<Representative> representativesList = representativesAbororaService.getAll();
        List<Location> locationList = locationRepository.findAll();

        model.addAttribute("representativesList", representativesList);
        model.addAttribute("locationList", locationList);
        model.addAttribute("beneficiaries", new Beneficiary());
        return "beneficiaries-register";
    }

    @PostMapping("/register/new")
    public String register(@Valid @ModelAttribute("beneficiaries") Beneficiary beneficiaries,
                           @RequestParam(value = "locationId", required = false) UUID locationId,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        // Check for duplicate NID
        Optional<Beneficiary> existingOpt = beneficiariesAmatungoService.getByNid(beneficiaries.getNid());
        if (existingOpt.isPresent()) {
            result.rejectValue("nid", "error.beneficiaries", "NID already exists");
        }

        // Additional validation for NID format
        if (beneficiaries.getNid() != null && !beneficiaries.getNid().matches("^[0-9]{16}$")) {
            result.rejectValue("nid", "error.beneficiaries", "National ID must be exactly 16 digits");
        }

        // Additional validation for phone format
        if (beneficiaries.getPhone() != null && !beneficiaries.getPhone().matches("^(078|079|072|073)[0-9]{7}$")) {
            result.rejectValue("phone", "error.beneficiaries", "Phone number must be 10 digits starting with 078, 079, 072, or 073");
        }

        // Validate location is selected
        if (locationId == null) {
            result.reject("error.location", "Location is required");
        }

        // Validate representative is selected
        if (beneficiaries.getRepresentativeIdValue() == null || beneficiaries.getRepresentativeIdValue().trim().isEmpty()) {
            result.rejectValue("representativeIdValue", "error.beneficiaries", "Representative is required");
        }

        // ✅ FIXED: Validate contract agreement is provided (was getAmasezerano)
        if (beneficiaries.getContractAgreement() == null || beneficiaries.getContractAgreement().trim().isEmpty()) {
            result.reject("error.contractAgreement", "Contract PDF is required");
        }

        // Check if there are validation errors
        if (result.hasErrors()) {
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));

            model.addAttribute("error", errorMessages);
            List<Representative> representativesList = representativesAbororaService.getAll();
            List<Location> locationList = locationRepository.findAll();
            model.addAttribute("representativesList", representativesList);
            model.addAttribute("locationList", locationList);
            return "beneficiaries-register";
        }

        try {
            // Set the representative
            if (beneficiaries.getRepresentativeIdValue() != null && !beneficiaries.getRepresentativeIdValue().trim().isEmpty()) {
                Representative representative = representativesAbororaService.getById(UUID.fromString(beneficiaries.getRepresentativeIdValue()))
                        .orElseThrow(() -> new IllegalArgumentException("Representative not found"));
                beneficiaries.setRepresentative(representative);
            }

            // Set the location
            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new IllegalArgumentException("Location not found"));
            beneficiaries.setLocation(location);

            // Set audit fields
            beneficiaries.setCreatedDate(new Date());

            beneficiariesAmatungoService.addNew(beneficiaries);
            redirectAttributes.addFlashAttribute("success", "Abaragizwa amatungo registered successfully!");
            return "redirect:/beneficiaries/list";
        } catch (Exception e) {
            model.addAttribute("error", "Error registering beneficiary: " + e.getMessage());
            List<Representative> representativesList = representativesAbororaService.getAll();
            List<Location> locationList = locationRepository.findAll();
            model.addAttribute("representativesList", representativesList);
            model.addAttribute("locationList", locationList);
            return "beneficiaries-register";
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable("id") UUID id, Model model) {
        Optional<Beneficiary> beneficiariesOpt = beneficiariesAmatungoService.getById(id);
        if (beneficiariesOpt.isEmpty()) {
            return "redirect:/beneficiaries/list";
        }

        List<Representative> representativesList = representativesAbororaService.getAll();
        List<Location> locationList = locationRepository.findAll();

        Beneficiary beneficiaries = beneficiariesOpt.get();

        // Ensure representative ID value is properly set
        if (beneficiaries.getRepresentative() != null) {
            beneficiaries.setRepresentativeIdValue(beneficiaries.getRepresentative().getId().toString());
        }

        // ✅ FIXED: Log file URLs for debugging (was getAmasezerano)
        if (beneficiaries.getPhoto() != null && !beneficiaries.getPhoto().isEmpty()) {
            System.out.println("DEBUG: Photo URL: " + beneficiaries.getPhoto());
        }

        if (beneficiaries.getContractAgreement() != null && !beneficiaries.getContractAgreement().isEmpty()) {
            System.out.println("DEBUG: PDF URL: " + beneficiaries.getContractAgreement());
        }

        model.addAttribute("beneficiaries", beneficiaries);
        model.addAttribute("representativesList", representativesList);
        model.addAttribute("locationList", locationList);
        return "beneficiaries-edit";
    }

    @PostMapping("/update/{id}")
    public String update(@PathVariable("id") UUID id,
                         @Valid @ModelAttribute("beneficiaries") Beneficiary beneficiaries,
                         @RequestParam(value = "locationId", required = false) UUID locationId,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        // Check for duplicate NID (excluding current record)
        Optional<Beneficiary> existingOpt = beneficiariesAmatungoService.getByNid(beneficiaries.getNid());
        if (existingOpt.isPresent() && !existingOpt.get().getId().equals(id)) {
            result.rejectValue("nid", "error.beneficiaries", "NID already exists");
        }

        // Additional validation for NID format
        if (beneficiaries.getNid() != null && !beneficiaries.getNid().matches("^[0-9]{16}$")) {
            result.rejectValue("nid", "error.beneficiaries", "National ID must be exactly 16 digits");
        }

        // Additional validation for phone format
        if (beneficiaries.getPhone() != null && !beneficiaries.getPhone().matches("^(078|079|072|073)[0-9]{7}$")) {
            result.rejectValue("phone", "error.beneficiaries", "Phone number must be 10 digits starting with 078, 079, 072, or 073");
        }

        // Validate location is selected
        if (locationId == null) {
            result.reject("error.location", "Location is required");
        }

        // Validate representative is selected
        if (beneficiaries.getRepresentativeIdValue() == null || beneficiaries.getRepresentativeIdValue().trim().isEmpty()) {
            result.rejectValue("representativeIdValue", "error.beneficiaries", "Representative is required");
        }

        if (result.hasErrors()) {
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));

            model.addAttribute("error", errorMessages);
            List<Representative> representativesList = representativesAbororaService.getAll();
            List<Location> locationList = locationRepository.findAll();
            model.addAttribute("representativesList", representativesList);
            model.addAttribute("locationList", locationList);
            return "beneficiaries-edit";
        }

        try {
            // Set the representative
            if (beneficiaries.getRepresentativeIdValue() != null && !beneficiaries.getRepresentativeIdValue().trim().isEmpty()) {
                Representative representative = representativesAbororaService.getById(UUID.fromString(beneficiaries.getRepresentativeIdValue()))
                        .orElseThrow(() -> new IllegalArgumentException("Representative not found"));
                beneficiaries.setRepresentative(representative);
            }

            // Set the location
            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new IllegalArgumentException("Location not found"));
            beneficiaries.setLocation(location);

            beneficiariesAmatungoService.update(id, beneficiaries);
            redirectAttributes.addFlashAttribute("success", "Abaragizwa amatungo updated successfully!");
            return "redirect:/beneficiaries/list";
        } catch (Exception e) {
            model.addAttribute("error", "Error updating beneficiary: " + e.getMessage());
            List<Representative> representativesList = representativesAbororaService.getAll();
            List<Location> locationList = locationRepository.findAll();
            model.addAttribute("representativesList", representativesList);
            model.addAttribute("locationList", locationList);
            return "beneficiaries-edit";
        }
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") UUID id,
                         RedirectAttributes redirectAttributes) {
        beneficiariesAmatungoService.delete(id);
        redirectAttributes.addFlashAttribute("success", "Abaragizwa amatungo deleted successfully!");
        return "redirect:/beneficiaries/list";
    }

    @GetMapping("/view/{id}")
    public String viewDetails(@PathVariable("id") UUID id, Model model) {
        Optional<Beneficiary> beneficiariesOpt = beneficiariesAmatungoService.getById(id);
        if (beneficiariesOpt.isEmpty()) {
            return "redirect:/beneficiaries/list";
        }

        model.addAttribute("beneficiaries", beneficiariesOpt.get());
        return "beneficiaries-view";
    }
}