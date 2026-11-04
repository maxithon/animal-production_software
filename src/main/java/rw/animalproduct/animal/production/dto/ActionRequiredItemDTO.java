package rw.animalproduct.animal.production.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A single row in the "⚠️ Actions Required" panel at the top of the
 * Breeding Management dashboard.
 */
@Getter
@Builder
public class ActionRequiredItemDTO {

    public enum Severity { RED, YELLOW }
    public enum Type {
        OVERDUE_PREGNANCY_CHECK,
        OVERDUE_DUE_DATE,
        BECOMING_READY_SOON,
        NOT_READY
    }

    private Type type;
    private Severity severity;
    private String icon;          // "🔴" / "🟡"
    private String title;         // short label
    private String message;       // full sentence for the row
    private UUID livestockId;
    private String tagNumber;
    private LocalDate relevantDate; // due date / check date / ready date
    private Long daysValue;         // days overdue OR days until ready
    private String actionUrl;
}