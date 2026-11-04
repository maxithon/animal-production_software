package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit trail of every bulk import.
 * Lets you answer: "who imported 500 animals on Tuesday, and what file did
 * they upload?" — a question every agriculture system gets asked eventually.
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "livestock_import_batch")
public class LivestockImportBatch {

    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED    = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "imported_by")
    private UUID importedBy;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "total_rows",   nullable = false)
    private Integer totalRows   = 0;

    @Column(name = "ok_rows",      nullable = false)
    private Integer okRows      = 0;

    @Column(name = "warning_rows", nullable = false)
    private Integer warningRows = 0;

    @Column(name = "error_rows",   nullable = false)
    private Integer errorRows   = 0;

    @Column(name = "created_count", nullable = false)
    private Integer createdCount = 0;

    @Column(name = "created_tags", columnDefinition = "TEXT")
    private String createdTags;

    @Column(name = "status", nullable = false, length = 30)
    private String status = STATUS_COMPLETED;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (importedAt == null) importedAt = LocalDateTime.now();
        if (status == null) status = STATUS_COMPLETED;
        if (totalRows == null)   totalRows = 0;
        if (okRows == null)      okRows = 0;
        if (warningRows == null) warningRows = 0;
        if (errorRows == null)   errorRows = 0;
        if (createdCount == null) createdCount = 0;
    }

    public String getStatusBadgeClass() {
        if (STATUS_COMPLETED.equals(status)) return "bg-success-subtle text-success-emphasis";
        if (STATUS_FAILED.equals(status))    return "bg-danger-subtle text-danger-emphasis";
        return "bg-secondary-subtle text-secondary-emphasis";
    }

    public String getFileSizeDisplay() {
        if (fileSizeBytes == null) return "—";
        if (fileSizeBytes < 1024)             return fileSizeBytes + " B";
        if (fileSizeBytes < 1024 * 1024)      return (fileSizeBytes / 1024) + " KB";
        return String.format("%.1f MB", fileSizeBytes / 1024.0 / 1024.0);
    }
}