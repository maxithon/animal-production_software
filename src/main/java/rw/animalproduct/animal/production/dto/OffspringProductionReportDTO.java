package rw.animalproduct.animal.production.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OffspringProductionReportDTO {

    // ── Animal Identification ──
    private UUID animalId;
    private String tagNumber;
    private String categoryName;
    private LocalDate motherBirthDate;
    private String status;
    private String reproductiveStatus;

    // ── Ranking ──
    private long rank;

    // ── Production Metrics ──
    private long numberOfBirths;
    private long totalOffspring;
    private long maleOffspring;
    private long femaleOffspring;
    private LocalDate firstBirthDate;
    private LocalDate lastBirthDate;
    private long deathCount;
    private long soldCount;
    private long transferredCount;
    private long weanedCount;

    // ── Reproductive Details ──
    private LocalDate lastBreedingDate;
    private LocalDate expectedDueDate;
    private Integer daysSinceLastBirth;
    private Integer daysSinceLastBreeding;

    // ── Location ──
    private String locationName;
    private String province;
    private String district;
    private String sector;
    private String cell;
    private String village;

    // ── Beneficiary (Dynamic from database) ──
    private String beneficiaryName;
    private String beneficiaryPhone;
    private String beneficiaryFirstName;
    private String beneficiaryLastName;

    // ── Calculated Fields ──

    public Double getAverageOffspringPerBirth() {
        if (numberOfBirths == 0) return 0.0;
        return Math.round((totalOffspring / (double) numberOfBirths) * 100) / 100.0;
    }

    public long getUnknownOffspring() {
        return Math.max(0, totalOffspring - maleOffspring - femaleOffspring);
    }

    /**
     * Survival Rate = Alive ÷ (Total Offspring - Sold - Transferred) × 100
     * Sold/transferred are excluded from the denominator because they are no
     * longer in the system to track.
     */
    public Double getSurvivalRate() {
        long denominator = totalOffspring - soldCount - transferredCount;
        if (denominator == 0) return null;
        long alive = getAliveCount();
        return Math.round((alive / (double) denominator) * 1000) / 10.0;
    }

    public long getAliveCount() {
        return Math.max(0, totalOffspring - deathCount - soldCount - transferredCount);
    }

    public String getCurrentAgeDisplay() {
        if (motherBirthDate == null) return "Unknown";
        Period p = Period.between(motherBirthDate, LocalDate.now());
        if (p.getYears() > 0) return p.getYears() + "y " + p.getMonths() + "m";
        if (p.getMonths() > 0) return p.getMonths() + "m " + p.getDays() + "d";
        return p.getDays() + "d";
    }

    public String getRankMedal() {
        return switch ((int) rank) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "";
        };
    }

    public String getFullLocationDisplay() {
        List<String> parts = new ArrayList<>();
        if (province != null && !province.isBlank()) parts.add(province.trim());
        if (district != null && !district.isBlank()) parts.add(district.trim());
        if (sector != null && !sector.isBlank()) parts.add(sector.trim());
        if (cell != null && !cell.isBlank()) parts.add(cell.trim());
        if (village != null && !village.isBlank()) parts.add(village.trim());
        if (!parts.isEmpty()) return String.join(" / ", parts);
        return (locationName != null && !locationName.isBlank()) ? locationName.trim() : "-";
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
            case "INACTIVE" -> "⏸️ Inactive";
            case "NOT_APPLICABLE" -> "N/A";
            default -> reproductiveStatus;
        };
    }

    public String getDaysSinceLastBirthDisplay() {
        if (daysSinceLastBirth == null) return "—";
        if (daysSinceLastBirth == 0) return "Today";
        if (daysSinceLastBirth == 1) return "1 day ago";
        return daysSinceLastBirth + " days ago";
    }

    public String getDaysSinceLastBreedingDisplay() {
        if (daysSinceLastBreeding == null) return "—";
        if (daysSinceLastBreeding == 0) return "Today";
        if (daysSinceLastBreeding == 1) return "1 day ago";
        return daysSinceLastBreeding + " days ago";
    }
}