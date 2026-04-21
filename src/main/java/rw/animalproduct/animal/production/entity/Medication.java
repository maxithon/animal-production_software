package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "medications")
public class Medication {

    // ── Enums ──────────────────────────────────────────────────────────────────
    public enum MedicationCategory {
        ANTIBIOTIC, ANTIPARASITIC, VACCINE, VITAMIN, HORMONE,
        ANTI_INFLAMMATORY, ANTIFUNGAL, DEWORMER, OTHER
    }

    public enum DosageUnit {
        ml, mg, g, tablets, IU, cc
    }

    // ── Fields ─────────────────────────────────────────────────────────────────
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotBlank(message = "Medication name is required")
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "generic_name", length = 150)
    private String genericName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 40)
    private MedicationCategory category;

    @Column(name = "default_dosage", length = 30)
    private String defaultDosage;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_dosage_unit", length = 20)
    private DosageUnit defaultDosageUnit;

    @Column(name = "manufacturer", length = 150)
    private String manufacturer;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "user_id")
    private Users createdBy;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @OneToMany(mappedBy = "medication", fetch = FetchType.LAZY)
    private List<LivestockTreatment> treatments = new ArrayList<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGenericName() { return genericName; }
    public void setGenericName(String genericName) { this.genericName = genericName; }

    public MedicationCategory getCategory() { return category; }
    public void setCategory(MedicationCategory category) { this.category = category; }

    public String getDefaultDosage() { return defaultDosage; }
    public void setDefaultDosage(String defaultDosage) { this.defaultDosage = defaultDosage; }

    public DosageUnit getDefaultDosageUnit() { return defaultDosageUnit; }
    public void setDefaultDosageUnit(DosageUnit defaultDosageUnit) { this.defaultDosageUnit = defaultDosageUnit; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Users getCreatedBy() { return createdBy; }
    public void setCreatedBy(Users createdBy) { this.createdBy = createdBy; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public List<LivestockTreatment> getTreatments() { return treatments; }
    public void setTreatments(List<LivestockTreatment> treatments) { this.treatments = treatments; }

    public String getDisplayName() {
        if (genericName != null && !genericName.isBlank() && !genericName.equalsIgnoreCase(name)) {
            return name + " (" + genericName + ")";
        }
        return name;
    }
}
