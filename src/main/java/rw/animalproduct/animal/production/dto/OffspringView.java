package rw.animalproduct.animal.production.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only projection of a linked offspring, used by view/manage pages.
 * Consolidates what was previously split across `linkedChildren` and
 * `offspringLinks` in the controller model.
 *
 * This is a DTO only — no table, no entity, no migration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OffspringView {

    private UUID offspringLinkId;
    private UUID childId;
    private String tagNumber;
    private String gender;
    private String categoryName;
    private LocalDate birthDate;
    private String status;
    private boolean draft;

    private String outcome;
    private Integer generation;
    private BigDecimal birthWeightKg;

    private UUID motherId;
    private String motherTag;
    private String motherCategoryName;

    private UUID fatherId;
    private String fatherTag;
}