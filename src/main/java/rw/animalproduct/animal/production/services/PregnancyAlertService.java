package rw.animalproduct.animal.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pregnancy-related alerts sourced ONLY from the currently-active
 * breeding record per mother. Never uses stale rows, never uses the
 * expected_pregnancy_check_date column as if it were the birth due date.
 *
 * Fixes the reported GOAT-035 bug where the alert was reading 2026-05-26
 * (the pregnancy-check date) instead of 2026-09-29 (the actual due date).
 *
 * Design: this is an isolated service — the dashboard's financial logic and
 * the existing generateAlerts() method stay untouched. The controller
 * concatenates these pregnancy alerts with the existing ones and re-sorts.
 */
@Service
public class PregnancyAlertService {

    private static final Logger log = LoggerFactory.getLogger(PregnancyAlertService.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Africa/Kigali");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Warn when a due date is within this many days from today. */
    private static final int DUE_SOON_DAYS = 7;

    private final LivestockBreedingRepository breedingRepository;

    public PregnancyAlertService(LivestockBreedingRepository breedingRepository) {
        this.breedingRepository = breedingRepository;
    }

    /**
     * @return list of alert maps: { type, severity, message, actionUrl, actionLabel }
     *         in the same shape as the dashboard's existing alerts, plus two
     *         extra fields the new top banner uses to make alerts clickable.
     */
    public List<Map<String, String>> generatePregnancyAlerts() {
        List<Map<String, String>> alerts = new ArrayList<>();
        LocalDate today = LocalDate.now(APP_ZONE);

        List<LivestockBreeding> active;
        try {
            // is_deleted was removed from livestock_breeding; the modern
            // repository method is findByStatusIn(...). Deleted records no
            // longer exist in the table (they are hard-deleted and captured
            // in audit_log), so no filter is needed here.
            active = breedingRepository.findByStatusIn(
                    List.of(LivestockBreeding.STATUS_CONFIRMED_PREGNANT,
                            LivestockBreeding.STATUS_PREGNANT));
        } catch (Exception e) {
            log.warn("[pregnancy-alerts] query failed: {}", e.getMessage());
            return alerts;
        }

        // De-duplicate by mother: keep the row with the LATEST due date.
        Map<UUID, LivestockBreeding> latestByMother = new LinkedHashMap<>();
        for (LivestockBreeding b : active) {
            if (b.getLivestock() == null) continue;
            if (b.getExpectedDueDate() == null) continue;
            UUID motherId = b.getLivestock().getId();
            LivestockBreeding existing = latestByMother.get(motherId);
            if (existing == null
                    || b.getExpectedDueDate().isAfter(existing.getExpectedDueDate())) {
                latestByMother.put(motherId, b);
            }
        }

        for (LivestockBreeding b : latestByMother.values()) {
            Livestock mother = b.getLivestock();
            if (mother == null) continue;
            if (Livestock.STATUS_DEAD.equals(mother.getStatus())
                    || Livestock.STATUS_SOLD.equals(mother.getStatus())) continue;

            String tag = mother.getTagNumber();
            LocalDate due = b.getExpectedDueDate();
            LocalDate conception = b.getConceptionDate() != null
                    ? b.getConceptionDate() : b.getBreedingDate();

            // ── Human-readable detail line ─────────────────────────────────
            StringBuilder detail = new StringBuilder();
            detail.append(tag);
            if (conception != null) {
                long daysPregnant = ChronoUnit.DAYS.between(conception, today);
                detail.append(" — Conception: ").append(conception.format(DATE_FMT));
                if (daysPregnant >= 0) {
                    detail.append(" · ").append(daysPregnant).append(" days pregnant");
                }
            }
            detail.append(" · Expected: ").append(due.format(DATE_FMT));
            long daysUntil = ChronoUnit.DAYS.between(today, due);
            if (daysUntil > 0) {
                detail.append(" (in ").append(daysUntil).append("d)");
            } else if (daysUntil == 0) {
                detail.append(" (TODAY)");
            } else {
                detail.append(" (").append(Math.abs(daysUntil)).append("d overdue)");
            }

            String actionUrl   = "/livestock/view/" + mother.getId();
            String actionLabel = "View animal";

            if (daysUntil < 0) {
                alerts.add(alert("OVERDUE_PREGNANCY", "CRITICAL",
                        "🚨 " + detail + " — OVERDUE", actionUrl, actionLabel));
            } else if (daysUntil == 0) {
                alerts.add(alert("PREGNANCY_DUE", "CRITICAL",
                        "🐣 " + detail + " — DUE TODAY", actionUrl, actionLabel));
            } else if (daysUntil <= DUE_SOON_DAYS) {
                alerts.add(alert("PREGNANCY_SOON", "WARNING",
                        "📅 " + detail, actionUrl, actionLabel));
            }
        }

        log.debug("[pregnancy-alerts] generated {} pregnancy alerts", alerts.size());
        return alerts;
    }

    /**
     * Same as above, but restricted to a specific set of livestock IDs.
     * Used by the user-dashboard path.
     */
    public List<Map<String, String>> generatePregnancyAlertsForLivestock(Collection<UUID> livestockIds) {
        if (livestockIds == null || livestockIds.isEmpty()) return Collections.emptyList();

        Set<UUID> ids = new HashSet<>(livestockIds);
        return generatePregnancyAlerts().stream()
                .filter(a -> {
                    String url = a.get("actionUrl");
                    if (url == null) return false;
                    for (UUID id : ids) {
                        if (url.endsWith(id.toString())) return true;
                    }
                    return false;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private Map<String, String> alert(String type, String severity, String message,
                                      String actionUrl, String actionLabel) {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("type",        type);
        a.put("severity",    severity);
        a.put("message",     message);
        a.put("actionUrl",   actionUrl);
        a.put("actionLabel", actionLabel);
        return a;
    }
}