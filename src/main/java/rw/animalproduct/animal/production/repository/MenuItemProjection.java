package rw.animalproduct.animal.production.dto;

/**
 * Spring Data JPA interface projection mapped onto columns returned by the
 * existing `v_user_permissions` view. Field names below are the projection's
 * getter names; Spring Data matches them to the aliases used in the native
 * @Query in MenuRepository.
 */
public interface MenuItemProjection {
    Integer getModuleId();
    String getModuleName();
    String getModuleCode();
    String getModuleUrl();
    Integer getParentModuleId();
    String getIcon();
    Integer getDisplayOrder();
    Boolean getCanView();
    Boolean getCanCreate();
    Boolean getCanEdit();
    Boolean getCanDelete();
}
