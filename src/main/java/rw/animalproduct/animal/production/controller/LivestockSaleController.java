package rw.animalproduct.animal.production.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.dto.BuyerSummary;
import rw.animalproduct.animal.production.entity.Buyer;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockSale;
import rw.animalproduct.animal.production.exception.DuplicateBuyerException;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSaleRepository;
import rw.animalproduct.animal.production.services.BuyerService;
import rw.animalproduct.animal.production.services.LivestockSaleService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
@RequestMapping("/livestock")
public class LivestockSaleController {

    private final LivestockSaleService saleService;
    private final LivestockRepository livestockRepository;
    private final LivestockSaleRepository saleRepository;
    private final BuyerService buyerService;

    private static final int DEFAULT_PAGE_SIZE = 10;

    // Kept identical to the Buyer entity's own validation rules so the
    // quick-add form on this page can never create a buyer that would have
    // failed validation on the real Buyer form.
    private static final String PHONE_REGEX = "^07[0-9]{8}$";
    private static final String NATIONAL_ID_REGEX = "^[0-9]{16}$";

    @Autowired
    public LivestockSaleController(LivestockSaleService saleService,
                                   LivestockRepository livestockRepository,
                                   LivestockSaleRepository saleRepository,
                                   BuyerService buyerService) {
        this.saleService = saleService;
        this.livestockRepository = livestockRepository;
        this.saleRepository = saleRepository;
        this.buyerService = buyerService;
    }

