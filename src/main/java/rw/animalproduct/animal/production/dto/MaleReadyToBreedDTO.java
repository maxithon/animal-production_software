package rw.animalproduct.animal.production.dto;

import java.time.LocalDate;
import java.util.UUID;

public class MaleReadyToBreedDTO {

    private UUID      id;
    private String    tagNumber;
    private String    categoryName;
    private Integer   ageMonths;
    private Long      totalBreedings;
    private Long      successfulBreedings;
    private LocalDate dateReceived;
    private LocalDate birthDate;
    private String    status;
    private String    gender;
    private String    acquisitionMethod;

    // ── No-arg constructor (required by service's setter pattern) ─────────────
    public MaleReadyToBreedDTO() {}

    // ── Setters ───────────────────────────────────────────────────────────────
    public void setId(UUID id)                               { this.id = id; }
    public void setTagNumber(String tagNumber)               { this.tagNumber = tagNumber; }
    public void setCategoryName(String categoryName)         { this.categoryName = categoryName; }
    public void setAgeMonths(Integer ageMonths)             { this.ageMonths = ageMonths; }
    public void setTotalBreedings(Long totalBreedings)       { this.totalBreedings = totalBreedings; }
    public void setSuccessfulBreedings(Long v)               { this.successfulBreedings = v; }
    public void setDateReceived(LocalDate dateReceived)      { this.dateReceived = dateReceived; }
    public void setBirthDate(LocalDate birthDate)            { this.birthDate = birthDate; }
    public void setStatus(String status)                     { this.status = status; }
    public void setGender(String gender)                     { this.gender = gender; }
    public void setAcquisitionMethod(String v)               { this.acquisitionMethod = v; }

    // ── Getters ───────────────────────────────────────────────────────────────
    public UUID      getId()                  { return id; }
    public String    getTagNumber()           { return tagNumber; }
    public String    getCategoryName()        { return categoryName; }
    public Integer   getAgeMonths()           { return ageMonths; }
    public Long      getTotalBreedings()      { return totalBreedings != null ? totalBreedings : 0L; }
    public Long      getSuccessfulBreedings() { return successfulBreedings != null ? successfulBreedings : 0L; }
    public LocalDate getDateReceived()        { return dateReceived; }
    public LocalDate getBirthDate()           { return birthDate; }
    public String    getStatus()              { return status; }
    public String    getGender()              { return gender; }
    public String    getAcquisitionMethod()   { return acquisitionMethod; }

    // ── Derived helpers for Thymeleaf ─────────────────────────────────────────
    public boolean isNeverBred() {
        return getTotalBreedings() == 0;
    }

    public double getSuccessRate() {
        if (getTotalBreedings() == 0) return 0.0;
        return (getSuccessfulBreedings() * 100.0) / getTotalBreedings();
    }
}