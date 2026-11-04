package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Log of automatic lifecycle notifications — used as a dedupe guard so each
 * milestone email is sent at most once per animal (or per breeding).
 *
 * ⚠️ Column mapping matches the ACTUAL database schema:
 *   - sent_at        → LocalDateTime (when the notification was sent)
 *   - reference_date → LocalDate     (business day the notification refers to)
 *   - additional_info→ free-form text
 *
 * The old getSentDate()/setSentDate()/getCreatedAt()/setCreatedAt() methods are
 * preserved as @Transient aliases so existing code (LifecycleNotificationScheduler)
 * continues to compile and run unchanged.
 *
 * @author HABANABAKIZE & NDAYIZEYE
 * @version 1.1.0
 */
@Entity
@Table(name = "livestock_notification_log",
        uniqueConstraints = @UniqueConstraint(columnNames = {"dedupe_key"}))
public class LivestockNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "livestock_id", nullable = false)
    private UUID livestockId;

    @Column(name = "breeding_id")
    private UUID breedingId;

    @Column(name = "notification_type", nullable = false, length = 100)
    private String notificationType;

    @Column(name = "dedupe_key", nullable = false, length = 255)
    private String dedupeKey;

    /** Real DB column: sent_at (timestamp). */
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    /** Real DB column: reference_date (date). */
    @Column(name = "reference_date")
    private LocalDate referenceDate;

    @Column(name = "additional_info", columnDefinition = "TEXT")
    private String additionalInfo;

    public LivestockNotificationLog() {}

    public LivestockNotificationLog(UUID livestockId, String notificationType, String dedupeKey) {
        this.livestockId = livestockId;
        this.notificationType = notificationType;
        this.dedupeKey = dedupeKey;
        this.sentAt = LocalDateTime.now();
        this.referenceDate = LocalDate.now();
    }

    public LivestockNotificationLog(UUID livestockId, UUID breedingId, String notificationType,
                                    String dedupeKey, LocalDate sentDate) {
        this.livestockId = livestockId;
        this.breedingId = breedingId;
        this.notificationType = notificationType;
        this.dedupeKey = dedupeKey;
        this.sentAt = LocalDateTime.now();
        this.referenceDate = sentDate != null ? sentDate : LocalDate.now();
    }

    @PrePersist
    protected void onCreate() {
        if (sentAt == null) sentAt = LocalDateTime.now();
        if (referenceDate == null) referenceDate = LocalDate.now();
    }

    // ─── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getLivestockId() { return livestockId; }
    public void setLivestockId(UUID livestockId) { this.livestockId = livestockId; }

    public UUID getBreedingId() { return breedingId; }
    public void setBreedingId(UUID breedingId) { this.breedingId = breedingId; }

    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }

    public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String dedupeKey) { this.dedupeKey = dedupeKey; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public LocalDate getReferenceDate() { return referenceDate; }
    public void setReferenceDate(LocalDate referenceDate) { this.referenceDate = referenceDate; }

    public String getAdditionalInfo() { return additionalInfo; }
    public void setAdditionalInfo(String additionalInfo) { this.additionalInfo = additionalInfo; }

    // ─── Backwards-compatibility aliases ────────────────────────────────────────
    // The scheduler calls setSentDate(...) and setCreatedAt(...). These aliases
    // map those calls onto the REAL DB columns (sent_at, reference_date) so no
    // other file has to change.

    /** @deprecated use {@link #getSentAt()} */
    @Deprecated
    @Transient
    public LocalDate getSentDate() {
        return sentAt != null ? sentAt.toLocalDate() : null;
    }

    /** @deprecated maps to sent_at (this schema has no separate sent_date column) */
    @Deprecated
    @Transient
    public void setSentDate(LocalDate sentDate) {
        if (sentDate != null) {
            LocalDateTime existing = this.sentAt;
            this.sentAt = (existing != null)
                    ? sentDate.atTime(existing.toLocalTime())
                    : sentDate.atStartOfDay();
            this.referenceDate = sentDate;
        }
    }

    /** @deprecated maps to sent_at (this schema has no separate created_at column) */
    @Deprecated
    @Transient
    public LocalDateTime getCreatedAt() {
        return sentAt;
    }

    /** @deprecated no created_at column exists; sets sent_at if not already set */
    @Deprecated
    @Transient
    public void setCreatedAt(LocalDateTime createdAt) {
        if (createdAt != null && this.sentAt == null) {
            this.sentAt = createdAt;
        }
    }
}