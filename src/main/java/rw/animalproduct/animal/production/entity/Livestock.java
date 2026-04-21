package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "livestock")
public class Livestock {

    // Status constants
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SOLD = "SOLD";
    public static final String STATUS_DEAD = "DEAD";
    public static final String STATUS_SICK = "SICK";
    public static final String STATUS_PREGNANT = "PREGNANT";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tag_number", unique = true)
    private String tagNumber;

    @Column(name = "gender")
    private String gender;

    @Column(name = "status")
    private String status;

    @Column(name = "acquisition_method")
    private String acquisitionMethod;

    @Column(name = "date_received")
    private LocalDate dateReceived;

    @Column(name = "current_value", precision = 12, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "last_birth_date")
    private LocalDate lastBirthDate;

    @Column(name = "offspring_count")
    private Integer offspringCount;

    @Column(name = "pregnancy_status")
    private String pregnancyStatus;

    @Column(name = "conception_date")
    private LocalDate conceptionDate;

    @Column(name = "last_breeding_date")
    private LocalDate lastBreedingDate;

    @Column(name = "first_breeding_date")
    private LocalDate firstBreedingDate;

    @Column(name = "expected_due_date")
    private LocalDate expectedDueDate;

    @Column(name = "photo")
    private String photo;

    @Column(name = "sold_price", precision = 12, scale = 2)
    private BigDecimal soldPrice;

    @Column(name = "is_pregnant")
    private Boolean isPregnant = false;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Transient
    private Integer pregnancyMonths;

    @ManyToOne
    @JoinColumn(name = "livestock_category_id")
    private LivestockCategory livestockCategory;

    @ManyToOne
    @JoinColumn(name = "abaragizwa_amatungo_id")
    private AbaragizwaAmatungo abaragizwaAmatungo;

    @ManyToOne
    @JoinColumn(name = "mother_id")
    private Livestock mother;

    @ManyToOne
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    // Transient fields for form binding
    @Transient
    private String livestockCategoryIdValue;

    @Transient
    private String abaragizwaAmatungoIdValue;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Livestock() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTagNumber() { return tagNumber; }
    public void setTagNumber(String tagNumber) { this.tagNumber = tagNumber; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAcquisitionMethod() { return acquisitionMethod; }
    public void setAcquisitionMethod(String acquisitionMethod) { this.acquisitionMethod = acquisitionMethod; }

    public LocalDate getDateReceived() { return dateReceived; }
    public void setDateReceived(LocalDate dateReceived) { this.dateReceived = dateReceived; }

    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }

    public LocalDate getLastBirthDate() { return lastBirthDate; }
    public void setLastBirthDate(LocalDate lastBirthDate) { this.lastBirthDate = lastBirthDate; }

    public Integer getOffspringCount() { return offspringCount; }
    public void setOffspringCount(Integer offspringCount) { this.offspringCount = offspringCount; }

    public String getPregnancyStatus() { return pregnancyStatus; }
    public void setPregnancyStatus(String pregnancyStatus) { this.pregnancyStatus = pregnancyStatus; }

    public LocalDate getConceptionDate() { return conceptionDate; }
    public void setConceptionDate(LocalDate conceptionDate) { this.conceptionDate = conceptionDate; }

    public LocalDate getLastBreedingDate() { return lastBreedingDate; }
    public void setLastBreedingDate(LocalDate lastBreedingDate) { this.lastBreedingDate = lastBreedingDate; }

    public LocalDate getFirstBreedingDate() { return firstBreedingDate; }
    public void setFirstBreedingDate(LocalDate firstBreedingDate) { this.firstBreedingDate = firstBreedingDate; }

    public LocalDate getExpectedDueDate() { return expectedDueDate; }
    public void setExpectedDueDate(LocalDate expectedDueDate) { this.expectedDueDate = expectedDueDate; }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public BigDecimal getSoldPrice() { return soldPrice; }
    public void setSoldPrice(BigDecimal soldPrice) { this.soldPrice = soldPrice; }

    public Boolean getIsPregnant() { return isPregnant; }
    public void setIsPregnant(Boolean isPregnant) { this.isPregnant = isPregnant; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public Integer getPregnancyMonths() { return pregnancyMonths; }
    public void setPregnancyMonths(Integer pregnancyMonths) { this.pregnancyMonths = pregnancyMonths; }

    public LivestockCategory getLivestockCategory() { return livestockCategory; }
    public void setLivestockCategory(LivestockCategory livestockCategory) {
        this.livestockCategory = livestockCategory;
    }

    public AbaragizwaAmatungo getAbaragizwaAmatungo() { return abaragizwaAmatungo; }
    public void setAbaragizwaAmatungo(AbaragizwaAmatungo abaragizwaAmatungo) {
        this.abaragizwaAmatungo = abaragizwaAmatungo;
    }

    public Livestock getMother() { return mother; }
    public void setMother(Livestock mother) { this.mother = mother; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public String getLivestockCategoryIdValue() { return livestockCategoryIdValue; }
    public void setLivestockCategoryIdValue(String livestockCategoryIdValue) {
        this.livestockCategoryIdValue = livestockCategoryIdValue;
    }

    public String getAbaragizwaAmatungoIdValue() { return abaragizwaAmatungoIdValue; }
    public void setAbaragizwaAmatungoIdValue(String abaragizwaAmatungoIdValue) {
        this.abaragizwaAmatungoIdValue = abaragizwaAmatungoIdValue;
    }
}