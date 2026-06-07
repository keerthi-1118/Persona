package com.persona;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Persona PWA — Spring Boot Application Entry Point
 * Replaces: backend/app.py
 */
@SpringBootApplication
@EnableScheduling
public class PersonaApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonaApplication.class, args);
        System.out.println("\n  Persona PWA (Java / Spring Boot)");
        System.out.println("  Frontend : http://localhost:5000");
        System.out.println("  API      : http://localhost:5000/api/health\n");
    }
}
