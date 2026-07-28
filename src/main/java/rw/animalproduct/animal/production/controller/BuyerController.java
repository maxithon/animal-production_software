package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.dto.BuyerSummary;
import rw.animalproduct.animal.production.entity.Buyer;
import rw.animalproduct.animal.production.services.AuditLogService;
import rw.animalproduct.animal.production.services.BuyerService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/buyers")
public class BuyerController {

    private final BuyerService buyerService;
    private final AuditLogService auditLogService;

    public BuyerController(BuyerService buyerService, AuditLogService auditLogService) {
        this.buyerService = buyerService;
        this.auditLogService = auditLogService;
    }

    // ── LIST (now backed by a single, fast projection query — see BuyerSummary) ──
    @GetMapping
    public String listBuyers(Model model) {
        model.addAttribute("buyers", buyerService.getAllSummaries());
        model.addAttribute("buyer", new Buyer());
        model.addAttribute("activeCount", buyerService.countActive());
        model.addAttribute("inactiveCount", buyerService.countInactive());
        model.addAttribute("topBuyers", buyerService.getTopBuyersByLimit(5));
        return "buyers-list";
    }

    // ── CREATE ─────────────────────────────────────────────────────────
    @PostMapping("/new")
    public String saveBuyer(@Valid @ModelAttribute("buyer") Buyer buyer,
                            BindingResult result,
                            @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                            Model model,
                            RedirectAttributes ra) {
        if (result.hasErrors()) {
            return reRenderListWithErrors(model);
        }
        try {
            Buyer saved = buyerService.addNew(buyer, photoFile);

            // Sanity check: never tell the user "saved" unless it truly has an id
            // from the database. This closes the "says saved but isn't in the DB" gap.
            if (saved == null || saved.getId() == null) {
                throw new IllegalStateException("Save did not return a persisted record.");
            }

            auditLogService.log(
                    "buyer",
                    saved.getId(),
                    "CREATE",
                    getCurrentUsername(),
                    null,
                    saved,
                    "Registered buyer: " + saved.getFullName()
            );

            ra.addFlashAttribute("success", "Buyer \"" + saved.getFullName() + "\" saved successfully!");
            return "redirect:/buyers";
        } catch (rw.animalproduct.animal.production.exception.DuplicateBuyerException dup) {
            result.addError(new FieldError("buyer", dup.getField(), dup.getMessage()));
            return reRenderListWithErrors(model);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not save buyer: " + e.getMessage());
            return "redirect:/buyers";
        }
    }

    @GetMapping("/edit/{id}")
    public String editBuyerForm(@PathVariable UUID id, Model model) {
        Optional<Buyer> opt = buyerService.getById(id);
        if (opt.isEmpty()) return "redirect:/buyers";
        model.addAttribute("buyer", opt.get());
        return "buyer-edit";
    }

    // ── UPDATE ─────────────────────────────────────────────────────────
    @PostMapping("/update/{id}")
    public String updateBuyer(@PathVariable UUID id,
                              @Valid @ModelAttribute("buyer") Buyer buyer,
                              BindingResult result,
                              @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                              Model model,
                              RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "buyer-edit";
        }
        try {
            String beforeSnapshot = buyerService.getById(id)
                    .map(auditLogService::snapshot)
                    .orElse(null);

            Buyer updated = buyerService.update(id, buyer, photoFile);

            auditLogService.log(
                    "buyer",
                    id,
                    "UPDATE",
                    getCurrentUsername(),
                    beforeSnapshot,
                    updated,
                    "Updated buyer: " + updated.getFullName()
            );

            ra.addFlashAttribute("success", "Buyer updated successfully!");
            return "redirect:/buyers";
        } catch (rw.animalproduct.animal.production.exception.DuplicateBuyerException dup) {
            result.addError(new FieldError("buyer", dup.getField(), dup.getMessage()));
            model.addAttribute("buyer", buyer);
            return "buyer-edit";
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Could not update buyer: " + e.getMessage());
            return "redirect:/buyers";
        }
    }

    // ── DELETE (soft-delete when the buyer has sales history) ────────────
    @PostMapping("/delete/{id}")
    public String deleteBuyer(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            Optional<Buyer> buyerOpt = buyerService.getById(id);

            if (buyerOpt.isPresent()) {
                Buyer buyer = buyerOpt.get();
                boolean hadSales = buyer.getSales() != null && !buyer.getSales().isEmpty();

                auditLogService.log(
                        "buyer",
                        id,
                        hadSales ? "SOFT_DELETE" : "DELETE",
                        getCurrentUsername(),
                        buyer,
                        null,
                        (hadSales
                                ? "Deactivated buyer (has sales history): "
                                : "Deleted buyer: ") + buyer.getFullName()
                );
            }

            buyerService.delete(id);
            ra.addFlashAttribute("success", "Buyer deactivated (soft delete) for history tracking.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/buyers";
    }

    @GetMapping("/search")
    @ResponseBody
    public List<BuyerSummary> searchBuyers(@RequestParam String query) {
        return buyerService.search(query);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * When the "Add Buyer" form fails validation, re-render the list page
     * with every attribute the template needs (this was missing before,
     * which is likely why validation failures looked broken/blank).
     */
    private String reRenderListWithErrors(Model model) {
        model.addAttribute("buyers", buyerService.getAllSummaries());
        model.addAttribute("activeCount", buyerService.countActive());
        model.addAttribute("inactiveCount", buyerService.countInactive());
        model.addAttribute("topBuyers", buyerService.getTopBuyersByLimit(5));
        model.addAttribute("openAddModal", true);
        return "buyers-list";
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "system";
    }
}