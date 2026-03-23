package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "livestock_births")
public class LivestockBirth {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull(message = "Birth date is required")
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "offspring_count")
    private Integer offspringCount;

    // MALE / FEMALE / MIXED
    @Column(name = "offspring_gender")
    private String offspringGender;

    @Column(name = "weaning_date")
    private LocalDate weaningDate;

    @Column(name = "next_breeding_date")
    private LocalDate nextBreedingDate;

    @Column(name = "notes")
    private String notes;

    // The MOTHER animal who gave birth.
    // When a calf (e.g. Calf B) grows up and gives birth, a NEW LivestockBirth
    // row is created with livestock = CalfB.  Same table, new generation.
    @ManyToOne
    @JoinColumn(name = "livestock_id", referencedColumnName = "id", nullable = false)
    private Livestock livestock;

    // Child animals linked to this birth event
    @OneToMany(mappedBy = "birthEvent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LivestockOffspring> children = new ArrayList<>();

    // Transient for form binding (mirrors pattern in Livestock.java)
    @Transient
    private String livestockIdValue;

    public LivestockBirth() {}

    // ── Getters & Setters ────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public Integer getOffspringCount() { return offspringCount; }
    public void setOffspringCount(Integer offspringCount) { this.offspringCount = offspringCount; }

    public String getOffspringGender() { return offspringGender; }
    public void setOffspringGender(String offspringGender) { this.offspringGender = offspringGender; }

    public LocalDate getWeaningDate() { return weaningDate; }
    public void setWeaningDate(LocalDate weaningDate) { this.weaningDate = weaningDate; }

    public LocalDate getNextBreedingDate() { return nextBreedingDate; }
    public void setNextBreedingDate(LocalDate nextBreedingDate) { this.nextBreedingDate = nextBreedingDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Livestock getLivestock() { return livestock; }
    public void setLivestock(Livestock livestock) { this.livestock = livestock; }

    public List<LivestockOffspring> getChildren() { return children; }
    public void setChildren(List<LivestockOffspring> children) { this.children = children; }

    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String livestockIdValue) { this.livestockIdValue = livestockIdValue; }
}
