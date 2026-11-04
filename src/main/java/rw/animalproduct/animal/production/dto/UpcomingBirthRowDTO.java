package rw.animalproduct.animal.production.dto;

import java.time.LocalDate;
import java.util.UUID;

public class UpcomingBirthRowDTO {
    private UUID livestockId;
    private UUID pregnancyId;
    private String tagNumber;
    private String categoryName;
    private LocalDate breedingDate;
    private LocalDate expectedBirthDate;
    private long daysUntilBirth;
    private String pregnancyStatus;
    private Integer expectedOffspring;
    private String location;
    private String status;       // UPCOMING | DUE_SOON | OVERDUE | BIRTH_RECORDED
    private String statusLabel;  // "🟢 Upcoming", "🔴 Overdue", etc.

    public UUID getLivestockId() { return livestockId; }
    public void setLivestockId(UUID livestockId) { this.livestockId = livestockId; }

    public UUID getPregnancyId() { return pregnancyId; }
    public void setPregnancyId(UUID pregnancyId) { this.pregnancyId = pregnancyId; }

    public String getTagNumber() { return tagNumber; }
    public void setTagNumber(String tagNumber) { this.tagNumber = tagNumber; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public LocalDate getBreedingDate() { return breedingDate; }
    public void setBreedingDate(LocalDate breedingDate) { this.breedingDate = breedingDate; }

    public LocalDate getExpectedBirthDate() { return expectedBirthDate; }
    public void setExpectedBirthDate(LocalDate expectedBirthDate) { this.expectedBirthDate = expectedBirthDate; }

    public long getDaysUntilBirth() { return daysUntilBirth; }
    public void setDaysUntilBirth(long daysUntilBirth) { this.daysUntilBirth = daysUntilBirth; }

    public String getPregnancyStatus() { return pregnancyStatus; }
    public void setPregnancyStatus(String pregnancyStatus) { this.pregnancyStatus = pregnancyStatus; }

    public Integer getExpectedOffspring() { return expectedOffspring; }
    public void setExpectedOffspring(Integer expectedOffspring) { this.expectedOffspring = expectedOffspring; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
}