package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
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

    @GetMapping
    public String listBuyers(Model model) {
        model.addAttribute("buyers", buyerService.getAll());
        model.addAttribute("buyer", new Buyer());
        model.addAttribute("activeCount", buyerService.countActive());
        model.addAttribute("inactiveCount", buyerService.countInactive());
        model.addAttribute("topBuyers", buyerService.getTopBuyersByLimit(5));
        return "buyers-list";
    }

    // ── CREATE (now logged) ─────────────────────────────────────────────────
    @PostMapping("/new")
    public String saveBuyer(@Valid @ModelAttribute("buyer") Buyer buyer,
                            BindingResult result, Model model,
                            RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("buyers", buyerService.getAll());
            return "buyers-list";
        }
        try {
            Buyer saved = buyerService.addNew(buyer);

            auditLogService.log(
                    "buyer",
                    saved.getId(),
                    "CREATE",
                    getCurrentUsername(),
                    null,
                    saved,
                    "Registered buyer: " + saved.getBuyerName()
            );

            ra.addFlashAttribute("success", "Buyer saved successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/buyers";
    }

    @GetMapping("/edit/{id}")
    public String editBuyerForm(@PathVariable UUID id, Model model) {
        Optional<Buyer> opt = buyerService.getById(id);
        if (opt.isEmpty()) return "redirect:/buyers";
        model.addAttribute("buyer", opt.get());
        return "buyer-edit";
    }

    // ── UPDATE (now logged) ─────────────────────────────────────────────────
    @PostMapping("/update/{id}")
    public String updateBuyer(@PathVariable UUID id,
                              @Valid @ModelAttribute("buyer") Buyer buyer,
                              BindingResult result, Model model,
                              RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "buyer-edit";
        }
        try {
            String beforeSnapshot = buyerService.getById(id)
                    .map(auditLogService::snapshot)
                    .orElse(null);

            Buyer updated = buyerService.update(id, buyer);

            auditLogService.log(
                    "buyer",
                    id,
                    "UPDATE",
                    getCurrentUsername(),
                    beforeSnapshot,
                    updated,
                    "Updated buyer: " + buyer.getBuyerName()
            );

            ra.addFlashAttribute("success", "Buyer updated successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/buyers";
    }

    // ── DELETE ────────────────────────────────────────────────────────────
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
                                : "Deleted buyer: ") + buyer.getBuyerName()
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
    public List<Buyer> searchBuyers(@RequestParam String query) {
        return buyerService.search(query);
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "system";
    }
}