    /**
     * GET /livestock/sales - List all sale records with pagination
     */
    @GetMapping("/sales")
    public String listSales(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sort", defaultValue = "saleDate") String sort,
            @RequestParam(value = "direction", defaultValue = "desc") String direction,
            Model model) {

        // Validate page size
        if (size > 50) size = 50;
        if (size < 1) size = DEFAULT_PAGE_SIZE;

        Sort.Direction dir = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sort));

        Page<LivestockSale> salePage;

        if (search != null && !search.trim().isEmpty()) {
            salePage = saleRepository.searchSales(search.trim(), pageable);
        } else {
            salePage = saleRepository.findAllActive(pageable);
        }

        // Get all non-deleted livestock for the dropdown
        List<Livestock> livestockList = livestockRepository.findAll();

        // Create a new sale object for the form
        LivestockSale sale = new LivestockSale();
        sale.setSaleDate(LocalDate.now());

        // Calculate pagination range for display
        int totalPages = salePage.getTotalPages();
        List<Integer> pageNumbers = IntStream.rangeClosed(1, totalPages).boxed().collect(Collectors.toList());

        model.addAttribute("sales", salePage.getContent());
        model.addAttribute("salePage", salePage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", size);
        model.addAttribute("pageNumbers", pageNumbers);
        model.addAttribute("search", search);
        model.addAttribute("sort", sort);
        model.addAttribute("direction", direction);
        model.addAttribute("livestockList", livestockList);
        model.addAttribute("sale", sale);

        return "livestock-sales-list";
    }

    /**
     * POST /livestock/sales/new - Create a new sale record
     */
    @PostMapping("/sales/new")
    public String createSale(
            @RequestParam("livestockId") String livestockIdStr,
            @RequestParam("saleDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate saleDate,
            @RequestParam(value = "salePrice", required = false) BigDecimal salePrice,
            @RequestParam(value = "saleLocation", required = false) String saleLocation,
            @RequestParam(value = "saleReason", required = false) String saleReason,
            @RequestParam(value = "buyerId", required = false) String buyerIdStr,
            RedirectAttributes redirectAttributes) {

        try {
            LivestockSale sale = new LivestockSale();
            sale.setLivestockIdValue(livestockIdStr);
            sale.setSaleDate(saleDate);
            sale.setSalePrice(salePrice);
            sale.setSaleLocation(saleLocation);
            sale.setSaleReason(saleReason);
            sale.setBuyerIdValue(buyerIdStr);
            sale.setIsDeleted(false);

            LivestockSale saved = saleService.addNew(sale);

            String animalTag = saved.getLivestock() != null ? saved.getLivestock().getTagNumber() : livestockIdStr;
            redirectAttributes.addFlashAttribute("success",
                    "Sale recorded successfully for animal: " + animalTag +
                            (salePrice != null ? " | Price: " + salePrice + " RWF" : ""));

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to record sale: " + e.getMessage());
        }

        return "redirect:/livestock/sales";
    }

    /**
     * GET /livestock/sales/edit/{id} - Show edit form
     */
    @GetMapping("/sales/edit/{id}")
    public String editSaleForm(@PathVariable UUID id, Model model) {
        LivestockSale sale = saleService.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid sale ID: " + id));

        List<Livestock> livestockList = livestockRepository.findAll();

        model.addAttribute("sale", sale);
        model.addAttribute("livestockList", livestockList);

        return "livestock-sale-edit";
    }

    /**
     * POST /livestock/sales/update/{id} - Update a sale record
     */
    @PostMapping("/sales/update/{id}")
    public String updateSale(
            @PathVariable UUID id,
            @RequestParam("livestockId") String livestockIdStr,
            @RequestParam("saleDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate saleDate,
            @RequestParam(value = "salePrice", required = false) BigDecimal salePrice,
            @RequestParam(value = "saleLocation", required = false) String saleLocation,
            @RequestParam(value = "saleReason", required = false) String saleReason,
            RedirectAttributes redirectAttributes) {

        try {
            LivestockSale existing = saleService.getById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid sale ID: " + id));

            existing.setLivestockIdValue(livestockIdStr);
            existing.setSaleDate(saleDate);
            existing.setSalePrice(salePrice);
            existing.setSaleLocation(saleLocation);
            existing.setSaleReason(saleReason);

            saleService.update(id, existing);
            redirectAttributes.addFlashAttribute("success",
                    "Sale record updated successfully");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to update sale: " + e.getMessage());
        }

        return "redirect:/livestock/sales";
    }

    /**
     * POST /livestock/sales/delete/{id} - Delete a sale record
     */
    @PostMapping("/sales/delete/{id}")
    public String deleteSale(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            saleService.delete(id);
            redirectAttributes.addFlashAttribute("success",
                    "Sale record deleted successfully. Animal status restored to ACTIVE.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Failed to delete sale: " + e.getMessage());
        }
        return "redirect:/livestock/sales";
    }

    /**
     * GET /livestock/buyers/search - Search buyers for autocomplete
     */
    @GetMapping("/buyers/search")
    @ResponseBody
    public List<BuyerSearchResult> searchBuyers(@RequestParam("q") String query) {
        List<BuyerSummary> buyerSummaries = buyerService.search(query);
        return buyerSummaries.stream()
                .map(b -> new BuyerSearchResult(
                        b.getId().toString(),
                        b.getFullName(),
                        b.getPhone(),
                        b.getNationalId()
                ))
                .collect(Collectors.toList());
    }

    /**
     * POST /livestock/buyers/quick-add - Quick add a new buyer via AJAX
     *
     * FIXED: this used to take a single free-text "name" field and split it
     * on the last space to guess firstName/lastName — which broke for
     * single-word names, middle names, etc. It also silently dropped email,
     * buyer type and notes, so a buyer created here never matched what the
     * real "Add Buyer" form (buyers-list.html) produces.
     *
     * The request DTO now mirrors the real Buyer form field-for-field, and
     * server-side validation mirrors the same Rwandan phone / National ID
     * rules enforced by the Buyer entity, so a buyer created from either
     * screen is guaranteed to look the same.
     */
    @PostMapping("/buyers/quick-add")
    @ResponseBody
    public BuyerAddResult quickAddBuyer(@RequestBody BuyerQuickAddRequest request) {
        try {
            if (!StringUtils.hasText(request.firstName) || request.firstName.trim().length() < 2) {
                return new BuyerAddResult(false, "First name must be at least 2 characters.");
            }
            if (!StringUtils.hasText(request.lastName) || request.lastName.trim().length() < 2) {
                return new BuyerAddResult(false, "Last name must be at least 2 characters.");
            }
            if (!StringUtils.hasText(request.phone) || !request.phone.trim().matches(PHONE_REGEX)) {
                return new BuyerAddResult(false, "Enter a valid Rwandan phone number (10 digits starting with 07).");
            }
            if (StringUtils.hasText(request.nationalId) && !request.nationalId.trim().matches(NATIONAL_ID_REGEX)) {
                return new BuyerAddResult(false, "National ID must be exactly 16 digits.");
            }

            Buyer buyer = buyerService.findOrCreate(
                    request.phone.trim(),
                    request.firstName.trim(),
                    request.lastName.trim(),
                    request.address,
                    request.nationalId,
                    request.email,
                    request.buyerType,
                    request.notes
            );

            // Return the same shape as /buyers/search results so the existing
            // selectBuyer() JS on the sales page can handle both without
            // branching on where the buyer came from.
            BuyerSearchResult result = new BuyerSearchResult(
                    buyer.getId().toString(),
                    buyer.getFullName(),
                    buyer.getPhone(),
                    buyer.getNationalId()
            );
            return new BuyerAddResult(true, result);

        } catch (DuplicateBuyerException dup) {
            return new BuyerAddResult(false, dup.getMessage());
        } catch (Exception e) {
            return new BuyerAddResult(false, "Could not save buyer: " + e.getMessage());
        }
    }

    // Inner classes for AJAX responses
    public static class BuyerSearchResult {
        public String id;
        public String name;
        public String phone;
        public String nationalId;
        public String displayName;

        public BuyerSearchResult(String id, String name, String phone, String nationalId) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.nationalId = nationalId;
            this.displayName = name + (phone != null && !phone.isEmpty() ? " (" + phone + ")" : "");
        }
    }

    public static class BuyerAddResult {
        public boolean success;
        public BuyerSearchResult buyer;
        public String error;

        public BuyerAddResult(boolean success, BuyerSearchResult buyer) {
            this.success = success;
            this.buyer = buyer;
        }

        public BuyerAddResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }

    /**
     * Mirrors the fields collected by the real "Add Buyer" form
     * (buyers-list.html) instead of a single free-text "name".
     */
    public static class BuyerQuickAddRequest {
        public String firstName;
        public String lastName;
        public String phone;
        public String address;
        public String nationalId;
        public String email;
        public String buyerType;
        public String notes;
    }
}
