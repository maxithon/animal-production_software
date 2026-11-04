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
    // NEW: natural-mating-specific counts, sourced from the view's
    // natural_breedings / natural_successful columns (breeding_method =
    // 'NATURAL'). Lets the UI show a success rate specific to natural
    // mating, distinct from the overall (all-methods) success rate.
    private Long naturalBreedings;
    private Long naturalSuccessful;

    public MaleReadyToBreedDTO(Object[] row) {
        if (row == null) return;

        int idx = 0;
        if (row[idx] instanceof UUID) this.id = (UUID) row[idx];
        else if (row[idx] != null) this.id = UUID.fromString(row[idx].toString());
        idx++;

        this.tagNumber = row[idx] != null ? row[idx].toString() : null;
        idx++;

        if (row[idx] instanceof UUID) this.categoryId = (UUID) row[idx];
        else if (row[idx] != null) this.categoryId = UUID.fromString(row[idx].toString());
        idx++;

        this.categoryName = row[idx] != null ? row[idx].toString() : null;
        idx++;

        this.categoryCode = row[idx] != null ? row[idx].toString() : null;
        idx++;

        if (row[idx] instanceof Number) this.ageMonths = ((Number) row[idx]).intValue();
        idx++;

        if (row[idx] instanceof Number) this.totalBreedings = ((Number) row[idx]).longValue();
        idx++;

        if (row[idx] instanceof Number) this.successfulBreedings = ((Number) row[idx]).longValue();
        idx++;

        this.dateReceived = toLocalDate(row[idx]);
        idx++;

        this.birthDate = toLocalDate(row[idx]);
        idx++;

        this.status = row[idx] != null ? row[idx].toString() : null;
        idx++;

        this.gender = row[idx] != null ? row[idx].toString() : null;
        idx++;

        if (row[idx] instanceof Number) this.currentValue = ((Number) row[idx]).doubleValue();
        idx++;

        this.acquisitionMethod = row[idx] != null ? row[idx].toString() : null;
        idx++;

        // NEW: indices 14 and 15
        if (row.length > idx && row[idx] instanceof Number) this.naturalBreedings = ((Number) row[idx]).longValue();
        idx++;

        if (row.length > idx && row[idx] instanceof Number) this.naturalSuccessful = ((Number) row[idx]).longValue();
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

    // NEW: success rate scoped to natural-mating attempts only.
    public double getNaturalSuccessRate() {
        if (naturalBreedings == null || naturalBreedings == 0) return 0.0;
        return (naturalSuccessful != null ? naturalSuccessful : 0) * 100.0 / naturalBreedings;
    }

    public boolean isNeverBred() {
        return totalBreedings == null || totalBreedings == 0;
    }

    // NEW
    public boolean isNeverNaturallyBred() {
        return naturalBreedings == null || naturalBreedings == 0;
    }

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

    public Long getNaturalBreedings() { return naturalBreedings; }
    public void setNaturalBreedings(Long naturalBreedings) { this.naturalBreedings = naturalBreedings; }

    public Long getNaturalSuccessful() { return naturalSuccessful; }
    public void setNaturalSuccessful(Long naturalSuccessful) { this.naturalSuccessful = naturalSuccessful; }
}