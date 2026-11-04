package rw.animalproduct.animal.production.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.VLivestockWithAge;
import rw.animalproduct.animal.production.repository.VLivestockWithAgeRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only service over the livestock overview view.
 *
 * <p>The view is backed by the {@code livestock} table which has
 * <strong>no soft-delete column</strong>. "Removed" animals are those with
 * {@code status} in {@code SOLD | DEAD | TRANSFERRED}. For the overview
 * dashboard, pass {@code status = "ACTIVE"} via {@link #findFiltered}.
 */
@Service
@Transactional(readOnly = true)
public class VLivestockWithAgeService {

    private final VLivestockWithAgeRepository repository;

    public VLivestockWithAgeService(VLivestockWithAgeRepository repository) {
        this.repository = repository;
    }

    // ── Basic reads ──────────────────────────────────────────────────────
    public List<VLivestockWithAge> getAll() {
        return repository.findAll();
    }

    public Page<VLivestockWithAge> getAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Optional<VLivestockWithAge> getById(UUID id) {
        return repository.findById(id);
    }

    public List<VLivestockWithAge> getByLifecycleStage(String stage) {
        return repository.findByLifecycleStage(stage);
    }

    public Page<VLivestockWithAge> getByLifecycleStage(String stage, Pageable pageable) {
        return repository.findByLifecycleStage(stage, pageable);
    }

    public List<VLivestockWithAge> getByStatus(String status) {
        return repository.findByStatus(status);
    }

    public Page<VLivestockWithAge> getByStatus(String status, Pageable pageable) {
        return repository.findByStatus(status, pageable);
    }

    public List<VLivestockWithAge> getByCategory(String categoryName) {
        return repository.findByCategoryName(categoryName);
    }

    public Page<VLivestockWithAge> getByCategory(String categoryName, Pageable pageable) {
        return repository.findByCategoryName(categoryName, pageable);
    }

    public List<VLivestockWithAge> getByGender(String gender) {
        return repository.findByGender(gender);
    }

    public Page<VLivestockWithAge> search(String tagNumber, Pageable pageable) {
        return repository.findByTagNumberContaining(tagNumber, pageable);
    }

    // ── Combined filtering — signature matches controller ───────────────
    /**
     * Filter with every optional parameter. Whitespace-only strings are
     * normalised to {@code null} so they don't accidentally restrict results.
     */
    public Page<VLivestockWithAge> findFiltered(
            String stage,
            String status,
            String category,
            String gender,
            String search,
            Pageable pageable) {
        return repository.findFiltered(
                normalise(stage),
                normalise(status),
                normalise(category),
                normalise(gender),
                normalise(search),
                pageable);
    }

    /** Convenience: live ({@code status = 'ACTIVE'}) animals only. */
    public Page<VLivestockWithAge> findActive(
            String stage,
            String category,
            String gender,
            String search,
            Pageable pageable) {
        return repository.findActive(
                normalise(stage),
                normalise(category),
                normalise(gender),
                normalise(search),
                pageable);
    }

    // ── Stats (defensive against null keys and Long/Integer drift) ───────
    public Map<String, Long> getLifecycleStageStats() {
        return toCountMap(repository.getLifecycleStageCounts());
    }

    public Map<String, Long> getCategoryStats() {
        return toCountMap(repository.getCategoryCounts());
    }

    public Map<String, Long> getStatusStats() {
        return toCountMap(repository.getStatusCounts());
    }

    public Map<String, Long> getHealthStatusStats() {
        return toCountMap(repository.getHealthStatusCounts());
    }

    public Map<String, Long> getReproductiveStatusStats() {
        return toCountMap(repository.getReproductiveStatusCounts());
    }

    public long countByLifecycleStage(String stage) {
        return repository.countByLifecycleStage(stage);
    }

    public long countByStatus(String status) {
        return repository.countByStatus(status);
    }

    /**
     * Aggregated dashboard counters in one call. Keys are stable strings
     * suitable for JSON serialisation from a controller.
     */
    public Map<String, Long> getOverviewCounts() {
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("total",       repository.count());
        out.put("active",      repository.countByStatus("ACTIVE"));
        out.put("sold",        repository.countByStatus("SOLD"));
        out.put("dead",        repository.countByStatus("DEAD"));
        out.put("transferred", repository.countByStatus("TRANSFERRED"));
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────
    private static String normalise(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Converts a {@code List<Object[]>} of {@code [key, count]} rows into a
     * {@code Map<String, Long>}. Skips null keys. Accepts any {@link Number}
     * for the count column (Hibernate may return {@link Long} or
     * {@link Integer} depending on dialect).
     */
    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (rows == null) return map;
        for (Object[] row : rows) {
            if (row == null || row.length < 2) continue;
            Object key = row[0];
            Object cnt = row[1];
            if (key == null || !(cnt instanceof Number)) continue;
            map.put(key.toString(), ((Number) cnt).longValue());
        }
        return map;
    }
}