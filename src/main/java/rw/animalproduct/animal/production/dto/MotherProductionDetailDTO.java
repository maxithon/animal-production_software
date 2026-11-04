package rw.animalproduct.animal.production.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MotherProductionDetailDTO {

    private UUID animalId;
    private String tagNumber;
    private String categoryName;
    private LocalDate motherBirthDate;
    private String status;
    private String reproductiveStatus;

    private long numberOfBirths;
    private long totalOffspring;
    private long maleOffspring;
    private long femaleOffspring;
    private long deathCount;
    private long soldCount;
    private long transferredCount;
    private long weanedCount;
    private long stillbornCount;

    private LocalDate lastBreedingDate;
    private LocalDate expectedDueDate;
    private Integer daysSinceLastBirth;

    private List<BirthEventDTO> birthHistory;

    public long getUnknownOffspring() {
        return Math.max(0, totalOffspring - maleOffspring - femaleOffspring);
    }

    public long getAliveCount() {
        return Math.max(0, totalOffspring - deathCount - soldCount - transferredCount);
    }

    public Double getAverageOffspringPerBirth() {
        if (numberOfBirths == 0) return 0.0;
        return Math.round((totalOffspring / (double) numberOfBirths) * 100) / 100.0;
    }

    public Double getSurvivalRate() {
        long denominator = totalOffspring - soldCount - transferredCount;
        if (denominator == 0) return null;
        long alive = getAliveCount();
        return Math.round((alive / (double) denominator) * 1000) / 10.0;
    }

    public String getCurrentAgeDisplay() {
        if (motherBirthDate == null) return "Unknown";
        Period p = Period.between(motherBirthDate, LocalDate.now());
        if (p.getYears() > 0) return p.getYears() + "y " + p.getMonths() + "m";
        if (p.getMonths() > 0) return p.getMonths() + "m " + p.getDays() + "d";
        return p.getDays() + "d";
    }

    public String getReproductiveStatusLabel() {
        if (reproductiveStatus == null) return "Unknown";
        return switch (reproductiveStatus.toUpperCase()) {
            case "PREGNANT" -> "🤰 Pregnant";
            case "READY_TO_BREED" -> "✅ Ready to Breed";
            case "LACTATING" -> "🍼 Lactating";
            case "NURSING" -> "🍼 Nursing";
            case "OPEN" -> "⭕ Open";
            case "DRY" -> "💧 Dry";
            case "RESTING" -> "😴 Resting";
            default -> reproductiveStatus;
        };
    }

    public String getDaysSinceLastBirthDisplay() {
        if (daysSinceLastBirth == null) return "—";
        if (daysSinceLastBirth == 0) return "Today";
        if (daysSinceLastBirth == 1) return "1 day ago";
        return daysSinceLastBirth + " days ago";
    }
}