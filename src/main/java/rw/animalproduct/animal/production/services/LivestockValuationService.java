package rw.animalproduct.animal.production.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockValuation;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockValuationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LivestockValuationService {

    private final LivestockValuationRepository valuationRepository;
    private final LivestockRepository livestockRepository;
    private final LifecycleEmailService emailService;

    @Transactional
    public LivestockValuation recordValuation(UUID livestockId, LocalDate valuationDate,
                                              BigDecimal estimatedValue, String valuationMethod,
                                              String notes, String valuedBy) {
        Livestock livestock = livestockRepository.findById(livestockId)
                .orElseThrow(() -> new RuntimeException("Livestock not found: " + livestockId));

        if (valuationDate == null) {
            valuationDate = LocalDate.now();
        }

        Double oldValue = valuationRepository
                .findFirstByLivestockIdOrderByValuationDateDesc(livestockId)
                .map(LivestockValuation::getEstimatedValue)
                .orElse(null);

        LivestockValuation valuation = new LivestockValuation(
                livestock,
                valuationDate,
                estimatedValue,
                valuationMethod,
                notes,
                valuedBy != null ? valuedBy : "system"
        );

        LivestockValuation saved = valuationRepository.save(valuation);
        log.info("✅ Saved valuation id={} for livestock {} value={} (was {})",
                saved.getId(), livestockId, estimatedValue, oldValue);

        if (estimatedValue != null) {
            livestock.setCurrentValue(estimatedValue);
            livestockRepository.save(livestock);
        }

        try {
            emailService.sendValuationChangedNotification(saved, oldValue);
        } catch (Exception e) {
            log.warn("Failed to send valuation email for {}: {}", livestockId, e.getMessage());
        }

        return saved;
    }

    @Transactional
    public LivestockValuation recordValuation(UUID livestockId, LocalDate valuationDate,
                                              Double estimatedValue, String valuationMethod,
                                              String notes, String valuedBy) {
        BigDecimal value = estimatedValue != null ? BigDecimal.valueOf(estimatedValue) : null;
        return recordValuation(livestockId, valuationDate, value, valuationMethod, notes, valuedBy);
    }

    public Optional<LivestockValuation> getLatest(UUID livestockId) {
        return valuationRepository.findFirstByLivestockIdOrderByValuationDateDesc(livestockId);
    }

    public List<LivestockValuation> getHistory(UUID livestockId) {
        return valuationRepository.findByLivestockIdOrderByValuationDateDesc(livestockId);
    }

    public BigDecimal changeSincePrevious(UUID livestockId) {
        List<LivestockValuation> history = getHistory(livestockId);
        if (history.size() < 2) return BigDecimal.ZERO;
        LivestockValuation latest = history.get(0);
        LivestockValuation previous = history.get(1);
        if (latest.getEstimatedValue() == null || previous.getEstimatedValue() == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(latest.getEstimatedValue() - previous.getEstimatedValue());
    }

    public Double getTotalLivestockValue() {
        Double total = valuationRepository.getTotalLivestockValue();
        return total != null ? total : 0.0;
    }

    public Double getCurrentValue(UUID livestockId) {
        return getLatest(livestockId).map(LivestockValuation::getEstimatedValue).orElse(null);
    }

    @Transactional
    public void softDelete(UUID valuationId, String deletedBy) {
        LivestockValuation v = valuationRepository.findById(valuationId)
                .orElseThrow(() -> new RuntimeException("Valuation not found: " + valuationId));
        v.softDelete(deletedBy);
        valuationRepository.save(v);
    }

    @Transactional
    public void restore(UUID valuationId) {
        LivestockValuation v = valuationRepository.findById(valuationId)
                .orElseThrow(() -> new RuntimeException("Valuation not found: " + valuationId));
        v.restore();
        valuationRepository.save(v);
    }

    @Transactional
    public void hardDelete(UUID valuationId) {
        LivestockValuation v = valuationRepository.findById(valuationId)
                .orElseThrow(() -> new RuntimeException("Valuation not found: " + valuationId));
        valuationRepository.delete(v);
    }
}