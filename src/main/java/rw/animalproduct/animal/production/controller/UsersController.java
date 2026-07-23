package rw.animalproduct.animal.production.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Beneficiary;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.entity.UsersType;
import rw.animalproduct.animal.production.services.BeneficiaryService;
import rw.animalproduct.animal.production.services.UsersService;
import rw.animalproduct.animal.production.services.UsersTypeService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
public class UsersController {

    private final UsersTypeService usersTypeService;
    private final UsersService usersService;
    // ── NEW: needed so registration/edit forms can offer a beneficiary picker.
    //    Without this, users are created with beneficiaryId = null, which is
    //    exactly why /user/dashboard shows all zeros — DashboardController's
    //    userDashboard() only calculates real figures when
    //    currentUser.getBeneficiaryId() != null.
    private final BeneficiaryService beneficiaryService;

    @Autowired
    public UsersController(UsersTypeService usersTypeService,
                           UsersService usersService,
                           BeneficiaryService beneficiaryService) {
        this.usersTypeService = usersTypeService;
        this.usersService = usersService;
        this.beneficiaryService = beneficiaryService;
    }

    // ⭐ MODIFIED: now also supplies the beneficiary list for the picker
    @GetMapping("/register")
    public String register(Model model){
        List<UsersType> usersTypes = usersTypeService.getAll();
        model.addAttribute("getAllTypes", usersTypes);
        model.addAttribute("beneficiaryList", beneficiaryService.getAll());
        model.addAttribute("users", new Users());
        return "register";
    }

    // ⭐ MODIFIED: binds the selected beneficiary (if any) onto the new user,
    //    and re-populates beneficiaryList on validation failure so the form
    //    doesn't lose its dropdown options.
    @PostMapping("/register/new")
    public String userRegistration(@Valid Users users,
                                   @RequestParam(value = "beneficiaryId", required = false) UUID beneficiaryId,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {

        Optional<Users> optionalUsers = usersService.getUserByEmail(users.getEmail());

        if(optionalUsers.isPresent()) {
            model.addAttribute("error", "Email already exists");
            List<UsersType> usersTypes = usersTypeService.getAll();
            model.addAttribute("getAllTypes", usersTypes);
            model.addAttribute("beneficiaryList", beneficiaryService.getAll());
            model.addAttribute("users", new Users());
            return "register";
        }

        if (beneficiaryId != null) {
            users.setBeneficiaryId(beneficiaryId);
        }

        usersService.addNew(users);
        redirectAttributes.addFlashAttribute("success",
                beneficiaryId != null
                        ? "User registered successfully and linked to a beneficiary!"
                        : "User registered successfully! Note: no beneficiary was linked, so their dashboard will show zeros until one is assigned.");
        return "redirect:/users/list";
    }

    // ⭐ NEW: List all users
    @GetMapping("/users/list")
    public String listUsers(Model model) {
        List<Users> usersList = usersService.getAllUsers();
        model.addAttribute("usersList", usersList);
        return "users-list";
    }

    // ⭐ MODIFIED: now also supplies the beneficiary list, and the user's
    //    currently linked beneficiary (if any), for the edit form.
    @GetMapping("/users/edit/{id}")
    public String editUser(@PathVariable("id") UUID id, Model model) {
        Optional<Users> user = usersService.getUserById(id);
        if (user.isEmpty()) {
            return "redirect:/users/list";
        }

        List<UsersType> usersTypes = usersTypeService.getAll();
        model.addAttribute("user", user.get());
        model.addAttribute("getAllTypes", usersTypes);
        model.addAttribute("beneficiaryList", beneficiaryService.getAll());
        model.addAttribute("currentBeneficiaryId",
                user.get().getBeneficiaryId() != null ? user.get().getBeneficiaryId().toString() : null);
        return "users-edit";
    }

    // ⭐ MODIFIED: accepts and persists the (possibly changed, possibly cleared)
    //    beneficiary link. Passing an empty value un-links the user.
    @PostMapping("/users/update/{id}")
    public String updateUser(@PathVariable("id") UUID id,
                             @ModelAttribute Users users,
                             @RequestParam(value = "beneficiaryId", required = false) UUID beneficiaryId,
                             RedirectAttributes redirectAttributes) {

        users.setBeneficiaryId(beneficiaryId); // null clears the link intentionally
        usersService.updateUser(id, users);
        redirectAttributes.addFlashAttribute("success", "User updated successfully!");
        return "redirect:/users/list";
    }

    // ⭐ NEW: Quick dedicated endpoint just for (un)linking a beneficiary,
    //    useful if you'd rather not touch the full edit form — e.g. a
    //    "Link Beneficiary" button directly on users-list.html.
    @PostMapping("/users/link-beneficiary/{id}")
    public String linkBeneficiary(@PathVariable("id") UUID id,
                                  @RequestParam(value = "beneficiaryId", required = false) UUID beneficiaryId,
                                  RedirectAttributes redirectAttributes) {
        Optional<Users> optionalUser = usersService.getUserById(id);
        if (optionalUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "User not found");
            return "redirect:/users/list";
        }
        Users user = optionalUser.get();
        user.setBeneficiaryId(beneficiaryId);
        usersService.updateUser(id, user);
        redirectAttributes.addFlashAttribute("success",
                beneficiaryId != null ? "Beneficiary linked successfully!" : "Beneficiary unlinked.");
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
