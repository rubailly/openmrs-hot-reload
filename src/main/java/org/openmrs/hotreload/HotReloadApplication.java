package org.openmrs.hotreload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Main Spring Boot application class for the Hot Reload tool.
 * Enables scheduling for file watching and caching for class data.
 */
@SpringBootApplication
@EnableScheduling
@EnableCaching
public class HotReloadApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotReloadApplication.class, args);
    }
}
