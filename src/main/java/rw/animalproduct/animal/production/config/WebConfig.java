package rw.animalproduct.animal.production.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve uploaded photos
        registry.addResourceHandler("/uploads/photos/**")
                .addResourceLocations("file:uploads/photos/");

        // Serve uploaded PDFs (contracts)
        registry.addResourceHandler("/uploads/contracts/**")
                .addResourceLocations("file:uploads/contracts/");
    }
}