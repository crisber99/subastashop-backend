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
@org.springframework.scheduling.annotation.EnableAsync
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@PostConstruct
    public void init() {
        // Configuramos la app para usar la zona horaria chilena (GMT-3/-4) para que los tiempos de subasta coincidan con el front
        TimeZone.setDefault(TimeZone.getTimeZone("America/Santiago"));
        System.out.println("Zona horaria configurada a: " + new java.util.Date());
    }

}
