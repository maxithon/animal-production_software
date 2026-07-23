package rw.animalproduct.animal.production.services;

import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.MenuItemProjection;
import rw.animalproduct.animal.production.dto.MenuNode;
import rw.animalproduct.animal.production.repository.MenuRepository;

import java.util.*;

@Service
public class MenuService {

    private final MenuRepository menuRepository;

    public MenuService(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    /**
     * Builds the two-level sidebar tree (section -> links) for one user,
     * based purely on what has been assigned to that user's user_type in
     * user_type_modules. Nothing is hard-coded — add/remove a checkbox on
     * the Module Assignment screen and every user of that type sees the
     * change on their next request.
     */
    public List<MenuNode> buildMenuForUser(UUID userId) {
        List<MenuItemProjection> rows = menuRepository.findMenuForUser(userId);

        Map<Integer, MenuNode> byId = new LinkedHashMap<>();
        for (MenuItemProjection row : rows) {
            MenuNode node = new MenuNode();
            node.setModuleId(row.getModuleId());
            node.setModuleName(row.getModuleName());
            node.setModuleCode(row.getModuleCode());
            node.setModuleUrl(row.getModuleUrl());
            node.setIcon(row.getIcon());
            node.setDisplayOrder(row.getDisplayOrder() == null ? 0 : row.getDisplayOrder());
            byId.put(row.getModuleId(), node);
        }

        List<MenuNode> roots = new ArrayList<>();
        for (MenuItemProjection row : rows) {
            MenuNode node = byId.get(row.getModuleId());
            Integer parentId = row.getParentModuleId();
            if (parentId == null) {
                roots.add(node);
            } else {
                MenuNode parent = byId.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(node);
                } else {
                    // Parent container wasn't assigned to this user type
                    // (shouldn't normally happen if assignment is done
                    // sensibly), fall back to showing it at top level so it
                    // isn't silently lost.
                    roots.add(node);
                }
            }
        }

        Comparator<MenuNode> byOrder = Comparator.comparing(
                n -> n.getDisplayOrder() == null ? 0 : n.getDisplayOrder());
        roots.sort(byOrder);
        for (MenuNode root : roots) {
            root.getChildren().sort(byOrder);
        }
        return roots;
    }
}
