package rw.animalproduct.animal.production.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.services.MenuService;
import rw.animalproduct.animal.production.services.UsersService;

import java.util.Collections;
import java.util.Optional;

/**
 * Runs before every @Controller handler and adds "sidebarMenu" (and
 * "isAdmin") to the Model automatically, for every page. This is the piece
 * that makes the menu dynamic: admin-dashboard.html and user-dashboard.html
 * no longer hard-code their <aside> — they just loop over ${sidebarMenu},
 * see fragments/sidebar.html.
 */
@ControllerAdvice
public class MenuControllerAdvice {

    private final MenuService menuService;
    private final UsersService usersService;

    public MenuControllerAdvice(MenuService menuService, UsersService usersService) {
        this.menuService = menuService;
        this.usersService = usersService;
    }

    @ModelAttribute
    public void injectSidebarMenu(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return;
        }

        Optional<Users> userOpt = usersService.findByEmail(auth.getName());
        if (userOpt.isEmpty()) {
            model.addAttribute("sidebarMenu", Collections.emptyList());
            return;
        }

        Users user = userOpt.get();
        model.addAttribute("sidebarMenu", menuService.buildMenuForUser(user.getUserId()));
        model.addAttribute("isAdmin",
                user.getUserTypeId() != null
                        && "ADMIN".equalsIgnoreCase(user.getUserTypeId().getUserTypeName()));
    }
}
