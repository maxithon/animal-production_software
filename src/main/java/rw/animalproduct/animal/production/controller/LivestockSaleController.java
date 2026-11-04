package rw.animalproduct.animal.production.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.dto.BuyerSummary;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.exception.DuplicateBuyerException;
import rw.animalproduct.animal.production.repository.LivestockCategoryRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockSaleRepository;
import rw.animalproduct.animal.production.repository.UsersRepository;
import rw.animalproduct.animal.production.services.BuyerService;
import rw.animalproduct.animal.production.services.LivestockSaleService;
import rw.animalproduct.animal.production.services.SalesAnalyticsService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
@RequestMapping("/livestock")
public class LivestockSaleController {

    private final LivestockSaleService saleService;
    private final LivestockRepository livestockRepository;
    private final LivestockSaleRepository saleRepository;
    private final BuyerService buyerService;
    private final SalesAnalyticsService analyticsService;
    private final LivestockCategoryRepository categoryRepository;
    private final UsersRepository usersRepository;

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final String PHONE_REGEX = "^07[0-9]{8}$";
    private static final String NATIONAL_ID_REGEX = "^[0-9]{16}$";
    private static final String SALE_REF_PREFIX = "SAL-";
    private static final int SALE_REF_PAD = 4;

    @Autowired
    public LivestockSaleController(LivestockSaleService saleService,
                                   LivestockRepository livestockRepository,
                                   LivestockSaleRepository saleRepository,
                                   BuyerService buyerService,
                                   SalesAnalyticsService analyticsService,
                                   LivestockCategoryRepository categoryRepository,
                                   UsersRepository usersRepository) {
        this.saleService = saleService;
        this.livestockRepository = livestockRepository;
        this.saleRepository = saleRepository;
        this.buyerService = buyerService;
        this.analyticsService = analyticsService;
        this.categoryRepository = categoryRepository;
        this.usersRepository = usersRepository;
    }

