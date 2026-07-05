package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Links a birth event to one specific child animal.
 * Matches database schema: id, birth_id, child_livestock_id, generation, is_deleted
 */
@Entity
@Table(name = "livestock_offspring")
public class LivestockOffspring {

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

    public LivestockOffspring() {}

    public LivestockOffspring(LivestockBirth birthEvent, Livestock childLivestock, Integer generation) {
        this.birthEvent = birthEvent;
        this.childLivestock = childLivestock;
        this.generation = generation;
        this.isAlive = true;
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
                '}';
    }
}