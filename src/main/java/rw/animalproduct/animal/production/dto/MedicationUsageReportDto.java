package rw.animalproduct.animal.production.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class MedicationUsageReportDto {
    private LocalDate fromDate;
    private LocalDate toDate;

    private int totalTreatments;
    private BigDecimal totalSpend = BigDecimal.ZERO;

    private Map<String, Long> countByCategory = new HashMap<>();
    private Map<String, BigDecimal> spendByCategory = new HashMap<>();

    private List<MedicationUsageRow> rows = new ArrayList<>();
}
