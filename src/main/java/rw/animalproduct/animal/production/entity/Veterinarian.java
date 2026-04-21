package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "veterinarians", indexes = {
        @Index(name = "idx_veterinarians_nid", columnList = "national_id"),
        @Index(name = "idx_veterinarians_active", columnList = "is_active"),
        @Index(name = "idx_veterinarians_search", columnList = "first_name, last_name, license_number")
})
public class Veterinarian {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotEmpty(message = "First name is required")
    @Size(min = 2, max = 100, message = "First name must be between 2 and 100 characters")
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotEmpty(message = "Last name is required")
    @Size(min = 2, max = 100, message = "Last name must be between 2 and 100 characters")
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Email(message = "Please provide a valid email address")
    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "license_number", length = 100)
    private String licenseNumber;

    @Pattern(regexp = "^[0-9]{16}$", message = "NID must be exactly 16 digits")
    @Column(name = "national_id", length = 16)
    private String nationalId;

    @Column(name = "specialization", length = 200)
    private String specialization;

    @Column(name = "clinic_name", length = 200)
    private String clinicName;

    @ManyToOne
    @JoinColumn(name = "location_id", referencedColumnName = "id")
    private Location location;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "user_id")
    private Users createdBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Transient
    private String locationIdValue;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Veterinarian() {}

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }

    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) {
        if (nationalId != null) {
            nationalId = nationalId.replaceAll("[\\s-]", "");
        }
        this.nationalId = nationalId;
    }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public String getClinicName() { return clinicName; }
    public void setClinicName(String clinicName) { this.clinicName = clinicName; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Users getCreatedBy() { return createdBy; }
    public void setCreatedBy(Users createdBy) { this.createdBy = createdBy; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public String getLocationIdValue() { return locationIdValue; }
    public void setLocationIdValue(String locationIdValue) { this.locationIdValue = locationIdValue; }

    // Convenience methods
    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getDisplayName() {
        StringBuilder display = new StringBuilder(getFullName());
        if (licenseNumber != null && !licenseNumber.isEmpty()) {
            display.append(" (Lic: ").append(licenseNumber).append(")");
        }
        if (nationalId != null && !nationalId.isEmpty()) {
            display.append(" [NID: ").append(maskNID()).append("]");
        }
        return display.toString();
    }

    public String maskNID() {
        if (nationalId == null || nationalId.length() != 16) {
            return "N/A";
        }
        return "************" + nationalId.substring(12);
    }

    public String getFormattedNID() {
        if (nationalId == null || nationalId.length() != 16) {
            return null;
        }
        return String.format("%s-%s-%s-%s",
                nationalId.substring(0, 4),
                nationalId.substring(4, 8),
                nationalId.substring(8, 12),
                nationalId.substring(12, 16)
        );
    }

    public boolean isProfileComplete() {
        return firstName != null && !firstName.isEmpty()
                && lastName != null && !lastName.isEmpty()
                && licenseNumber != null && !licenseNumber.isEmpty()
                && nationalId != null && !nationalId.isEmpty()
                && phone != null && !phone.isEmpty();
    }

    @Override
    public String toString() {
        return "Veterinarian{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", licenseNumber='" + licenseNumber + '\'' +
                ", nid='" + maskNID() + '\'' +
                ", clinicName='" + clinicName + '\'' +
                '}';
    }
}