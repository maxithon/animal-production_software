package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.dto.LocationDistributionReportDto;
import rw.animalproduct.animal.production.dto.LocationDistributionRow;
import rw.animalproduct.animal.production.entity.*;
import rw.animalproduct.animal.production.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationDistributionService {

    private final LocationRepository locationRepository;
    private final LivestockRepository livestockRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final RepresentativeRepository representativeRepository;

    // Cache for frequently accessed data
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private static final int CACHE_TTL_MINUTES = 30;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    // Order used to render the full administrative path top-down
    private static final List<String> HIERARCHY_ORDER =
            List.of("PROVINCE", "DISTRICT", "SECTOR", "CELL", "VILLAGE");

    /**
     * Generate paginated report with FAO standards
     * Only returns locations that have livestock.
     *
     * NOTE: Sorting/pagination happens IN MEMORY over the built rows, not at
     * the database level, because fields like totalAnimals, activeRate, etc.
     * are computed after aggregating livestock per location — they don't
     * exist as columns on the Location entity, so the DB can't sort by them.
     */
    @Transactional(readOnly = true)
    public LocationDistributionReportDto generateReport(int page, int size, String sortBy, String direction) {
        log.info("Generating location distribution report - Page: {}, Size: {}, Sort: {}, Direction: {}",
                page, size, sortBy, direction);

        // Validate and sanitize inputs
        int validatedPage = Math.max(0, page);
        int validatedSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        // Get locations with livestock only (optimized query)
        List<UUID> locationIdsWithLivestock = getLocationIdsWithLivestock();

        log.info("Found {} locations with livestock", locationIdsWithLivestock.size());

        if (locationIdsWithLivestock.isEmpty()) {
            return createEmptyReport();
        }

        // Fetch ALL matching locations unpaged (no DB-level sort — see note above)
        List<Location> locations = locationRepository.findAllById(locationIdsWithLivestock);

        // Fetch all required data in bulk for performance
        Map<UUID, List<Livestock>> livestockByLocation = getLivestockGroupedByLocation(locationIdsWithLivestock);
        Map<UUID, List<Beneficiary>> beneficiariesByLocation = getBeneficiariesGroupedByLocation(locationIdsWithLivestock);
        Map<UUID, List<Representative>> representativesByLocation = getRepresentativesGroupedByLocation(locationIdsWithLivestock);

        // Build a row for every matching location (unpaginated at this point)
        List<LocationDistributionRow> allRows = new ArrayList<>();

        // Global aggregates for FAO metrics — computed across ALL matching locations,
        // not just the current page, so KPI cards reflect the whole report
        int totalAnimals = 0;
        BigDecimal totalValue = BigDecimal.ZERO;
        int totalActive = 0;
        int totalSick = 0;
        int totalSold = 0;
        int totalDead = 0;

        // Global category breakdown (e.g. "Cattle" -> 342, "Goat" -> 118), across ALL locations
        Map<String, Long> categoryTotals = new HashMap<>();

        for (Location location : locations) {
            LocationDistributionRow row = buildLocationRow(
                    location,
                    livestockByLocation.getOrDefault(location.getId(), Collections.emptyList()),
                    beneficiariesByLocation.getOrDefault(location.getId(), Collections.emptyList()),
                    representativesByLocation.getOrDefault(location.getId(), Collections.emptyList())
            );

            totalAnimals += row.getTotalAnimals();
            totalValue = totalValue.add(row.getTotalValue());
            totalActive += row.getActiveCount();
            totalSick += row.getSickCount();
            totalSold += row.getSoldCount();
            totalDead += row.getDeadCount();

            row.getByCategory().forEach((category, count) -> categoryTotals.merge(category, count, Long::sum));

            allRows.add(row);
        }

        // Sort rows in memory by the requested field
        Comparator<LocationDistributionRow> comparator = getRowComparator(sortBy);
        if ("desc".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        }
        allRows.sort(comparator);

        // Paginate the sorted list manually
        int totalElements = allRows.size();
        int totalPages = (int) Math.ceil((double) totalElements / validatedSize);
        if (totalPages == 0) {
            totalPages = 1;
        }
        // Clamp page if it's beyond available pages (e.g. after a filter shrinks the set)
        if (validatedPage >= totalPages) {
            validatedPage = Math.max(0, totalPages - 1);
        }

        int fromIndex = Math.min(validatedPage * validatedSize, totalElements);
        int toIndex = Math.min(fromIndex + validatedSize, totalElements);
        List<LocationDistributionRow> pageRows = allRows.subList(fromIndex, toIndex);

        log.info("Pagination result - totalElements: {}, totalPages: {}, page: {}, size: {}, rowsOnPage: {}",
                totalElements, totalPages, validatedPage, validatedSize, pageRows.size());

        // Calculate FAO metrics (based on ALL matching locations, not just the page)
        int totalLocationsWithLivestock = locationIdsWithLivestock.size();
        double avgAnimalsPerLocation = totalLocationsWithLivestock > 0 ?
                (double) totalAnimals / totalLocationsWithLivestock : 0;
        double avgValuePerAnimal = totalAnimals > 0 ?
                totalValue.divide(BigDecimal.valueOf(totalAnimals), 2, RoundingMode.HALF_UP).doubleValue() : 0;
        double activeRate = totalAnimals > 0 ? (totalActive * 100.0) / totalAnimals : 0;
        double sickRate = totalAnimals > 0 ? (totalSick * 100.0) / totalAnimals : 0;
        double mortalityRate = totalAnimals > 0 ? (totalDead * 100.0) / totalAnimals : 0;

        // Sort category totals descending by count (biggest category first) for display
        Map<String, Long> sortedCategoryTotals = categoryTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        // Set report metadata
        LocationDistributionReportDto report = new LocationDistributionReportDto();
        report.setRows(pageRows);
        report.setTotalLocations(totalLocationsWithLivestock);
        report.setTotalAnimals(totalAnimals);
        report.setTotalValue(totalValue);
        report.setTotalActiveAnimals(totalActive);
        report.setTotalSickAnimals(totalSick);
        report.setTotalSoldAnimals(totalSold);
        report.setTotalDeadAnimals(totalDead);
        report.setLivestockDensityPerLocation(avgAnimalsPerLocation);
        report.setAverageValuePerAnimal(avgValuePerAnimal);
        report.setActiveRate(activeRate);
        report.setSickRate(sickRate);
        report.setCategoryTotals(sortedCategoryTotals);

        // Set pagination metadata
        report.setPage(validatedPage);
        report.setSize(validatedSize);
        report.setTotalElements(totalElements);
        report.setTotalPages(totalPages);
        report.setFirst(validatedPage == 0);
        report.setLast(validatedPage >= totalPages - 1);

        log.info("Report generation completed - {} locations with livestock", totalLocationsWithLivestock);
        return report;
    }

    /**
     * Maps a sortBy string from the request to a comparator over the built rows.
     * Falls back to sorting by location name if the field isn't recognized.
     */
    private Comparator<LocationDistributionRow> getRowComparator(String sortBy) {
        if (sortBy == null) {
            return Comparator.comparing(LocationDistributionRow::getLocationName, Comparator.nullsLast(String::compareToIgnoreCase));
        }
        return switch (sortBy) {
            case "totalAnimals" -> Comparator.comparingInt(LocationDistributionRow::getTotalAnimals);
            case "totalValue" -> Comparator.comparing(LocationDistributionRow::getTotalValue);
            case "activeCount" -> Comparator.comparingInt(LocationDistributionRow::getActiveCount);
            case "sickCount" -> Comparator.comparingInt(LocationDistributionRow::getSickCount);
            case "soldCount" -> Comparator.comparingInt(LocationDistributionRow::getSoldCount);
            case "deadCount" -> Comparator.comparingInt(LocationDistributionRow::getDeadCount);
            case "healthRate" -> Comparator.comparingDouble(LocationDistributionRow::getHealthRate);
            case "mortalityRate" -> Comparator.comparingDouble(LocationDistributionRow::getMortalityRate);
            case "productivityIndex" -> Comparator.comparingDouble(LocationDistributionRow::getProductivityIndex);
            case "beneficiaryCount" -> Comparator.comparingInt(LocationDistributionRow::getBeneficiaryCount);
            case "representativeCount" -> Comparator.comparingInt(LocationDistributionRow::getRepresentativeCount);
            case "locationType" -> Comparator.comparing(LocationDistributionRow::getLocationType, Comparator.nullsLast(String::compareToIgnoreCase));
            case "name", "locationName" -> Comparator.comparing(LocationDistributionRow::getLocationName, Comparator.nullsLast(String::compareToIgnoreCase));
            default -> Comparator.comparing(LocationDistributionRow::getLocationName, Comparator.nullsLast(String::compareToIgnoreCase));
        };
    }

    /**
     * Get only location IDs that have livestock (performance optimization)
     */
    private List<UUID> getLocationIdsWithLivestock() {
        String cacheKey = "locations_with_livestock";

        @SuppressWarnings("unchecked")
        List<UUID> cached = (List<UUID>) cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<UUID> locationIds = livestockRepository.findDistinctLocationIdsWithLivestock();
        cache.put(cacheKey, locationIds);

        // Schedule cache eviction
        scheduleCacheEviction(cacheKey, CACHE_TTL_MINUTES);

        return locationIds;
    }

    /**
     * Group livestock by location in a single query
     */
    private Map<UUID, List<Livestock>> getLivestockGroupedByLocation(List<UUID> locationIds) {
        if (locationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String cacheKey = "livestock_by_location_" + locationIds.hashCode();

        @SuppressWarnings("unchecked")
        Map<UUID, List<Livestock>> cached = (Map<UUID, List<Livestock>>) cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<Livestock> livestock = livestockRepository.findByLocationIdIn(locationIds);

        Map<UUID, List<Livestock>> grouped = livestock.stream()
                .filter(l -> l.getLocation() != null)
                .collect(Collectors.groupingBy(
                        l -> l.getLocation().getId(),
                        Collectors.toList()
                ));

        cache.put(cacheKey, grouped);
        scheduleCacheEviction(cacheKey, CACHE_TTL_MINUTES);

        return grouped;
    }

    /**
     * Group beneficiaries by location
     */
    private Map<UUID, List<Beneficiary>> getBeneficiariesGroupedByLocation(List<UUID> locationIds) {
        if (locationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Beneficiary> beneficiaries = beneficiaryRepository.findByLocationIdInAndIsDeletedFalse(locationIds);

        return beneficiaries.stream()
                .filter(b -> b.getLocation() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getLocation().getId(),
                        Collectors.toList()
                ));
    }

    /**
     * Group representatives by location
     */
    private Map<UUID, List<Representative>> getRepresentativesGroupedByLocation(List<UUID> locationIds) {
        if (locationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Representative> representatives = representativeRepository.findByLocationIdInAndIsDeletedFalse(locationIds);

        return representatives.stream()
                .filter(r -> r.getLocation() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getLocation().getId(),
                        Collectors.toList()
                ));
    }

    /**
     * Build a location row with all calculations
     */
    private LocationDistributionRow buildLocationRow(
            Location location,
            List<Livestock> livestock,
            List<Beneficiary> beneficiaries,
            List<Representative> representatives) {

        LocationDistributionRow row = new LocationDistributionRow();

        // Location details
        row.setLocationId(location.getId().toString());
        row.setLocationName(location.getName());
        row.setLocationCode(location.getCode());
        row.setLocationType(location.getType() != null ? location.getType() : "Unknown");

        // Walk up the parent chain to get the full administrative path
        Map<String, String> hierarchy = buildLocationHierarchy(location);
        row.setProvince(hierarchy.get("PROVINCE"));
        row.setDistrict(hierarchy.get("DISTRICT"));
        row.setSector(hierarchy.get("SECTOR"));
        row.setCell(hierarchy.get("CELL"));
        row.setVillage(hierarchy.get("VILLAGE"));
        row.setFullLocationPath(buildFullLocationPath(hierarchy));

        // Livestock statistics
        int total = livestock.size();
        row.setTotalAnimals(total);

        BigDecimal totalValue = BigDecimal.ZERO;
        int active = 0, sick = 0, sold = 0, dead = 0;
        Map<String, Long> categoryMap = new HashMap<>();

        for (Livestock l : livestock) {
            BigDecimal value = l.getCurrentValue() != null ? l.getCurrentValue() : BigDecimal.ZERO;
            totalValue = totalValue.add(value);

            String status = l.getStatus() != null ? l.getStatus() : "UNKNOWN";
            switch (status) {
                case "ACTIVE": active++; break;
                case "SICK": sick++; break;
                case "SOLD": sold++; break;
                case "DEAD": dead++; break;
                default: break;
            }

            String catName = l.getLivestockCategory() != null ?
                    l.getLivestockCategory().getName() : "Uncategorized";
            categoryMap.merge(catName, 1L, Long::sum);
        }

        row.setTotalValue(totalValue);
        row.setActiveCount(active);
        row.setSickCount(sick);
        row.setSoldCount(sold);
        row.setDeadCount(dead);
        row.setByCategory(categoryMap);

        row.setBeneficiaryCount(beneficiaries.size());
        row.setRepresentativeCount(representatives.size());

        double healthRate = total > 0 ? (active * 100.0) / total : 0;
        double mortalityRate = total > 0 ? (dead * 100.0) / total : 0;
        double productivityIndex = calculateProductivityIndex(row);

        row.setHealthRate(healthRate);
        row.setMortalityRate(mortalityRate);
        row.setProductivityIndex(productivityIndex);

        return row;
    }

    /**
     * Walks the self-referencing parent chain (Village -> Cell -> Sector -> District -> Province)
     * and returns a map of locationType -> name for whichever levels exist above this location.
     * Guarded with a depth limit in case of a bad/cyclic parent reference in the data.
     */
    private Map<String, String> buildLocationHierarchy(Location location) {
        Map<String, String> hierarchy = new HashMap<>();
        Location current = location;
        int depth = 0;

        while (current != null && depth < 10) {
            String type = current.getType();
            if (type != null) {
                hierarchy.putIfAbsent(type.toUpperCase(), current.getName());
            }
            current = current.getParent();
            depth++;
        }

        return hierarchy;
    }

    /**
     * Renders the hierarchy map top-down as "Province › District › Sector › Cell › Village",
     * skipping any levels that aren't present for this particular location.
     */
    private String buildFullLocationPath(Map<String, String> hierarchy) {
        return HIERARCHY_ORDER.stream()
                .map(hierarchy::get)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" › "));
    }

    /**
     * Calculate productivity index (FAO standard)
     */
    private double calculateProductivityIndex(LocationDistributionRow row) {
        int total = row.getTotalAnimals();
        if (total == 0) return 0.0;

        double healthFactor = (row.getActiveCount() * 100.0) / total;
        double valueFactor = Math.min(row.getTotalValue().doubleValue() / (total * 1000), 100);
        double diversityFactor = Math.min(row.getByCategory().size() * 10, 100);

        return (healthFactor * 0.5) + (valueFactor * 0.3) + (diversityFactor * 0.2);
    }

    /**
     * Create empty report
     */
    private LocationDistributionReportDto createEmptyReport() {
        LocationDistributionReportDto report = new LocationDistributionReportDto();
        report.setTotalLocations(0);
        report.setTotalAnimals(0);
        report.setTotalValue(BigDecimal.ZERO);
        report.setRows(Collections.emptyList());
        report.setCategoryTotals(Collections.emptyMap());
        report.setFirst(true);
        report.setLast(true);
        report.setTotalPages(0);
        report.setTotalElements(0);
        return report;
    }

    /**
     * Simple cache eviction scheduler
     */
    private void scheduleCacheEviction(String key, int ttlMinutes) {
        new Thread(() -> {
            try {
                Thread.sleep(ttlMinutes * 60 * 1000L);
                cache.remove(key);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * Clear all caches
     */
    public void clearCache() {
        cache.clear();
        log.info("Location distribution cache cleared");
    }
}