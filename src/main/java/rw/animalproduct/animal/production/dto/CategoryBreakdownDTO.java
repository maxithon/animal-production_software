package rw.animalproduct.animal.production.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryBreakdownDTO {

    private String categoryName;
    private long totalPregnant;
    private long dueWithin3Days;
    private long dueThisWeek;
    private long dueThisMonth;
    private long overduePregnancies;
}