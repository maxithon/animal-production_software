package rw.animalproduct.animal.production.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * A single node in the rendered sidebar tree — either a top-level section
 * (e.g. "Livestock") with children, or a leaf link (e.g. "Sick Animals").
 * Built by MenuService from the flat rows returned by v_user_permissions.
 */
public class MenuNode {

    private Integer moduleId;
    private String moduleName;
    private String moduleCode;
    private String moduleUrl;
    private String icon;
    private Integer displayOrder;
    private List<MenuNode> children = new ArrayList<>();

    public Integer getModuleId() { return moduleId; }
    public void setModuleId(Integer moduleId) { this.moduleId = moduleId; }

    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }

    public String getModuleCode() { return moduleCode; }
    public void setModuleCode(String moduleCode) { this.moduleCode = moduleCode; }

    public String getModuleUrl() { return moduleUrl; }
    public void setModuleUrl(String moduleUrl) { this.moduleUrl = moduleUrl; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public List<MenuNode> getChildren() { return children; }
    public void setChildren(List<MenuNode> children) { this.children = children; }
}
