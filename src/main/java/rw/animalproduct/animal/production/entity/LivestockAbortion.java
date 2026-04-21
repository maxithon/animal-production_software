package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "livestock_abortions")
public class LivestockAbortion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull(message = "Abortion date is required")
    @Column(name = "abortion_date", nullable = false)
    private LocalDate abortionDate;

    @Column(name = "expected_birth_date")
    private LocalDate expectedBirthDate;

    @Column(name = "pregnancy_number")
    private Integer pregnancyNumber;

    @Column(name = "abortion_reason")
    private String abortionReason;

    @Column(name = "stage_of_pregnancy")
    private String stageOfPregnancy;

    @ManyToOne
    @JoinColumn(name = "livestock_id", referencedColumnName = "id", nullable = false)
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
    }

    public LivestockAbortion() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LocalDate getAbortionDate() { return abortionDate; }
    public void setAbortionDate(LocalDate abortionDate) { this.abortionDate = abortionDate; }

    public LocalDate getExpectedBirthDate() { return expectedBirthDate; }
    public void setExpectedBirthDate(LocalDate expectedBirthDate) { this.expectedBirthDate = expectedBirthDate; }

    public Integer getPregnancyNumber() { return pregnancyNumber; }
    public void setPregnancyNumber(Integer pregnancyNumber) { this.pregnancyNumber = pregnancyNumber; }

    public String getAbortionReason() { return abortionReason; }
    public void setAbortionReason(String abortionReason) { this.abortionReason = abortionReason; }

    public String getStageOfPregnancy() { return stageOfPregnancy; }
    public void setStageOfPregnancy(String stageOfPregnancy) { this.stageOfPregnancy = stageOfPregnancy; }

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

    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String livestockIdValue) { this.livestockIdValue = livestockIdValue; }

    public String getBreedingIdValue() { return breedingIdValue; }
    public void setBreedingIdValue(String breedingIdValue) { this.breedingIdValue = breedingIdValue; }

    public String getVeterinarianIdValue() { return veterinarianIdValue; }
    public void setVeterinarianIdValue(String veterinarianIdValue) { this.veterinarianIdValue = veterinarianIdValue; }
}
