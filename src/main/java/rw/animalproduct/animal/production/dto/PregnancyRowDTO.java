package rw.animalproduct.animal.production.dto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * DTO for one row in the Pregnancy Tracking table.
 *
 * FAO-standard day-counting rules:
 * ─────────────────────────────────────────────────────────────────────────────
 *   daysPregnant  = today − conceptionDate          (always ≥ 0)
 *   daysUntilDue  = expectedDueDate − today
 *                   > 0  → days remaining
 *                   = 0  → due TODAY  (NOT overdue — this is a critical fix)
 *                   < 0  → overdue by |daysUntilDue| days
 *
 *   critical      = daysUntilDue < 0  (strictly negative — overdue)
 *                   daysUntilDue == 0 is "due today", NOT critical
 *
 *   dueSoon       = daysUntilDue in [1, 14]  (positive, within 14 days)
 *
 * Gestation progress %:
 *   totalGestationDays = conceptionDate → expectedDueDate distance
 *                      = daysPregnant + max(0, daysUntilDue)
 *   pct = min(100, daysPregnant * 100 / totalGestationDays)
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class PregnancyRowDTO {

    private UUID      livestockId;
    private String    tagNumber;
    private String    categoryName;
    private String    acquisitionMethod;
    private LocalDate conceptionDate;
    private LocalDate expectedDueDate;
    private String    inseminationMethod;

    // ── Derived / computed ────────────────────────────────────────────────
    private Integer daysPregnant;
    private Integer daysUntilDue;
    private boolean critical;   // strictly overdue  (daysUntilDue < 0)
    private boolean dueSoon;    // 1–14 days left     (0 < daysUntilDue ≤ 14)
    private boolean dueToday;   // due today          (daysUntilDue == 0)

    // ─────────────────────────────────────────────────────────────────────
    // CONSTRUCTORS
    // ─────────────────────────────────────────────────────────────────────

    public PregnancyRowDTO() {}

    /**
     * Full constructor — call this from your service/controller.
     * All day-count fields are computed here; call site only provides dates.
     */
    public PregnancyRowDTO(UUID      livestockId,
                           String    tagNumber,
                           String    categoryName,
                           String    acquisitionMethod,
                           LocalDate conceptionDate,
                           LocalDate expectedDueDate,
                           String    inseminationMethod) {
        this.livestockId         = livestockId;
        this.tagNumber           = tagNumber;
        this.categoryName        = categoryName;
        this.acquisitionMethod   = acquisitionMethod;
        this.conceptionDate      = conceptionDate;
        this.expectedDueDate     = expectedDueDate;
        this.inseminationMethod  = inseminationMethod;

        recalculate();
    }

    // ─────────────────────────────────────────────────────────────────────
    // FAO CALCULATION
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Recomputes all derived fields from conceptionDate and expectedDueDate.
     * Safe to call any time either date changes.
     */
    public void recalculate() {
        LocalDate today = LocalDate.now();

        // ── Days Pregnant ──────────────────────────────────────────────────
        if (conceptionDate != null) {
            long dp = ChronoUnit.DAYS.between(conceptionDate, today);
            this.daysPregnant = (int) Math.max(0, dp);
        } else {
            this.daysPregnant = null;
        }

        // ── Days Until Due ─────────────────────────────────────────────────
        if (expectedDueDate != null) {
            // Positive  → days remaining
            // Zero      → due today
            // Negative  → overdue
            this.daysUntilDue = (int) ChronoUnit.DAYS.between(today, expectedDueDate);
        } else {
            this.daysUntilDue = null;
        }

        // ── FAO flags ─────────────────────────────────────────────────────
        // critical = strictly overdue (daysUntilDue < 0)
        // dueToday = on the due date  (daysUntilDue == 0) — NOT critical
        // dueSoon  = 1–14 days left   (0 < daysUntilDue <= 14)
        if (this.daysUntilDue != null) {
            this.critical  = this.daysUntilDue < 0;
            this.dueToday  = this.daysUntilDue == 0;
            this.dueSoon   = this.daysUntilDue > 0 && this.daysUntilDue <= 14;
        } else {
            this.critical  = false;
            this.dueToday  = false;
            this.dueSoon   = false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // LABEL HELPERS (safe to call from Thymeleaf)
    // ─────────────────────────────────────────────────────────────────────

    /** Human-readable label for insemination method. */
    public String getInseminationMethodLabel() {
        if (inseminationMethod == null || inseminationMethod.isBlank()
                || "UNKNOWN".equals(inseminationMethod)) return "Not recorded";
        return switch (inseminationMethod) {
            case "NATURAL_MATING"          -> "Natural Mating";
            case "ARTIFICIAL_INSEMINATION" -> "Artificial Insemination (AI)";
            case "EMBRYO_TRANSFER"         -> "Embryo Transfer (ET)";
            default                        -> inseminationMethod;
        };
    }

    /**
     * Gestation progress as a percentage (0–100).
     * Uses the planned gestation length (conception → due date) as denominator.
     */
    public int getGestationProgressPercent() {
        if (daysPregnant == null) return 0;
        int total = daysPregnant + (daysUntilDue != null && daysUntilDue > 0 ? daysUntilDue : 0);
        if (total == 0) return 100;
        return Math.min(100, daysPregnant * 100 / total);
    }

    /** Stage label derived from gestation progress percent. */
    public String getGestationStageLabel() {
        int pct = getGestationProgressPercent();
        if (pct < 33)  return "Early";
        if (pct < 66)  return "Mid";
        if (pct < 90)  return "Late";
        if (pct < 100) return "Near Term";
        return "Full Term";
    }

    // ─────────────────────────────────────────────────────────────────────
    // GETTERS & SETTERS
    // ─────────────────────────────────────────────────────────────────────

    public UUID getLivestockId()  { return livestockId; }
    public void setLivestockId(UUID livestockId)  { this.livestockId = livestockId; }

    public String getTagNumber()  { return tagNumber; }
    public void setTagNumber(String tagNumber)  { this.tagNumber = tagNumber; }

    public String getCategoryName()  { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getAcquisitionMethod()  { return acquisitionMethod; }
    public void setAcquisitionMethod(String acquisitionMethod) { this.acquisitionMethod = acquisitionMethod; }

    public LocalDate getConceptionDate()  { return conceptionDate; }
    public void setConceptionDate(LocalDate conceptionDate) {
        this.conceptionDate = conceptionDate;
        recalculate();
    }

    public LocalDate getExpectedDueDate()  { return expectedDueDate; }
    public void setExpectedDueDate(LocalDate expectedDueDate) {
        this.expectedDueDate = expectedDueDate;
        recalculate();
    }

    public String getInseminationMethod()  { return inseminationMethod; }
    public void setInseminationMethod(String inseminationMethod) { this.inseminationMethod = inseminationMethod; }

    public Integer getDaysPregnant()  { return daysPregnant; }
    public Integer getDaysUntilDue()  { return daysUntilDue; }
    public boolean isCritical()       { return critical; }
    public boolean isDueSoon()        { return dueSoon; }
    public boolean isDueToday()       { return dueToday; }

    // Thymeleaf-friendly aliases (th:if="${row.critical}" works with getters)
    public boolean getCritical()  { return critical; }
    public boolean getDueSoon()   { return dueSoon; }
    public boolean getDueToday()  { return dueToday; }
}
