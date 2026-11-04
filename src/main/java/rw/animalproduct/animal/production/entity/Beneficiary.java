package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Date;
import java.util.LinkedList;
import java.util.UUID;

@Entity
@Table(name = "beneficiaries")
public class Beneficiary {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

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

    @Column(name = "contract_agreement", length = 1000)
    private String contractAgreement;

    @ManyToOne
    @JoinColumn(name = "representative_id", referencedColumnName = "id")
    private Representative representative;

    @ManyToOne
    @JoinColumn(name = "location_id", referencedColumnName = "id")
    private Location location;

    @Column(name = "photo", length = 1000)
    private String photo;

    @Column(name = "created_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "user_id")
    private Users createdBy;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Transient
    private String representativeIdValue;

    public Beneficiary() {
    }

    // ── Standard getters / setters ──────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getMaritialStatus() { return maritialStatus; }
    public void setMaritialStatus(String maritialStatus) { this.maritialStatus = maritialStatus; }

    public String getNid() { return nid; }
    public void setNid(String nid) { this.nid = nid; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getContractAgreement() { return contractAgreement; }
    public void setContractAgreement(String contractAgreement) { this.contractAgreement = contractAgreement; }

    public Representative getRepresentative() { return representative; }
    public void setRepresentative(Representative representative) { this.representative = representative; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    public Users getCreatedBy() { return createdBy; }
    public void setCreatedBy(Users createdBy) { this.createdBy = createdBy; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isActive() {
        return STATUS_ACTIVE.equalsIgnoreCase(status);
    }

    public String getRepresentativeIdValue() { return representativeIdValue; }
    public void setRepresentativeIdValue(String representativeIdValue) { this.representativeIdValue = representativeIdValue; }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getPhoneNumber() {
        return this.phone;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phone = phoneNumber;
    }

    // ── Derived / helper getters ─────────────────────────────────────────────

    @Transient
    public String getPhotoUrl() {
        return normaliseAssetUrl(photo);
    }

    @Transient
    public String getContractUrl() {
        return normaliseAssetUrl(contractAgreement);
    }

    /**
     * Full location hierarchy: Province › District › Sector › Cell › Village.
     * Walks the parent chain up to 10 levels deep (cycle guard).
     * Returns "N/A" when no location is assigned.
     */
    @Transient
    public String getFullLocationPath() {
        if (location == null) return "N/A";
        LinkedList<String> parts = new LinkedList<>();
        Location cur = location;
        int guard = 0;
        while (cur != null && guard++ < 10) {
            if (cur.getName() != null && !cur.getName().isBlank()) {
                parts.addFirst(cur.getName().trim());
            }
            cur = cur.getParent();
        }
        return parts.isEmpty() ? "N/A" : String.join(" › ", parts);
    }

    /**
     * Compact two-level location for table rows: Cell › Village.
     */
    @Transient
    public String getLocationShort() {
        if (location == null) return "N/A";
        LinkedList<String> parts = new LinkedList<>();
        Location cur = location;
        int taken = 0;
        while (cur != null && taken < 2) {
            if (cur.getName() != null && !cur.getName().isBlank()) {
                parts.addFirst(cur.getName().trim());
                taken++;
            }
            cur = cur.getParent();
        }
        return parts.isEmpty() ? "N/A" : String.join(" › ", parts);
    }

    private static String normaliseAssetUrl(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.startsWith("http://") || v.startsWith("https://")
                || v.startsWith("data:") || v.startsWith("/")) {
            return v;
        }
        return "/" + v;
    }

    @Override
    public String toString() {
        return "Beneficiary{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", nid='" + nid + '\'' +
                ", status='" + status + '\'' +
                ", location=" + (location != null ? location.getName() : "null") +
                '}';
    }
}