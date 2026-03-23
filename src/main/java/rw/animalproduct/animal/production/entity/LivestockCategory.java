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

    @NotEmpty(message = "Category name is required")
    @Size(min = 2, max = 100, message = "Category name must be between 2 and 100 characters")
    @Column(name = "name", nullable = false)
    private String name;

    @NotEmpty(message = "Category code is required")
    @Size(min = 1, max = 20, message = "Category code must be between 1 and 20 characters")
    @Column(name = "code", unique = true, nullable = false)
    private String code;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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
                '}';
    }
}
