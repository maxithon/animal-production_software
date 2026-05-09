package rw.animalproduct.animal.production.dto;

import rw.animalproduct.animal.production.entity.LivestockBreeding;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * DTO that pre-computes all date/ChronoUnit calculations for a breeding-record
 * pregnancy row so the Thymeleaf template never needs T(...) SpEL expressions.
 */
public class PregnancyRowDTO {

    private final LivestockBreeding breeding;
    private final long daysPregnant;
    private final long daysRemaining;   // 9999 when no due date
    private final long totalDays;
    private final long pct;             // 0-100
    private final int  trimester;       // 1, 2, or 3
    private final boolean critical;     // due in < 7 days (or overdue)
    private final boolean dueSoon;      // due in 7-30 days

    public PregnancyRowDTO(LivestockBreeding b, LocalDate today) {
        this.breeding = b;

        // Days pregnant (from breeding date to today)
        this.daysPregnant = (b.getBreedingDate() != null)
                ? ChronoUnit.DAYS.between(b.getBreedingDate(), today)
                : 0L;

        // Days remaining (today to due date; negative = overdue)
        long due = (b.getExpectedDueDate() != null)
                ? ChronoUnit.DAYS.between(today, b.getExpectedDueDate())
                : 9999L;
        this.daysRemaining = due;

        // Total gestation length (breeding → due); default 283 days if unknown
        this.totalDays = (b.getBreedingDate() != null && b.getExpectedDueDate() != null)
                ? ChronoUnit.DAYS.between(b.getBreedingDate(), b.getExpectedDueDate())
                : 283L;

        // Percentage of gestation completed (clamped 0-100)
        this.pct = (totalDays > 0)
                ? Math.min(100L, Math.max(0L, daysPregnant * 100L / totalDays))
                : 0L;

        // Trimester
        if (totalDays > 0) {
            if (daysPregnant < totalDays / 3)          this.trimester = 1;
            else if (daysPregnant < totalDays * 2 / 3) this.trimester = 2;
            else                                        this.trimester = 3;
        } else {
            this.trimester = 1;
        }

        // Urgency flags
        this.critical = (b.getExpectedDueDate() != null) && due < 7;
        this.dueSoon  = (b.getExpectedDueDate() != null) && due >= 7 && due <= 30;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public LivestockBreeding getBreeding()  { return breeding; }
    public long getDaysPregnant()           { return daysPregnant; }
    public long getDaysRemaining()          { return daysRemaining; }
    public long getTotalDays()              { return totalDays; }
    public long getPct()                    { return pct; }
    public int  getTrimester()              { return trimester; }
    public boolean isCritical()             { return critical; }
    public boolean isDueSoon()              { return dueSoon; }
}