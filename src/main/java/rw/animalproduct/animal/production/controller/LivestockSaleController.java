package rw.animalproduct.animal.production.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Buyer;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockSale;
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
        List<Buyer> buyers = buyerService.search(query);
        return buyers.stream()
                .map(b -> new BuyerSearchResult(
                        b.getId().toString(),
                        b.getBuyerName(),
                        b.getBuyerPhone(),
                        b.getBuyerNationalId()
                ))
                .collect(Collectors.toList());
    }

    /**
     * POST /livestock/buyers/quick-add - Quick add a new buyer via AJAX
     */
    @PostMapping("/buyers/quick-add")
    @ResponseBody
    public BuyerAddResult quickAddBuyer(@RequestBody BuyerQuickAddRequest request) {
        try {
            Buyer buyer = buyerService.findOrCreate(
                    request.phone,
                    request.name,
                    request.address,
                    request.nationalId
            );
            return new BuyerAddResult(true, buyer);
        } catch (Exception e) {
            return new BuyerAddResult(false, e.getMessage());
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
        public Buyer buyer;
        public String error;

        public BuyerAddResult(boolean success, Buyer buyer) {
            this.success = success;
            this.buyer = buyer;
        }

        public BuyerAddResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }

    public static class BuyerQuickAddRequest {
        public String name;
        public String phone;
        public String address;
        public String nationalId;
    }
}