package rw.animalproduct.animal.production.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OffspringTrendPointDTO {

    private static final DateTimeFormatter LABEL_FMT =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private YearMonth month;
    private long mothers;
    private long births;
    private long offspring;
    private double avgOffspringPerBirth;

    public String getMonthLabel() {
        return month == null ? "" : month.format(LABEL_FMT);
    }
}