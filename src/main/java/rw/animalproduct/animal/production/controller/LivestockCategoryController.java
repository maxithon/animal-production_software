package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.services.LivestockCategoryService;

import java.util.UUID;

@Controller
@RequestMapping("/livestock/categories")
public class LivestockCategoryController {

    private final LivestockCategoryService categoryService;

    public LivestockCategoryController(LivestockCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "system";
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.getAll());
        model.addAttribute("category",   new LivestockCategory());
        model.addAttribute("usageCount", categoryService.getUsageCounts());
        return "livestock-categories-list";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("category") LivestockCategory category,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", firstError(bindingResult));
            return "redirect:/livestock/categories";
        }
        try {
            categoryService.addNew(category);
            redirectAttributes.addFlashAttribute("success", "Category added successfully.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error adding category: " + e.getMessage());
        }
        return "redirect:/livestock/categories";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable UUID id, Model model, RedirectAttributes redirectAttributes) {
        return categoryService.getById(id)
                .map(cat -> {
                    model.addAttribute("category", cat);
                    return "livestock-category-edit";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Category not found.");
                    return "redirect:/livestock/categories";
                });
    }

    @PostMapping("/update/{id}")
    public String update(@PathVariable UUID id,
                         @Valid @ModelAttribute("category") LivestockCategory category,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", firstError(bindingResult));
            return "redirect:/livestock/categories/edit/" + id;
        }
        try {
            categoryService.update(id, category);
            redirectAttributes.addFlashAttribute("success", "Category updated successfully.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/livestock/categories/edit/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating category: " + e.getMessage());
        }
        return "redirect:/livestock/categories";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            LivestockCategoryService.DeleteResult result = categoryService.delete(id);
            redirectAttributes.addFlashAttribute("success",
                    "Category '" + result.categoryName() + "' was deleted permanently.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Cannot delete category: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting category: " + e.getMessage());
        }
        return "redirect:/livestock/categories";
    }

    private String firstError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getDefaultMessage())
                .orElse("Invalid input — please check the form.");
    }
}