package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Maps to the existing `modules` table (already populated in your DB with
 * module_id 1-70). This is READ-mostly from the app's point of view — module
 * definitions themselves are seeded via SQL, not created through the UI, so
 * there is no generated-id concern here.
 */
@Entity
@Table(name = "modules")
public class Module {

    @Id
    @Column(name = "module_id")
    private Integer moduleId;

    @Column(name = "module_name")
    private String moduleName;

    @Column(name = "module_code")
    private String moduleCode;

    private String description;

    private String icon;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "is_active")
    private boolean active;

    @Column(name = "parent_module_id")
    private Integer parentModuleId;

    @Column(name = "module_url")
    private String moduleUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Module() {
    }

    public Integer getModuleId() { return moduleId; }
    public void setModuleId(Integer moduleId) { this.moduleId = moduleId; }

    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public String getModuleCode() { return moduleCode; }
    public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getParentModuleId() { return parentModuleId; }
    public void setParentModuleId(Integer parentModuleId) { this.parentModuleId = parentModuleId; }

    public String getModuleUrl() { return moduleUrl; }
    public void setModuleUrl(String moduleUrl) { this.moduleUrl = moduleUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
