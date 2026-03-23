package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "a_location")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // FIXED: Changed from String to Integer to match database
    // 1 = active, 0 = inactive (based on your database screenshot)
    @Column(name="state")
    private Integer state;

    private Integer version;

    private String code;

    @Column(name = "regulator_code")
    private String regulatorCode;

    private String name;

    @Column(name = "location_type")
    private String locationType; // COUNTRY, PROVINCE, DISTRICT, SECTOR, CELL, VILLAGE

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Location parent;

    private String comments;

    // Constructors
    public Location() {
    }

    public Location(UUID id, Integer state, Integer version, String code, String regulatorCode,
                    String name, String locationType, Location parent, String comments) {
        this.id = id;
        this.state = state;
        this.version = version;
        this.code = code;
        this.regulatorCode = regulatorCode;
        this.name = name;
        this.locationType = locationType;
        this.parent = parent;
        this.comments = comments;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getState() {
        return state;
    }

    public void setState(Integer state) {
        this.state = state;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getRegulatorCode() {
        return regulatorCode;
    }

    public void setRegulatorCode(String regulatorCode) {
        this.regulatorCode = regulatorCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocationType() {
        return locationType;
    }

    public void setLocationType(String locationType) {
        this.locationType = locationType;
    }

    public Location getParent() {
        return parent;
    }

    public void setParent(Location parent) {
        this.parent = parent;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    @Override
    public String toString() {
        return "Location{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", locationType='" + locationType + '\'' +
                '}';
    }
}