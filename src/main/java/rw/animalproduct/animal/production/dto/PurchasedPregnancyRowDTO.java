package rw.animalproduct.animal.production.dto;

import rw.animalproduct.animal.production.entity.Livestock;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * DTO that pre-computes all date/ChronoUnit calculations for a purchased-animal
 * pregnancy row so the Thymeleaf template never needs T(...) SpEL expressions.
 *
 * These are animals where is_pregnant = true but no breeding record exists yet.
 */
public class PurchasedPregnancyRowDTO {

    private final Livestock  livestock;
    private final LocalDate  conceptionDate; // conceptionDate ?? lastBreedingDate
    private final long daysPregnant;   // -1 when conception date is unknown
    private final long daysRemaining;  // 9999 when no due date
    private final long totalDays;      // 0 when dates are missing
    private final long pct;            // -1 when dates are unknown
    private final int  trimester;      // 0 = unknown, 1/2/3 otherwise
    private final boolean critical;
    private final boolean dueSoon;

    public PurchasedPregnancyRowDTO(Livestock ls, LocalDate today) {
        this.livestock = ls;

        // Best-effort conception date: prefer conceptionDate, fall back to lastBreedingDate
        LocalDate cd = (ls.getConceptionDate() != null)
                ? ls.getConceptionDate()
                : ls.getLastBreedingDate();
        this.conceptionDate = cd;

        // Days pregnant
        this.daysPregnant = (cd != null)
                ? ChronoUnit.DAYS.between(cd, today)
                : -1L;

        // Days remaining
        long due = (ls.getExpectedDueDate() != null)
                ? ChronoUnit.DAYS.between(today, ls.getExpectedDueDate())
                : 9999L;
        this.daysRemaining = due;

        // Total gestation span
        this.totalDays = (cd != null && ls.getExpectedDueDate() != null)
                ? ChronoUnit.DAYS.between(cd, ls.getExpectedDueDate())
                : 0L;

        // Percentage (only meaningful when both dates exist)
        this.pct = (totalDays > 0 && daysPregnant >= 0)
                ? Math.min(100L, daysPregnant * 100L / totalDays)
                : -1L;

        // Trimester (0 = unknown)
        if (totalDays > 0 && daysPregnant >= 0) {
            if (daysPregnant < totalDays / 3)          this.trimester = 1;
            else if (daysPregnant < totalDays * 2 / 3) this.trimester = 2;
            else                                        this.trimester = 3;
        } else {
            this.trimester = 0;
        }

        // Urgency
        this.critical = (ls.getExpectedDueDate() != null) && due < 7;
        this.dueSoon  = (ls.getExpectedDueDate() != null) && due >= 7 && due <= 30;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Livestock  getLivestock()    { return livestock; }
    public LocalDate  getConceptionDate(){ return conceptionDate; }
    public long getDaysPregnant()       { return daysPregnant; }
    public long getDaysRemaining()      { return daysRemaining; }
    public long getTotalDays()          { return totalDays; }
    public long getPct()                { return pct; }
    public int  getTrimester()          { return trimester; }
    public boolean isCritical()         { return critical; }
    public boolean isDueSoon()          { return dueSoon; }
}