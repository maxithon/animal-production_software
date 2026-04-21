package rw.animalproduct.animal.production.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class FemaleReadyToBreedDTO {

    private UUID      id;
    private String    tagNumber;
    private String    categoryName;
    private Integer   ageMonths;
    private LocalDate dateReceived;
    private LocalDate birthDate;
    private LocalDate lastBreedingDate;
    private LocalDate expectedDueDate;
    private String    status;
    private String    pregnancyStatus;
    private Boolean   isPregnant;
    private Integer   offspringCount;
    private Long      totalBreedings;
    private Long      successfulBreedings;

    // ── Constructor from Object[] ────────────────────────────────────────────
    // Column order must match the SELECT in FemalesReadyToBreedRepository:
    //  0  id
    //  1  tag_number
    //  2  category_name
    //  3  age_months
    //  4  date_received
    //  5  birth_date
    //  6  last_breeding_date
    //  7  expected_due_date
    //  8  status
    //  9  pregnancy_status
    // 10  is_pregnant
    // 11  offspring_count
    // 12  total_breedings
    // 13  successful_breedings

    public FemaleReadyToBreedDTO(Object[] row) {
        this.id                  = row[0] != null ? UUID.fromString(row[0].toString()) : null;
        this.tagNumber           = (String)  row[1];
        this.categoryName        = (String)  row[2];
        this.ageMonths           = row[3] != null ? ((Number) row[3]).intValue()  : null;
        this.dateReceived        = toLocalDate(row[4]);
        this.birthDate           = toLocalDate(row[5]);
        this.lastBreedingDate    = toLocalDate(row[6]);
        this.expectedDueDate     = toLocalDate(row[7]);
        this.status              = (String)  row[8];
        this.pregnancyStatus     = (String)  row[9];
        this.isPregnant          = row[10] != null ? (Boolean) row[10] : false;
        this.offspringCount      = row[11] != null ? ((Number) row[11]).intValue()  : 0;
        this.totalBreedings      = row[12] != null ? ((Number) row[12]).longValue() : 0L;
        this.successfulBreedings = row[13] != null ? ((Number) row[13]).longValue() : 0L;
    }

    private LocalDate toLocalDate(Object val) {
        if (val == null) return null;
        if (val instanceof LocalDate ld) return ld;
        if (val instanceof java.sql.Date sd) return sd.toLocalDate();
        return LocalDate.parse(val.toString());
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID      getId()                  { return id; }
    public String    getTagNumber()           { return tagNumber; }
    public String    getCategoryName()        { return categoryName; }
    public Integer   getAgeMonths()           { return ageMonths; }
    public LocalDate getDateReceived()        { return dateReceived; }
    public LocalDate getBirthDate()           { return birthDate; }
    public LocalDate getLastBreedingDate()    { return lastBreedingDate; }
    public LocalDate getExpectedDueDate()     { return expectedDueDate; }
    public String    getStatus()              { return status; }
    public String    getPregnancyStatus()     { return pregnancyStatus; }
    public Boolean   getIsPregnant()          { return isPregnant; }
    public Integer   getOffspringCount()      { return offspringCount; }
    public Long      getTotalBreedings()      { return totalBreedings; }
    public Long      getSuccessfulBreedings() { return successfulBreedings; }

    // ── Derived helpers for Thymeleaf ─────────────────────────────────────────

    public boolean isNeverBred() {
        return totalBreedings == null || totalBreedings == 0;
    }

    public double getSuccessRate() {
        if (totalBreedings == null || totalBreedings == 0) return 0.0;
        return (successfulBreedings * 100.0) / totalBreedings;
    }
}