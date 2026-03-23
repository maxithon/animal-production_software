package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.entity.UsersType;
import rw.animalproduct.animal.production.services.UsersService;
import rw.animalproduct.animal.production.services.UsersTypeService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
public class UsersController {

    private final UsersTypeService usersTypeService;
    private final UsersService usersService;

    @Autowired
    public UsersController(UsersTypeService usersTypeService, UsersService usersService) {
        this.usersTypeService = usersTypeService;
        this.usersService = usersService;
    }

    // ⭐ MODIFIED: Now returns fragment for iframe
    @GetMapping("/register")
    public String register(Model model){
        List<UsersType> usersTypes = usersTypeService.getAll();
        model.addAttribute("getAllTypes", usersTypes);
        model.addAttribute("users", new Users());
        return "register";
    }

    // ⭐ MODIFIED: Redirect after successful registration
    @PostMapping("/register/new")
    public String userRegistration(@Valid Users users,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {

        Optional<Users> optionalUsers = usersService.getUserByEmail(users.getEmail());

        if(optionalUsers.isPresent()) {
            model.addAttribute("error", "Email already exists");
            List<UsersType> usersTypes = usersTypeService.getAll();
            model.addAttribute("getAllTypes", usersTypes);
            model.addAttribute("users", new Users());
            return "register";
        }

        usersService.addNew(users);
        redirectAttributes.addFlashAttribute("success", "User registered successfully!");
        return "redirect:/users/list";
    }

    // ⭐ NEW: List all users
    @GetMapping("/users/list")
    public String listUsers(Model model) {
        List<Users> usersList = usersService.getAllUsers();
        model.addAttribute("usersList", usersList);
        return "users-list";
    }

    // ⭐ NEW: Show edit form
    @GetMapping("/users/edit/{id}")
    public String editUser(@PathVariable("id") UUID id, Model model) {
        Optional<Users> user = usersService.getUserById(id);
        if (user.isEmpty()) {
            return "redirect:/users/list";
        }

        List<UsersType> usersTypes = usersTypeService.getAll();
        model.addAttribute("user", user.get());
        model.addAttribute("getAllTypes", usersTypes);
        return "users-edit";
    }

    // ⭐ NEW: Update user
    @PostMapping("/users/update/{id}")
    public String updateUser(@PathVariable("id") UUID id,
                             @ModelAttribute Users users,
                             RedirectAttributes redirectAttributes) {

        usersService.updateUser(id, users);
        redirectAttributes.addFlashAttribute("success", "User updated successfully!");
        return "redirect:/users/list";
    }

    // ⭐ NEW: Delete user
    @PostMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable("id") UUID id,
                             RedirectAttributes redirectAttributes) {
        usersService.deleteUser(id);
        redirectAttributes.addFlashAttribute("success", "User deleted successfully!");
        return "redirect:/users/list";
    }

    // ⭐ NEW: Toggle user status
    @PostMapping("/users/toggle-status/{id}")
    public String toggleUserStatus(@PathVariable("id") UUID id,
                                   RedirectAttributes redirectAttributes) {
        usersService.toggleUserStatus(id);
        redirectAttributes.addFlashAttribute("success", "User status updated successfully!");
        return "redirect:/users/list";
    }
}