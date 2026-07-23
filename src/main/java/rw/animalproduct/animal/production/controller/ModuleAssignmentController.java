package rw.animalproduct.animal.production.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import rw.animalproduct.animal.production.dto.ModuleAssignmentRow;
import rw.animalproduct.animal.production.entity.Users;
import rw.animalproduct.animal.production.services.ModuleAssignmentService;
import rw.animalproduct.animal.production.services.UsersService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin-only screen at /settings/module-assignment. An admin picks a user
 * type (tab), sees every module with four checkboxes (view/create/edit/
 * delete), and saves. That save writes straight into user_type_modules,
 * which is exactly the table v_user_permissions (and therefore MenuService)
 * reads from — so the effect is immediate for every user of that type.
 */
@Controller
@RequestMapping("/settings/module-assignment")
public class ModuleAssignmentController {
    // NOTE: access control for ADMIN-only is enforced in SecurityConfig via
    // .requestMatchers("/settings/module-assignment/**").hasRole("ADMIN")
    // — see README.md. Not using @PreAuthorize here since this codebase
    // doesn't have @EnableMethodSecurity configured anywhere else, and
    // adding it just for this controller would be inconsistent with how
    // every other admin-only area (/users/**, /admin/**) is protected.

    private final ModuleAssignmentService moduleAssignmentService;
    private final UsersService usersService;

    public ModuleAssignmentController(ModuleAssignmentService moduleAssignmentService,
                                       UsersService usersService) {
        this.moduleAssignmentService = moduleAssignmentService;
        this.usersService = usersService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("userTypes", moduleAssignmentService.getAllUserTypes());
        model.addAttribute("modules", moduleAssignmentService.getAllModulesOrdered());
        return "settings-module-assignment";
    }

    @GetMapping("/api/{userTypeId}")
    @ResponseBody
    public List<ModuleAssignmentRow> getMatrix(@PathVariable UUID userTypeId) {
        return moduleAssignmentService.getAssignmentMatrix(userTypeId);
    }

    @PostMapping("/api/{userTypeId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveMatrix(@PathVariable UUID userTypeId,
                                                            @RequestBody List<ModuleAssignmentRow> rows,
                                                            Authentication authentication) {
        UUID assignedBy = null;
        if (authentication != null) {
            Optional<Users> admin = usersService.findByEmail(authentication.getName());
            assignedBy = admin.map(Users::getUserId).orElse(null);
        }
        moduleAssignmentService.saveAssignmentMatrix(userTypeId, rows, assignedBy);
        return ResponseEntity.ok(Map.of("success", true, "savedRows", rows.size()));
    }
}
