package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /**
     * The mother animal.
     * NULLABLE — when isExternalBirth = true, the mother is unknown (animal was purchased).
     * For farm births this must be set.
     */
    @ManyToOne
    @JoinColumn(name = "livestock_id", referencedColumnName = "id", nullable = true)
    private Livestock livestock;

    @ManyToOne
    @JoinColumn(name = "breeding_id", referencedColumnName = "id")
    private LivestockBreeding breeding;

    @ManyToOne
    @JoinColumn(name = "veterinarian_id", referencedColumnName = "id")
    private Veterinarian veterinarian;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "user_id")
    private Users createdBy;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "offspring_count")
    private Integer offspringCount;

    @Column(name = "offspring_gender", length = 20)
    private String offspringGender;

    @Column(name = "weaning_date")
    private LocalDate weaningDate;

    @Column(name = "next_breeding_date")
    private LocalDate nextBreedingDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * TRUE  → animal was purchased from outside; mother is unknown; livestock field is null.
     * FALSE → animal was born on this farm; livestock field holds the mother.
     *
     * birth_date in both cases holds the actual birth date of the offspring.
     * For purchased animals this is the date given by the seller (or an estimate).
     * This date is used to calculate breeding eligibility.
     */
    @Column(name = "is_external_birth")
    private Boolean isExternalBirth = false;

    /**
     * Where the animal was purchased / sourced from.
     * Only relevant when isExternalBirth = true.
     * Example: "Nyagatare livestock market", "Private farm in Huye"
     */
    @Column(name = "source_location", length = 255)
    private String sourceLocation;

    @OneToMany(mappedBy = "birthEvent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LivestockOffspring> children = new ArrayList<>();

    @Transient
    private String livestockIdValue;

    @Transient
    private String breedingIdValue;

    @Transient
    private String veterinarianIdValue;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isExternalBirth == null) {
            isExternalBirth = false;
        }
    }

    public LivestockBirth() {}

    // ── Convenience helpers ──────────────────────────────────────────

    public boolean isExternal() {
        return Boolean.TRUE.equals(isExternalBirth);
    }

    // ── Getters & Setters ────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public Livestock getLivestock() { return livestock; }
    public void setLivestock(Livestock livestock) { this.livestock = livestock; }

    public LivestockBreeding getBreeding() { return breeding; }
    public void setBreeding(LivestockBreeding breeding) { this.breeding = breeding; }

    public Veterinarian getVeterinarian() { return veterinarian; }
    public void setVeterinarian(Veterinarian veterinarian) { this.veterinarian = veterinarian; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Users getCreatedBy() { return createdBy; }
    public void setCreatedBy(Users createdBy) { this.createdBy = createdBy; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

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

    public Boolean getIsExternalBirth() { return isExternalBirth; }
    public void setIsExternalBirth(Boolean isExternalBirth) { this.isExternalBirth = isExternalBirth; }

    public String getSourceLocation() { return sourceLocation; }
    public void setSourceLocation(String sourceLocation) { this.sourceLocation = sourceLocation; }

    public List<LivestockOffspring> getChildren() { return children; }
    public void setChildren(List<LivestockOffspring> children) { this.children = children; }

    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String livestockIdValue) { this.livestockIdValue = livestockIdValue; }

    public String getBreedingIdValue() { return breedingIdValue; }
    public void setBreedingIdValue(String breedingIdValue) { this.breedingIdValue = breedingIdValue; }

    public String getVeterinarianIdValue() { return veterinarianIdValue; }
    public void setVeterinarianIdValue(String veterinarianIdValue) { this.veterinarianIdValue = veterinarianIdValue; }
}
