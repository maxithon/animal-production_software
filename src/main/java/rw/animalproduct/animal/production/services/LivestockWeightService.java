package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockOffspring;
import rw.animalproduct.animal.production.entity.LivestockWeightHistory;
import rw.animalproduct.animal.production.repository.LivestockOffspringRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.repository.LivestockWeightHistoryRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class LivestockWeightService {

    private static final Logger log = LoggerFactory.getLogger(LivestockWeightService.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Africa/Kigali");

    private static final double SUDDEN_LOSS_PCT = 10.0;
    private static final double ONGOING_LOSS_PCT = 3.0;

    private final LivestockWeightHistoryRepository weightRepository;
    private final LivestockRepository livestockRepository;
    private final LivestockOffspringRepository offspringRepository;

    public LivestockWeightService(LivestockWeightHistoryRepository weightRepository,
                                  LivestockRepository livestockRepository,
                                  LivestockOffspringRepository offspringRepository) {
        this.weightRepository = weightRepository;
        this.livestockRepository = livestockRepository;
        this.offspringRepository = offspringRepository;
    }

    // ── RECORD ──────────────────────────────────────────────────────────────

    @Transactional
    public LivestockWeightHistory recordWeight(UUID livestockId,
                                               LocalDate weighedOn,
                                               BigDecimal weightKg,
                                               String method,
                                               Integer headCount,
                                               String bodyCondition,
                                               String recordedBy,
                                               String notes) {

        Livestock animal = livestockRepository.findById(livestockId)
                .orElseThrow(() -> new IllegalArgumentException("Animal not found"));

        if (weightKg == null || weightKg.signum() <= 0) {
            throw new IllegalArgumentException("Weight must be greater than zero");
        }
        if (weightKg.compareTo(BigDecimal.valueOf(2000)) > 0) {
            throw new IllegalArgumentException(
                    "Weight " + weightKg + " kg exceeds plausible maximum (2000 kg) — check your unit");
        }
        if (weighedOn == null) weighedOn = LocalDate.now(APP_ZONE);
        if (weighedOn.isAfter(LocalDate.now(APP_ZONE))) {
            throw new IllegalArgumentException("Weighing date cannot be in the future");
        }

        int effectiveHeadCount = (headCount != null && headCount > 0) ? headCount : 1;

        LivestockWeightHistory w = new LivestockWeightHistory();
        w.setLivestock(animal);
        w.setWeighedOn(weighedOn);
        w.setWeightKg(weightKg.setScale(2, RoundingMode.HALF_UP));
        w.setWeightMethod(method != null && !method.isBlank()
                ? method : LivestockWeightHistory.METHOD_SCALE);
        w.setHeadCount(effectiveHeadCount);
        w.setBodyCondition(bodyCondition);
        w.setRecordedBy(recordedBy);
        w.setNotes(notes);
        w.setIsDeleted(false);

        LivestockWeightHistory saved = weightRepository.save(w);
        log.info("[weight] recorded {} kg × {} head for {} on {}",
                weightKg, effectiveHeadCount, animal.getTagNumber(), weighedOn);
        return saved;
    }

    // ── SEED FROM BIRTH ─────────────────────────────────────────────────────

    @Transactional
    public void seedFromBirthIfAvailable(UUID childLivestockId) {
        if (childLivestockId == null) return;

        Livestock child = livestockRepository.findById(childLivestockId).orElse(null);
        if (child == null) return;

        if (weightRepository.countByLivestockIdAndIsDeletedFalse(childLivestockId) > 0) return;

        List<LivestockOffspring> links =
                offspringRepository.findByChildLivestockIdAndIsDeletedFalse(childLivestockId);
        if (links.isEmpty()) return;

        LivestockOffspring link = links.get(0);
        BigDecimal birthWeight = link.getBirthWeightKg();
        if (birthWeight == null || birthWeight.signum() <= 0) return;

        LocalDate birthDate = child.getBirthDate();
        if (birthDate == null && link.getBirthEvent() != null) {
            birthDate = link.getBirthEvent().getBirthDate();
        }
        if (birthDate == null) birthDate = LocalDate.now(APP_ZONE);

        LivestockWeightHistory w = new LivestockWeightHistory();
        w.setLivestock(child);
        w.setWeighedOn(birthDate);
        w.setWeightKg(birthWeight.setScale(2, RoundingMode.HALF_UP));
        w.setWeightMethod(LivestockWeightHistory.METHOD_SCALE);
        w.setHeadCount(1);
        w.setRecordedBy("system");
        w.setNotes("Auto-recorded from birth weight");
        w.setIsDeleted(false);
        weightRepository.save(w);

        log.info("[weight] seeded birth weight {} kg for {} on {}",
                birthWeight, child.getTagNumber(), birthDate);
    }

    // ── READ ────────────────────────────────────────────────────────────────

    public List<LivestockWeightHistory> history(UUID livestockId) {
        return weightRepository.findByLivestockIdAndIsDeletedFalseOrderByWeighedOnAsc(livestockId);
    }

    public Optional<LivestockWeightHistory> latest(UUID livestockId) {
        return weightRepository.findFirstByLivestockIdAndIsDeletedFalseOrderByWeighedOnDesc(livestockId);
    }

    /**
     * ✅ NEW — used by the Weight dashboard's stat tiles so that the
     * "Weighed" number is always global (all animals), not page-scoped.
     *
     * <p>Counts distinct non-deleted animals that have at least one
     * non-deleted weight record. Currently a Java-side dedup for simplicity;
     * for very large datasets, replace the body with a native SQL
     * {@code SELECT COUNT(DISTINCT livestock_id)} query.</p>
     */
    public long countAnimalsWithWeight() {
        return weightRepository.findAll().stream()
                .filter(w -> !Boolean.TRUE.equals(w.getIsDeleted()))
                .map(w -> w.getLivestock() != null ? w.getLivestock().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .count();
    }

    public Map<UUID, LivestockWeightHistory> latestForIds(List<UUID> livestockIds) {
        if (livestockIds == null || livestockIds.isEmpty()) return Collections.emptyMap();
        Map<UUID, LivestockWeightHistory> result = new HashMap<>();
        for (UUID id : livestockIds) latest(id).ifPresent(w -> result.put(id, w));
        return result;
    }

    // ── ANALYSIS ────────────────────────────────────────────────────────────

    public BigDecimal averageDailyGain(UUID livestockId) {
        List<LivestockWeightHistory> list = history(livestockId);
        if (list.size() < 2) return null;

        LivestockWeightHistory prev = list.get(list.size() - 2);
        LivestockWeightHistory last = list.get(list.size() - 1);
        long days = ChronoUnit.DAYS.between(prev.getWeighedOn(), last.getWeighedOn());
        if (days <= 0) return null;

        return last.getWeightKg().subtract(prev.getWeightKg())
                .divide(BigDecimal.valueOf(days), 3, RoundingMode.HALF_UP);
    }

    public BigDecimal percentChange(UUID livestockId) {
        List<LivestockWeightHistory> list = history(livestockId);
        if (list.size() < 2) return null;

        LivestockWeightHistory prev = list.get(list.size() - 2);
        LivestockWeightHistory last = list.get(list.size() - 1);
        if (prev.getWeightKg().signum() == 0) return null;

        return last.getWeightKg().subtract(prev.getWeightKg())
                .multiply(BigDecimal.valueOf(100))
                .divide(prev.getWeightKg(), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal marketValue(UUID livestockId, BigDecimal pricePerKg) {
        if (pricePerKg == null) return null;
        return latest(livestockId)
                .map(w -> w.getWeightKg()
                        .multiply(BigDecimal.valueOf(
                                w.getHeadCount() == null ? 1 : w.getHeadCount()))
                        .multiply(pricePerKg)
                        .setScale(0, RoundingMode.HALF_UP))
                .orElse(null);
    }

    // ── ALERTS ──────────────────────────────────────────────────────────────

    public List<Map<String, String>> alertsFor(UUID livestockId) {
        List<Map<String, String>> alerts = new ArrayList<>();
        List<LivestockWeightHistory> list = history(livestockId);
        if (list.size() < 2) return alerts;

        LivestockWeightHistory prev = list.get(list.size() - 2);
        LivestockWeightHistory last = list.get(list.size() - 1);
        long days = ChronoUnit.DAYS.between(prev.getWeighedOn(), last.getWeighedOn());
        if (days <= 0) return alerts;
        if (prev.getWeightKg().signum() == 0) return alerts;

        BigDecimal changePct = last.getWeightKg().subtract(prev.getWeightKg())
                .multiply(BigDecimal.valueOf(100))
                .divide(prev.getWeightKg(), 2, RoundingMode.HALF_UP);

        String tag = last.getLivestock() != null
                ? last.getLivestock().getTagNumber() : "?";

        if (changePct.doubleValue() <= -SUDDEN_LOSS_PCT && days <= 7) {
            alerts.add(alert("WEIGHT_SUDDEN_LOSS", "CRITICAL",
                    "🚨 " + tag + " lost " + changePct.abs() + "% weight in "
                            + days + " day(s) — possible illness"));
        } else if (changePct.doubleValue() <= -ONGOING_LOSS_PCT) {
            alerts.add(alert("WEIGHT_LOSS", "WARNING",
                    "⚠️ " + tag + " lost " + changePct.abs()
                            + "% weight over " + days + " day(s)"));
        }
        return alerts;
    }

    public List<LivestockWeightHistory> staleWeighings(int days) {
        return weightRepository.findStaleWeighings(
                LocalDate.now(APP_ZONE).minusDays(days));
    }

    @Transactional
    public void softDelete(UUID weightId) {
        weightRepository.findById(weightId).ifPresent(w -> {
            w.setIsDeleted(true);
            weightRepository.save(w);
        });
    }

    private Map<String, String> alert(String type, String severity, String message) {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("type", type);
        a.put("severity", severity);
        a.put("message", message);
        return a;
    }
}