package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "livestock_births",
        indexes = {
                @Index(name = "idx_livestock_births_birth_date", columnList = "birth_date DESC"),
                @Index(name = "idx_livestock_births_livestock_id", columnList = "livestock_id"),
                @Index(name = "idx_births_breeding", columnList = "breeding_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"livestock", "breeding", "veterinarian", "children"})
public class LivestockBirth {

    public static final String BIRTH_TYPE_NORMAL    = "NORMAL";
    public static final String BIRTH_TYPE_DIFFICULT = "DIFFICULT";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "livestock_id")
    private UUID livestockId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livestock_id", insertable = false, updatable = false)
    private Livestock livestock;

    @Column(name = "breeding_id")
    private UUID breedingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "breeding_id", insertable = false, updatable = false)
    private LivestockBreeding breeding;

    @Column(name = "veterinarian_id")
    private UUID veterinarianId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veterinarian_id", insertable = false, updatable = false)
    private Veterinarian veterinarian;

    @Transient
    private String veterinarianIdValue;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "birth_date_note", length = 500)
    private String birthDateNote;

    @Column(name = "weaning_date")
    private LocalDate weaningDate;

    @Column(name = "next_breeding_date")
    private LocalDate nextBreedingDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "offspring_count")
    private Integer offspringCount;

    /**
     * Stay within the DB's varchar(20) — the summary is generated as
     * "2M, 1F, 1U" style tokens by the service. Don't widen the column.
     */
    @Column(name = "offspring_gender", length = 20)
    private String offspringGender;

    @Column(name = "source_location", length = 255)
    private String sourceLocation;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_external_birth", nullable = false)
    private Boolean isExternalBirth = false;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "birth_type", length = 20)
    private String birthType;

    @Column(name = "assistance_required")
    private Boolean assistanceRequired = false;

    @Column(name = "number_born_alive")
    private Integer numberBornAlive;

    @Column(name = "number_stillborn")
    private Integer numberStillborn = 0;

    @Column(name = "attendant_name", length = 150)
    private String attendantName;

    @OneToMany(mappedBy = "birthEvent", cascade = CascadeType.ALL, orphanRemoval = false, fetch = FetchType.LAZY)
    private List<LivestockOffspring> children = new ArrayList<>();

    @Transient
    private String livestockIdValue;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        this.updatedAt = now;
        if (this.assistanceRequired == null) this.assistanceRequired = false;
        if (this.numberStillborn == null)    this.numberStillborn = 0;
        if (this.isExternalBirth == null)    this.isExternalBirth = false;
        if (this.offspringCount == null)     this.offspringCount = 0;
    }

    @PreUpdate
    public void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public void setLivestockId(UUID id) {
        this.livestockId = id;
        if (this.livestock != null && !this.livestock.getId().equals(id)) {
            this.livestock = null;
        }
    }

    public void setVeterinarianId(UUID id) {
        this.veterinarianId = id;
        if (this.veterinarian != null && !this.veterinarian.getId().equals(id)) {
            this.veterinarian = null;
        }
    }

    public boolean hasBreedingRecord() { return breedingId != null; }
    public boolean isLocked()          { return completedAt != null; }

    public String getAttendantDisplayName() {
        if (veterinarian != null) {
            String label = veterinarian.getFullName();
            if (veterinarian.getLicenseNumber() != null && !veterinarian.getLicenseNumber().isBlank()) {
                label += " (Lic: " + veterinarian.getLicenseNumber() + ")";
            }
            return label;
        }
        if (attendantName != null && !attendantName.isBlank()) return attendantName;
        return null;
    }
}