package rw.animalproduct.animal.production.dto;

import java.time.LocalDate;
import java.util.UUID;

public class FemaleReadyToBreedDTO {
    private UUID id;
    private String tagNumber;
    private UUID categoryId;
    private String categoryName;
    private String categoryCode;
    private Integer gestationPeriodMonths;
    private Integer ageMonths;
    private Integer offspringCount;
    private LocalDate lastBreedingDate;
    private LocalDate firstBreedingDate;
    private LocalDate lastBirthDate;
    private Boolean isPregnant;
    private String pregnancyStatus;
    private LocalDate conceptionDate;
    private LocalDate expectedDueDate;
    private Long totalBreedings;
    private Long successfulBreedings;
    private LocalDate dateReceived;
    private LocalDate birthDate;
    private String status;
    private String gender;
    private Double currentValue;

    // Constructor that matches your view's column order
    public FemaleReadyToBreedDTO(Object[] row) {
        if (row == null) return;

        int idx = 0;
        // 0: id
        if (row[idx] instanceof UUID) this.id = (UUID) row[idx];
        else if (row[idx] != null) this.id = UUID.fromString(row[idx].toString());
        idx++;

        // 1: tag_number
        this.tagNumber = row[idx] != null ? row[idx].toString() : null;
        idx++;

        // 2: category_id
        if (row[idx] instanceof UUID) this.categoryId = (UUID) row[idx];
        else if (row[idx] != null) this.categoryId = UUID.fromString(row[idx].toString());
        idx++;

        // 3: category_name
        this.categoryName = row[idx] != null ? row[idx].toString() : null;
        idx++;

        // 4: category_code
        this.categoryCode = row[idx] != null ? row[idx].toString() : null;
        idx++;

        // 5: gestation_period_months
        if (row[idx] instanceof Number) this.gestationPeriodMonths = ((Number) row[idx]).intValue();
        idx++;

        // 6: age_months
        if (row[idx] instanceof Number) this.ageMonths = ((Number) row[idx]).intValue();
        idx++;

        // 7: offspring_count
        if (row[idx] instanceof Number) this.offspringCount = ((Number) row[idx]).intValue();
        idx++;

        // 8: last_breeding_date
        this.lastBreedingDate = toLocalDate(row[idx]);
        idx++;

        // 9: first_breeding_date
        this.firstBreedingDate = toLocalDate(row[idx]);
        idx++;

        // 10: last_birth_date
        this.lastBirthDate = toLocalDate(row[idx]);
        idx++;

        // 11: is_pregnant
        if (row[idx] instanceof Boolean) this.isPregnant = (Boolean) row[idx];
        else if (row[idx] instanceof Number) this.isPregnant = ((Number) row[idx]).intValue() == 1;
        idx++;

        // 12: pregnancy_status
        this.pregnancyStatus = row[idx] != null ? row[idx].toString() : null;
        idx++;

        // 13: conception_date
        this.conceptionDate = toLocalDate(row[idx]);
        idx++;

        // 14: expected_due_date
        this.expectedDueDate = toLocalDate(row[idx]);
        idx++;

        // 15: total_breedings
        if (row[idx] instanceof Number) this.totalBreedings = ((Number) row[idx]).longValue();
        idx++;

        // 16: successful_breedings
        if (row[idx] instanceof Number) this.successfulBreedings = ((Number) row[idx]).longValue();
        idx++;

        // 17: date_received
        this.dateReceived = toLocalDate(row[idx]);
        idx++;

        // 18: birth_date
        this.birthDate = toLocalDate(row[idx]);
        idx++;

        // 19: status
        this.status = row[idx] != null ? row[idx].toString() : null;
        idx++;

        // 20: gender
        this.gender = row[idx] != null ? row[idx].toString() : null;
        idx++;

        // 21: current_value
        if (row[idx] instanceof Number) this.currentValue = ((Number) row[idx]).doubleValue();
    }

    private LocalDate toLocalDate(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDate) return (LocalDate) val;
        if (val instanceof java.sql.Date) return ((java.sql.Date) val).toLocalDate();
        try {
            return LocalDate.parse(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isNeverBred() {
        return totalBreedings == null || totalBreedings == 0;
    }

    // ADDED: Success rate calculation
    public double getSuccessRate() {
        if (totalBreedings == null || totalBreedings == 0) return 0.0;
        return (successfulBreedings != null ? successfulBreedings : 0) * 100.0 / totalBreedings;
    }

    // ADDED: Safe isPregnant getter that returns primitive boolean (handles null)
    public boolean isPregnant() {
        return isPregnant != null && isPregnant;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTagNumber() { return tagNumber; }
    public void setTagNumber(String tagNumber) { this.tagNumber = tagNumber; }

    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }

    public Integer getGestationPeriodMonths() { return gestationPeriodMonths; }
    public void setGestationPeriodMonths(Integer gestationPeriodMonths) { this.gestationPeriodMonths = gestationPeriodMonths; }

    public Integer getAgeMonths() { return ageMonths; }
    public void setAgeMonths(Integer ageMonths) { this.ageMonths = ageMonths; }

    public Integer getOffspringCount() { return offspringCount; }
    public void setOffspringCount(Integer offspringCount) { this.offspringCount = offspringCount; }

    public LocalDate getLastBreedingDate() { return lastBreedingDate; }
    public void setLastBreedingDate(LocalDate lastBreedingDate) { this.lastBreedingDate = lastBreedingDate; }

    public LocalDate getFirstBreedingDate() { return firstBreedingDate; }
    public void setFirstBreedingDate(LocalDate firstBreedingDate) { this.firstBreedingDate = firstBreedingDate; }

    public LocalDate getLastBirthDate() { return lastBirthDate; }
    public void setLastBirthDate(LocalDate lastBirthDate) { this.lastBirthDate = lastBirthDate; }

    // Keep the original getter for the Boolean object (if needed elsewhere)
    public Boolean getIsPregnant() { return isPregnant; }
    public void setIsPregnant(Boolean isPregnant) { this.isPregnant = isPregnant; }

    public String getPregnancyStatus() { return pregnancyStatus; }
    public void setPregnancyStatus(String pregnancyStatus) { this.pregnancyStatus = pregnancyStatus; }

    public LocalDate getConceptionDate() { return conceptionDate; }
    public void setConceptionDate(LocalDate conceptionDate) { this.conceptionDate = conceptionDate; }

    public LocalDate getExpectedDueDate() { return expectedDueDate; }
    public void setExpectedDueDate(LocalDate expectedDueDate) { this.expectedDueDate = expectedDueDate; }

    public Long getTotalBreedings() { return totalBreedings; }
    public void setTotalBreedings(Long totalBreedings) { this.totalBreedings = totalBreedings; }

    public Long getSuccessfulBreedings() { return successfulBreedings; }
    public void setSuccessfulBreedings(Long successfulBreedings) { this.successfulBreedings = successfulBreedings; }

    public LocalDate getDateReceived() { return dateReceived; }
    public void setDateReceived(LocalDate dateReceived) { this.dateReceived = dateReceived; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Double getCurrentValue() { return currentValue; }
    public void setCurrentValue(Double currentValue) { this.currentValue = currentValue; }
}