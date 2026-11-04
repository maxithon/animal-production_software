package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
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

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.getAll());
        model.addAttribute("category", new LivestockCategory());
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

    // FIX: this now correctly renders the dedicated edit template instead of the list page.
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

    // FIX: matches the edit template's actual form action (/update/{id}), which was previously
    // pointing at a mapping that didn't exist.
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
            categoryService.delete(id);
            redirectAttributes.addFlashAttribute("success", "Category deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Cannot delete category: " + e.getMessage());
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