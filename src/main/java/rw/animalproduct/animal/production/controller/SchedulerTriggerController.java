package rw.animalproduct.animal.production.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rw.animalproduct.animal.production.scheduler.LifecycleNotificationScheduler;

/**
 * TEMPORARY testing endpoint — only loaded under the "dev" profile.
 *
 * Uses GET (not POST) so Spring Security's CSRF filter doesn't block the call.
 *
 * Usage (open in browser after logging in):
 *   http://localhost:8888/api/admin/run-ready-to-breed
 *   http://localhost:8888/api/admin/run-pregnancy-jobs
 *   http://localhost:8888/api/admin/run-awaiting-pregnancy-jobs
 *   http://localhost:8888/api/admin/run-all-lifecycle-jobs
 *
 * IMPORTANT: This class is disabled in production because of @Profile("dev").
 *
 * @author HABANABAKIZE & NDAYIZEYE
 */
@RestController
@RequestMapping("/api/admin")
@Profile("dev")
public class SchedulerTriggerController {

    private final LifecycleNotificationScheduler scheduler;

    public SchedulerTriggerController(LifecycleNotificationScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @GetMapping("/run-ready-to-breed")
    public String runReadyToBreed() {
        scheduler.notifyReadyToBreed();
        return "Ready-to-breed job triggered — check logs.";
    }

    @GetMapping("/run-pregnancy-jobs")
    public String runPregnancy() {
        scheduler.notifyPregnancyTimelines();
        return "Pregnancy job triggered — check logs.";
    }

    @GetMapping("/run-awaiting-pregnancy-jobs")
    public String runAwaiting() {
        scheduler.notifyAwaitingPregnancyConfirmation();
        return "Awaiting-pregnancy job triggered — check logs.";
    }

    @GetMapping("/run-all-lifecycle-jobs")
    public String runAll() {
        scheduler.runAllJobs();
        return "All weekly reminder jobs triggered — check logs.";
    }
}