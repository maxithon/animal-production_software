package rw.animalproduct.animal.production;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // MAKE SURE THIS IS PRESENT
public class AnimalProductionApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnimalProductionApplication.class, args);
    }
}