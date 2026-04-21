package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * LivestockBreeding entity - represents breeding events
 * Database schema: id, livestock_id, breeding_date, breeding_method, male_livestock_id,
 * veterinarian_id, status, expected_pregnancy_check_date, expected_due_date, notes,
 * created_at, created_by, is_deleted
 */
@Entity
@Table(name = "livestock_breeding")
public class LivestockBreeding {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "livestock_id", referencedColumnName = "id")
    @NotNull(message = "Female livestock is required")
    private Livestock livestock;

    @NotNull(message = "Breeding date is required")
    @Column(name = "breeding_date")
    private LocalDate breedingDate;

    @Column(name = "breeding_method", length = 50)
    private String breedingMethod; // NATURAL, ARTIFICIAL_INSEMINATION, etc.

    @ManyToOne
    @JoinColumn(name = "male_livestock_id", referencedColumnName = "id")
    private Livestock maleLivestock;

    @ManyToOne
    @JoinColumn(name = "veterinarian_id", referencedColumnName = "id")
    private Veterinarian veterinarian;

    @Column(name = "status", length = 30)
    private String status; // PENDING, CONFIRMED_PREGNANT, FAILED, COMPLETED

    @Column(name = "expected_pregnancy_check_date")
    private LocalDate expectedPregnancyCheckDate;

    @Column(name = "expected_due_date")
    private LocalDate expectedDueDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "user_id")
    private Users createdBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    // Transient fields for form binding
    @Transient
    private String livestockIdValue;

    @Transient
    private String maleLivestockIdValue;

    @Transient
    private String veterinarianIdValue;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public LivestockBreeding() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Livestock getLivestock() { return livestock; }
    public void setLivestock(Livestock livestock) { this.livestock = livestock; }

    public LocalDate getBreedingDate() { return breedingDate; }
    public void setBreedingDate(LocalDate breedingDate) { this.breedingDate = breedingDate; }

    public String getBreedingMethod() { return breedingMethod; }
    public void setBreedingMethod(String breedingMethod) { this.breedingMethod = breedingMethod; }

    public Livestock getMaleLivestock() { return maleLivestock; }
    public void setMaleLivestock(Livestock maleLivestock) { this.maleLivestock = maleLivestock; }

    public Veterinarian getVeterinarian() { return veterinarian; }
    public void setVeterinarian(Veterinarian veterinarian) { this.veterinarian = veterinarian; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getExpectedPregnancyCheckDate() { return expectedPregnancyCheckDate; }
    public void setExpectedPregnancyCheckDate(LocalDate expectedPregnancyCheckDate) {
        this.expectedPregnancyCheckDate = expectedPregnancyCheckDate;
    }

    public LocalDate getExpectedDueDate() { return expectedDueDate; }
    public void setExpectedDueDate(LocalDate expectedDueDate) { this.expectedDueDate = expectedDueDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Users getCreatedBy() { return createdBy; }
    public void setCreatedBy(Users createdBy) { this.createdBy = createdBy; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String livestockIdValue) { this.livestockIdValue = livestockIdValue; }

    public String getMaleLivestockIdValue() { return maleLivestockIdValue; }
    public void setMaleLivestockIdValue(String maleLivestockIdValue) {
        this.maleLivestockIdValue = maleLivestockIdValue;
    }

    public String getVeterinarianIdValue() { return veterinarianIdValue; }
    public void setVeterinarianIdValue(String veterinarianIdValue) {
        this.veterinarianIdValue = veterinarianIdValue;
    }

    // Constants for breeding methods
    public static final String METHOD_NATURAL = "NATURAL";
    public static final String METHOD_ARTIFICIAL = "ARTIFICIAL_INSEMINATION";

    // Constants for status
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED_PREGNANT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_COMPLETED = "COMPLETED";
}
