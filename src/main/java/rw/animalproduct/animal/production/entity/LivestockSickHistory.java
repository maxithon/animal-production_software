package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit trail for every status change on a LivestockSick episode.
 */
@Entity
@Table(name = "livestock_sick_history",
        indexes = {
                @Index(name = "idx_sick_history_sick_id",    columnList = "sick_id"),
                @Index(name = "idx_sick_history_changed_at", columnList = "changed_at"),
                @Index(name = "idx_sick_history_status",     columnList = "status")
        })
public class LivestockSickHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sick_id", nullable = false)
    private LivestockSick livestockSick;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LivestockSick.SickStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity_level", length = 20)
    private LivestockSick.SeverityLevel severityLevel;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by", length = 150)
    private String changedBy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    public LivestockSickHistory() {}

    public LivestockSickHistory(LivestockSick sick,
                                LivestockSick.SickStatus status,
                                LivestockSick.SeverityLevel severityLevel,
                                String changedBy,
                                String notes) {
        this.livestockSick = sick;
        this.status        = status;
        this.severityLevel = severityLevel;
        this.changedAt     = LocalDateTime.now();
        this.changedBy     = changedBy;
        this.notes         = notes;
    }

    // ── Getters & Setters ────────────────────────────────────────────
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LivestockSick getLivestockSick() { return livestockSick; }
    public void setLivestockSick(LivestockSick livestockSick) { this.livestockSick = livestockSick; }

    public LivestockSick.SickStatus getStatus() { return status; }
    public void setStatus(LivestockSick.SickStatus status) { this.status = status; }

    public LivestockSick.SeverityLevel getSeverityLevel() { return severityLevel; }
    public void setSeverityLevel(LivestockSick.SeverityLevel severityLevel) { this.severityLevel = severityLevel; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }

    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }
}
