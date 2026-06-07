package com.subastashop.backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.annotation.Order;

@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    // Almacena un "balde" (Bucket) de tokens virtual para cada IP
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    // Creamos un bucket que permita 120 peticiones, y recargue 120 cada minuto.
    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.classic(120, Refill.greedy(120, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket resolveBucket(String ip) {
        return cache.computeIfAbsent(ip, k -> createNewBucket());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Aplicamos Rate Limiting a toda la API
        if (path.startsWith("/api/")) {
            
            // Extraer la IP del cliente
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }

            Bucket bucket = resolveBucket(ip);

            // Intentar consumir 1 token
            if (bucket.tryConsume(1)) {
                // Hay tokens disponibles, añadir header informativo
                response.setHeader("X-Rate-Limit-Remaining", String.valueOf(bucket.getAvailableTokens()));
                filterChain.doFilter(request, response);
            } else {
                // No hay tokens, bloquear
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // Error 429
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\": \"Demasiadas peticiones\", \"message\": \"Has excedido el límite de peticiones (120/minuto). Por favor espera un minuto.\"}");
                return;
            }
        } else {
            // Pasamos de largo las rutas seguras
            filterChain.doFilter(request, response);
        }
    }
}
