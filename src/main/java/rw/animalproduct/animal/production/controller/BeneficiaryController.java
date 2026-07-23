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
import rw.animalproduct.animal.production.repository.BeneficiaryRepository;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.services.AuditLogService;
import rw.animalproduct.animal.production.services.BeneficiaryService;
import rw.animalproduct.animal.production.services.LocationService;
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
    private final LocationService locationService;
    private final AuditLogService auditLogService;

    @Autowired
    public BeneficiaryController(BeneficiaryService beneficiariesAmatungoService,
                                 RepresentativeService representativesAbororaService,
                                 LocationRepository locationRepository,
                                 BeneficiaryRepository beneficiaryRepository,
                                 LocationService locationService,
                                 AuditLogService auditLogService) {
        this.beneficiariesAmatungoService = beneficiariesAmatungoService;
        this.representativesAbororaService = representativesAbororaService;
        this.locationRepository = locationRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.locationService = locationService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/list")
    public String listAll(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sort", defaultValue = "createdDate") String sort,
            @RequestParam(value = "representativeId", required = false) UUID representativeId,
            Model model) {

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, sort);

            Page<Beneficiary> pageContent = (representativeId != null)
                    ? beneficiaryRepository.findByRepresentativeId(representativeId, pageable)
                    : beneficiaryRepository.findAll(pageable);

            model.addAttribute("beneficiariesList", pageContent.getContent());
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", pageContent.getTotalPages());
            model.addAttribute("totalItems", pageContent.getTotalElements());
            model.addAttribute("pageSize", size);
        } catch (Exception e) {
            List<Beneficiary> list = (representativeId != null)
                    ? beneficiariesAmatungoService.getByUhagarariye(representativeId)
                    : beneficiariesAmatungoService.getAll();
            model.addAttribute("beneficiariesList", list);
            model.addAttribute("totalItems", list.size());
            model.addAttribute("totalPages", 1);
            model.addAttribute("currentPage", 0);
            model.addAttribute("pageSize", 10);
        }

        if (representativeId != null) {
            model.addAttribute("filterRepresentativeId", representativeId);
            representativesAbororaService.getById(representativeId)
                    .ifPresent(rep -> model.addAttribute("filterRepresentativeName",
                            rep.getFirstName() + " " + rep.getLastName()));
        }

        return "beneficiaries-list";
    }

    @GetMapping("/register")
    public String showRegistrationForm(
            @RequestParam(value = "representativeId", required = false) UUID representativeId,
            Model model) {
        List<Representative> representativesList = representativesAbororaService.getAll();
        List<Location> locationList = locationRepository.findAll();

        model.addAttribute("representativesList", representativesList);
        model.addAttribute("locationList", locationList);

        Beneficiary beneficiaries = new Beneficiary();
        if (representativeId != null) {
            beneficiaries.setRepresentativeIdValue(representativeId.toString());
        }
        model.addAttribute("beneficiaries", beneficiaries);
        return "beneficiaries-register";
    }

    // ── CREATE (now logged) ─────────────────────────────────────────────────
    @PostMapping("/register/new")
    public String register(@Valid @ModelAttribute("beneficiaries") Beneficiary beneficiaries,
                           @RequestParam(value = "locationId", required = false) UUID locationId,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {

        Optional<Beneficiary> existingOpt = beneficiariesAmatungoService.getByNid(beneficiaries.getNid());
        if (existingOpt.isPresent()) {
            result.rejectValue("nid", "error.beneficiaries", "NID already exists");
        }

        if (beneficiaries.getNid() != null && !beneficiaries.getNid().matches("^[0-9]{16}$")) {
            result.rejectValue("nid", "error.beneficiaries", "National ID must be exactly 16 digits");
        }

        if (beneficiaries.getPhone() != null && !beneficiaries.getPhone().matches("^(078|079|072|073)[0-9]{7}$")) {
            result.rejectValue("phone", "error.beneficiaries", "Phone number must be 10 digits starting with 078, 079, 072, or 073");
        }

        if (locationId == null) {
            result.reject("error.location", "Location is required");
        }

        if (beneficiaries.getRepresentativeIdValue() == null || beneficiaries.getRepresentativeIdValue().trim().isEmpty()) {
            result.rejectValue("representativeIdValue", "error.beneficiaries", "Representative is required");
        }

        if (beneficiaries.getContractAgreement() == null || beneficiaries.getContractAgreement().trim().isEmpty()) {
            result.reject("error.contractAgreement", "Contract PDF is required");
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
            return "beneficiaries-register";
        }

        try {
            if (beneficiaries.getRepresentativeIdValue() != null && !beneficiaries.getRepresentativeIdValue().trim().isEmpty()) {
                Representative representative = representativesAbororaService.getById(UUID.fromString(beneficiaries.getRepresentativeIdValue()))
                        .orElseThrow(() -> new IllegalArgumentException("Representative not found"));
                beneficiaries.setRepresentative(representative);
            }

            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new IllegalArgumentException("Location not found"));
            beneficiaries.setLocation(location);

            beneficiaries.setCreatedDate(new Date());

            Beneficiary saved = beneficiariesAmatungoService.addNew(beneficiaries);

            auditLogService.log(
                    "beneficiary",
                    saved.getId(),
                    "CREATE",
                    getCurrentUsername(),
                    null,
                    saved,
                    "Registered beneficiary: " + saved.getFirstName() + " " + saved.getLastName()
            );

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

        Beneficiary beneficiaries = beneficiariesOpt.get();

        if (beneficiaries.getRepresentative() != null) {
            beneficiaries.setRepresentativeIdValue(beneficiaries.getRepresentative().getId().toString());
        }

        model.addAttribute("beneficiaries", beneficiaries);
        model.addAttribute("representativesList", representativesList);

        Location parent = null;
        Location grandParent = null;
        Location greatGrandParent = null;
        Location greatGreatGrandParent = null;

        if (beneficiaries.getLocation() != null) {
            Location currentLocation = beneficiaries.getLocation();
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

        return "beneficiaries-edit";
    }

    // ── UPDATE (now logged) ─────────────────────────────────────────────────
    @PostMapping("/update/{id}")
    public String update(@PathVariable("id") UUID id,
                         @Valid @ModelAttribute("beneficiaries") Beneficiary beneficiaries,
                         @RequestParam(value = "locationId", required = false) UUID locationId,
                         BindingResult result,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        Optional<Beneficiary> existingOpt = beneficiariesAmatungoService.getByNid(beneficiaries.getNid());
        if (existingOpt.isPresent() && !existingOpt.get().getId().equals(id)) {
            result.rejectValue("nid", "error.beneficiaries", "NID already exists");
        }

        if (beneficiaries.getNid() != null && !beneficiaries.getNid().matches("^[0-9]{16}$")) {
            result.rejectValue("nid", "error.beneficiaries", "National ID must be exactly 16 digits");
        }

        if (beneficiaries.getPhone() != null && !beneficiaries.getPhone().matches("^(078|079|072|073)[0-9]{7}$")) {
            result.rejectValue("phone", "error.beneficiaries", "Phone number must be 10 digits starting with 078, 079, 072, or 073");
        }

        if (locationId == null) {
            result.reject("error.location", "Location is required");
        }

        if (beneficiaries.getRepresentativeIdValue() == null || beneficiaries.getRepresentativeIdValue().trim().isEmpty()) {
            result.rejectValue("representativeIdValue", "error.beneficiaries", "Representative is required");
        }

        if (result.hasErrors()) {
            String errorMessages = result.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));

            model.addAttribute("error", errorMessages);
            List<Representative> representativesList = representativesAbororaService.getAll();
            model.addAttribute("representativesList", representativesList);
            model.addAttribute("provinces", locationService.getLocationsByType("PROVINCE"));
            return "beneficiaries-edit";
        }

        try {
            // Capture "before" as a JSON string NOW, before update() mutates the managed entity
            String beforeSnapshot = beneficiariesAmatungoService.getById(id)
                    .map(auditLogService::snapshot)
                    .orElse(null);

            if (beneficiaries.getRepresentativeIdValue() != null && !beneficiaries.getRepresentativeIdValue().trim().isEmpty()) {
                Representative representative = representativesAbororaService.getById(UUID.fromString(beneficiaries.getRepresentativeIdValue()))
                        .orElseThrow(() -> new IllegalArgumentException("Representative not found"));
                beneficiaries.setRepresentative(representative);
            }

            Location location = locationRepository.findById(locationId)
                    .orElseThrow(() -> new IllegalArgumentException("Location not found"));
            beneficiaries.setLocation(location);

            Beneficiary updated = beneficiariesAmatungoService.update(id, beneficiaries);

            auditLogService.log(
                    "beneficiary",
                    id,
                    "UPDATE",
                    getCurrentUsername(),
                    beforeSnapshot,
                    updated,
                    "Updated beneficiary: " + beneficiaries.getFirstName() + " " + beneficiaries.getLastName()
            );

            redirectAttributes.addFlashAttribute("success", "Abaragizwa amatungo updated successfully!");
            return "redirect:/beneficiaries/list";
        } catch (Exception e) {
            model.addAttribute("error", "Error updating beneficiary: " + e.getMessage());
            List<Representative> representativesList = representativesAbororaService.getAll();
            model.addAttribute("representativesList", representativesList);
            model.addAttribute("provinces", locationService.getLocationsByType("PROVINCE"));
            return "beneficiaries-edit";
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") UUID id,
                         RedirectAttributes redirectAttributes) {

        Optional<Beneficiary> beneficiaryOpt = beneficiariesAmatungoService.getById(id);

        if (beneficiaryOpt.isPresent()) {
            Beneficiary beneficiary = beneficiaryOpt.get();

            auditLogService.log(
                    "beneficiary",
                    id,
                    "DELETE",
                    getCurrentUsername(),
                    beneficiary,
                    null,
                    "Deleted beneficiary: " + beneficiary.getFirstName() + " " + beneficiary.getLastName()
            );
        }

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

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "system";
    }
}