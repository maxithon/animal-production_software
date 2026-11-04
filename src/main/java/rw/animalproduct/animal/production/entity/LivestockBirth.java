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

    @Column(name = "birth_type")
    private String birthType;

    @Column(name = "assistance_required")
    private Boolean assistanceRequired = false;

    @Column(name = "number_born_alive")
    private Integer numberBornAlive;

    @Column(name = "number_stillborn")
    private Integer numberStillborn = 0;

    @Column(name = "attendant_name")
    private String attendantName; // free-text fallback when no registered veterinarian is linked

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
        if (this.assistanceRequired == null) {
            this.assistanceRequired = false;
        }
        if (this.numberStillborn == null) {
            this.numberStillborn = 0;
        }
    }
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    public boolean hasBreedingRecord() {
        return breedingId != null;
    }
    public boolean isLocked() {
        return completedAt != null;
    }
    public String getAttendantDisplayName() {
        if (veterinarian != null) {
            String label = veterinarian.getFullName();
            if (veterinarian.getLicenseNumber() != null && !veterinarian.getLicenseNumber().isBlank()) {
                label += " (Lic: " + veterinarian.getLicenseNumber() + ")";
            }
            return label;
        }
        if (attendantName != null && !attendantName.isBlank()) {
            return attendantName;
        }
        return null;
    }

    public UUID getBreedingId() { return breedingId; }
    public LivestockBreeding getBreeding() { return breeding; }
    public void setBreedingId(UUID breedingId) { this.breedingId = breedingId; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getLivestockId() { return livestockId; }
    public void setLivestockId(UUID livestockId) { this.livestockId = livestockId; }

    public Livestock getLivestock() { return livestock; }
    public void setLivestock(Livestock livestock) { this.livestock = livestock; }

    public void setBreeding(LivestockBreeding breeding) { this.breeding = breeding; }

    public UUID getVeterinarianId() { return veterinarianId; }
    public void setVeterinarianId(UUID veterinarianId) { this.veterinarianId = veterinarianId; }

    public Veterinarian getVeterinarian() { return veterinarian; }
    public void setVeterinarian(Veterinarian veterinarian) { this.veterinarian = veterinarian; }

    public String getVeterinarianIdValue() { return veterinarianIdValue; }
    public void setVeterinarianIdValue(String veterinarianIdValue) { this.veterinarianIdValue = veterinarianIdValue; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getBirthDateNote() { return birthDateNote; }
    public void setBirthDateNote(String birthDateNote) { this.birthDateNote = birthDateNote; }

    public LocalDate getWeaningDate() { return weaningDate; }
    public void setWeaningDate(LocalDate weaningDate) { this.weaningDate = weaningDate; }

    public LocalDate getNextBreedingDate() { return nextBreedingDate; }
    public void setNextBreedingDate(LocalDate nextBreedingDate) { this.nextBreedingDate = nextBreedingDate; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public Integer getOffspringCount() { return offspringCount; }
    public void setOffspringCount(Integer offspringCount) { this.offspringCount = offspringCount; }

    public String getOffspringGender() { return offspringGender; }
    public void setOffspringGender(String offspringGender) { this.offspringGender = offspringGender; }

    public String getSourceLocation() { return sourceLocation; }
    public void setSourceLocation(String sourceLocation) { this.sourceLocation = sourceLocation; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Boolean getExternalBirth() { return isExternalBirth; }
    public void setExternalBirth(Boolean externalBirth) { isExternalBirth = externalBirth; }
    public Boolean getIsExternalBirth() { return isExternalBirth; }
    public void setIsExternalBirth(Boolean externalBirth) { isExternalBirth = externalBirth; }

    public Boolean getDeleted() { return isDeleted; }
    public void setDeleted(Boolean deleted) { isDeleted = deleted; }
    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean deleted) { isDeleted = deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public List<LivestockOffspring> getChildren() { return children; }
    public void setChildren(List<LivestockOffspring> children) { this.children = children; }

    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String livestockIdValue) { this.livestockIdValue = livestockIdValue; }

    public String getBirthType() { return birthType; }
    public void setBirthType(String birthType) { this.birthType = birthType; }

    public Boolean getAssistanceRequired() { return assistanceRequired; }
    public void setAssistanceRequired(Boolean assistanceRequired) { this.assistanceRequired = assistanceRequired; }

    public Integer getNumberBornAlive() { return numberBornAlive; }
    public void setNumberBornAlive(Integer numberBornAlive) { this.numberBornAlive = numberBornAlive; }

    public Integer getNumberStillborn() { return numberStillborn; }
    public void setNumberStillborn(Integer numberStillborn) { this.numberStillborn = numberStillborn; }

    public String getAttendantName() { return attendantName; }
    public void setAttendantName(String attendantName) { this.attendantName = attendantName; }
}