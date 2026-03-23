package rw.animalproduct.animal.production.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.repository.UsersRepository;
import rw.animalproduct.animal.production.services.UsersService;
import rw.animalproduct.animal.production.services.UsersTypeService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final UsersRepository usersRepository;
    private final UsersService usersService;
    private final UsersTypeService usersTypeService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public SettingsController(UsersRepository usersRepository,
                              UsersService usersService,
                              UsersTypeService usersTypeService,
                              PasswordEncoder passwordEncoder) {
        this.usersRepository = usersRepository;
        this.usersService = usersService;
        this.usersTypeService = usersTypeService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/profile")
    public String showProfile(Authentication authentication, Model model) {
        String email = authentication.getName();
        Optional<Users> userOpt = usersRepository.findByEmail(email);
        if (userOpt.isEmpty()) return "redirect:/";
        model.addAttribute("user", userOpt.get());
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        model.addAttribute("isSuperAdmin", isSuperAdmin);
        return "settings-profile";
    }

    @GetMapping("/change-password")
    public String showChangePassword() {
        return "settings-change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(
            Authentication authentication,
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            RedirectAttributes redirectAttributes) {

        String email = authentication.getName();
        Optional<Users> userOpt = usersRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "User not found");
            return "redirect:/settings/change-password";
        }

        Users user = userOpt.get();

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "Current password is incorrect");
            return "redirect:/settings/change-password";
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "New password cannot be empty");
            return "redirect:/settings/change-password";
        }
        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "New password must be at least 6 characters");
            return "redirect:/settings/change-password";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New passwords do not match");
            return "redirect:/settings/change-password";
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "New password must be different from current password");
            return "redirect:/settings/change-password";
        }

        try {
            user.setPassword(passwordEncoder.encode(newPassword));
            usersRepository.save(user);
            redirectAttributes.addFlashAttribute("success", "Password changed successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "An error occurred: " + e.getMessage());
        }
        return "redirect:/settings/change-password";
    }

    @GetMapping("/notifications")
    public String showNotificationSettings(Authentication authentication, Model model) {
        String email = authentication.getName();
        Optional<Users> userOpt = usersRepository.findByEmail(email);
        if (userOpt.isEmpty()) return "redirect:/";
        model.addAttribute("user", userOpt.get());
        return "settings-notifications";
    }

    @PostMapping("/notifications")
    public String updateNotificationSettings(
            Authentication authentication,
            @RequestParam(value = "emailNotifications", defaultValue = "false") boolean emailNotifications,
            @RequestParam(value = "smsNotifications", defaultValue = "false") boolean smsNotifications,
            @RequestParam(value = "appNotifications", defaultValue = "false") boolean appNotifications,
            @RequestParam(value = "frequency", defaultValue = "daily") String frequency,
            @RequestParam(value = "quietStart", defaultValue = "22:00") String quietStart,
            @RequestParam(value = "quietEnd", defaultValue = "08:00") String quietEnd,
            RedirectAttributes redirectAttributes) {

        Optional<Users> userOpt = usersRepository.findByEmail(authentication.getName());
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "User not found");
            return "redirect:/settings/notifications";
        }

        try {
            redirectAttributes.addFlashAttribute("success", "Notification settings updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "An error occurred: " + e.getMessage());
        }
        return "redirect:/settings/notifications";
    }

    /**
     * ADMIN: View all users
     */
    @GetMapping("/manage-users")
    public String manageUsers(Authentication authentication, Model model) {
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isSuperAdmin) return "redirect:/settings/profile";

        List<Users> allUsers = usersService.getAllUsers();
        long activeUserCount = allUsers.stream().filter(Users::isActive).count();

        model.addAttribute("users", allUsers);
        model.addAttribute("activeUserCount", activeUserCount);
        model.addAttribute("userTypes", usersTypeService.getAll());
        model.addAttribute("currentUserEmail", authentication.getName());
        return "settings-manage-users";
    }

    /**
     * ⭐ NEW: ADMIN: Show register new user form
     */
    @GetMapping("/manage-users/register")
    public String showRegisterUser(Authentication authentication, Model model) {
        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isSuperAdmin) return "redirect:/settings/profile";

        model.addAttribute("userTypes", usersTypeService.getAll());
        return "settings-register-user";
    }

    /**
     * ⭐ NEW: ADMIN: Process register new user with photo
     */
    @PostMapping("/manage-users/register")
    public String registerUser(
            Authentication authentication,
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            @RequestParam("userTypeId") String userTypeId,
            @RequestParam(value = "photo", required = false) MultipartFile photo,
            RedirectAttributes redirectAttributes) {

        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isSuperAdmin) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized access");
            return "redirect:/settings/profile";
        }

        // Validation
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Passwords do not match");
            return "redirect:/settings/manage-users/register";
        }
        if (password.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "Password must be at least 6 characters");
            return "redirect:/settings/manage-users/register";
        }
        if (usersRepository.findByEmail(email).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Email already exists: " + email);
            return "redirect:/settings/manage-users/register";
        }

        try {
            usersService.registerUser(email, password, userTypeId, photo);
            redirectAttributes.addFlashAttribute("success", "User '" + email + "' registered successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error registering user: " + e.getMessage());
            return "redirect:/settings/manage-users/register";
        }

        return "redirect:/settings/manage-users";
    }

    /**
     * ADMIN: Enable/Disable user
     */
    @PostMapping("/manage-users/toggle-status/{id}")
    public String toggleUserStatus(
            Authentication authentication,
            @PathVariable("id") UUID id,
            RedirectAttributes redirectAttributes) {

        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isSuperAdmin) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized access");
            return "redirect:/settings/profile";
        }

        Optional<Users> currentUserOpt = usersRepository.findByEmail(authentication.getName());
        if (currentUserOpt.isPresent() && currentUserOpt.get().getUserId().equals(id)) {
            redirectAttributes.addFlashAttribute("error", "You cannot disable your own account");
            return "redirect:/settings/manage-users";
        }

        try {
            usersService.toggleUserStatus(id);
            redirectAttributes.addFlashAttribute("success", "User status updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating user status: " + e.getMessage());
        }
        return "redirect:/settings/manage-users";
    }

    /**
     * ADMIN: Show edit user form
     */
    @GetMapping("/manage-users/edit/{id}")
    public String showEditUser(
            Authentication authentication,
            @PathVariable("id") UUID id,
            Model model) {

        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isSuperAdmin) return "redirect:/settings/profile";

        Optional<Users> userOpt = usersService.getUserById(id);
        if (userOpt.isEmpty()) return "redirect:/settings/manage-users";

        model.addAttribute("user", userOpt.get());
        model.addAttribute("userTypes", usersTypeService.getAll());
        return "settings-edit-user";
    }

    /**
     * ADMIN: Update user (with optional new photo)
     */
    @PostMapping("/manage-users/edit/{id}")
    public String updateUser(
            Authentication authentication,
            @PathVariable("id") UUID id,
            @RequestParam("email") String email,
            @RequestParam(value = "userTypeId", required = false) UUID userTypeId,
            @RequestParam(value = "active", defaultValue = "false") boolean active,
            @RequestParam(value = "photo", required = false) MultipartFile photo,
            RedirectAttributes redirectAttributes) {

        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isSuperAdmin) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized access");
            return "redirect:/settings/profile";
        }

        try {
            Optional<Users> userOpt = usersService.getUserById(id);
            if (userOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "User not found");
                return "redirect:/settings/manage-users";
            }

            Users user = userOpt.get();
            user.setEmail(email);
            user.setActive(active);

            if (userTypeId != null) {
                usersTypeService.getAll().stream()
                        .filter(ut -> ut.getUserTypeId().equals(userTypeId))
                        .findFirst()
                        .ifPresent(user::setUserTypeId);
            }

            // ⭐ Update photo if a new one was uploaded
            if (photo != null && !photo.isEmpty()) {
                String photoUrl = usersService.savePhoto(photo);
                user.setPhotoUrl(photoUrl);
            }

            usersRepository.save(user);
            redirectAttributes.addFlashAttribute("success", "User updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error updating user: " + e.getMessage());
        }
        return "redirect:/settings/manage-users";
    }

    /**
     * ADMIN: Delete user
     */
    @PostMapping("/manage-users/delete/{id}")
    public String deleteUser(
            Authentication authentication,
            @PathVariable("id") UUID id,
            RedirectAttributes redirectAttributes) {

        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isSuperAdmin) {
            redirectAttributes.addFlashAttribute("error", "Unauthorized access");
            return "redirect:/settings/profile";
        }

        try {
            Optional<Users> currentUserOpt = usersRepository.findByEmail(authentication.getName());
            if (currentUserOpt.isPresent() && currentUserOpt.get().getUserId().equals(id)) {
                redirectAttributes.addFlashAttribute("error", "You cannot delete your own account");
                return "redirect:/settings/manage-users";
            }
            usersService.deleteUser(id);
            redirectAttributes.addFlashAttribute("success", "User deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error deleting user: " + e.getMessage());
        }
        return "redirect:/settings/manage-users";
    }
}
