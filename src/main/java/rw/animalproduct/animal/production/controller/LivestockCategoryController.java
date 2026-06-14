package rw.animalproduct.animal.production.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.LivestockCategory;
import rw.animalproduct.animal.production.services.LivestockCategoryService;

import java.util.UUID;

@Controller
@RequestMapping("/livestock/categories")
public class LivestockCategoryController {

    @Autowired
    private LivestockCategoryService categoryService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.getAll());
        model.addAttribute("category", new LivestockCategory());
        return "livestock-categories-list";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute LivestockCategory category,
                         RedirectAttributes redirectAttributes) {
        try {
            if (categoryService.existsByCode(category.getCode())) {
                redirectAttributes.addFlashAttribute("error", "A category with this code already exists.");
                return "redirect:/livestock/categories";
            }
            if (categoryService.existsByName(category.getName())) {
                redirectAttributes.addFlashAttribute("error", "A category with this name already exists.");
                return "redirect:/livestock/categories";
            }
            categoryService.addNew(category);
            redirectAttributes.addFlashAttribute("success", "Category added successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error adding category: " + e.getMessage());
        }
        return "redirect:/livestock/categories";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable UUID id, Model model) {
        var categoryOpt = categoryService.getById(id);
        if (categoryOpt.isEmpty()) {
            return "redirect:/livestock/categories";
        }
        model.addAttribute("categories", categoryService.getAll());
        model.addAttribute("category", categoryOpt.get());
        model.addAttribute("editMode", true);
        return "livestock-categories-list";
    }

    @PostMapping("/edit/{id}")
    public String update(@PathVariable UUID id, @ModelAttribute LivestockCategory category,
                         RedirectAttributes redirectAttributes) {
        try {
            categoryService.update(id, category);
            redirectAttributes.addFlashAttribute("success", "Category updated successfully.");
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
            redirectAttributes.addFlashAttribute("error",
                    "Cannot delete category: it is likely still in use by livestock records.");
        }
        return "redirect:/livestock/categories";
    }
}