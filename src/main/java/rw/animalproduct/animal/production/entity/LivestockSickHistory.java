package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit trail for every status change on a LivestockSick episode.
 * One row is written each time the status (or severity) changes.
 * Records are never updated — only inserted.
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

    /** Which sick episode this history row belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sick_id", nullable = false)
    private LivestockSick livestockSick;

    /** The new status that was set at this point in time. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LivestockSick.SickStatus status;

    /** The severity at the time of this change (may be null if not changed). */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity_level", length = 20)
    private LivestockSick.SeverityLevel severityLevel;

    /** Exact timestamp when the change was recorded. */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    /**
     * Username of the person who made the change.
     * Auto-captured from Spring Security principal.
     */
    @Column(name = "changed_by", length = 150)
    private String changedBy;

    /**
     * Free-text reason / notes provided at the time of the status change.
     * Mirrors treatment_notes from the sick record at that moment.
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public LivestockSickHistory() {}

    // ── Convenience constructor ───────────────────────────────────────
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
}
