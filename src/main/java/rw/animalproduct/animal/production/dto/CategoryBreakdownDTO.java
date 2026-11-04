package rw.animalproduct.animal.production.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoryBreakdownDTO {
    private String categoryName;
    private long totalPregnant;
    private long dueWithin3Days;
    private long dueThisWeek;
    private long dueThisMonth;
    private long overduePregnancies;
}