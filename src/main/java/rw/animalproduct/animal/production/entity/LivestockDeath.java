package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "livestock_deaths")
public class LivestockDeath {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull(message = "Death date is required")
    @Column(name = "death_date", nullable = false)
    private LocalDate deathDate;

    @Column(name = "cause_of_death", columnDefinition = "TEXT")
    private String causeOfDeath;

    @ManyToOne
    @JoinColumn(name = "livestock_id", referencedColumnName = "id", nullable = false)
    private Livestock livestock;

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

    @Transient
    private String livestockIdValue;

    @Transient
    private String veterinarianIdValue;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public LivestockDeath() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LocalDate getDeathDate() { return deathDate; }
    public void setDeathDate(LocalDate deathDate) { this.deathDate = deathDate; }

    public String getCauseOfDeath() { return causeOfDeath; }
    public void setCauseOfDeath(String causeOfDeath) { this.causeOfDeath = causeOfDeath; }

    /**
     * Convenience method: getCause() is an alias for getCauseOfDeath()
     */
    public String getCause() {
        return this.causeOfDeath;
    }

    public void setCause(String cause) {
        this.causeOfDeath = cause;
    }

    public Livestock getLivestock() { return livestock; }
    public void setLivestock(Livestock livestock) { this.livestock = livestock; }

    public Veterinarian getVeterinarian() { return veterinarian; }
    public void setVeterinarian(Veterinarian veterinarian) { this.veterinarian = veterinarian; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Users getCreatedBy() { return createdBy; }
    public void setCreatedBy(Users createdBy) { this.createdBy = createdBy; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String livestockIdValue) { this.livestockIdValue = livestockIdValue; }

    public String getVeterinarianIdValue() { return veterinarianIdValue; }
    public void setVeterinarianIdValue(String veterinarianIdValue) { this.veterinarianIdValue = veterinarianIdValue; }
}
