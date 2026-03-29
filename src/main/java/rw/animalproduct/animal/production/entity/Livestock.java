package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "livestock",
        indexes = {
                @Index(name = "idx_livestock_status",     columnList = "status"),
                @Index(name = "idx_livestock_tag_number", columnList = "tag_number")
        }
)
public class Livestock {

    // ── Status constants ─────────────────────────────────────────────
    // Use ONLY these constants everywhere in your code.
    // They match the PostgreSQL CHECK constraint:
    //   CHECK (status IN ('ACTIVE', 'SOLD', 'DEAD', 'SICK', 'PREGNANT'))
    public static final String STATUS_ACTIVE   = "ACTIVE";    // On farm, healthy
    public static final String STATUS_SOLD     = "SOLD";      // Has been sold
    public static final String STATUS_DEAD     = "DEAD";      // Deceased
    public static final String STATUS_SICK     = "SICK";      // Under treatment
    public static final String STATUS_PREGNANT = "PREGNANT";  // Currently pregnant
    // ────────────────────────────────────────────────────────────────

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotEmpty(message = "Tag number is required")
    @Column(name = "tag_number", unique = true, nullable = false)
    private String tagNumber;

    @Column(name = "gender")
    private String gender;

    @Column(name = "photo")
    private String photo;

    @Column(name = "date_received")
    private LocalDate dateReceived;

    @Column(name = "last_birth_date")
    private LocalDate lastBirthDate;

    @Column(name = "offspring_count")
    private Integer offspringCount = 0;

    @Column(name = "is_pregnant")
    private Boolean isPregnant = false;

    @Column(name = "pregnancy_months")
    private Integer pregnancyMonths;

    @Column(name = "current_value")
    private BigDecimal currentValue;

    @Column(name = "acquisition_method")
    private String acquisitionMethod;

    @Column(name = "sold_price")
    private BigDecimal soldPrice;

    // ── Status field ─────────────────────────────────────────────────
    // columnDefinition keeps DB and Java in sync.
    // The CHECK constraint is enforced by PostgreSQL (see SQL migration).
    @NotNull(message = "Status is required")
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ACTIVE;
    // ────────────────────────────────────────────────────────────────

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "userId")
    private Users createdBy;

    @ManyToOne
    @JoinColumn(name = "livestock_category_id", referencedColumnName = "id")
    private LivestockCategory livestockCategory;

    @ManyToOne
    @JoinColumn(name = "abaragizwa_amatungo_id", referencedColumnName = "id")
    private AbaragizwaAmatungo abaragizwaAmatungo;

    @ManyToOne
    @JoinColumn(name = "location_id", referencedColumnName = "id")
    private Location location;

    // ── Self-join: mother / child lineage ────────────────────────────
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mother_id", referencedColumnName = "id")
    private Livestock mother;

    @OneToMany(mappedBy = "mother", fetch = FetchType.LAZY)
    private List<Livestock> offspring = new ArrayList<>();
    // ────────────────────────────────────────────────────────────────

    @Transient
    private String livestockCategoryIdValue;

    @Transient
    private String abaragizwaAmatungoIdValue;

    public Livestock() {}

    // ── Convenience helpers ──────────────────────────────────────────

    public boolean isOnFarm() {
        return STATUS_ACTIVE.equals(this.status)
                || STATUS_SICK.equals(this.status)
                || STATUS_PREGNANT.equals(this.status);
    }

    public boolean isSold()             { return STATUS_SOLD.equals(this.status); }
    public boolean isDead()             { return STATUS_DEAD.equals(this.status); }
    public boolean isCurrentlyPregnant(){ return STATUS_PREGNANT.equals(this.status); }

    // ── Getters & Setters ────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTagNumber() { return tagNumber; }
    public void setTagNumber(String tagNumber) { this.tagNumber = tagNumber; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public LocalDate getDateReceived() { return dateReceived; }
    public void setDateReceived(LocalDate dateReceived) { this.dateReceived = dateReceived; }

    public LocalDate getLastBirthDate() { return lastBirthDate; }
    public void setLastBirthDate(LocalDate lastBirthDate) { this.lastBirthDate = lastBirthDate; }

    public Integer getOffspringCount() { return offspringCount; }
    public void setOffspringCount(Integer offspringCount) { this.offspringCount = offspringCount; }

    public Boolean getIsPregnant() { return isPregnant; }
    public void setIsPregnant(Boolean isPregnant) { this.isPregnant = isPregnant; }

    public Integer getPregnancyMonths() { return pregnancyMonths; }
    public void setPregnancyMonths(Integer pregnancyMonths) { this.pregnancyMonths = pregnancyMonths; }

    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }

    public String getAcquisitionMethod() { return acquisitionMethod; }
    public void setAcquisitionMethod(String acquisitionMethod) { this.acquisitionMethod = acquisitionMethod; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Users getCreatedBy() { return createdBy; }
    public void setCreatedBy(Users createdBy) { this.createdBy = createdBy; }

    public LivestockCategory getLivestockCategory() { return livestockCategory; }
    public void setLivestockCategory(LivestockCategory lc) { this.livestockCategory = lc; }

    public AbaragizwaAmatungo getAbaragizwaAmatungo() { return abaragizwaAmatungo; }
    public void setAbaragizwaAmatungo(AbaragizwaAmatungo a) { this.abaragizwaAmatungo = a; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public Livestock getMother() { return mother; }
    public void setMother(Livestock mother) { this.mother = mother; }

    public List<Livestock> getOffspring() { return offspring; }
    public void setOffspring(List<Livestock> offspring) { this.offspring = offspring; }

    public String getLivestockCategoryIdValue() { return livestockCategoryIdValue; }
    public void setLivestockCategoryIdValue(String v) { this.livestockCategoryIdValue = v; }

    public String getAbaragizwaAmatungoIdValue() { return abaragizwaAmatungoIdValue; }
    public void setAbaragizwaAmatungoIdValue(String v) { this.abaragizwaAmatungoIdValue = v; }

    public BigDecimal getSoldPrice() {
        return soldPrice;
    }

    public void setSoldPrice(BigDecimal soldPrice) {
        this.soldPrice = soldPrice;
    }

    @Override
    public String toString() {
        return "Livestock{id=" + id + ", tagNumber='" + tagNumber
                + "', gender='" + gender + "', status='" + status + "'}";
    }
}
