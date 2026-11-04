package rw.animalproduct.animal.production.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class OverduePregnanciesReportDTO {

    private UUID breedingId;
    private UUID livestockId;
    private String tagNumber;
    private String categoryName;
    private String gender;
    private String animalStatus;      // livestock.status (ACTIVE/SOLD/DEAD)
    private String breedingStatus;    // livestock_breeding.status
    private LocalDate conceptionDate;
    private LocalDate expectedDueDate;
    private LocalDate pregnancyConfirmedDate;
    private String breedingMethod;
    private String inseminationMethod;
    private String acquisitionMethod;

    /** Always positive here — the query only returns rows past their due date. */
    private Long daysOverdue;

    // -- Derived (not persisted, not set by the builder) ---------------------

    /** FAO-style severity tiering: 1-7d Watch, 8-14d Concern, 15d+ Critical. */
    public String getSeverity() {
        if (daysOverdue == null) return "UNKNOWN";
        if (daysOverdue >= 15) return "CRITICAL";
        if (daysOverdue >= 8) return "CONCERN";
        return "WATCH";
    }

    public String getSeverityLabel() {
        return switch (getSeverity()) {
            case "CRITICAL" -> "Critical";
            case "CONCERN" -> "Concern";
            case "WATCH" -> "Watch";
            default -> "Unknown";
        };
    }
}
