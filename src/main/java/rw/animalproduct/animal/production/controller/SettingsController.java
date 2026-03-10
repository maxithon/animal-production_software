package rw.animalproduct.animal.production.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.repository.UsersRepository;
import rw.animalproduct.animal.production.services.UsersService;

import java.util.Optional;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final UsersRepository usersRepository;
    private final UsersService usersService;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public SettingsController(UsersRepository usersRepository,
                             UsersService usersService,
                             PasswordEncoder passwordEncoder) {
        this.usersRepository = usersRepository;
        this.usersService = usersService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Display user profile page
     */
    @GetMapping("/profile")
    public String showProfile(Authentication authentication, Model model) {
        String email = authentication.getName();
        Optional<Users> userOpt = usersRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            return "redirect:/";
        }

        model.addAttribute("user", userOpt.get());
        return "settings-profile";
    }

    /**
     * Display change password page
     */
    @GetMapping("/change-password")
    public String showChangePassword() {
        return "settings-change-password";
    }

    /**
     * Handle password change request
     */
    @PostMapping("/change-password")
    public String changePassword(
            Authentication authentication,
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            RedirectAttributes redirectAttributes,
            Model model) {

        String email = authentication.getName();
        Optional<Users> userOpt = usersRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "User not found");
            return "redirect:/settings/change-password";
        }

        Users user = userOpt.get();

        // Validate current password
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "Current password is incorrect");
            return "redirect:/settings/change-password";
        }

        // Validate new password is not empty
        if (newPassword == null || newPassword.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "New password cannot be empty");
            return "redirect:/settings/change-password";
        }

        // Validate new password length
        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("error", "New password must be at least 6 characters");
            return "redirect:/settings/change-password";
        }

        // Validate passwords match
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New passwords do not match");
            return "redirect:/settings/change-password";
        }

        // Validate new password is different from current
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "New password must be different from current password");
            return "redirect:/settings/change-password";
        }

        try {
            // Update password
            user.setPassword(passwordEncoder.encode(newPassword));
            usersRepository.save(user);
            redirectAttributes.addFlashAttribute("success", "Password changed successfully!");
            return "redirect:/settings/change-password";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "An error occurred while changing password: " + e.getMessage());
            return "redirect:/settings/change-password";
        }
    }

    /**
     * Display notification settings page
     */
    @GetMapping("/notifications")
    public String showNotificationSettings(Authentication authentication, Model model) {
        String email = authentication.getName();
        Optional<Users> userOpt = usersRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            return "redirect:/";
        }

        model.addAttribute("user", userOpt.get());
        return "settings-notifications";
    }

    /**
     * Handle notification settings update
     * Note: Currently just shows success message. 
     * In production, create a NotificationSettings entity and table to persist these settings.
     */
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

        String email = authentication.getName();
        Optional<Users> userOpt = usersRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "User not found");
            return "redirect:/settings/notifications";
        }

        try {
            // TODO: In production, create NotificationSettings entity and save preferences
            // For now, just validate the input and show success
            
            // Log the settings (optional)
            System.out.println("User: " + email);
            System.out.println("Email Notifications: " + emailNotifications);
            System.out.println("SMS Notifications: " + smsNotifications);
            System.out.println("App Notifications: " + appNotifications);
            System.out.println("Frequency: " + frequency);
            System.out.println("Quiet Hours: " + quietStart + " to " + quietEnd);

            redirectAttributes.addFlashAttribute("success", "Notification settings updated successfully!");
            return "redirect:/settings/notifications";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "An error occurred: " + e.getMessage());
            return "redirect:/settings/notifications";
        }
    }
}
