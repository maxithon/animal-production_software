package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "livestock_breeding",
        indexes = {
                @Index(name = "idx_breeding_livestock_id", columnList = "livestock_id"),
                @Index(name = "idx_breeding_status",       columnList = "status"),
                @Index(name = "idx_breeding_due_date",     columnList = "expected_due_date")
        }
)
public class LivestockBreeding {

    // ─────────────────────────────────────────────────────────────────────────────
    // STATUS CONSTANTS
    // ─────────────────────────────────────────────────────────────────────────────

    /** Breeding recorded but pregnancy not yet confirmed. */
    public static final String STATUS_PENDING = "PENDING";

    /** Pregnancy confirmed successfully. */
    public static final String STATUS_CONFIRMED_PREGNANT = "CONFIRMED_PREGNANT";

    /** Alias used by some services — maps to CONFIRMED_PREGNANT. */
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    /** Breeding attempt failed. */
    public static final String STATUS_FAILED = "FAILED";

    /** Pregnancy completed — birth recorded. */
    public static final String STATUS_COMPLETED = "COMPLETED";

    // ─────────────────────────────────────────────────────────────────────────────
    // BREEDING METHOD CONSTANTS
    // ─────────────────────────────────────────────────────────────────────────────

    public static final String METHOD_NATURAL    = "NATURAL";
    public static final String METHOD_ARTIFICIAL = "ARTIFICIAL_INSEMINATION";
    public static final String METHOD_EMBRYO     = "EMBRYO_TRANSFER";
    public static final String METHOD_PURCHASE_PREGNANT = "PURCHASE_PREGNANT";

    // ─────────────────────────────────────────────────────────────────────────────
    // PRIMARY KEY
    // ─────────────────────────────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    // ─────────────────────────────────────────────────────────────────────────────
    // RELATIONSHIPS
    // ─────────────────────────────────────────────────────────────────────────────

    /** Female animal being bred / tracked. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livestock_id", referencedColumnName = "id", nullable = false)
    private Livestock livestock;

    /**
     * Male sire animal.
     * NULL for AI, embryo transfer, and PURCHASE_PREGNANT records.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "male_livestock_id", referencedColumnName = "id")
    private Livestock maleLivestock;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinarian_id", referencedColumnName = "id")
    private Veterinarian veterinarian;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "user_id")
    private Users createdBy;

    // ─────────────────────────────────────────────────────────────────────────────
    // BREEDING DETAILS
    // ─────────────────────────────────────────────────────────────────────────────

    @Column(name = "breeding_date", nullable = false)
    private LocalDate breedingDate;
    /**
     * NATURAL | ARTIFICIAL_INSEMINATION | EMBRYO_TRANSFER | PURCHASE_PREGNANT
     *
     * The DB check constraint must allow all four values — see migration SQL above.
     */
    @Column(name = "breeding_method", length = 50)
    private String breedingMethod;
    /**
     * PENDING | CONFIRMED_PREGNANT | FAILED | COMPLETED
     */
    @Column(name = "status", length = 30, nullable = false)
    private String status = STATUS_PENDING;

    @Column(name = "expected_pregnancy_check_date")
    private LocalDate expectedPregnancyCheckDate;

    @Column(name = "expected_due_date")
    private LocalDate expectedDueDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // ─────────────────────────────────────────────────────────────────────────────
    // AUDIT FIELDS
    // ─────────────────────────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    // ─────────────────────────────────────────────────────────────────────────────
    // TRANSIENT FIELDS (FORM BINDING)
    // ─────────────────────────────────────────────────────────────────────────────

    @Transient
    private String livestockIdValue;

    @Transient
    private String maleLivestockIdValue;

    @Transient
    private String veterinarianIdValue;

    // ─────────────────────────────────────────────────────────────────────────────
    // ENTITY LIFECYCLE
    // ─────────────────────────────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null || status.isBlank()) status = STATUS_PENDING;
        if (isDeleted == null) isDeleted = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // CONSTRUCTORS
    // ─────────────────────────────────────────────────────────────────────────────

    public LivestockBreeding() {}

    // ─────────────────────────────────────────────────────────────────────────────
    // HELPER METHODS
    // ─────────────────────────────────────────────────────────────────────────────

    public boolean isPending() {
        return STATUS_PENDING.equals(status) && !Boolean.TRUE.equals(isDeleted);
    }

    public boolean isActivePregnancy() {
        return STATUS_CONFIRMED_PREGNANT.equals(status) && !Boolean.TRUE.equals(isDeleted);
    }
    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status) && !Boolean.TRUE.equals(isDeleted);
    }

    public boolean isFailed() {
        return STATUS_FAILED.equals(status) && !Boolean.TRUE.equals(isDeleted);
    }
    /**
     * True when this record was auto-generated because the animal
     * was purchased/received while already pregnant.
     */
    public boolean isPurchasePregnant() {
        return METHOD_PURCHASE_PREGNANT.equals(breedingMethod);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // GETTERS & SETTERS
    // ─────────────────────────────────────────────────────────────────────────────

    public UUID getId()          { return id; }
    public void setId(UUID id)   { this.id = id; }
    public Livestock getLivestock()               { return livestock; }
    public void      setLivestock(Livestock ls)   { this.livestock = ls; }
    public Livestock getMaleLivestock()                  { return maleLivestock; }
    public void      setMaleLivestock(Livestock male)    { this.maleLivestock = male; }

    public Veterinarian getVeterinarian()                    { return veterinarian; }
    public void         setVeterinarian(Veterinarian vet)    { this.veterinarian = vet; }

    public Users getCreatedBy()                  { return createdBy; }
    public void  setCreatedBy(Users createdBy)   { this.createdBy = createdBy; }

    public LocalDate getBreedingDate()                { return breedingDate; }
    public void      setBreedingDate(LocalDate d)     { this.breedingDate = d; }

    public String getBreedingMethod()               { return breedingMethod; }
    public void   setBreedingMethod(String method)  { this.breedingMethod = method; }

    public String getStatus()             { return status; }
    public void   setStatus(String s)     { this.status = s; }

    public LocalDate getExpectedPregnancyCheckDate()              { return expectedPregnancyCheckDate; }
    public void      setExpectedPregnancyCheckDate(LocalDate d)   { this.expectedPregnancyCheckDate = d; }

    public LocalDate getExpectedDueDate()             { return expectedDueDate; }
    public void      setExpectedDueDate(LocalDate d)  { this.expectedDueDate = d; }

    public String getNotes()           { return notes; }
    public void   setNotes(String n)   { this.notes = n; }

    public LocalDateTime getCreatedAt()                { return createdAt; }
    public void          setCreatedAt(LocalDateTime t) { this.createdAt = t; }

    public LocalDateTime getUpdatedAt()                { return updatedAt; }
    public void          setUpdatedAt(LocalDateTime t) { this.updatedAt = t; }

    public Boolean getIsDeleted()               { return isDeleted; }
    public void    setIsDeleted(Boolean v)      { this.isDeleted = v; }

    public String getLivestockIdValue()                  { return livestockIdValue; }
    public void   setLivestockIdValue(String v)          { this.livestockIdValue = v; }

    public String getMaleLivestockIdValue()              { return maleLivestockIdValue; }
    public void   setMaleLivestockIdValue(String v)      { this.maleLivestockIdValue = v; }

    public String getVeterinarianIdValue()               { return veterinarianIdValue; }
    public void   setVeterinarianIdValue(String v)       { this.veterinarianIdValue = v; }
}