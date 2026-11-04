package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregated result of parsing/validating an uploaded file.
 * Used both for the preview screen (before confirmation) and
 * the success screen (after persistence).
 */
@Data
public class BulkImportResult {

    // ── File metadata ───────────────────────────────────────────────────────
    private String  fileName;
    private long    fileSizeBytes;
    private String  fileError;

    // ── Per-row results ─────────────────────────────────────────────────────
    private List<BulkImportRow> rows = new ArrayList<>();

    // ── Counters ────────────────────────────────────────────────────────────
    private int totalRows;
    private int okRows;
    private int warningRows;
    private int errorRows;

    // ── Populated after a confirmed import ──────────────────────────────────
    private int          createdCount;
    private List<String> createdTags = new ArrayList<>();
    private UUID         importBatchId;
    private String       importBatchTimestamp;

    // ── Helpers ─────────────────────────────────────────────────────────────
    public boolean hasFileError() {
        return fileError != null && !fileError.isBlank();
    }

    public boolean isReadyToImport() {
        return !hasFileError() && errorRows == 0 && (okRows + warningRows) > 0;
    }

    public boolean hasAnyImportableRows() {
        return (okRows + warningRows) > 0;
    }

    public String getFileSizeDisplay() {
        if (fileSizeBytes < 1024)              return fileSizeBytes + " B";
        if (fileSizeBytes < 1024 * 1024)       return (fileSizeBytes / 1024) + " KB";
        return String.format("%.1f MB", fileSizeBytes / 1024.0 / 1024.0);
    }
}