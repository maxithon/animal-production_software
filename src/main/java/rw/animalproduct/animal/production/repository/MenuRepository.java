package rw.animalproduct.animal.production.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rw.animalproduct.animal.production.dto.MenuItemProjection;
import rw.animalproduct.animal.production.entity.Module;

import java.util.List;
import java.util.UUID;

/**
 * Reads the menu a given user is allowed to see, straight from your existing
 * v_user_permissions view. This is what makes the sidebar dynamic: whatever
 * an admin assigns in the Module Assignment screen shows up here on the
 * user's very next page load — no code changes, no redeploys.
 */
@Repository
public interface MenuRepository extends JpaRepository<Module, Integer> {

    @Query(value =
            "SELECT module_id        AS moduleId, " +
            "       module_name      AS moduleName, " +
            "       module_code      AS moduleCode, " +
            "       module_url       AS moduleUrl, " +
            "       parent_module_id AS parentModuleId, " +
            "       icon             AS icon, " +
            "       display_order    AS displayOrder, " +
            "       can_view         AS canView, " +
            "       can_create       AS canCreate, " +
            "       can_edit         AS canEdit, " +
            "       can_delete       AS canDelete " +
            "FROM v_user_permissions " +
            "WHERE user_id = :userId AND can_view = true " +
            "ORDER BY COALESCE(parent_module_id, module_id), display_order",
            nativeQuery = true)
    List<MenuItemProjection> findMenuForUser(@Param("userId") UUID userId);
}
