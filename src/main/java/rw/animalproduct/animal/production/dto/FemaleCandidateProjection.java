package rw.animalproduct.animal.production.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lightweight projection for the breeding female picker.
 * Avoids loading full Livestock entities + associations for the list.
 */
public interface FemaleCandidateProjection {

    UUID getId();

    String getTagNumber();

    Integer getAgeMonths();

    String getHealthStatus();

    String getReproductiveStatus();

    Long getTotalBreedings();

    Long getSuccessfulBreedings();

    LocalDate getLastBirthDate();

    UUID getCategoryId();

    String getCategoryName();

    String getGender();
}