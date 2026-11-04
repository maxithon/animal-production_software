package rw.animalproduct.animal.production.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Enables Spring's @Scheduled task execution.
 *
 * This configuration is CRITICAL for automatic email notifications to work.
 * Without @EnableScheduling, all @Scheduled methods are silently ignored.
 *
 * The thread pool is configured with:
 * - poolSize: 5 concurrent scheduled tasks
 * - Thread name prefix: "scheduler-" for easy identification in logs
 * - Proper shutdown handling
 *
 * @author HABANABAKIZE & NDAYIZEYE
 * @version 1.0.0
 */
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(SchedulingConfig.class);

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(5);
        taskScheduler.setThreadNamePrefix("scheduler-");
        taskScheduler.setAwaitTerminationSeconds(60);
        taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        taskScheduler.initialize();

        taskRegistrar.setTaskScheduler(taskScheduler);

        logger.info("✅ SchedulingConfig initialized with thread pool size: 5");
        logger.info("✅ Scheduled tasks will run with prefix: 'scheduler-'");
    }
}