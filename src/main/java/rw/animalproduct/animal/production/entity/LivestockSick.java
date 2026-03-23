package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Column(name = "vet_name")
    private String vetName;

    @Column(name = "temperature", precision = 4, scale = 1)
    private BigDecimal temperature;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity_level")
    private SeverityLevel severityLevel;

    @Column(name = "treatment_cost", precision = 12, scale = 2)
    private BigDecimal treatmentCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SickStatus status = SickStatus.SICK;

    @Column(name = "recovery_date")
    private LocalDate recoveryDate;

    @ManyToOne
    @JoinColumn(name = "livestock_id", referencedColumnName = "id", nullable = false)
    private Livestock livestock;

    /**
     * Full audit trail — one row per status/severity change.
     * CascadeType.ALL means deleting a sick record also removes its history.
     */
    @OneToMany(mappedBy = "livestockSick",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("changedAt ASC")
    private List<LivestockSickHistory> statusHistory = new ArrayList<>();

    @Transient
    private String livestockIdValue;

    // ── Enums ─────────────────────────────────────────────────────────
    public enum SickStatus {
        SICK, CRITICAL, RECOVERING, RECOVERED
    }

    public enum SeverityLevel {
        MILD, MODERATE, SEVERE, LIFE_THREATENING
    }

    // ── Constructors ──────────────────────────────────────────────────
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

    public String getVetName() { return vetName; }
    public void setVetName(String vetName) { this.vetName = vetName; }

    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }

    public SeverityLevel getSeverityLevel() { return severityLevel; }
    public void setSeverityLevel(SeverityLevel severityLevel) { this.severityLevel = severityLevel; }

    public BigDecimal getTreatmentCost() { return treatmentCost; }
    public void setTreatmentCost(BigDecimal treatmentCost) { this.treatmentCost = treatmentCost; }

    public SickStatus getStatus() { return status; }
    public void setStatus(SickStatus status) { this.status = status; }

    public LocalDate getRecoveryDate() { return recoveryDate; }
    public void setRecoveryDate(LocalDate recoveryDate) { this.recoveryDate = recoveryDate; }

    public Livestock getLivestock() { return livestock; }
    public void setLivestock(Livestock livestock) { this.livestock = livestock; }

    public List<LivestockSickHistory> getStatusHistory() { return statusHistory; }
    public void setStatusHistory(List<LivestockSickHistory> statusHistory) { this.statusHistory = statusHistory; }

    /**
     * FIX: Alias getter so Thymeleaf's s.history resolves correctly
     * without changing every reference in the template.
     * Thymeleaf resolves s.history → getHistory().
     */
    public List<LivestockSickHistory> getHistory() { return statusHistory; }

    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String livestockIdValue) { this.livestockIdValue = livestockIdValue; }
}
