package rw.animalproduct.animal.production.dto;

import lombok.Builder;
import lombok.Getter;

/** KPI cards for the redesigned Breeding Management dashboard (spec D.1). */
@Getter
@Builder
public class BreedingManagementKpiDTO {
    private long activeBreedings;
    private long femalesReady;
    private long pregnantAnimals;
    private long pendingPregnancyChecks; // overdue checks specifically
    private double breedingSuccessRate;
}