package com.persona;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Persona PWA — Spring Boot Application Entry Point
 *
 * DATABASE_URL format conversion (Render → JDBC) is handled by
 * com.persona.config.DatabaseUrlPostProcessor before Spring starts.
 */
@SpringBootApplication
@EnableScheduling
public class PersonaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonaApplication.class, args);
        System.out.println("\n  Persona PWA (Java / Spring Boot)");
        System.out.println("  API: /api/health | DB check: /api/debug/db\n");
    }
}
