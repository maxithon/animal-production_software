package rw.animalproduct.animal.production.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.services.UsersService;

import java.util.List;

/**
 * Handles user management from the settings menu.
 * URL: /settings/users (updated from /settings/manage-users to avoid conflict)
 */
@Controller
public class SettingsUsersController {

    private final UsersService usersService;

    public SettingsUsersController(UsersService usersService) {
        this.usersService = usersService;
    }

    @GetMapping("/settings/users")  // CHANGED: Was "/settings/manage-users"
    public String manageUsers(Model model) {
        List<Users> usersList = usersService.getAllUsers();
        model.addAttribute("usersList", usersList);
        return "users-list";
    }
}