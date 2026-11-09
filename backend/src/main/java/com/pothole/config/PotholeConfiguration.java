package com.pothole.config;

import java.time.Clock;

import jakarta.servlet.MultipartConfigElement;

import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
public class PotholeConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    MultipartConfigElement multipartConfigElement(PotholeProperties properties) {
        long maxBytes = properties.getImage().getMaxBytes();
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofBytes(maxBytes));
        factory.setMaxRequestSize(DataSize.ofBytes(maxBytes + 1024 * 1024));
        return factory.createMultipartConfig();
    }
}
