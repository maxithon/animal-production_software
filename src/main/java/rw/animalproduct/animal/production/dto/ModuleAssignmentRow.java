package rw.animalproduct.animal.production.dto;

/**
 * One row of the Module Assignment matrix for a given user type.
 * Used as the JSON shape for both GET (load matrix) and POST (save matrix)
 * on ModuleAssignmentController.
 */
public class ModuleAssignmentRow {

    private Integer moduleId;
    private String moduleName;
    private String moduleCode;
    private Integer parentModuleId;
    private String icon;
    private Integer displayOrder;
    private boolean canView;
    private boolean canCreate;
    private boolean canEdit;
    private boolean canDelete;

    public ModuleAssignmentRow() {
    }

    public Integer getModuleId() { return moduleId; }
    public void setModuleId(Integer moduleId) { this.moduleId = moduleId; }

    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public String getModuleCode() { return moduleCode; }
    public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }

    public Integer getParentModuleId() { return parentModuleId; }
    public void setParentModuleId(Integer parentModuleId) { this.parentModuleId = parentModuleId; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public boolean isCanView() { return canView; }
    public void setCanView(boolean canView) { this.canView = canView; }

    public boolean isCanCreate() { return canCreate; }
    public void setCanCreate(boolean canCreate) { this.canCreate = canCreate; }

    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }

    public boolean isCanDelete() { return canDelete; }
    public void setCanDelete(boolean canDelete) { this.canDelete = canDelete; }
}
