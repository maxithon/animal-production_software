package rw.animalproduct.animal.production.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.entity.LivestockBreeding;
import rw.animalproduct.animal.production.entity.LivestockNotificationLog;
import rw.animalproduct.animal.production.notification.LifecycleNotificationType;
import rw.animalproduct.animal.production.notification.LivestockLifecycleService;
import rw.animalproduct.animal.production.repository.LivestockBreedingRepository;
import rw.animalproduct.animal.production.repository.LivestockNotificationLogRepository;
import rw.animalproduct.animal.production.repository.LivestockRepository;
import rw.animalproduct.animal.production.services.LifecycleEmailService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Weekly scheduler for TIME-BASED lifecycle reminders.
 *
 * ── Scope ─────────────────────────────────────────────────────────────────
 * ONLY these four reminders are sent automatically (Monday 08:00 by default):
 *   1. READY_TO_BREED                       (once per animal)
 *   2. DUE_SOON                             (once per pregnancy)
 *   3. OVERDUE                              (once per pregnancy)
 *   4. AWAITING_PREGNANCY_CONFIRMATION      (once per breeding)
 *
 * CRUD-triggered emails (animal registered, breeding recorded, sale created,
 * payment recorded, death recorded, etc.) are sent IMMEDIATELY by the
 * corresponding service classes calling LifecycleEmailService.* directly.
 *
 * Newborn / Young / Pre-breeding automatic emails were intentionally removed
 * to reduce email volume — those milestones are visible in the app.
 *
 * ── Deduplication ─────────────────────────────────────────────────────────
 * Every reminder is deduped via LivestockNotificationLog:
 *   READY_TO_BREED                    → READY_TO_BREED:{animalId}
 *   DUE_SOON                          → DUE_SOON:{breedingId}
 *   OVERDUE                           → OVERDUE:{breedingId}
 *   AWAITING_PREGNANCY_CONFIRMATION   → AWAITING_PREGNANCY_CONFIRMATION:{breedingId}
 *   SCHEDULER_CATCHUP                 → SCHEDULER_CATCHUP:{scheduledDate}
 *
 * ── Timezone ──────────────────────────────────────────────────────────────
 * All @Scheduled methods are pinned to Africa/Kigali so the schedule is
 * independent of the JVM's default timezone.
 *
 * ── Startup catch-up ──────────────────────────────────────────────────────
 * If the app was down at the scheduled time, the missed run is executed on
 * the next startup. Two windows are allowed:
 *   - Same day as the scheduled day, after the scheduled hour
 *   - The DAY AFTER the scheduled day, before 12:00 (grace window)
 * In both cases, at most ONE catch-up per scheduled week is executed,
 * guarded by the SCHEDULER_CATCHUP:{scheduledDate} dedupe key.
 *
 * @author HABANABAKIZE & NDAYIZEYE
 * @version 2.2.0
 */
