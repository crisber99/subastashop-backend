package com.subastashop.backend.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
    // Activa el caché concurrente local de Spring (útil para consultas pesadas de baja rotación)
}
