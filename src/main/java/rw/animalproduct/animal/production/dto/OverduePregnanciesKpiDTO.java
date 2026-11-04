package rw.animalproduct.animal.production.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OverduePregnanciesKpiDTO {
    private long totalOverdue;
    private long watchCount;     // 1-7 days overdue
    private long concernCount;   // 8-14 days overdue
    private long criticalCount;  // 15+ days overdue
    private double averageDaysOverdue;
    private Long maxDaysOverdue;
}
