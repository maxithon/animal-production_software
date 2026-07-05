package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.DeathCauseAnalyticsDto;
import rw.animalproduct.animal.production.entity.LivestockDeath;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ADD-ON for your existing deaths report controller. Call
 * {@code deathAnalyticsService.generateCauseAnalytics(from, to)} and add
 * the result to your existing Model as "deathAnalytics", then drop the
 * fragment in death-report-additions.html into your deaths-report.html.
 */
@Service
@RequiredArgsConstructor
public class DeathAnalyticsService {

    private final LivestockDeathService deathService;
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");

    public DeathCauseAnalyticsDto generateCauseAnalytics(LocalDate from, LocalDate to) {
        DeathCauseAnalyticsDto dto = new DeathCauseAnalyticsDto();

        List<LivestockDeath> deaths = deathService.getAll().stream()
                .filter(d -> d.getDeathDate() != null && !d.getDeathDate().isBefore(from) && !d.getDeathDate().isAfter(to))
                .collect(Collectors.toList());

        dto.setTotalDeaths(deaths.size());

        for (LivestockDeath d : deaths) {
            String cause = d.getCauseOfDeath() != null && !d.getCauseOfDeath().isBlank()
                    ? d.getCauseOfDeath() : "Unspecified";
            dto.getCountByCause().merge(cause, 1L, Long::sum);

            if (d.getLivestock() != null) {
                if (d.getLivestock().getCurrentValue() != null) {
                    dto.setTotalValueLost(dto.getTotalValueLost().add(d.getLivestock().getCurrentValue()));
                }
                String category = d.getLivestock().getLivestockCategory() != null
                        ? d.getLivestock().getLivestockCategory().getName() : "Uncategorized";
                dto.getCountByCategory().merge(category, 1L, Long::sum);

                String gender = d.getLivestock().getGender() != null ? d.getLivestock().getGender() : "UNKNOWN";
                dto.getCountByGender().merge(gender, 1L, Long::sum);
            }

            dto.getCountByMonth().merge(d.getDeathDate().format(MONTH_KEY), 1L, Long::sum);
        }

        return dto;
    }
}
