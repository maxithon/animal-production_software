package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.BirthPerformanceAnalyticsDto;
import rw.animalproduct.animal.production.entity.LivestockBirth;
import rw.animalproduct.animal.production.repository.LivestockBirthRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ADD-ON for your existing birth report controller. Call
 * {@code birthAnalyticsService.generateAnalytics(from, to)} and add the
 * result to your existing Model as "birthAnalytics", then drop the
 * fragment in birth-report-additions.html into your birth report page.
 */
@Service
@RequiredArgsConstructor
public class BirthAnalyticsService {

    private final LivestockBirthRepository birthRepository;

    public BirthPerformanceAnalyticsDto generateAnalytics(LocalDate from, LocalDate to) {
        BirthPerformanceAnalyticsDto dto = new BirthPerformanceAnalyticsDto();

        List<LivestockBirth> births = birthRepository.findAll().stream()
                .filter(b -> b.getBirthDate() != null && !b.getBirthDate().isBefore(from) && !b.getBirthDate().isAfter(to))
                .collect(Collectors.toList());

        dto.setTotalBirthEvents(births.size());

        int totalOffspring = 0;
        for (LivestockBirth b : births) {
            int count = b.getOffspringCount() != null ? b.getOffspringCount() : 0;
            totalOffspring += count;

            String genderMix = b.getOffspringGender(); // e.g. "MALE", "FEMALE", or "MIXED" depending on your data entry
            if (genderMix == null) continue;
            if (genderMix.equalsIgnoreCase("MALE")) {
                dto.setMaleOffspringCount(dto.getMaleOffspringCount() + count);
            } else if (genderMix.equalsIgnoreCase("FEMALE")) {
                dto.setFemaleOffspringCount(dto.getFemaleOffspringCount() + count);
            }
            // "MIXED" litters aren't split further since sex isn't recorded per-offspring in this table.
        }
        dto.setTotalOffspring(totalOffspring);
        dto.setAverageLitterSize(births.isEmpty() ? 0.0
                : Math.round(totalOffspring * 10.0 / births.size()) / 10.0);

        // ── Average interval between successive births of the same dam (all-time) ──
        List<LivestockBirth> allBirths = birthRepository.findAll().stream()
                .filter(b -> b.getBirthDate() != null && b.getLivestock() != null)
                .collect(Collectors.toList());

        Map<UUID, List<LocalDate>> byDam = new HashMap<>();
        for (LivestockBirth b : allBirths) {
            byDam.computeIfAbsent(b.getLivestock().getId(), k -> new ArrayList<>()).add(b.getBirthDate());
        }

        List<Long> intervals = new ArrayList<>();
        for (List<LocalDate> dates : byDam.values()) {
            if (dates.size() < 2) continue;
            dates.sort(Comparator.naturalOrder());
            for (int i = 1; i < dates.size(); i++) {
                intervals.add(ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i)));
            }
        }
        dto.setAverageBirthIntervalDays(intervals.isEmpty() ? 0.0
                : Math.round(intervals.stream().mapToLong(Long::longValue).average().orElse(0.0) * 10) / 10.0);

        return dto;
    }
}
