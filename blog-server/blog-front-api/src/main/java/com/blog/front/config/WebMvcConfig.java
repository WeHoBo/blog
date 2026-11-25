package com.blog.front.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String directory = Path.of(uploadPath).toAbsolutePath().normalize().toUri().toASCIIString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(directory.endsWith("/") ? directory : directory + "/");
    }
}
