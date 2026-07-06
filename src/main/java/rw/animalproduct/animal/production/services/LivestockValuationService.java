package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * NEW: every valuation change is now (a) written to the audit log AND
 * (b) emailed to the notification address, mirroring how newborn
 * registrations already trigger an email via LifecycleEmailService.
 */
@Service
public class LivestockValuationService {

    private static final Logger log = LoggerFactory.getLogger(LivestockValuationService.class);

    private final LivestockValuationRepository valuationRepository;
    private final LivestockRepository livestockRepository;
    private final AuditLogService auditLogService;
    private final LifecycleEmailService emailService; // NEW

    @Autowired
    public LivestockValuationService(LivestockValuationRepository valuationRepository,
                                     LivestockRepository livestockRepository,
                                     AuditLogService auditLogService,
                                     LifecycleEmailService emailService) { // NEW param
        this.valuationRepository = valuationRepository;
        this.livestockRepository = livestockRepository;
        this.auditLogService = auditLogService;
        this.emailService = emailService;
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

        // NEW: email notification, same pattern as newborn/lifecycle emails.
        // Wrapped in try/catch so a mail server hiccup never rolls back a
        // valid valuation entry.
        try {
            emailService.sendValuationChangedNotification(livestock, previousValue, value, method, notes);
        } catch (Exception e) {
            log.error("❌ Failed to send valuation-changed email for {}: {}",
                    livestock.getTagNumber(), e.getMessage());
        }

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

    public Map<UUID, LivestockValuation> getLatestForIds(List<UUID> livestockIds) {
        if (livestockIds == null || livestockIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<LivestockValuation> rows = valuationRepository.findLatestValuationsForLivestockIds(livestockIds);
        return rows.stream()
                .collect(Collectors.toMap(v -> v.getLivestock().getId(), v -> v));
    }

    public BigDecimal changeSincePrevious(UUID livestockId) {
        List<LivestockValuation> history = getHistory(livestockId);
        if (history.size() < 2) return BigDecimal.ZERO;
        return history.get(0).getValue().subtract(history.get(1).getValue());
    }
}
