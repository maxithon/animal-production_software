package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "livestock_births")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LivestockBirth {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The raw FK column — this is what JPA actually writes to the database.
     *
     * <p><strong>IMPORTANT:</strong> The {@code livestock} association below is
     * mapped with {@code insertable=false, updatable=false}, which means calling
     * {@code setLivestock()} alone does NOT persist the foreign key.  You must
     * ALWAYS call {@code setLivestockId()} as well whenever you change the mother.
     * See {@code LivestockBirthService.resolveAndSetLivestock()} for the canonical
     * setter pattern.</p>
     */
    @Column(name = "livestock_id")
    private UUID livestockId;

    /**
     * Read-only JPA association for convenient in-memory access.
     * {@code insertable=false, updatable=false} — the FK value is controlled
     * entirely by {@code livestockId} above.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livestock_id", insertable = false, updatable = false)
    private Livestock livestock;

    @Column(name = "breeding_id")
    private UUID breedingId;

    /**
     * Read-only JPA association for breeding relationship.
     * {@code insertable=false, updatable=false} — the FK value is controlled
     * entirely by {@code breedingId} above.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "breeding_id", insertable = false, updatable = false)
    private LivestockBreeding breeding;

    @Column(name = "veterinarian_id")
    private UUID veterinarianId;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "birth_date_note")
    private String birthDateNote;

    @Column(name = "weaning_date")
    private LocalDate weaningDate;

    @Column(name = "next_breeding_date")
    private LocalDate nextBreedingDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "offspring_count")
    private Integer offspringCount;

    @Column(name = "offspring_gender")
    private String offspringGender;

    @Column(name = "source_location")
    private String sourceLocation;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_external_birth")
    private Boolean isExternalBirth;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @OneToMany(mappedBy = "birthEvent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LivestockOffspring> children = new ArrayList<>();

    @Transient
    private String livestockIdValue;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Helper Methods ──────────────────────────────────────────────────────

    /**
     * Convenience method to check if this birth is linked to a breeding record.
     */
    public boolean hasBreedingRecord() {
        return breedingId != null;
    }

    /**
     * Get the breeding ID safely.
     */
    public UUID getBreedingId() {
        return breedingId;
    }

    /**
     * Get the breeding entity (lazy-loaded).
     * Returns null if no breeding record exists.
     */
    public LivestockBreeding getBreeding() {
        return breeding;
    }

    /**
     * Set the breeding ID (use this instead of setBreeding for persistence).
     */
    public void setBreedingId(UUID breedingId) {
        this.breedingId = breedingId;
    }
}