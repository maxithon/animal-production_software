package rw.animalproduct.animal.production.dto;

import java.time.LocalDate;
import java.util.UUID;

public interface MaleCandidateProjection {

    UUID getId();

    String getTagNumber();

    Integer getAgeMonths();

    String getHealthStatus();

    Long getTotalBreedings();

    Long getSuccessfulBreedings();

    UUID getCategoryId();

    String getCategoryName();

    String getGender();
}