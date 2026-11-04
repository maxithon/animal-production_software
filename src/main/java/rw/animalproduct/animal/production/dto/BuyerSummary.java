package rw.animalproduct.animal.production.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class BuyerSummary {
    private UUID id;
    private String firstName;
    private String lastName;
    private String phone;
    private String address;
    private String nationalId;
    private String email;
    private String buyerType;
    private String notes;
    private Boolean isActive;
    private String photo;
    private LocalDateTime createdAt;       // NEW
    private Integer totalPurchases;
    private BigDecimal totalSpent;

    // Default constructor (required)
    public BuyerSummary() {
    }

    // Full constructor - MUST match the query exactly
    public BuyerSummary(UUID id, String firstName, String lastName, String phone,
                        String address, String nationalId, String email,
                        String buyerType, String notes, Boolean isActive,
                        String photo, LocalDateTime createdAt,
                        Integer totalPurchases, BigDecimal totalSpent) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.address = address;
        this.nationalId = nationalId;
        this.email = email;
        this.buyerType = buyerType;
        this.notes = notes;
        this.isActive = isActive;
        this.photo = photo;
        this.createdAt = createdAt;
        this.totalPurchases = totalPurchases;
        this.totalSpent = totalSpent;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBuyerType() {
        return buyerType;
    }

    public void setBuyerType(String buyerType) {
        this.buyerType = buyerType;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getPhoto() {
        return photo;
    }

    public void setPhoto(String photo) {
        this.photo = photo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getTotalPurchases() {
        return totalPurchases;
    }

    public void setTotalPurchases(Integer totalPurchases) {
        this.totalPurchases = totalPurchases;
    }

    public BigDecimal getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(BigDecimal totalSpent) {
        this.totalSpent = totalSpent;
    }

    // Convenience method for display
    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }
}