    /**
     * Get the currently authenticated user
     */
    private Users getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String email = authentication.getName();
            Optional<Users> user = usersRepository.findByEmail(email);
            return user.orElse(null);
        }
        return null;
    }

    /**
     * Get current user ID or null if not authenticated
     */
    private UUID getCurrentUserId() {
        Users user = getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    // ─── LIST PAGE ──────────────────────────────────────────────────

    @GetMapping("/sales")
    public String listSales(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sort", defaultValue = "saleDate") String sort,
            @RequestParam(value = "direction", defaultValue = "desc") String direction,
            @RequestParam(value = "saleStatus", required = false) String saleStatusFilter,
            @RequestParam(value = "paymentStatus", required = false) String paymentStatusFilter,
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @RequestParam(value = "buyerId", required = false) UUID buyerId,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model) {

        if (size > 50) size = 50;
        if (size < 1) size = DEFAULT_PAGE_SIZE;

        Sort.Direction dir = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sort));

        Page<LivestockSale> salePage;

        if (search != null && !search.trim().isEmpty()) {
            salePage = saleRepository.searchSales(search.trim(), pageable);
        } else if (hasAdvancedFilters(saleStatusFilter, paymentStatusFilter, categoryId, buyerId, fromDate, toDate)) {
            salePage = saleRepository.findWithFilters(
                    fromDate, toDate, categoryId, saleStatusFilter, paymentStatusFilter, buyerId,
                    null, null, pageable);
        } else if (saleStatusFilter != null && !saleStatusFilter.isEmpty()) {
            salePage = saleRepository.findBySaleStatusPaged(saleStatusFilter, pageable);
        } else if (paymentStatusFilter != null && !paymentStatusFilter.isEmpty()) {
            salePage = saleRepository.findByPaymentStatusPaged(paymentStatusFilter, pageable);
        } else {
            salePage = saleRepository.findAllActive(pageable);
        }

        List<Livestock> eligibleAnimals = livestockRepository.findEligibleForSale();
        List<Buyer> buyersList = buyerService.getActive();
        List<Users> usersList = usersRepository.findAll();

        LivestockSale sale = new LivestockSale();
        sale.setSaleDate(LocalDate.now());
        sale.setAmountPaid(BigDecimal.ZERO);
        sale.setSaleReference(previewNextSaleReference());
        sale.setSaleStatus(LivestockSale.SALE_DRAFT);

        // Auto-assign current user as creator
        Users currentUser = getCurrentUser();
        if (currentUser != null) {
            sale.setCreatedByIdValue(currentUser.getUserId().toString());
            // Also pass current user to model for display
            model.addAttribute("currentUser", currentUser);
        }

        int totalPages = salePage.getTotalPages();
        List<Integer> pageNumbers = IntStream.rangeClosed(1, totalPages).boxed().collect(Collectors.toList());

        // Dashboard stats
        long draftCount = saleRepository.countBySaleStatus(LivestockSale.SALE_DRAFT);
        long confirmedCount = saleRepository.countBySaleStatus(LivestockSale.SALE_CONFIRMED);
        long cancelledCount = saleRepository.countBySaleStatus(LivestockSale.SALE_CANCELLED);
        long refundedCount = saleRepository.countBySaleStatus(LivestockSale.SALE_REFUNDED);

        long unpaidCount = saleRepository.countByPaymentStatus(LivestockSale.PAYMENT_UNPAID);
        long partialCount = saleRepository.countByPaymentStatus(LivestockSale.PAYMENT_PARTIAL);
        long paidCount = saleRepository.countByPaymentStatus(LivestockSale.PAYMENT_PAID);

        BigDecimal totalRevenue = saleRepository.getTotalRevenue();
        BigDecimal totalOutstanding = saleRepository.getTotalOutstanding();
        BigDecimal grossSales = saleRepository.getGrossSales();
        BigDecimal collectedAmount = saleRepository.getCollectedAmount();
        BigDecimal totalGrossProfit = saleRepository.getTotalGrossProfit();

        model.addAttribute("sales", salePage.getContent());
        model.addAttribute("salePage", salePage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("pageSize", size);
        model.addAttribute("pageNumbers", pageNumbers);
        model.addAttribute("search", search);
        model.addAttribute("sort", sort);
        model.addAttribute("direction", direction);

        model.addAttribute("eligibleAnimals", eligibleAnimals);
        model.addAttribute("buyersList", buyersList);
        model.addAttribute("usersList", usersList);
        model.addAttribute("sale", sale);

        model.addAttribute("draftCount", draftCount);
        model.addAttribute("confirmedCount", confirmedCount);
        model.addAttribute("cancelledCount", cancelledCount);
        model.addAttribute("refundedCount", refundedCount);
        model.addAttribute("unpaidCount", unpaidCount);
        model.addAttribute("partialCount", partialCount);
        model.addAttribute("paidCount", paidCount);

        model.addAttribute("activeSaleStatusFilter", saleStatusFilter);
        model.addAttribute("activePaymentStatusFilter", paymentStatusFilter);

        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalOutstanding", totalOutstanding);
        model.addAttribute("grossSales", grossSales);
        model.addAttribute("collectedAmount", collectedAmount);
        model.addAttribute("totalGrossProfit", totalGrossProfit);
        model.addAttribute("totalSalesCount", saleRepository.countActive());

        model.addAttribute("allCategories", categoryRepository.findAllActiveOrderByName());
        model.addAttribute("allBuyers", buyerService.getActive());
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedBuyerId", buyerId);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);

        return "livestock-sales-list";
    }

    private boolean hasAdvancedFilters(String saleStatus, String paymentStatus, UUID categoryId,
                                       UUID buyerId, LocalDate fromDate, LocalDate toDate) {
        return (saleStatus != null && !saleStatus.isEmpty()) ||
                (paymentStatus != null && !paymentStatus.isEmpty()) ||
                categoryId != null || buyerId != null || fromDate != null || toDate != null;
    }

    // ─── CREATE SALE ──────────────────────────────────────────────

    @PostMapping("/sales/new")
    public String createSale(
            @RequestParam("livestockId") String livestockIdStr,
            @RequestParam("saleDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate saleDate,
            @RequestParam(value = "salePrice", required = false) BigDecimal salePrice,
            @RequestParam(value = "amountPaid", required = false) BigDecimal amountPaid,
            @RequestParam(value = "saleLocation", required = false) String saleLocation,
            @RequestParam(value = "saleReason", required = false) String saleReason,
            @RequestParam(value = "buyerId", required = false) String buyerIdStr,
            @RequestParam(value = "paymentMethod", required = false) String paymentMethod,
            @RequestParam(value = "paymentReference", required = false) String paymentReference,
            @RequestParam(value = "notes", required = false) String notes,
            RedirectAttributes redirectAttributes) {

        try {
            // Validate sale date is not in future
            if (saleDate != null && saleDate.isAfter(LocalDate.now())) {
                throw new RuntimeException("Sale date cannot be in the future.");
            }

            Livestock livestock = livestockRepository.findById(UUID.fromString(livestockIdStr))
                    .orElseThrow(() -> new IllegalArgumentException("Selected animal was not found."));

            // Check if animal already has an active sale
            List<LivestockSale> existingSales = saleRepository.findByLivestockId(livestock.getId());
            boolean hasActiveSale = existingSales.stream()
                    .anyMatch(s -> LivestockSale.SALE_CONFIRMED.equals(s.getSaleStatus()) ||
                            LivestockSale.SALE_DRAFT.equals(s.getSaleStatus()));
            if (hasActiveSale) {
                throw new RuntimeException(
                        "Animal " + livestock.getTagNumber() + " already has an active sale. " +
                                "Delete or cancel the existing sale first."
                );
            }

            BigDecimal acquisitionCost = livestock.getCurrentValue() != null
                    ? livestock.getCurrentValue() : BigDecimal.ZERO;

            BigDecimal effectiveAmountPaid = amountPaid != null ? amountPaid : BigDecimal.ZERO;
            validatePayment(salePrice, effectiveAmountPaid);

            LivestockSale sale = new LivestockSale();
            sale.setLivestockIdValue(livestockIdStr);
            sale.setSaleDate(saleDate);
            sale.setSalePrice(salePrice);
            sale.setAcquisitionCost(acquisitionCost);
            sale.setAmountPaid(effectiveAmountPaid);
            sale.setSaleLocation(saleLocation);
            sale.setSaleReason(saleReason);
            sale.setBuyerIdValue(buyerIdStr);
            sale.setPaymentMethod(paymentMethod);
            sale.setPaymentReference(paymentReference);
            sale.setSaleReference(generateSaleReference());
            sale.setNotes(notes);
            sale.setIsDeleted(false);

            // Auto-assign current user as creator
            UUID currentUserId = getCurrentUserId();
            if (currentUserId != null) {
                sale.setCreatedByIdValue(currentUserId.toString());
            }

            LivestockSale saved = saleService.addNew(sale);

            String animalTag = saved.getLivestock() != null ? saved.getLivestock().getTagNumber() : livestockIdStr;
            redirectAttributes.addFlashAttribute("success",
                    "✅ Sale created successfully!\n" +
                            "Animal: " + animalTag + "\n" +
                            "Reference: " + saved.getSaleReference() + "\n" +
                            "Status: DRAFT - Confirm to complete the sale.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "❌ Failed to create sale: " + e.getMessage());
        }

        return "redirect:/livestock/sales";
    }

    // ─── CONFIRM SALE ─────────────────────────────────────────────

    @PostMapping("/sales/confirm/{id}")
    public String confirmSale(
            @PathVariable UUID id,
            @RequestParam(value = "notes", required = false) String notes,
            RedirectAttributes redirectAttributes) {

        try {
            // Auto-assign current user as confirmer
            UUID currentUserId = getCurrentUserId();
            saleService.confirmSale(id, currentUserId, notes);
            redirectAttributes.addFlashAttribute("success",
                    "✅ Sale confirmed successfully. Animal marked as SOLD.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "❌ Failed to confirm sale: " + e.getMessage());
        }
        return "redirect:/livestock/sales";
    }

    // ─── EDIT SALE ─────────────────────────────────────────────────

    @GetMapping("/sales/edit/{id}")
    public String editSaleForm(@PathVariable UUID id, Model model) {
        LivestockSale sale = saleService.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid sale ID: " + id));

        if (!sale.isEditable()) {
            throw new IllegalStateException("Cannot edit a " + sale.getSaleStatus() + " sale.");
        }

        List<Livestock> eligibleAnimals = livestockRepository.findEligibleForSale();
        List<Buyer> buyersList = buyerService.getActive();
        List<Users> usersList = usersRepository.findAll();

        model.addAttribute("sale", sale);
        model.addAttribute("eligibleAnimals", eligibleAnimals);
        model.addAttribute("buyersList", buyersList);
        model.addAttribute("usersList", usersList);

        return "livestock-sale-edit";
    }

    @PostMapping("/sales/update/{id}")
    public String updateSale(
            @PathVariable UUID id,
            @RequestParam("livestockId") String livestockIdStr,
            @RequestParam("saleDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate saleDate,
            @RequestParam(value = "salePrice", required = false) BigDecimal salePrice,
            @RequestParam(value = "amountPaid", required = false) BigDecimal amountPaid,
            @RequestParam(value = "saleLocation", required = false) String saleLocation,
            @RequestParam(value = "saleReason", required = false) String saleReason,
            @RequestParam(value = "buyerId", required = false) String buyerIdStr,
            @RequestParam(value = "paymentMethod", required = false) String paymentMethod,
            @RequestParam(value = "paymentReference", required = false) String paymentReference,
            @RequestParam(value = "notes", required = false) String notes,
            RedirectAttributes redirectAttributes) {

        try {
            LivestockSale existing = saleService.getById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid sale ID: " + id));

            if (!existing.isEditable()) {
                throw new RuntimeException("Cannot edit a " + existing.getSaleStatus() + " sale.");
            }

            Livestock livestock = livestockRepository.findById(UUID.fromString(livestockIdStr))
                    .orElseThrow(() -> new IllegalArgumentException("Selected animal was not found."));

            BigDecimal acquisitionCost = livestock.getCurrentValue() != null
                    ? livestock.getCurrentValue() : BigDecimal.ZERO;

            BigDecimal effectiveAmountPaid = amountPaid != null ? amountPaid : BigDecimal.ZERO;
            validatePayment(salePrice, effectiveAmountPaid);

            existing.setLivestockIdValue(livestockIdStr);
            existing.setSaleDate(saleDate);
            existing.setSalePrice(salePrice);
            existing.setAcquisitionCost(acquisitionCost);
            existing.setAmountPaid(effectiveAmountPaid);
            existing.setSaleLocation(saleLocation);
            existing.setSaleReason(saleReason);
            existing.setBuyerIdValue(buyerIdStr);
            existing.setPaymentMethod(paymentMethod);
            existing.setPaymentReference(paymentReference);
            existing.setNotes(notes);

            saleService.update(id, existing);
            redirectAttributes.addFlashAttribute("success",
                    "✅ Sale record updated successfully.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "❌ Failed to update sale: " + e.getMessage());
        }

        return "redirect:/livestock/sales";
    }

    // ─── DELETE SALE ──────────────────────────────────────────────

    @PostMapping("/sales/delete/{id}")
    public String deleteSale(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            saleService.delete(id);
            redirectAttributes.addFlashAttribute("success",
                    "✅ Sale record deleted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "❌ Failed to delete sale: " + e.getMessage());
        }
        return "redirect:/livestock/sales";
    }

    // ─── CANCEL SALE ──────────────────────────────────────────────

    @PostMapping("/sales/cancel/{id}")
    public String cancelSale(
            @PathVariable UUID id,
            @RequestParam(value = "reason", required = true) String reason,
            RedirectAttributes redirectAttributes) {

        try {
            if (reason == null || reason.trim().isEmpty()) {
                throw new RuntimeException("Cancellation reason is required.");
            }
            // Auto-assign current user as canceller
            UUID currentUserId = getCurrentUserId();
            saleService.cancelSale(id, currentUserId, reason);
            redirectAttributes.addFlashAttribute("success",
                    "✅ Sale cancelled successfully. Animal status restored to ACTIVE.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "❌ Failed to cancel sale: " + e.getMessage());
        }
        return "redirect:/livestock/sales";
    }

    // ─── REFUND SALE ──────────────────────────────────────────────

    @PostMapping("/sales/refund/{id}")
    public String refundSale(
            @PathVariable UUID id,
            @RequestParam(value = "refundAmount", required = true) BigDecimal refundAmount,
            @RequestParam(value = "reason", required = true) String reason,
            RedirectAttributes redirectAttributes) {

        try {
            if (reason == null || reason.trim().isEmpty()) {
                throw new RuntimeException("Refund reason is required.");
            }
            // Auto-assign current user as refund processor
            UUID currentUserId = getCurrentUserId();
            saleService.refundSale(id, currentUserId, refundAmount, reason);
            redirectAttributes.addFlashAttribute("success",
                    "✅ Sale refunded successfully. Animal status restored to ACTIVE.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "❌ Failed to refund sale: " + e.getMessage());
        }
        return "redirect:/livestock/sales";
    }

    // ─── RECORD PAYMENT ───────────────────────────────────────────

    @PostMapping("/sales/payment/{id}")
    public String recordPayment(
            @PathVariable UUID id,
            @RequestParam("paymentAmount") BigDecimal paymentAmount,
            @RequestParam(value = "paymentMethod", required = false) String paymentMethod,
            @RequestParam(value = "paymentReference", required = false) String paymentReference,
            @RequestParam(value = "notes", required = false) String notes,
            RedirectAttributes redirectAttributes) {

        try {
            // Auto-assign current user as payment recorder
            UUID currentUserId = getCurrentUserId();

            // Validate payment amount is valid (must be <= remaining balance)
            LivestockSale sale = saleService.getById(id)
                    .orElseThrow(() -> new RuntimeException("Sale not found"));

            BigDecimal balance = sale.getBalance();
            if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Payment amount must be greater than zero.");
            }
            if (paymentAmount.compareTo(balance) > 0) {
                throw new RuntimeException(
                        "Payment amount (" + formatMoney(paymentAmount) + " RWF) " +
                                "exceeds remaining balance (" + formatMoney(balance) + " RWF). " +
                                "Please enter an amount up to " + formatMoney(balance) + " RWF."
                );
            }

            saleService.recordPayment(id, paymentAmount, currentUserId, paymentMethod, paymentReference, notes);
            redirectAttributes.addFlashAttribute("success",
                    "✅ Payment of " + formatMoney(paymentAmount) + " RWF recorded successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "❌ Failed to record payment: " + e.getMessage());
        }
        return "redirect:/livestock/sales";
    }

    // ─── SALE DETAILS PAGE ────────────────────────────────────────

    @GetMapping("/sales/details/{id}")
    public String saleDetails(@PathVariable UUID id, Model model) {
        LivestockSale sale = saleService.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid sale ID: " + id));

        List<LivestockSalePayment> payments = saleService.getPayments(id);

        model.addAttribute("sale", sale);
        model.addAttribute("payments", payments);
        model.addAttribute("usersList", usersRepository.findAll());

        return "livestock-sale-details";
    }

    // ─── ANIMAL SEARCH (Autocomplete) ────────────────────────────

    @GetMapping("/animals/search")
    @ResponseBody
    public List<AnimalSearchResult> searchAnimals(@RequestParam("q") String query) {
        List<Livestock> animals = livestockRepository.findEligibleForSale();
        return animals.stream()
                .filter(a -> a.getTagNumber().toLowerCase().contains(query.toLowerCase()) ||
                        (a.getLivestockCategory() != null &&
                                a.getLivestockCategory().getName().toLowerCase().contains(query.toLowerCase())))
                .map(a -> new AnimalSearchResult(
                        a.getId().toString(),
                        a.getTagNumber(),
                        a.getLivestockCategory() != null ? a.getLivestockCategory().getName() : null,
                        a.getCurrentValue()
                ))
                .collect(Collectors.toList());
    }

    // ─── ANIMAL SALE INFO ─────────────────────────────────────────

    @GetMapping("/{id}/sale-info")
    @ResponseBody
    public AnimalSaleInfo getAnimalSaleInfo(@PathVariable("id") UUID id) {
        Livestock livestock = livestockRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Animal not found: " + id));

        List<LivestockSale> existingSales = saleRepository.findByLivestockId(id);
        boolean hasActiveSale = existingSales.stream()
                .anyMatch(s -> LivestockSale.SALE_CONFIRMED.equals(s.getSaleStatus()) ||
                        LivestockSale.SALE_DRAFT.equals(s.getSaleStatus()));

        String category = livestock.getLivestockCategory() != null
                ? livestock.getLivestockCategory().getName() : null;
        BigDecimal acquisitionCost = livestock.getCurrentValue() != null
                ? livestock.getCurrentValue() : BigDecimal.ZERO;

        return new AnimalSaleInfo(livestock.getTagNumber(), category, acquisitionCost, hasActiveSale);
    }

    // ─── BUYER SEARCH ─────────────────────────────────────────────

    @GetMapping("/buyers/search")
    @ResponseBody
    public List<BuyerSearchResult> searchBuyers(@RequestParam("q") String query) {
        List<BuyerSummary> buyerSummaries = buyerService.search(query);
        return buyerSummaries.stream()
                .map(b -> new BuyerSearchResult(
                        b.getId().toString(),
                        b.getFullName(),
                        b.getPhone(),
                        b.getNationalId(),
                        b.getAddress()
                ))
                .collect(Collectors.toList());
    }

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

            BuyerSearchResult result = new BuyerSearchResult(
                    buyer.getId().toString(),
                    buyer.getFullName(),
                    buyer.getPhone(),
                    buyer.getNationalId(),
                    buyer.getAddress()
            );
            return new BuyerAddResult(true, result);

        } catch (DuplicateBuyerException dup) {
            return new BuyerAddResult(false, dup.getMessage());
        } catch (Exception e) {
            return new BuyerAddResult(false, "Could not save buyer: " + e.getMessage());
        }
    }

    // ─── SALES REPORT ─────────────────────────────────────────────

    @GetMapping("/sales-report-detailed")
    public String getDetailedSalesReport(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "category", required = false) UUID categoryId,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "buyer", required = false) UUID buyerId,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "paymentStatus", required = false) String paymentStatus,
            Model model) {

        if (fromDate == null) {
            fromDate = LocalDate.now().minusMonths(12);
        }
        if (toDate == null) {
            toDate = LocalDate.now();
        }

        Map<String, Object> analytics = analyticsService.getAnalyticsForReport(
                fromDate, toDate, categoryId, gender, buyerId, minPrice, maxPrice, paymentStatus
        );

        model.addAttribute("sales", analytics.get("filteredSales"));
        model.addAttribute("totalSalesCount", analytics.get("totalSalesCount"));
        model.addAttribute("confirmedCount", analytics.get("confirmedCount"));
        model.addAttribute("totalRevenue", analytics.get("totalRevenue"));
        model.addAttribute("totalAmountPaid", analytics.get("totalAmountPaid"));
        model.addAttribute("totalPaid", analytics.get("totalPaid"));
        model.addAttribute("totalOutstanding", analytics.get("totalOutstanding"));
        model.addAttribute("totalCost", analytics.get("totalCost"));
        model.addAttribute("totalAcquisitionCost", analytics.get("totalAcquisitionCost"));
        model.addAttribute("totalGrossProfit", analytics.get("totalProfit"));
        model.addAttribute("grossMargin", analytics.get("grossMargin"));
        model.addAttribute("salesMissingCostCount", analytics.get("salesMissingCostCount"));
        model.addAttribute("averageSalePrice", analytics.get("averageSalePrice"));
        model.addAttribute("highestSale", analytics.get("highestSale"));
        model.addAttribute("lowestSale", analytics.get("lowestSale"));
        model.addAttribute("categorySummaries", analytics.get("categorySummaries"));
        model.addAttribute("buyerAnalytics", analytics.get("buyerAnalytics"));
        model.addAttribute("statusCounts", analytics.get("statusCounts"));
        model.addAttribute("paymentStatusCounts", analytics.get("paymentStatusCounts"));
        model.addAttribute("paymentStatusAmounts", analytics.get("paymentStatusAmounts"));
        model.addAttribute("trendData", analytics.get("trendData"));
        model.addAttribute("unpaidCount", analytics.get("unpaidCount"));
        model.addAttribute("partialCount", analytics.get("partialCount"));
        model.addAttribute("paidCount", analytics.get("paidCount"));
        model.addAttribute("unpaidAmount", analytics.get("unpaidAmount"));
        model.addAttribute("partialAmount", analytics.get("partialAmount"));
        model.addAttribute("paidAmount", analytics.get("paidAmount"));

        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedGender", gender);
        model.addAttribute("selectedBuyerId", buyerId);
        model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice);
        model.addAttribute("selectedPaymentStatus", paymentStatus);

        model.addAttribute("allCategories", categoryRepository.findAllActiveOrderByName());
        model.addAttribute("allBuyers", buyerService.getActive());
        model.addAttribute("allGenders", Arrays.asList("MALE", "FEMALE"));
        model.addAttribute("allPaymentStatuses", Arrays.asList(
                LivestockSale.PAYMENT_UNPAID,
                LivestockSale.PAYMENT_PARTIAL,
                LivestockSale.PAYMENT_PAID
        ));
        model.addAttribute("allSaleStatuses", Arrays.asList(
                LivestockSale.SALE_DRAFT,
                LivestockSale.SALE_CONFIRMED,
                LivestockSale.SALE_CANCELLED,
                LivestockSale.SALE_REFUNDED
        ));
        return "sales-report-detailed";
    }

    // ─── Private Helpers ──────────────────────────────────────────

    private void validatePayment(BigDecimal salePrice, BigDecimal amountPaid) {
        if (salePrice == null) {
            throw new RuntimeException("Sale price is required.");
        }
        if (amountPaid != null && amountPaid.compareTo(salePrice) > 0) {
            throw new RuntimeException(
                    "Amount paid cannot exceed the sale price of " + formatMoney(salePrice) + " RWF."
            );
        }
    }

    private String generateSaleReference() {
        String prefix = SALE_REF_PREFIX + LocalDate.now().getYear() + "-";
        long count = saleRepository.countBySaleReferencePrefix(prefix);
        int next = (int) count + 1;
        String candidate = prefix + String.format("%0" + SALE_REF_PAD + "d", next);
        while (saleRepository.findBySaleReference(candidate) != null) {
            next++;
            candidate = prefix + String.format("%0" + SALE_REF_PAD + "d", next);
        }
        return candidate;
    }

    private String previewNextSaleReference() {
        return generateSaleReference();
    }

    private String formatMoney(BigDecimal value) {
        if (value == null) return "0";
        return value.setScale(0, RoundingMode.HALF_UP).toBigInteger().toString()
                .replaceAll("\\B(?=(\\d{3})+(?!\\d))", ",");
    }

    // ─── Inner Classes ─────────────────────────────────────────────

    public static class AnimalSearchResult {
        public String id;
        public String tagNumber;
        public String category;
        public BigDecimal acquisitionCost;

        public AnimalSearchResult(String id, String tagNumber, String category, BigDecimal acquisitionCost) {
            this.id = id;
            this.tagNumber = tagNumber;
            this.category = category;
            this.acquisitionCost = acquisitionCost;
        }
    }

    public static class AnimalSaleInfo {
        public String tagNumber;
        public String category;
        public BigDecimal acquisitionCost;
        public boolean hasActiveSale;

        public AnimalSaleInfo(String tagNumber, String category, BigDecimal acquisitionCost, boolean hasActiveSale) {
            this.tagNumber = tagNumber;
            this.category = category;
            this.acquisitionCost = acquisitionCost;
            this.hasActiveSale = hasActiveSale;
        }
    }

    public static class BuyerSearchResult {
        public String id;
        public String name;
        public String phone;
        public String nationalId;
        public String address;
        public String displayName;

        public BuyerSearchResult(String id, String name, String phone, String nationalId, String address) {
            this.id = id;
            this.name = name;
            this.phone = phone;
            this.nationalId = nationalId;
            this.address = address;
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