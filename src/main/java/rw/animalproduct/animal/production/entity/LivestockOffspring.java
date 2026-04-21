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

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    public LivestockOffspring() {}

    public LivestockOffspring(LivestockBirth birthEvent, Livestock childLivestock, Integer generation) {
        this.birthEvent = birthEvent;
        this.childLivestock = childLivestock;
        this.generation = generation;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LivestockBirth getBirthEvent() { return birthEvent; }
    public void setBirthEvent(LivestockBirth birthEvent) { this.birthEvent = birthEvent; }

    public Livestock getChildLivestock() { return childLivestock; }
    public void setChildLivestock(Livestock childLivestock) { this.childLivestock = childLivestock; }

    public Integer getGeneration() { return generation; }
    public void setGeneration(Integer generation) { this.generation = generation; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }
}
