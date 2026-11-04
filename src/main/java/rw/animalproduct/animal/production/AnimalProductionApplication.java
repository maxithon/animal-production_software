package rw.animalproduct.animal.production;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application entry point.
 *
 * @EnableScheduling is CRITICAL for automatic email notifications to work.
 * Without this annotation, all @Scheduled methods are silently ignored.
 *
 * @author HABANABAKIZE & NDAYIZEYE
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling  // ← CRITICAL: Enables @Scheduled methods
public class AnimalProductionApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnimalProductionApplication.class, args);
    }
}