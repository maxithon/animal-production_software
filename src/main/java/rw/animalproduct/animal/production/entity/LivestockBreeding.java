package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * LivestockBreeding entity — maps to the livestock_breeding table.
 *
 * IMPORTANT: Every @Column name is explicit to prevent Hibernate from
 * accidentally inheriting field names from a parent/joined entity.
 */
@Entity
@Table(name = "livestock_breeding")
public class LivestockBreeding {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The female animal being bred.
     * livestock_id FK — NOT NULL enforced via livestockIdValue transient field.
     */
    @ManyToOne
    @JoinColumn(name = "livestock_id", referencedColumnName = "id", nullable = false)
    private Livestock livestock;

    @Column(name = "breeding_date", nullable = false)
    private LocalDate breedingDate;

    /** NATURAL | ARTIFICIAL_INSEMINATION | EMBRYO_TRANSFER */
    @Column(name = "breeding_method", length = 50)
    private String breedingMethod;

    /** Male sire — optional for AI / Embryo Transfer */
    @ManyToOne
    @JoinColumn(name = "male_livestock_id", referencedColumnName = "id", nullable = true)
    private Livestock maleLivestock;

    @ManyToOne
    @JoinColumn(name = "veterinarian_id", referencedColumnName = "id", nullable = true)
    private Veterinarian veterinarian;

    /** PENDING | CONFIRMED_PREGNANT | FAILED | COMPLETED */
    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "expected_pregnancy_check_date")
    private LocalDate expectedPregnancyCheckDate;

    @Column(name = "expected_due_date")
    private LocalDate expectedDueDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "user_id", nullable = true)
    private Users createdBy;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "boolean default false")
    private Boolean isDeleted = false;

    // ── Transient form-binding fields (never persisted) ───────────────────────

    @Transient
    @NotBlank(message = "Female livestock is required")
    private String livestockIdValue;

    @Transient
    private String maleLivestockIdValue;

    @Transient
    private String veterinarianIdValue;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (isDeleted == null) isDeleted = false;
    }

    public LivestockBreeding() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────

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
    public void setExpectedPregnancyCheckDate(LocalDate d) { this.expectedPregnancyCheckDate = d; }

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
    public void setLivestockIdValue(String v) { this.livestockIdValue = v; }

    public String getMaleLivestockIdValue() { return maleLivestockIdValue; }
    public void setMaleLivestockIdValue(String v) { this.maleLivestockIdValue = v; }

    public String getVeterinarianIdValue() { return veterinarianIdValue; }
    public void setVeterinarianIdValue(String v) { this.veterinarianIdValue = v; }

    // ── Constants ─────────────────────────────────────────────────────────────

    public static final String METHOD_NATURAL    = "NATURAL";
    public static final String METHOD_ARTIFICIAL = "ARTIFICIAL_INSEMINATION";
    public static final String METHOD_EMBRYO     = "EMBRYO_TRANSFER";

    public static final String STATUS_PENDING   = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED_PREGNANT";
    public static final String STATUS_FAILED    = "FAILED";
    public static final String STATUS_COMPLETED = "COMPLETED";
}
