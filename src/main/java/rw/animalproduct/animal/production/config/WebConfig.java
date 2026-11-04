package rw.animalproduct.animal.production.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        Path base = Paths.get("uploads").toAbsolutePath().normalize();
        String baseUri = base.toUri().toString();

        // Photos
        registry.addResourceHandler("/uploads/photos/**")
                .addResourceLocations(baseUri + "photos/");

        // Contracts
        registry.addResourceHandler("/uploads/contracts/**")
                .addResourceLocations(baseUri + "contracts/");

        // ★ Vets
        registry.addResourceHandler("/uploads/vets/**")
                .addResourceLocations(baseUri + "vets/");

        // Buyers (already in your setup)
        registry.addResourceHandler("/uploads/buyers/**")
                .addResourceLocations(baseUri + "buyers/");

        // Safety net
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(baseUri);

        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/");
    }
}