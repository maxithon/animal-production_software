package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "livestock_treatments")
public class LivestockTreatment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull(message = "Treatment date is required")
    @Column(name = "treatment_date", nullable = false)
    private LocalDate treatmentDate;

    @Column(name = "medication")
    private String medication;

    @Column(name = "treatment_cost")
    private BigDecimal treatmentCost;

    @Column(name = "next_treatment_date")
    private LocalDate nextTreatmentDate;

    @ManyToOne
    @JoinColumn(name = "sick_livestock_id", referencedColumnName = "id", nullable = false)
    private Livestock livestock;

    @Transient
    private String livestockIdValue;

    public LivestockTreatment() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LocalDate getTreatmentDate() { return treatmentDate; }
    public void setTreatmentDate(LocalDate treatmentDate) { this.treatmentDate = treatmentDate; }

    public String getMedication() { return medication; }
    public void setMedication(String medication) { this.medication = medication; }

    public BigDecimal getTreatmentCost() { return treatmentCost; }
    public void setTreatmentCost(BigDecimal treatmentCost) { this.treatmentCost = treatmentCost; }

    public LocalDate getNextTreatmentDate() { return nextTreatmentDate; }
    public void setNextTreatmentDate(LocalDate nextTreatmentDate) { this.nextTreatmentDate = nextTreatmentDate; }

    public Livestock getLivestock() { return livestock; }
    public void setLivestock(Livestock livestock) { this.livestock = livestock; }

    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String livestockIdValue) { this.livestockIdValue = livestockIdValue; }
}
