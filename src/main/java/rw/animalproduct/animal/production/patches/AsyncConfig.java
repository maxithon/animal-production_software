package rw.animalproduct.animal.production.patches;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Asynchronous execution configuration for email notifications and other async tasks.
 *
 * @author HABANABAKIZE & NDAYIZEYE
 * @version 1.0.0
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);

    // ─── EXECUTOR NAMES ──────────────────────────────────────────────────────
    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";
    public static final String EMAIL_EXECUTOR = "emailTaskExecutor";

    /**
     * Executor for general notification tasks (OtherExpense, etc.)
     * Core pool size: 2, Max pool size: 6, Queue capacity: 200
     */
    @Bean(name = NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notify-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        logger.info("✅ notificationExecutor initialized: corePoolSize=2, maxPoolSize=6, queueCapacity=200");
        return executor;
    }

    /**
     * Executor for email notification tasks.
     * Core pool size: 3, Max pool size: 10, Queue capacity: 100
     */
    @Bean(name = EMAIL_EXECUTOR)
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        logger.info("✅ emailTaskExecutor initialized: corePoolSize=3, maxPoolSize=10, queueCapacity=100");
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return emailTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            logger.error("❌ Async method execution failed: {} - {}", method.getName(), ex.getMessage(), ex);
        };
    }
}