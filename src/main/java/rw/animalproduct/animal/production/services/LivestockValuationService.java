package rw.animalproduct.animal.production.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockValuation;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockValuationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owns every read and write of livestock valuation data.
 *
 * FAO standard implemented here:
 *   - An animal's value is never edited in place.
 *   - Every valuation event is appended as a new, immutable history row.
 *   - Livestock.currentValue is refreshed as a read-only cache pointing at
 *     the most recent valuation by date — never set directly anywhere else
 *     in the codebase.
 */
@Service
public class LivestockValuationService {

    private final LivestockValuationRepository valuationRepository;
    private final LivestockRepository livestockRepository;
    private final AuditLogService auditLogService;

    @Autowired
    public LivestockValuationService(LivestockValuationRepository valuationRepository,
                                     LivestockRepository livestockRepository,
                                     AuditLogService auditLogService) {
        this.valuationRepository = valuationRepository;
        this.livestockRepository = livestockRepository;
        this.auditLogService = auditLogService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WRITE — the only correct path for changing an animal's value
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public LivestockValuation recordValuation(UUID livestockId,
                                              LocalDate valuationDate,
                                              BigDecimal value,
                                              String method,
                                              String notes,
                                              String recordedBy) {
        Livestock livestock = livestockRepository.findById(livestockId)
                .orElseThrow(() -> new RuntimeException("Livestock not found: " + livestockId));

        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("Valuation amount must be zero or positive");
        }
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Valuation method is required");
        }
        if (valuationDate == null) {
            valuationDate = LocalDate.now();
        }
        if (valuationDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Valuation date cannot be in the future");
        }

        BigDecimal previousValue = livestock.getCurrentValue();

        LivestockValuation entry = new LivestockValuation(
                livestock, valuationDate, value, method, notes, recordedBy);
        valuationRepository.save(entry);

        // Refresh the cached "current" figure only from whatever is now the
        // most recent valuation chronologically. This protects the cache from
        // being clobbered by a back-dated entry that isn't actually the latest.
        Optional<LivestockValuation> latest = valuationRepository.findLatestForLivestock(livestockId);
        latest.ifPresent(l -> {
            livestock.setCurrentValue(l.getValue());
            livestockRepository.save(livestock);
        });

        auditLogService.log(
                "livestock_valuation",
                livestockId,
                "VALUATION_RECORDED",
                recordedBy,
                previousValue != null ? previousValue.toString() : "none",
                value.toString(),
                "Valuation recorded via " + method + (notes != null && !notes.isBlank() ? " — " + notes : "")
        );

        return entry;
    }

    /**
     * Convenience wrapper used at registration time — records the animal's
     * very first (INITIAL) valuation. Called once, right after the Livestock
     * row itself has been persisted.
     */
    @Transactional
    public void recordInitialValuation(Livestock livestock, BigDecimal value, String recordedBy) {
        if (value == null) return;
        LocalDate initialDate = livestock.getDateReceived() != null
                ? livestock.getDateReceived()
                : LocalDate.now();
        recordValuation(
                livestock.getId(),
                initialDate,
                value,
                LivestockValuation.METHOD_INITIAL,
                "Initial valuation at registration",
                recordedBy
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    public List<LivestockValuation> getHistory(UUID livestockId) {
        return valuationRepository.findHistoryForLivestock(livestockId);
    }

    public Optional<LivestockValuation> getLatest(UUID livestockId) {
        return valuationRepository.findLatestForLivestock(livestockId);
    }

    public long countValuations(UUID livestockId) {
        return valuationRepository.countByLivestockId(livestockId);
    }

    /**
     * Bulk latest-valuation lookup, keyed by livestock id, scoped to the
     * given ids. Used by the livestock list page so it can render "Valued" /
     * "Needs Valuation" badges for a page of animals without issuing one
     * query per row. Delegates to the repository's MAX(id)-per-animal query,
     * which guarantees exactly one row per livestock id even when two
     * valuations for the same animal share the same valuationDate.
     */
    public Map<UUID, LivestockValuation> getLatestForIds(List<UUID> livestockIds) {
        if (livestockIds == null || livestockIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<LivestockValuation> rows = valuationRepository.findLatestValuationsForLivestockIds(livestockIds);
        return rows.stream()
                .collect(Collectors.toMap(v -> v.getLivestock().getId(), v -> v));
    }
    /**
     * Change (delta) between the most recent valuation and the one before it.
     * Positive = value increased, Negative = value dropped. Returns ZERO if
     * there's fewer than two valuation records.
     */
    public BigDecimal changeSincePrevious(UUID livestockId) {
        List<LivestockValuation> history = getHistory(livestockId);
        if (history.size() < 2) return BigDecimal.ZERO;
        return history.get(0).getValue().subtract(history.get(1).getValue());
    }
}