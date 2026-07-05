package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class VeterinarianActivityRow {

    private String veterinarianId;
    private String fullName;
    private String licenseNumber;
    private String clinicName;
    private boolean active;

    private int treatmentsHandled;
    private BigDecimal treatmentRevenueHandled = BigDecimal.ZERO;

    private int sickCasesAttended;
    private int sickCasesRecovered;

    private int breedingsAssisted;
    private int abortionsAttended;

    public double getRecoveryRatePercent() {
        if (sickCasesAttended == 0) return 0.0;
        return Math.round(sickCasesRecovered * 1000.0 / sickCasesAttended) / 10.0;
    }
}
