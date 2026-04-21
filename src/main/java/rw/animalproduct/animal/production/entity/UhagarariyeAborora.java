package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "uhagarariye_aborora")
public class UhagarariyeAborora {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotEmpty(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be between 2 and 50 characters")
    @Pattern(regexp = "^[A-Za-z\\s]+$", message = "First name must contain only letters and spaces")
    @Column(name = "first_name")
    private String firstName;

    @NotEmpty(message = "Last name is required")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    @Pattern(regexp = "^[A-Za-z\\s]+$", message = "Last name must contain only letters and spaces")
    @Column(name = "last_name")
    private String lastName;

    @Column(name = "gender")
    private String gender;

    @Column(name = "maritial_status")
    private String maritialStatus;

    @Column(unique = true, name = "nid")
    @Pattern(regexp = "^[0-9]{16}$", message = "National ID must be exactly 16 digits")
    private String nid;

    @Column(name = "phone")
    @Pattern(regexp = "^(078|079|072|073)[0-9]{7}$", message = "Phone number must be 10 digits starting with 078, 079, 072, or 073")
    private String phone;

    @Column(name = "email")
    @Email(message = "Please provide a valid email address")
    @Pattern(regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "Invalid email format")
    private String email;

    @Column(name = "icyo_akora")
    private String icyoAkora;

    @Column(name = "amasezerano")
    private String amasezerano;

    @Column(name = "photo")
    private String photo;

    @ManyToOne
    @JoinColumn(name = "location_id", referencedColumnName = "id")
    private Location location;

    @Column(name = "created_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "user_id")
    private Users createdBy;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Transient
    private String locationIdValue;

    public UhagarariyeAborora() {
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getMaritialStatus() {
        return maritialStatus;
    }

    public void setMaritialStatus(String maritialStatus) {
        this.maritialStatus = maritialStatus;
    }

    public String getNid() {
        return nid;
    }

    public void setNid(String nid) {
        this.nid = nid;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getIcyoAkora() {
        return icyoAkora;
    }

    public void setIcyoAkora(String icyoAkora) {
        this.icyoAkora = icyoAkora;
    }

    public String getAmasezerano() {
        return amasezerano;
    }

    public void setAmasezerano(String amasezerano) {
        this.amasezerano = amasezerano;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Users getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Users createdBy) {
        this.createdBy = createdBy;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public String getLocationIdValue() {
        return locationIdValue;
    }

    public void setLocationIdValue(String locationIdValue) {
        this.locationIdValue = locationIdValue;
    }

    @Override
    public String toString() {
        return "UhagarariyeAborora{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", nid='" + nid + '\'' +
                '}';
    }
}
