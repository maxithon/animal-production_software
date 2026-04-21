package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Buyer;
import rw.animalproduct.animal.production.services.BuyerService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/buyers")
public class BuyerController {

    private final BuyerService buyerService;

    public BuyerController(BuyerService buyerService) {
        this.buyerService = buyerService;
    }

    @GetMapping
    public String listBuyers(Model model) {
        model.addAttribute("buyers", buyerService.getAll());
        model.addAttribute("buyer", new Buyer());
        // KPIs for dashboard cards
        model.addAttribute("activeCount", buyerService.countActive());
        model.addAttribute("inactiveCount", buyerService.countInactive());
        model.addAttribute("topBuyers", buyerService.getTopBuyersByLimit(5)); // top 5 buyers
        return "buyers-list";
    }

    @PostMapping("/new")
    public String saveBuyer(@Valid @ModelAttribute("buyer") Buyer buyer,
                            BindingResult result, Model model,
                            RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("buyers", buyerService.getAll());
            return "buyers-list";
        }
        try {
            buyerService.addNew(buyer);
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

    @PostMapping("/update/{id}")
    public String updateBuyer(@PathVariable UUID id,
                              @Valid @ModelAttribute("buyer") Buyer buyer,
                              BindingResult result, Model model,
                              RedirectAttributes ra) {
        if (result.hasErrors()) {
            return "buyer-edit";
        }
        try {
            buyerService.update(id, buyer);
            ra.addFlashAttribute("success", "Buyer updated successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/buyers";
    }

    @PostMapping("/delete/{id}")
    public String deleteBuyer(@PathVariable UUID id, RedirectAttributes ra) {
        try {
            buyerService.delete(id);
            ra.addFlashAttribute("success", "Buyer deactivated (soft delete) for history tracking.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/buyers";
    }

    // Optional: search via AJAX
    @GetMapping("/search")
    @ResponseBody
    public List<Buyer> searchBuyers(@RequestParam String query) {
        return buyerService.search(query);
    }
}