package rw.animalproduct.animal.production.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's @Scheduled task execution.
 *
 * This is the MISSING piece that caused the scheduler emails to never fire.
 * Without @EnableScheduling, all @Scheduled methods in
 * LifecycleNotificationScheduler are silently ignored.
 *
 * Alternatively, you can add @EnableScheduling directly to your main
 * application class (AnimalProductionApplication or similar).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // no bean definitions needed — annotation does the work
}