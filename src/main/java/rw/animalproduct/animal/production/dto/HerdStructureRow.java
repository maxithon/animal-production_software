package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * FAO-style herd structure row: closing-stock headcount broken down by
 * species/category AND sex/age class (Adult Female, Adult Male, Young
 * Female, Young Male). This is the standard way FAO livestock inventories
 * report herd composition, since breeding-capable stock vs immature stock
 * matters far more than a flat headcount.
 */
@Data
public class HerdStructureRow {

    private String categoryName;

    /** Machine code: ADULT_FEMALE, ADULT_MALE, YOUNG_FEMALE, YOUNG_MALE, UNKNOWN */
    private String sexAgeClass;

    /** Human label, e.g. "Adult Female (Breeding Stock)" */
    private String sexAgeLabel;

    private int count;
    private BigDecimal value = BigDecimal.ZERO;

    /** Percentage this class represents of its category's total closing stock. */
    private double percentOfCategory;
}