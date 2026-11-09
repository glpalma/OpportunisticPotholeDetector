package com.pothole;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PotholeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PotholeApiApplication.class, args);
    }
}
