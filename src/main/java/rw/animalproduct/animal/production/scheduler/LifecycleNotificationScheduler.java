package rw.animalproduct.animal.production.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rw.animalproduct.animal.production.entity.Livestock;
import rw.animalproduct.animal.production.services.LifecycleEmailService;
import rw.animalproduct.animal.production.services.LivestockLifecycleService;

import java.util.List;

@Component
public class LifecycleNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(LifecycleNotificationScheduler.class);

    private final LivestockLifecycleService lifecycleService;
    private final LifecycleEmailService     emailService;

    public LifecycleNotificationScheduler(LivestockLifecycleService lifecycleService,
                                          LifecycleEmailService emailService) {
        this.lifecycleService = lifecycleService;
        this.emailService     = emailService;
    }

    /**
     * Newborns registered in the last 2 days — runs daily at 06:55
     */
    @Scheduled(cron = "0 55 6 * * *")
    public void notifyNewborns() {
        try {
            List<Livestock> animals = lifecycleService.getActiveAnimalsByAgeRange(0, 2);
            animals.forEach(animal -> {
                try {
                    emailService.sendNewbornNotification(animal);
                } catch (Exception e) {
                    log.error("❌ Failed to send newborn email for {}: {}", animal.getTagNumber(), e.getMessage());
                }
            });
            if (!animals.isEmpty()) {
                log.info("📧 Newborn notifications sent: {}", animals.size());
            } else {
                log.debug("⏩ No newborns in the last 2 days");
            }
        } catch (Exception e) {
            log.error("❌ Error in notifyNewborns scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Animals entering YOUNG stage (30–32 days old) — runs daily at 07:00
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void notifyYoungStage() {
        try {
            List<Livestock> animals = lifecycleService.getActiveAnimalsByAgeRange(30, 32);
            animals.forEach(animal -> {
                try {
                    emailService.sendYoungStageNotification(animal);
                } catch (Exception e) {
                    log.error("❌ Failed to send young-stage email for {}: {}", animal.getTagNumber(), e.getMessage());
                }
            });
            if (!animals.isEmpty()) {
                log.info("📧 Young-stage notifications sent: {}", animals.size());
            } else {
                log.debug("⏩ No animals entering YOUNG stage today");
            }
        } catch (Exception e) {
            log.error("❌ Error in notifyYoungStage scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Animals entering PRE_BREEDING (180–182 days old) — runs daily at 07:05
     */
    @Scheduled(cron = "0 5 7 * * *")
    public void notifyPreBreedingStage() {
        try {
            List<Livestock> animals = lifecycleService.getActiveAnimalsByAgeRange(180, 182);
            animals.forEach(animal -> {
                try {
                    emailService.sendPreBreedingNotification(animal);
                } catch (Exception e) {
                    log.error("❌ Failed to send pre-breeding email for {}: {}", animal.getTagNumber(), e.getMessage());
                }
            });
            if (!animals.isEmpty()) {
                log.info("📧 Pre-breeding notifications sent: {}", animals.size());
            } else {
                log.debug("⏩ No animals entering PRE_BREEDING stage today");
            }
        } catch (Exception e) {
            log.error("❌ Error in notifyPreBreedingStage scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Animals entering READY_TO_BREED (365–367 days old) — runs daily at 07:10
     */
    @Scheduled(cron = "0 10 7 * * *")
    public void notifyReadyToBreed() {
        try {
            List<Livestock> animals = lifecycleService.getActiveAnimalsByAgeRange(365, 367);
            animals.forEach(animal -> {
                try {
                    emailService.sendReadyToBreedNotification(animal);
                } catch (Exception e) {
                    log.error("❌ Failed to send ready-to-breed email for {}: {}", animal.getTagNumber(), e.getMessage());
                }
            });
            if (!animals.isEmpty()) {
                log.info("📧 Ready-to-breed notifications sent: {}", animals.size());
            } else {
                log.debug("⏩ No animals entering READY_TO_BREED stage today");
            }
        } catch (Exception e) {
            log.error("❌ Error in notifyReadyToBreed scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Animals due within 14 days — runs daily at 07:30
     */
    @Scheduled(cron = "0 30 7 * * *")
    public void notifyDueSoon() {
        try {
            List<Livestock> dueSoon = lifecycleService.getDueSoon(14);
            if (!dueSoon.isEmpty()) {
                emailService.sendDueSoonNotification(dueSoon, 14);
                log.info("📧 Due-soon notifications sent: {}", dueSoon.size());
            } else {
                log.debug("⏩ No animals due within 14 days");
            }
        } catch (Exception e) {
            log.error("❌ Error in notifyDueSoon scheduler: {}", e.getMessage(), e);
        }
    }

    /**
     * Overdue animals — runs daily at 08:00
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void notifyOverdue() {
        try {
            List<Livestock> overdue = lifecycleService.getOverdue();
            if (!overdue.isEmpty()) {
                emailService.sendOverdueNotification(overdue);
                log.info("📧 Overdue notifications sent: {}", overdue.size());
            } else {
                log.debug("⏩ No overdue animals");
            }
        } catch (Exception e) {
            log.error("❌ Error in notifyOverdue scheduler: {}", e.getMessage(), e);
        }
    }
}