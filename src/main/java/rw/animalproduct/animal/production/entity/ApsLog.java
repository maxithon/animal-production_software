package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "aps_log")
public class ApsLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "table_name", nullable = false, length = 100)
    private String tableName;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    // ✅ ENUM instead of String (matches CHECK constraint)
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private ActionType action;

    @Column(name = "field_changed", length = 100)
    private String fieldChanged;

    // TEXT fields

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // FK → sec_user(user_id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by", referencedColumnName = "user_id")
    private Users performedBy;

    @Column(name = "performed_at", nullable = false)
    private LocalDateTime performedAt;

    // ── ENUM ─────────────────────────────────────────────
    public enum ActionType {
        INSERT,
        UPDATE,
        DELETE,
        RESTORE,
        STATUS_CHANGE
    }
    // ─────────────────────────────────────────────────────

    // ── Lifecycle ─────────────────────────────────────────
    @PrePersist
    protected void onCreate() {
        if (performedAt == null) {
            performedAt = LocalDateTime.now();
        }
    }

    // ── Constructors ──────────────────────────────────────
    public ApsLog() {}

    public ApsLog(String tableName, UUID recordId, ActionType action, String notes) {
        this.tableName = tableName;
        this.recordId = recordId;
        this.action = action;
        this.notes = notes;
    }

    // ── Getters & Setters ─────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public UUID getRecordId() { return recordId; }
    public void setRecordId(UUID recordId) { this.recordId = recordId; }

    public ActionType getAction() { return action; }
    public void setAction(ActionType action) { this.action = action; }

    public String getFieldChanged() { return fieldChanged; }
    public void setFieldChanged(String fieldChanged) { this.fieldChanged = fieldChanged; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Users getPerformedBy() { return performedBy; }
    public void setPerformedBy(Users performedBy) { this.performedBy = performedBy; }

    public LocalDateTime getPerformedAt() { return performedAt; }
    public void setPerformedAt(LocalDateTime performedAt) { this.performedAt = performedAt; }

    // ── Helper Methods ────────────────────────────────────

    public boolean isInsert() { return action == ActionType.INSERT; }
    public boolean isUpdate() { return action == ActionType.UPDATE; }
    public boolean isDelete() { return action == ActionType.DELETE; }

    @Override
    public String toString() {
        return "ApsLog{" +
                "tableName='" + tableName + '\'' +
                ", recordId=" + recordId +
                ", action=" + action +
                ", performedAt=" + performedAt +
                '}';
    }
}