package rw.animalproduct.animal.production.patches;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * WHY THIS FIXES "Register takes long":
 * LivestockController.register() calls emailService.sendAnimalRegisteredNotification(saved)
 * synchronously (wrapped in try/catch so it never *fails* the request, but it
 * still *blocks* the request thread until SMTP finishes — often 1-5+ seconds
 * per email, sometimes much longer on slow/blocked networks). The user's
 * browser waits for that entire round trip before the redirect happens.
 *
 * Marking the email-sending method @Async (see LifecycleEmailService patch)
 * hands the email off to this background thread pool immediately, so
 * register() returns and redirects the instant the DB save + audit log are
 * done — typically under 100ms instead of several seconds.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    // ADD THIS CONSTANT - fixes the compilation error
    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("email-async-");
        executor.initialize();
        return executor;
    }

    @Bean(name = NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notify-");
        executor.initialize();
        return executor;
    }
}