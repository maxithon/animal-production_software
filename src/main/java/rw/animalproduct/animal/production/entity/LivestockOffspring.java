package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Links a birth event to one specific child animal.
 * Matches database schema: id, birth_id, child_livestock_id, generation,
 * is_deleted, is_alive, outcome, birth_weight_kg
 */
@Entity
@Table(name = "livestock_offspring")
public class LivestockOffspring {

    public static final String OUTCOME_ALIVE               = "ALIVE";
    public static final String OUTCOME_STILLBORN            = "STILLBORN";
    public static final String OUTCOME_DIED_SHORTLY_AFTER    = "DIED_SHORTLY_AFTER";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "birth_id", nullable = false)
    private LivestockBirth birthEvent;

    @ManyToOne
    @JoinColumn(name = "child_livestock_id", referencedColumnName = "id")
    private Livestock childLivestock;

    @Column(name = "generation")
    private Integer generation = 1;

    @Column(name = "is_alive")
    private Boolean isAlive = true;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    // ✅ NEW — per-offspring birth outcome (improvement #4). Richer than the
    // legacy is_alive boolean: distinguishes a stillbirth from a live birth
    // that died shortly after. is_alive is kept for backward compatibility
    // and derived automatically whenever outcome is set — nothing that
    // already reads getIsAlive()/isAlive() needs to change.
    @Column(name = "outcome", length = 30)
    private String outcome = OUTCOME_ALIVE;

    // ✅ NEW — improvement #5, optional
    @Column(name = "birth_weight_kg", precision = 6, scale = 2)
    private BigDecimal birthWeightKg;

    public LivestockOffspring() {}

    public LivestockOffspring(LivestockBirth birthEvent, Livestock childLivestock, Integer generation) {
        this.birthEvent = birthEvent;
        this.childLivestock = childLivestock;
        this.generation = generation;
        this.isAlive = true;
        this.outcome = OUTCOME_ALIVE;
    }

    // ── Getters and Setters ──────────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LivestockBirth getBirthEvent() {
        return birthEvent;
    }

    public void setBirthEvent(LivestockBirth birthEvent) {
        this.birthEvent = birthEvent;
    }

    public Livestock getChildLivestock() {
        return childLivestock;
    }

    public void setChildLivestock(Livestock childLivestock) {
        this.childLivestock = childLivestock;
    }

    public Integer getGeneration() {
        return generation;
    }

    public void setGeneration(Integer generation) {
        this.generation = generation;
    }

    public Boolean getIsAlive() {
        return isAlive;
    }

    public void setIsAlive(Boolean isAlive) {
        this.isAlive = isAlive;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    // ✅ NEW — setOutcome() keeps isAlive in sync automatically, so anything
    // already relying on isAlive()/getIsAlive() (e.g.
    // countLiveOffspringByBirthEventId) keeps working exactly as before.
    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = (outcome != null && !outcome.isBlank()) ? outcome : OUTCOME_ALIVE;
        this.isAlive = !OUTCOME_STILLBORN.equals(this.outcome)
                && !OUTCOME_DIED_SHORTLY_AFTER.equals(this.outcome);
    }

    public String getOutcomeLabel() {
        if (outcome == null) return "Alive";
        return switch (outcome) {
            case OUTCOME_STILLBORN -> "Stillborn";
            case OUTCOME_DIED_SHORTLY_AFTER -> "Died Shortly After";
            default -> "Alive";
        };
    }

    public BigDecimal getBirthWeightKg() {
        return birthWeightKg;
    }

    public void setBirthWeightKg(BigDecimal birthWeightKg) {
        this.birthWeightKg = birthWeightKg;
    }

    // ── Helper Methods ──────────────────────────────────────────────────────

    /**
     * Get gender from the child livestock entity.
     * This is the correct way to get the gender since the gender
     * is stored in the Livestock table, not in livestock_offspring.
     *
     * @return The gender of the child livestock, or null if not available
     */
    public String getGender() {
        return childLivestock != null ? childLivestock.getGender() : null;
    }

    /**
     * Check if the offspring is alive.
     * This checks both the isAlive flag and the child livestock status.
     *
     * @return true if the offspring is alive, false otherwise
     */
    public boolean isAlive() {
        // If isAlive flag is explicitly false, return false
        if (isAlive != null && !isAlive) {
            return false;
        }

        // Check child livestock status
        if (childLivestock != null) {
            String status = childLivestock.getStatus();
            if (status != null) {
                // If status is DEAD or SOLD, consider it not alive
                if (status.equals(Livestock.STATUS_DEAD) ||
                        status.equals(Livestock.STATUS_SOLD)) {
                    return false;
                }
                // If status is ACTIVE, PREGNANT, or SICK, consider it alive
                return true;
            }
        }

        // Default to the isAlive flag value
        return isAlive != null && isAlive;
    }

    /**
     * Get the tag number of the child livestock.
     *
     * @return The tag number, or null if not available
     */
    public String getChildTagNumber() {
        return childLivestock != null ? childLivestock.getTagNumber() : null;
    }

    /**
     * Get the status of the child livestock.
     *
     * @return The status, or null if not available
     */
    public String getChildStatus() {
        return childLivestock != null ? childLivestock.getStatus() : null;
    }

    @Override
    public String toString() {
        return "LivestockOffspring{" +
                "id=" + id +
                ", birthEvent=" + (birthEvent != null ? birthEvent.getId() : "null") +
                ", childLivestock=" + (childLivestock != null ? childLivestock.getTagNumber() : "null") +
                ", generation=" + generation +
                ", isAlive=" + isAlive +
                ", outcome=" + outcome +
                '}';
    }
}