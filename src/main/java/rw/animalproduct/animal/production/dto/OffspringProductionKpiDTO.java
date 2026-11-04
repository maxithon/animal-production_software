package rw.animalproduct.animal.production.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OffspringProductionKpiDTO {

    // ── Core Production Metrics ──
    private long totalMothers;              // All female animals in the system
    private long productiveMothers;         // Mothers with >= 1 birth event in period
    private long nonProductiveMothers;      // Mothers with 0 birth events in period
    private long totalBirthEvents;
    private long totalOffspring;

    // ── Sex Distribution ──
    private long totalMale;
    private long totalFemale;

    // ── Offspring Outcomes ──
    private long totalAlive;
    private long totalDied;
    private long totalSold;
    private long totalTransferred;

    // ── Additional Metrics ──
    private long totalWeaned;
    private long totalStillborn;

    // ── Calculated Rates ──
    private Double survivalRate;            // Alive ÷ (Total Offspring - Sold - Transferred) × 100
    private Double stillbirthRate;          // Stillborn ÷ Total Offspring × 100
    private Double weaningRate;             // Weaned ÷ Total Offspring × 100
    private Double productiveRate;          // Productive Mothers ÷ Total Mothers × 100

    // ── Averages ──
    private double averageOffspringPerBirth;
    private double averageOffspringPerMother;
    private double averageOffspringPerProductiveMother;

    // ── Outcome Percentages ──
    public Double getAlivePercent() {
        if (totalOffspring == 0) return 0.0;
        return Math.round((totalAlive / (double) totalOffspring) * 1000) / 10.0;
    }

    public Double getDiedPercent() {
        if (totalOffspring == 0) return 0.0;
        return Math.round((totalDied / (double) totalOffspring) * 1000) / 10.0;
    }

    public Double getSoldPercent() {
        if (totalOffspring == 0) return 0.0;
        return Math.round((totalSold / (double) totalOffspring) * 1000) / 10.0;
    }

    public Double getTransferredPercent() {
        if (totalOffspring == 0) return 0.0;
        return Math.round((totalTransferred / (double) totalOffspring) * 1000) / 10.0;
    }

    public long getTotalUnknown() {
        return Math.max(0, totalOffspring - totalMale - totalFemale);
    }

    public double getMalePercent() {
        if (totalOffspring == 0) return 0.0;
        return Math.round((totalMale / (double) totalOffspring) * 1000) / 10.0;
    }

    public double getFemalePercent() {
        if (totalOffspring == 0) return 0.0;
        return Math.round((totalFemale / (double) totalOffspring) * 1000) / 10.0;
    }

    public double getUnknownPercent() {
        if (totalOffspring == 0) return 0.0;
        return Math.round((getTotalUnknown() / (double) totalOffspring) * 1000) / 10.0;
    }
}