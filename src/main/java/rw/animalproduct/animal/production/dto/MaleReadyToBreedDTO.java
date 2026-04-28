package rw.animalproduct.animal.production.dto;

import java.time.LocalDate;
import java.util.UUID;

public class MaleReadyToBreedDTO {
    private UUID id;
    private String tagNumber;
    private UUID categoryId;
    private String categoryName;
    private String categoryCode;
    private Integer ageMonths;
    private Long totalBreedings;
    private Long successfulBreedings;
    private LocalDate dateReceived;
    private LocalDate birthDate;
    private String status;
    private String gender;
    private Double currentValue;
    private String acquisitionMethod;

    // Constructor that matches your view's column order
    public MaleReadyToBreedDTO(Object[] row) {
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

        // 5: age_months
        if (row[idx] instanceof Number) this.ageMonths = ((Number) row[idx]).intValue();
        idx++;

        // 6: total_breedings
        if (row[idx] instanceof Number) this.totalBreedings = ((Number) row[idx]).longValue();
        idx++;

        // 7: successful_breedings
        if (row[idx] instanceof Number) this.successfulBreedings = ((Number) row[idx]).longValue();
        idx++;

        // 8: date_received
        this.dateReceived = toLocalDate(row[idx]);
        idx++;

        // 9: birth_date
        this.birthDate = toLocalDate(row[idx]);
        idx++;

        // 10: status
        this.status = row[idx] != null ? row[idx].toString() : null;
        idx++;

        // 11: gender
        this.gender = row[idx] != null ? row[idx].toString() : null;
        idx++;

        // 12: current_value
        if (row[idx] instanceof Number) this.currentValue = ((Number) row[idx]).doubleValue();
        idx++;

        // 13: acquisition_method
        this.acquisitionMethod = row[idx] != null ? row[idx].toString() : null;
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

    public double getSuccessRate() {
        if (totalBreedings == null || totalBreedings == 0) return 0.0;
        return (successfulBreedings != null ? successfulBreedings : 0) * 100.0 / totalBreedings;
    }

    public boolean isNeverBred() {
        return totalBreedings == null || totalBreedings == 0;
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

    public Integer getAgeMonths() { return ageMonths; }
    public void setAgeMonths(Integer ageMonths) { this.ageMonths = ageMonths; }

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

    public String getAcquisitionMethod() { return acquisitionMethod; }
    public void setAcquisitionMethod(String acquisitionMethod) { this.acquisitionMethod = acquisitionMethod; }
}