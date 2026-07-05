package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.UUID;

@Entity
@Table(name="sec_user")
public class Users {

    @Id
    @GeneratedValue
    @org.hibernate.annotations.UuidGenerator
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(unique = true)
    @Email(message = "Invalid email format")
    private String email;

    @NotEmpty
    private String password;

    private boolean isActive;

    @DateTimeFormat(pattern = "dd-MM-yyyy")
    private Date registrationDate;

    @ManyToOne
    @JoinColumn(name="userTypeId", referencedColumnName = "userTypeId")
    private UsersType userTypeId;

    private String photoUrl;

    // NEW: Link user to a beneficiary
    @Column(name = "beneficiary_id")
    private UUID beneficiaryId;

    @Transient
    private String userTypeIdValue;

    // Constructors
    public Users() {}

    public Users(UUID userId, String email, String password, boolean isActive, Date registrationDate, UsersType userTypeId) {
        this.userId = userId;
        this.email = email;
        this.password = password;
        this.isActive = isActive;
        this.registrationDate = registrationDate;
        this.userTypeId = userTypeId;
    }

    // Getters and Setters
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Date getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(Date registrationDate) { this.registrationDate = registrationDate; }

    public UsersType getUserTypeId() { return userTypeId; }
    public void setUserTypeId(UsersType userTypeId) { this.userTypeId = userTypeId; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getUserTypeIdValue() { return userTypeIdValue; }
    public void setUserTypeIdValue(String userTypeIdValue) { this.userTypeIdValue = userTypeIdValue; }

    // NEW: Getter and Setter for beneficiaryId
    public UUID getBeneficiaryId() { return beneficiaryId; }
    public void setBeneficiaryId(UUID beneficiaryId) { this.beneficiaryId = beneficiaryId; }

    @Override
    public String toString() {
        return "Users{" +
                "userId=" + userId +
                ", email='" + email + '\'' +
                ", isActive=" + isActive +
                ", registrationDate=" + registrationDate +
                ", userTypeId=" + userTypeId +
                ", photoUrl='" + photoUrl + '\'' +
                ", beneficiaryId=" + beneficiaryId +
                '}';
    }
}