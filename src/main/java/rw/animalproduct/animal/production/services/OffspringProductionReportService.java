package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.dto.*;
import rw.animalproduct.animal.production.entity.DateRange;
import rw.animalproduct.animal.production.entity.Location;
import rw.animalproduct.animal.production.entity.ProductionPeriod;
import rw.animalproduct.animal.production.repository.LocationRepository;
import rw.animalproduct.animal.production.repository.OffspringProductionReportRepository;

import java.sql.Date;
import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OffspringProductionReportService {

    private final OffspringProductionReportRepository repository;
    private final LocationRepository locationRepository;

    private final Map<UUID, Map<String, String>> locationHierarchyCache = new ConcurrentHashMap<>();
    private static final List<String> HIERARCHY_ORDER = List.of("PROVINCE", "DISTRICT", "SECTOR", "CELL", "VILLAGE");
    private static final int DEFAULT_TREND_MONTHS = 12;

    // ──────────────────────────────────────────────────────────────────────────
    // Production Period resolution
    // ──────────────────────────────────────────────────────────────────────────

    public DateRange resolvePeriod(ProductionPeriod period, LocalDate customFrom, LocalDate customTo) {
        ProductionPeriod effective = period == null ? ProductionPeriod.ALL_TIME : period;
        LocalDate today = LocalDate.now();

        return switch (effective) {
            case ALL_TIME -> new DateRange(null, null);
            case THIS_YEAR -> new DateRange(today.withDayOfYear(1), today);
            case LAST_YEAR -> {
                LocalDate start = today.minusYears(1).withDayOfYear(1);
                LocalDate end = start.withMonth(12).withDayOfMonth(31);
                yield new DateRange(start, end);
            }
            case THIS_MONTH -> new DateRange(today.withDayOfMonth(1), today);
            case CUSTOM -> {
                if (customFrom != null && customTo != null && customFrom.isAfter(customTo)) {
                    yield new DateRange(customTo, customFrom);
                }
                yield new DateRange(customFrom, customTo);
            }
        };
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Main table + KPIs
    // ──────────────────────────────────────────────────────────────────────────

    public Page<OffspringProductionReportDTO> getReport(UUID categoryId, String status, String search,
                                                        DateRange range, Pageable pageable) {
        Pageable unsortedPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<OffspringProductionReportRepository.OffspringRow> page =
                repository.findOffspringProduction(categoryId, status, blankToNull(search),
                        range.from(), range.to(), unsortedPage);
        return page.map(this::toDto);
    }

    public OffspringProductionKpiDTO getKpis(UUID categoryId, String status, DateRange range) {
        OffspringProductionReportRepository.KpiRow row =
                repository.findKpis(categoryId, status, range.from(), range.to());

        long totalMothers = nz(row.getTotal_mothers());
        long productiveMothers = nz(row.getProductive_mothers());
        long nonProductiveMothers = nz(row.getNon_productive_mothers());
        long totalBirthEvents = nz(row.getTotal_birth_events());
        long totalOffspring = nz(row.getTotal_offspring());
        long totalMale = nz(row.getTotal_male());
        long totalFemale = nz(row.getTotal_female());
        long totalDied = nz(row.getTotal_died());
        long totalSold = nz(row.getTotal_sold());
        long totalTransferred = nz(row.getTotal_transferred());
        long totalWeaned = nz(row.getTotal_weaned());
        long totalStillborn = nz(row.getTotal_stillborn());

        // Calculate total alive
        long totalAlive = Math.max(0, totalOffspring - totalDied - totalSold - totalTransferred);

        // Calculate rates
        double avgPerBirth = totalBirthEvents == 0 ? 0.0 : round1(totalOffspring / (double) totalBirthEvents);
        double avgPerMother = totalMothers == 0 ? 0.0 : round1(totalOffspring / (double) totalMothers);
        double avgPerProductiveMother = productiveMothers == 0 ? 0.0 : round1(totalOffspring / (double) productiveMothers);

        // Survival Rate = Alive ÷ (Total Offspring - Sold - Transferred) × 100
        long survivalDenominator = totalOffspring - totalSold - totalTransferred;
        Double survivalRate = survivalDenominator > 0 ? round1((totalAlive / (double) survivalDenominator) * 100) : null;

        Double stillbirthRate = totalOffspring > 0 ? round1((totalStillborn / (double) totalOffspring) * 100) : null;
        Double weaningRate = totalOffspring > 0 ? round1((totalWeaned / (double) totalOffspring) * 100) : null;
        Double productiveRate = totalMothers > 0 ? round1((productiveMothers / (double) totalMothers) * 100) : null;

        return OffspringProductionKpiDTO.builder()
                .totalMothers(totalMothers)
                .productiveMothers(productiveMothers)
                .nonProductiveMothers(nonProductiveMothers)
                .totalBirthEvents(totalBirthEvents)
                .totalOffspring(totalOffspring)
                .totalMale(totalMale)
                .totalFemale(totalFemale)
                .totalAlive(totalAlive)
                .totalDied(totalDied)
                .totalSold(totalSold)
                .totalTransferred(totalTransferred)
                .totalWeaned(totalWeaned)
                .totalStillborn(totalStillborn)
                .survivalRate(survivalRate)
                .stillbirthRate(stillbirthRate)
                .weaningRate(weaningRate)
                .productiveRate(productiveRate)
                .averageOffspringPerBirth(avgPerBirth)
                .averageOffspringPerMother(avgPerMother)
                .averageOffspringPerProductiveMother(avgPerProductiveMother)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Production trend chart
    // ──────────────────────────────────────────────────────────────────────────

    public List<OffspringTrendPointDTO> getTrend(UUID categoryId, String status, String search, DateRange range) {
        List<OffspringProductionReportRepository.TrendRow> raw =
                repository.findMonthlyTrend(categoryId, status, blankToNull(search), range.from(), range.to());

        List<OffspringTrendPointDTO> points = raw.stream()
                .map(r -> OffspringTrendPointDTO.builder()
                        .month(toYearMonth(r.getMonth()))
                        .mothers(nz(r.getMothers()))
                        .births(nz(r.getBirths()))
                        .offspring(nz(r.getOffspring()))
                        .avgOffspringPerBirth(r.getAvg_offspring_per_birth() != null ? r.getAvg_offspring_per_birth() : 0.0)
                        .build())
                .collect(Collectors.toList());

        if (range.from() == null && points.size() > DEFAULT_TREND_MONTHS) {
            points = points.subList(points.size() - DEFAULT_TREND_MONTHS, points.size());
        }
        return points;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mother detail
    // ──────────────────────────────────────────────────────────────────────────

    public MotherProductionDetailDTO getMotherDetail(UUID motherId) {
        OffspringProductionReportRepository.MotherSummaryRow summary = repository.findMotherSummary(motherId);
        if (summary == null) return null;

        List<BirthEventDTO> history = repository.findBirthHistory(motherId).stream()
                .map(h -> BirthEventDTO.builder()
                        .birthDate(toLocalDate(h.getBirth_date()))
                        .offspringCount(nz(h.getOffspring_count()))
                        .build())
                .collect(Collectors.toList());

        // Calculate days since last birth
        Integer daysSinceLastBirth = null;
        if (summary.getLast_breeding_date() != null) {
            daysSinceLastBirth = (int) Period.between(
                    summary.getLast_breeding_date().toLocalDate(),
                    LocalDate.now()
            ).getDays();
        }

        return MotherProductionDetailDTO.builder()
                .animalId(summary.getAnimal_id())
                .tagNumber(summary.getTag_number())
                .categoryName(summary.getCategory_name() != null ? summary.getCategory_name() : "Uncategorized")
                .motherBirthDate(toLocalDate(summary.getMother_birth_date()))
                .status(summary.getStatus())
                .reproductiveStatus(summary.getReproductive_status())
                .numberOfBirths(nz(summary.getNumber_of_births()))
                .totalOffspring(nz(summary.getTotal_offspring()))
                .maleOffspring(nz(summary.getMale_offspring()))
                .femaleOffspring(nz(summary.getFemale_offspring()))
                .deathCount(nz(summary.getDeath_count()))
                .soldCount(nz(summary.getSold_count()))
                .transferredCount(nz(summary.getTransferred_count()))
                .weanedCount(nz(summary.getWeaned_count()))
                .stillbornCount(nz(summary.getStillborn_count()))
                .lastBreedingDate(toLocalDate(summary.getLast_breeding_date()))
                .expectedDueDate(toLocalDate(summary.getExpected_due_date()))
                .daysSinceLastBirth(daysSinceLastBirth)
                .birthHistory(history)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Location Hierarchy Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private Map<String, String> buildLocationHierarchy(UUID locationId) {
        if (locationId == null) return new HashMap<>();
        if (locationHierarchyCache.containsKey(locationId)) {
            return locationHierarchyCache.get(locationId);
        }

        Map<String, String> hierarchy = new LinkedHashMap<>();
        try {
            Optional<Location> locationOpt = locationRepository.findById(locationId);
            if (!locationOpt.isPresent()) {
                locationHierarchyCache.put(locationId, hierarchy);
                return hierarchy;
            }

            Location current = locationOpt.get();
            int depth = 0;
            while (current != null && depth < 10) {
                String type = current.getType();
                if (type != null && !type.trim().isEmpty()) {
                    String normalizedType = type.trim().toUpperCase();
                    if (!hierarchy.containsKey(normalizedType)) {
                        hierarchy.put(normalizedType, current.getName());
                    }
                }
                if (current.getParent() != null) {
                    UUID parentId = current.getParent().getId();
                    if (parentId != null) {
                        Optional<Location> parentOpt = locationRepository.findById(parentId);
                        current = parentOpt.orElse(null);
                    } else {
                        current = null;
                    }
                } else {
                    current = null;
                }
                depth++;
            }
        } catch (Exception e) {
            log.warn("Error building location hierarchy: {}", e.getMessage());
        }

        locationHierarchyCache.put(locationId, hierarchy);
        return hierarchy;
    }

    private String buildFullLocationPath(Map<String, String> hierarchy) {
        if (hierarchy == null || hierarchy.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (String level : HIERARCHY_ORDER) {
            if (hierarchy.containsKey(level) && hierarchy.get(level) != null) {
                String value = hierarchy.get(level).trim();
                if (!value.isEmpty()) parts.add(value);
            }
        }
        return parts.isEmpty() ? null : String.join(" › ", parts);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mapping helpers
    // ──────────────────────────────────────────────────────────────────────────

    private OffspringProductionReportDTO toDto(OffspringProductionReportRepository.OffspringRow r) {
        UUID locationId = r.getLocation_id();
        Map<String, String> hierarchy = buildLocationHierarchy(locationId);

        String province = hierarchy.get("PROVINCE");
        String district = hierarchy.get("DISTRICT");
        String sector = hierarchy.get("SECTOR");
        String cell = hierarchy.get("CELL");
        String village = hierarchy.get("VILLAGE");
        String fullPath = buildFullLocationPath(hierarchy);

        if (fullPath == null || fullPath.isEmpty()) {
            fullPath = r.getLocation_name() != null ? r.getLocation_name() : "Unassigned";
        }

        // Calculate days since last birth
        Integer daysSinceLastBirth = null;
        if (r.getLast_birth_date() != null) {
            daysSinceLastBirth = (int) Period.between(
                    r.getLast_birth_date().toLocalDate(),
                    LocalDate.now()
            ).getDays();
        }

        return OffspringProductionReportDTO.builder()
                .animalId(r.getAnimal_id())
                .tagNumber(r.getTag_number())
                .categoryName(r.getCategory_name() != null ? r.getCategory_name() : "Uncategorized")
                .motherBirthDate(toLocalDate(r.getMother_birth_date()))
                .status(r.getStatus())
                .reproductiveStatus(r.getReproductive_status())
                .locationName(fullPath)
                .province(province)
                .district(district)
                .sector(sector)
                .cell(cell)
                .village(village)
                // ── Dynamic Beneficiary Data from Database ──
                .beneficiaryName(r.getBeneficiary_name() != null ? r.getBeneficiary_name() : "Unknown")
                .beneficiaryPhone(r.getBeneficiary_phone() != null ? r.getBeneficiary_phone() : "—")
                .rank(nz(r.getProduction_rank()))
                .numberOfBirths(nz(r.getNumber_of_births()))
                .totalOffspring(nz(r.getTotal_offspring()))
                .maleOffspring(nz(r.getMale_offspring()))
                .femaleOffspring(nz(r.getFemale_offspring()))
                .firstBirthDate(toLocalDate(r.getFirst_birth_date()))
                .lastBirthDate(toLocalDate(r.getLast_birth_date()))
                .deathCount(nz(r.getDeath_count()))
                .soldCount(nz(r.getSold_count()))
                .transferredCount(nz(r.getTransferred_count()))
                .weanedCount(nz(r.getWeaned_count()))
                .lastBreedingDate(toLocalDate(r.getLast_breeding_date()))
                .expectedDueDate(toLocalDate(r.getExpected_due_date()))
                .daysSinceLastBirth(daysSinceLastBirth)
                .build();
    }

    private static LocalDate toLocalDate(Date d) {
        return d == null ? null : d.toLocalDate();
    }

    private static YearMonth toYearMonth(Date d) {
        return d == null ? null : YearMonth.from(d.toLocalDate());
    }

    private static long nz(Long v) { return v == null ? 0L : v; }
    private static double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}