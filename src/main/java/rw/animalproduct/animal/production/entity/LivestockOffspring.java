package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Links a birth event to one specific child animal.
 *
 * Multi-generation example:
 *   Gen 1: CowA births CalfB  → LivestockOffspring(birthEvent=B1, child=CalfB, generation=1)
 *   Gen 2: CalfB births CalfE → LivestockOffspring(birthEvent=B2, child=CalfE, generation=2)
 *   CalfE.mother = CalfB, CalfB.mother = CowA  (grandmother found automatically via mother_id chain)
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

    // The actual child animal registered in the livestock table.
    // child.mother is set to birthEvent.livestock when linked.
    @OneToOne
    @JoinColumn(name = "child_livestock_id", referencedColumnName = "id")
    private Livestock childLivestock;

    // 1 = direct child of mother, 2 = grandchild, 3 = great-grandchild, etc.
    @Column(name = "generation")
    private Integer generation = 1;

    public LivestockOffspring() {}

    public LivestockOffspring(LivestockBirth birthEvent, Livestock childLivestock, Integer generation) {
        this.birthEvent = birthEvent;
        this.childLivestock = childLivestock;
        this.generation = generation;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LivestockBirth getBirthEvent() { return birthEvent; }
    public void setBirthEvent(LivestockBirth birthEvent) { this.birthEvent = birthEvent; }

    public Livestock getChildLivestock() { return childLivestock; }
    public void setChildLivestock(Livestock childLivestock) { this.childLivestock = childLivestock; }

    public Integer getGeneration() { return generation; }
    public void setGeneration(Integer generation) { this.generation = generation; }
}
