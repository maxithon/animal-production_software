package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class VeterinarianActivityReportDto {
    private LocalDate fromDate;
    private LocalDate toDate;
    private int activeVets;
    private int totalTreatmentsInPeriod;
    private int totalSickCasesInPeriod;
    private List<VeterinarianActivityRow> rows = new ArrayList<>();
}
