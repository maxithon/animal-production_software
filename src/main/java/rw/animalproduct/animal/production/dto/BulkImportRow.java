package rw.animalproduct.animal.production.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * One row parsed from the uploaded Excel file.
 * Also carries the validation result (status + message) so the preview
 * screen can render each row's outcome without a second pass.
 *
 * Supports both individual animals and batch/group rows (poultry, fish,
 * young rabbits, etc.). When isGroup is true, the row represents N
 * animals (see the quantity field), not one.
 */
@Data
public class BulkImportRow {

    /** 1-based row number in the uploaded file (row 1 = first data row). */
    private int rowNumber;

    // ── Required ────────────────────────────────────────────────────────────
    private String tagNumber;
    private String species;
    private String gender;

    // ── Optional ────────────────────────────────────────────────────────────
    private String breed;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;
    private String acquisitionMethod;
    private String healthStatus;
    private String reproductiveStatus;
    private String locationName;
    private String beneficiaryNid;
    private String representativeName;
    private String notes;
    private String photoUrl;

    // ── Batch support ───────────────────────────────────────────────────────
    private boolean isGroup = false;
    private Integer quantity;

    // ── Validation result ───────────────────────────────────────────────────
    private String status;
    private String message;

    // ── Auto-tag suggestion for duplicate tags ─────────────────────────────
    /**
     * When the row has an ERROR because its Tag Number already exists,
     * this field carries the next available tag (e.g. GOAT-046).
     * The UI shows it as a clickable "Use GOAT-046" pill.
     */
    private String suggestedTag;

    /**
     * True once the user clicks the "accept suggestion" pill.
     * The service will then auto-rewrite tagNumber = suggestedTag and
     * re-validate the row to OK.
     */
    private boolean suggestionAccepted = false;

    // ── Convenience helpers ─────────────────────────────────────────────────
    public boolean isOk()      { return "OK".equals(status); }
    public boolean isWarning() { return "WARNING".equals(status); }
    public boolean isError()   { return "ERROR".equals(status); }

    public boolean isImportable() { return isOk() || isWarning(); }

    public String getStatusBadgeClass() {
        if ("OK".equals(status))      return "bg-success-subtle text-success-emphasis";
        if ("WARNING".equals(status)) return "bg-warning-subtle text-warning-emphasis";
        return "bg-danger-subtle text-danger-emphasis";
    }

    /** True when this row has a fixable duplicate-tag problem. */
    public boolean hasSuggestedTag() {
        return suggestedTag != null && !suggestedTag.isBlank();
    }

    // ── Explicit accessors for the boolean fields (Lombok quirk) ────────────
    public boolean isGroup() { return isGroup; }
    public void setIsGroup(boolean isGroup) { this.isGroup = isGroup; }

    public boolean isSuggestionAccepted() { return suggestionAccepted; }
    public void setSuggestionAccepted(boolean suggestionAccepted) {
        this.suggestionAccepted = suggestionAccepted;
    }
}