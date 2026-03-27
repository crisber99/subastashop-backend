package com.subastashop.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;
import org.springframework.cache.annotation.EnableCaching;
import jakarta.annotation.PostConstruct;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@PostConstruct
    public void init() {
        // Forzamos a que la aplicación Java use UTC, igual que Azure SQL
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.out.println("Zona horaria configurada a: " + new java.util.Date());
    }

}
