package rw.animalproduct.animal.production.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "livestock_abortions")
public class LivestockAbortion {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotNull(message = "Abortion date is required")
    @Column(name = "abortion_date", nullable = false)
    private LocalDate abortionDate;

    @Column(name = "pregnancy_number")
    private Integer pregnancyNumber;

    @Column(name = "expected_birth_date")
    private LocalDate expectedBirthDate;

    @ManyToOne
    @JoinColumn(name = "livestock_id", referencedColumnName = "id", nullable = false)
    private Livestock livestock;

    @Transient
    private String livestockIdValue;

    public LivestockAbortion() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public LocalDate getAbortionDate() { return abortionDate; }
    public void setAbortionDate(LocalDate abortionDate) { this.abortionDate = abortionDate; }

    public Integer getPregnancyNumber() { return pregnancyNumber; }
    public void setPregnancyNumber(Integer pregnancyNumber) { this.pregnancyNumber = pregnancyNumber; }

    public LocalDate getExpectedBirthDate() { return expectedBirthDate; }
    public void setExpectedBirthDate(LocalDate expectedBirthDate) { this.expectedBirthDate = expectedBirthDate; }

    public Livestock getLivestock() { return livestock; }
    public void setLivestock(Livestock livestock) { this.livestock = livestock; }

    public String getLivestockIdValue() { return livestockIdValue; }
    public void setLivestockIdValue(String livestockIdValue) { this.livestockIdValue = livestockIdValue; }
}
