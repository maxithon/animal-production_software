package rw.animalproduct.animal.production.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class UpcomingBirthsReportDTO {

    private UUID breedingId;
    private UUID livestockId;
    private String tagNumber;
    private String categoryName;
    private String gender;
    private String animalStatus;
    private String breedingStatus;
    private LocalDate breedingDate;
    private LocalDate conceptionDate;
    private LocalDate expectedDueDate;
    private LocalDate pregnancyConfirmedDate;
    private String breedingMethod;
    private String inseminationMethod;
    private String acquisitionMethod;
    private String location;
    private Integer expectedOffspring;

    /** Negative = overdue, 0 = due today, positive = days remaining. */
    private Long daysUntilDue;

    // -- Derived --------------------------------------------------------------

    /** Four-tier operational status: OVERDUE, DUE_SOON (≤3d), DUE_THIS_WEEK (≤7d), UPCOMING. */
    public String getStatus() {
        if (daysUntilDue == null) return "UNKNOWN";
        if (daysUntilDue < 0) return "OVERDUE";
        if (daysUntilDue <= 3) return "DUE_SOON";
        if (daysUntilDue <= 7) return "DUE_THIS_WEEK";
        return "UPCOMING";
    }

    public String getStatusLabel() {
        return switch (getStatus()) {
            case "OVERDUE" -> "Overdue";
            case "DUE_SOON" -> "Due \u22643 Days";
            case "DUE_THIS_WEEK" -> "Due This Week";
            case "UPCOMING" -> "Upcoming";
            default -> "Unknown";
        };
    }
}