@Component
public class LifecycleNotificationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(LifecycleNotificationScheduler.class);

    /** Dedupe marker for the startup catch-up (at most one per scheduled week). */
    private static final String CATCHUP_TYPE = "SCHEDULER_CATCHUP";
    private static final String CATCHUP_KEY_PREFIX = "SCHEDULER_CATCHUP:";

    /** Grace hour on the day after the scheduled day — recovery window ends here. */
    private static final int GRACE_HOUR_NEXT_DAY = 12;

    private final LivestockRepository livestockRepository;
    private final LivestockBreedingRepository breedingRepository;
    private final LivestockNotificationLogRepository notificationLogRepository;
    private final LivestockLifecycleService lifecycleService;
    private final LifecycleEmailService emailService;

    @Value("${app.notifications.scheduler.cron:0 0 8 ? * MON}")
    private String schedulerCron;

    @Value("${app.notifications.birth-lookahead-days:14}")
    private int birthLookaheadDays;

    @Value("${app.notifications.pregnancy-check-after-days:21}")
    private int pregnancyCheckAfterDays;

    @Value("${app.notifications.scheduler.day-of-week:MONDAY}")
    private String schedulerDayOfWeek;

    @Value("${app.notifications.scheduler.hour:8}")
    private int schedulerHour;

    public LifecycleNotificationScheduler(
            LivestockRepository livestockRepository,
            LivestockBreedingRepository breedingRepository,
            LivestockNotificationLogRepository notificationLogRepository,
            LivestockLifecycleService lifecycleService,
            LifecycleEmailService emailService) {
        this.livestockRepository = livestockRepository;
        this.breedingRepository = breedingRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.lifecycleService = lifecycleService;
        this.emailService = emailService;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // STARTUP CATCH-UP
    // ═════════════════════════════════════════════════════════════════════════
    @EventListener(ApplicationReadyEvent.class)
    public void runCatchUpOnStartup() {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek today = now.getDayOfWeek();
        DayOfWeek scheduledDay = parseDayOfWeek(schedulerDayOfWeek);

        // Determine if a catch-up is allowed right now:
        //   1) Same day as scheduled day, at or after the scheduled hour
        //   2) Next day after scheduled day, before the grace hour (recovery)
        boolean sameDayAfterHour =
                (today == scheduledDay) && (now.getHour() >= schedulerHour);

        boolean nextDayRecovery =
                (today == scheduledDay.plus(1)) && (now.getHour() < GRACE_HOUR_NEXT_DAY);

        if (!sameDayAfterHour && !nextDayRecovery) {
            logger.info("⏭️ Startup catch-up skipped: today={} time={} (scheduled={} @ {}h)",
                    today, now.toLocalTime(), scheduledDay, schedulerHour);
            return;
        }

        // Dedupe key is tied to the ORIGINAL scheduled day, so a Tuesday
        // recovery does not produce a second catch-up for the same week.
        LocalDate scheduledDate = (today == scheduledDay)
                ? LocalDate.now()
                : LocalDate.now().minusDays(1);

        String dedupeKey = CATCHUP_KEY_PREFIX + scheduledDate;
        if (notificationLogRepository.existsByNotificationTypeAndDedupeKey(CATCHUP_TYPE, dedupeKey)) {
            logger.info("⏭️ Startup catch-up already ran for {} (key={}). Skipping.",
                    scheduledDate, dedupeKey);
            return;
        }

        logger.info("╔══════════════════════════════════════════════════════════╗");
        logger.info("║  🔁 STARTUP CATCH-UP — {} {}                       ║", today, now.toLocalTime());
        logger.info("╚══════════════════════════════════════════════════════════╝");

        try {
            logCatchUp(dedupeKey);
            runAllJobs();
        } catch (Exception e) {
            logger.error("❌ Startup catch-up failed: {}", e.getMessage(), e);
        }
    }

    private DayOfWeek parseDayOfWeek(String s) {
        try {
            return DayOfWeek.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            logger.warn("⚠️ Invalid app.notifications.scheduler.day-of-week='{}'. Defaulting to MONDAY.", s);
            return DayOfWeek.MONDAY;
        }
    }

    private void logCatchUp(String dedupeKey) {
        try {
            LivestockNotificationLog log = new LivestockNotificationLog();
            log.setNotificationType(CATCHUP_TYPE);
            log.setDedupeKey(dedupeKey);
            log.setSentDate(LocalDate.now());
            log.setCreatedAt(LocalDateTime.now());
            notificationLogRepository.save(log);
            logger.info("✅ Catch-up marker logged: {}", dedupeKey);
        } catch (Exception e) {
            logger.warn("Failed to log catch-up marker: {}", e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // JOB 1 — READY TO BREED
    // ═════════════════════════════════════════════════════════════════════════
    @Scheduled(cron = "${app.notifications.scheduler.cron:0 0 8 ? * MON}", zone = "Africa/Kigali")
    public void checkReadyToBreed() {
        logger.info("========================================");
        logger.info("📧 WEEKLY CHECK: Ready to Breed (ONE-TIME NOTIFICATIONS)");
        logger.info("========================================");

        try {
            List<Livestock> animals = livestockRepository.findAllReadyToBreedFemales();
            if (animals.isEmpty()) {
                logger.info("No animals currently ready to breed.");
                return;
            }

            int sent = 0;
            for (Livestock animal : animals) {
                if (sendOnce(animal.getId(), "READY_TO_BREED")) {
                    emailService.sendReadyToBreedNotification(animal);
                    sent++;
                    logger.info("✅ SENT (ONCE): READY_TO_BREED for: {}", animal.getTagNumber());
                } else {
                    logger.debug("⏭️ SKIPPED: READY_TO_BREED already sent for: {}", animal.getTagNumber());
                }
            }

            logger.info("✅ Ready-to-breed check complete. {} NEW notifications sent.", sent);
            logger.info("========================================");
        } catch (Exception e) {
            logger.error("❌ Error in ready-to-breed check", e);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // JOB 2 — DUE SOON + OVERDUE
    // ═════════════════════════════════════════════════════════════════════════
    @Scheduled(cron = "${app.notifications.scheduler.cron:0 0 8 ? * MON}", zone = "Africa/Kigali")
    public void checkPregnancyTimelines() {
        logger.info("========================================");
        logger.info("📧 WEEKLY CHECK: Pregnancy Timelines (ONE-TIME NOTIFICATIONS)");
        logger.info("========================================");

        try {
            List<LivestockBreeding> activePregnancies =
                    breedingRepository.findActivePregnanciesWithDueDate();
            if (activePregnancies.isEmpty()) {
                logger.info("No active pregnancies with a due date found.");
                return;
            }

            int sent = 0;
            for (LivestockBreeding breeding : activePregnancies) {
                if (breeding.getLivestock() == null || breeding.getExpectedDueDate() == null) {
                    continue;
                }

                long daysUntilDue = lifecycleService.daysUntilDue(breeding.getExpectedDueDate());

                // ── DUE_SOON ────────────────────────────────────────────────
                if (daysUntilDue > 0 && daysUntilDue <= birthLookaheadDays) {
                    if (sendOncePerPregnancy(
                            breeding.getLivestock().getId(), breeding.getId(), "DUE_SOON")) {
                        emailService.sendPregnancyTimelineNotification(
                                LifecycleNotificationType.UPCOMING_BIRTH,
                                breeding, (int) daysUntilDue);
                        sent++;
                        logger.info("✅ SENT (ONCE): DUE_SOON for: {} (due in {} days)",
                                breeding.getLivestock().getTagNumber(), daysUntilDue);
                    } else {
                        logger.debug("⏭️ SKIPPED: DUE_SOON already sent for: {}",
                                breeding.getLivestock().getTagNumber());
                    }
                }

                // ── OVERDUE ─────────────────────────────────────────────────
                if (daysUntilDue < 0) {
                    long daysOverdue = -daysUntilDue;
                    if (daysOverdue <= 7) {
                        if (sendOncePerPregnancy(
                                breeding.getLivestock().getId(), breeding.getId(), "OVERDUE")) {
                            int tier = lifecycleService.overdueTierCrossed(daysOverdue);
                            emailService.sendOverduePregnancyNotification(
                                    breeding, (int) daysOverdue, tier);
                            sent++;
                            logger.info("⚠️ SENT (ONCE): OVERDUE for: {} ({} days overdue)",
                                    breeding.getLivestock().getTagNumber(), daysOverdue);
                        } else {
                            logger.debug("⏭️ SKIPPED: OVERDUE already sent for: {}",
                                    breeding.getLivestock().getTagNumber());
                        }
                    }
                }
            }

            logger.info("✅ Pregnancy check complete. {} NEW notifications sent.", sent);
            logger.info("========================================");
        } catch (Exception e) {
            logger.error("❌ Error in pregnancy timeline check", e);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // JOB 3 — AWAITING PREGNANCY CONFIRMATION
    // ═════════════════════════════════════════════════════════════════════════
    @Scheduled(cron = "${app.notifications.scheduler.cron:0 0 8 ? * MON}", zone = "Africa/Kigali")
    public void checkAwaitingPregnancyConfirmation() {
        logger.info("========================================");
        logger.info("📧 WEEKLY CHECK: Awaiting Pregnancy Confirmation (ONE-TIME)");
        logger.info("========================================");

        try {
            LocalDate cutoff = LocalDate.now().minusDays(pregnancyCheckAfterDays);
            List<LivestockBreeding> pending =
                    breedingRepository.findAwaitingPregnancyConfirmation(cutoff);
            if (pending.isEmpty()) {
                logger.info("No breedings awaiting pregnancy confirmation (cutoff: {}).", cutoff);
                return;
            }

            int sent = 0;
            for (LivestockBreeding breeding : pending) {
                if (breeding.getLivestock() == null || breeding.getBreedingDate() == null) {
                    continue;
                }
                long daysSinceBreeding = ChronoUnit.DAYS.between(
                        breeding.getBreedingDate(), LocalDate.now());

                if (sendOncePerPregnancy(
                        breeding.getLivestock().getId(),
                        breeding.getId(),
                        "AWAITING_PREGNANCY_CONFIRMATION")) {
                    emailService.sendPregnancyTimelineNotification(
                            LifecycleNotificationType.PREGNANCY_CONFIRMED,
                            breeding, (int) -daysSinceBreeding);
                    sent++;
                    logger.info("✅ SENT (ONCE): AWAITING_PREGNANCY_CONFIRMATION for: {} ({} days since breeding)",
                            breeding.getLivestock().getTagNumber(), daysSinceBreeding);
                } else {
                    logger.debug("⏭️ SKIPPED: Awaiting confirmation already sent for: {}",
                            breeding.getLivestock().getTagNumber());
                }
            }

            logger.info("✅ Awaiting-pregnancy check complete. {} NEW notifications sent.", sent);
            logger.info("========================================");
        } catch (Exception e) {
            logger.error("❌ Error in awaiting-pregnancy check", e);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DEDUPLICATION HELPERS
    // ═════════════════════════════════════════════════════════════════════════
    private boolean sendOnce(UUID animalId, String type) {
        String dedupeKey = type + ":" + animalId;
        if (notificationLogRepository.existsByNotificationTypeAndDedupeKey(type, dedupeKey)) {
            return false;
        }
        try {
            LivestockNotificationLog log = new LivestockNotificationLog();
            log.setLivestockId(animalId);
            log.setNotificationType(type);
            log.setDedupeKey(dedupeKey);
            log.setSentDate(LocalDate.now());
            log.setCreatedAt(LocalDateTime.now());
            notificationLogRepository.save(log);
            return true;
        } catch (Exception e) {
            logger.warn("Failed to log notification: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendOncePerPregnancy(UUID animalId, UUID breedingId, String type) {
        String dedupeKey = type + ":" + breedingId;
        if (notificationLogRepository.existsByNotificationTypeAndDedupeKey(type, dedupeKey)) {
            return false;
        }
        try {
            LivestockNotificationLog log = new LivestockNotificationLog();
            log.setLivestockId(animalId);
            log.setBreedingId(breedingId);
            log.setNotificationType(type);
            log.setDedupeKey(dedupeKey);
            log.setSentDate(LocalDate.now());
            log.setCreatedAt(LocalDateTime.now());
            notificationLogRepository.save(log);
            return true;
        } catch (Exception e) {
            logger.warn("Failed to log notification: {}", e.getMessage());
            return false;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // MANUAL TRIGGERS
    // ═════════════════════════════════════════════════════════════════════════
    public void notifyReadyToBreed() { checkReadyToBreed(); }
    public void notifyPregnancyTimelines() { checkPregnancyTimelines(); }
    public void notifyAwaitingPregnancyConfirmation() { checkAwaitingPregnancyConfirmation(); }

    public void runAllJobs() {
        logger.info("========================================");
        logger.info("🔄 Running all weekly reminder jobs...");
        logger.info("========================================");
        checkReadyToBreed();
        checkPregnancyTimelines();
        checkAwaitingPregnancyConfirmation();
        logger.info("✅ All weekly reminder jobs completed.");
        logger.info("========================================");
    }
}