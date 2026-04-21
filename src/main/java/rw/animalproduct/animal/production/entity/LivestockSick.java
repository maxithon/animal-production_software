package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "livestock_sick")
public class LivestockSick {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull(message = "Reported date is required")
    @Column(name = "reported_date", nullable = false)
    private LocalDate reportedDate;

    @Column(name = "symptoms", columnDefinition = "TEXT")
    private String symptoms;

    @Column(name = "diagnosis", columnDefinition = "TEXT")
    private String diagnosis;

    @Column(name = "treatment_notes", columnDefinition = "TEXT")
    private String treatmentNotes;

    @Column(name = "temperature", precision = 4, scale = 1)
    private BigDecimal temperature;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity_level")
    private SeverityLevel severityLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SickStatus status = SickStatus.SICK;

    @Column(name = "recovery_date")
    private LocalDate recoveryDate;

    @ManyToOne
    @JoinColumn(name = "livestock_id", referencedColumnName = "id", nullable = false)
    private Livestock livestock;

    @ManyToOne
    @JoinColumn(name = "veterinarian_id", referencedColumnName = "id")
    private Veterinarian veterinarian;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "created_by", referencedColumnName = "user_id")
    private Users createdBy;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @OneToMany(mappedBy = "livestockSick",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("changedAt ASC")
    private List<LivestockSickHistory> statusHistory = new ArrayList<>();

    @Transient
    private String livestockIdValue;

    @Transient
    private String veterinarianIdValue;

    // ── Enums ─────────────────────────────────────────────────────────
    public enum SickStatus {
        SICK, CRITICAL, RECOVERING, RECOVERED
    }

    public enum SeverityLevel {
        MILD, MODERATE, SEVERE, LIFE_THREATENING
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public LivestockSick() {}

    // ── Getters & Setters ─────────────────────────────────────────────
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LocalDate getReportedDate() { return reportedDate; }
    public void setReportedDate(LocalDate reportedDate) { this.reportedDate = reportedDate; }

    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public String getTreatmentNotes() { return treatmentNotes; }
    public void setTreatmentNotes(String treatmentNotes) { this.treatmentNotes = treatmentNotes; }

    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }

    public SeverityLevel getSeverityLevel() { return severityLevel; }
    public void setSeverityLevel(SeverityLevel severityLevel) { this.severityLevel = severityLevel; }

    public SickStatus getStatus() { return status; }
    public void setStatus(SickStatus status) { this.status = status; }

    public LocalDate getRecoveryDate() { return recoveryDate; }
    public void setRecoveryDate(LocalDate recoveryDate) { this.recoveryDate = recoveryDate; }

    public Livestock getLivestock() { return livestock; }
    public void setLivestock(Livestock livestock) { this.livestock = livestock; }

    public Veterinarian getVeterinarian() { return veterinarian; }
    public void setVeterinarian(Veterinarian veterinarian) { this.veterinarian = veterinarian; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Users getCreatedBy() { return createdBy; }
    public void setCreatedBy(Users createdBy) { this.createdBy = createdBy; }

    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public List<LivestockSickHistory> getStatusHistory() { return statusHistory; }
    public void setStatusHistory(List<LivestockSickHistory> statusHistory) { this.statusHistory = statusHistory; }

    /**
     * Alias getter for Thymeleaf compatibility
     */
    public List<LivestockSickHistory> getHistory() { return statusHistory; }

    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String livestockIdValue) { this.livestockIdValue = livestockIdValue; }

    public String getVeterinarianIdValue() { return veterinarianIdValue; }
    public void setVeterinarianIdValue(String veterinarianIdValue) { this.veterinarianIdValue = veterinarianIdValue; }

    /**
     * ✅ FIX: Kept getVetName() for Thymeleaf/JS backward compatibility (reads veterinarian entity).
     * setVetName() is intentionally a no-op — use setVeterinarian() instead.
     */
    @Deprecated
    @Transient
    public String getVetName() {
        return veterinarian != null ? veterinarian.getFullName() : null;
    }

    @Deprecated
    @Transient
    public void setVetName(String vetName) {
        // Intentional no-op — use setVeterinarian(Veterinarian) instead.
        // Kept only so existing code that calls this doesn't break at compile time.
    }
}
