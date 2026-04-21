package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "livestock_categories")
public class LivestockCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotEmpty(message = "Category code is required")
    @Size(min = 1, max = 20, message = "Category code must be between 1 and 20 characters")
    @Column(name = "code", unique = true, nullable = false)
    private String code;

    @NotEmpty(message = "Category name is required")
    @Size(min = 2, max = 100, message = "Category name must be between 2 and 100 characters")
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "gestation_period_months")
    private Integer gestationPeriodMonths;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @OneToMany(mappedBy = "livestockCategory", cascade = CascadeType.ALL)
    private List<Livestock> livestockList;

    // Constructors
    public LivestockCategory() {
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getGestationPeriodMonths() {
        return gestationPeriodMonths;
    }

    public void setGestationPeriodMonths(Integer gestationPeriodMonths) {
        this.gestationPeriodMonths = gestationPeriodMonths;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public List<Livestock> getLivestockList() {
        return livestockList;
    }

    public void setLivestockList(List<Livestock> livestockList) {
        this.livestockList = livestockList;
    }

    @Override
    public String toString() {
        return "LivestockCategory{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", code='" + code + '\'' +
                ", gestationPeriodMonths=" + gestationPeriodMonths +
                '}';
    }
}
