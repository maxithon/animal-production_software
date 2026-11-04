package rw.animalproduct.animal.production.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BreedingEligibilityKpiDTO {
    // Primary summary cards
    private long totalAnimals;
    private long eligibleForBreeding;
    private long tooYoung;
    private long nearMaximumAge;
    private long maximumAgeExceeded;
    private long retirementRecommended;

    // Secondary breakdown (shown as a smaller strip under the main cards)
    private long currentlyPregnant;
    private long notAvailable;
    private long retired;
}
