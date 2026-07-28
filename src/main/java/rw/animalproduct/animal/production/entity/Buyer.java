package rw.animalproduct.animal.production.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Buyer entity.
 *
 * Enhancements:
 *  - firstName / lastName instead of a single free-text "buyerName"
 *  - Rwandan phone number validation (07XXXXXXXX -> 10 digits)
 *  - Rwandan National ID validation (16 digits)
 *  - Unique constraints on phone, nationalId and email at the DB level
 *    (in addition to the service-layer checks) so duplicates can NEVER
 *    slip through, even under concurrent requests.
 *  - Photo support (stores the file name; the actual file lives on disk
 *    under the configured upload directory, see FileStorageService).
 *  - Switched the id generation strategy from the deprecated
 *    org.hibernate.id.UUIDGenerator (removed in Hibernate 6 /
 *    Spring Boot 3.x) to the standard JPA GenerationType.UUID.
 *    This alone can explain "it says saved but it's not in the DB":
 *    if your Hibernate 6 runtime silently ignores/rejects the old
 *    GenericGenerator, the insert can fail after the flash message has
 *    already been queued, or fall back to a null id and get skipped by
 *    a later rollback. GenerationType.UUID is the safe, modern choice.
 */
@Entity
@Table(
        name = "buyers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_buyer_phone", columnNames = "buyer_phone"),
                @UniqueConstraint(name = "uk_buyer_national_id", columnNames = "buyer_national_id"),
                @UniqueConstraint(name = "uk_buyer_email", columnNames = "buyer_email")
        }
)
public class Buyer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 60, message = "First name must be between 2 and 60 characters")
    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 60, message = "Last name must be between 2 and 60 characters")
    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    /**
     * Rwandan mobile format: starts with 07 followed by 8 digits = 10 digits total.
     * Examples: 0788123456, 0722123456, 0733123456, 0798123456
     */
    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^07[0-9]{8}$",
            message = "Phone must be a valid Rwandan number: 10 digits starting with 07 (e.g. 0788123456)"
    )
    @Column(name = "buyer_phone", nullable = false, length = 10)
    private String phone;

    @Column(name = "buyer_address", length = 255)
    private String address;

    /**
     * Rwandan National ID: exactly 16 digits.
     */
    @Pattern(regexp = "^[0-9]{16}$", message = "National ID must be exactly 16 digits")
    @Column(name = "buyer_national_id", length = 16)
    private String nationalId;

    @Email(message = "Enter a valid email address")
    @Column(name = "buyer_email", length = 100)
    private String email;

    @Column(name = "buyer_type", length = 50)
    private String buyerType;

    /**
     * Stored file name only (e.g. "a1b2c3.jpg"). The actual bytes live on
     * disk under the configured upload directory. Kept nullable/optional.
     */
    @Column(name = "buyer_photo", length = 255)
    private String photo;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "user_id")
    private Users createdBy;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @JsonIgnore
    @OneToMany(mappedBy = "buyer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("saleDate DESC")
    private List<LivestockSale> sales = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        normalize();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        normalize();
    }

    /** Trim/normalize a few fields so "  0788123456 " and "0788123456" aren't treated as different values. */
    private void normalize() {
        if (phone != null) phone = phone.trim();
        if (nationalId != null && nationalId.isBlank()) nationalId = null;
        if (email != null) {
            email = email.trim();
            if (email.isBlank()) email = null;
        }
        if (firstName != null) firstName = firstName.trim();
        if (lastName != null) lastName = lastName.trim();
    }

    // ── Getters / Setters ───────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getBuyerType() { return buyerType; }
    public void setBuyerType(String buyerType) { this.buyerType = buyerType; }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public Users getCreatedBy() { return createdBy; }
    public void setCreatedBy(Users createdBy) { this.createdBy = createdBy; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public List<LivestockSale> getSales() { return sales; }
    public void setSales(List<LivestockSale> sales) { this.sales = sales; }

    // ── Derived / convenience fields ────────────────────────────────────

    @Transient
    public String getFullName() {
        String f = firstName != null ? firstName : "";
        String l = lastName != null ? lastName : "";
        return (f + " " + l).trim();
    }

    /**
     * Kept for backward compatibility with any code (e.g. AuditLogService
     * messages) still calling getBuyerName().
     */
    @Transient
    public String getBuyerName() {
        return getFullName();
    }

    @Transient
    public int getTotalPurchases() {
        return sales != null ? sales.size() : 0;
    }

    @Transient
    public String getDisplayName() {
        StringBuilder display = new StringBuilder(getFullName());
        if (phone != null && !phone.isEmpty()) {
            display.append(" (").append(phone).append(")");
        }
        return display.toString();
    }

    @Transient
    public String getInitials() {
        String f = (firstName != null && !firstName.isBlank()) ? firstName.substring(0, 1) : "";
        String l = (lastName != null && !lastName.isBlank()) ? lastName.substring(0, 1) : "";
        String initials = (f + l).toUpperCase();
        return initials.isBlank() ? "?" : initials;
    }
}