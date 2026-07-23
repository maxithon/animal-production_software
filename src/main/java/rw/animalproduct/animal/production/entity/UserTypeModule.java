package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Maps to the existing `user_type_modules` table. One row = "this user type
 * (Admin / Regular User / Veterinarian / ...) has these permissions on this
 * module". This is exactly what the Module Assignment admin screen edits.
 */
@Entity
@Table(name = "user_type_modules")
public class UserTypeModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_type_module_id")
    private Integer userTypeModuleId;

    @Column(name = "user_type_id", nullable = false)
    private UUID userTypeId;

    @Column(name = "module_id", nullable = false)
    private Integer moduleId;

    @Column(name = "can_view")
    private boolean canView;

    @Column(name = "can_create")
    private boolean canCreate;

    @Column(name = "can_edit")
    private boolean canEdit;

    @Column(name = "can_delete")
    private boolean canDelete;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    public UserTypeModule() {
    }

    public Integer getUserTypeModuleId() { return userTypeModuleId; }
    public void setUserTypeModuleId(Integer userTypeModuleId) { this.userTypeModuleId = userTypeModuleId; }

    public UUID getUserTypeId() { return userTypeId; }
    public void setUserTypeId(UUID userTypeId) { this.userTypeId = userTypeId; }

    public Integer getModuleId() { return moduleId; }
    public void setModuleId(Integer moduleId) { this.moduleId = moduleId; }

    public boolean isCanView() { return canView; }
    public void setCanView(boolean canView) { this.canView = canView; }

    public boolean isCanCreate() { return canCreate; }
    public void setCanCreate(boolean canCreate) { this.canCreate = canCreate; }

    public boolean isCanEdit() { return canEdit; }
    public void setCanEdit(boolean canEdit) { this.canEdit = canEdit; }

    public boolean isCanDelete() { return canDelete; }
    public void setCanDelete(boolean canDelete) { this.canDelete = canDelete; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public UUID getAssignedBy() { return assignedBy; }
    public void setAssignedBy(UUID assignedBy) { this.assignedBy = assignedBy; }
}
