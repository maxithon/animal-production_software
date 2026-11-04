package rw.animalproduct.animal.production.dto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class PregnancyRowDTO {

    private UUID      livestockId;
    private String    tagNumber;
    private String    categoryName;
    private String    acquisitionMethod;
    private LocalDate conceptionDate;
    private LocalDate expectedDueDate;
    private String    inseminationMethod;

    // NEW: IDs used by the controller to look up beneficiary + location
    private UUID beneficiaryId;
    private UUID locationId;

    private Integer daysPregnant;
    private Integer daysUntilDue;
    private boolean critical;
    private boolean dueSoon;
    private boolean dueToday;

    public PregnancyRowDTO() {}

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

    public void recalculate() {
        LocalDate today = LocalDate.now();

        if (conceptionDate != null) {
            long dp = ChronoUnit.DAYS.between(conceptionDate, today);
            this.daysPregnant = (int) Math.max(0, dp);
        } else {
            this.daysPregnant = null;
        }

        if (expectedDueDate != null) {
            this.daysUntilDue = (int) ChronoUnit.DAYS.between(today, expectedDueDate);
        } else {
            this.daysUntilDue = null;
        }

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

    public int getGestationProgressPercent() {
        if (daysPregnant == null) return 0;
        int total = daysPregnant + (daysUntilDue != null && daysUntilDue > 0 ? daysUntilDue : 0);
        if (total == 0) return 100;
        return Math.min(100, daysPregnant * 100 / total);
    }

    public String getGestationStageLabel() {
        int pct = getGestationProgressPercent();
        if (pct < 33)  return "Early";
        if (pct < 66)  return "Mid";
        if (pct < 90)  return "Late";
        if (pct < 100) return "Near Term";
        return "Full Term";
    }

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

    // NEW
    public UUID getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(UUID beneficiaryId) { this.beneficiaryId = beneficiaryId; }

    public UUID getLocationId() { return locationId; }
    public void setLocationId(UUID locationId) { this.locationId = locationId; }

    public Integer getDaysPregnant()  { return daysPregnant; }
    public Integer getDaysUntilDue()  { return daysUntilDue; }
    public boolean isCritical()       { return critical; }
    public boolean isDueSoon()        { return dueSoon; }
    public boolean isDueToday()       { return dueToday; }

    public boolean getCritical()  { return critical; }
    public boolean getDueSoon()   { return dueSoon; }
    public boolean getDueToday()  { return dueToday; }
}