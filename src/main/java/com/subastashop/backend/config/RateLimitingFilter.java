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

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    // Almacena un "balde" (Bucket) de tokens virtual para cada IP
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    // Creamos un bucket que permita 10 peticiones, y recargue 10 cada minuto.
    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket resolveBucket(String ip) {
        return cache.computeIfAbsent(ip, k -> createNewBucket());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Solo aplicamos Rate Limiting a las rutas de Autenticación para evitar Fuerza Bruta
        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register")) {
            
            // Extraer la IP del cliente
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }

            Bucket bucket = resolveBucket(ip);

            // Intentar consumir 1 token
            if (bucket.tryConsume(1)) {
                // Hay tokens disponibles, dejar pasar la petición
                filterChain.doFilter(request, response);
            } else {
                // No hay tokens (DDoS o Fuerza Bruta), bloquear temporalmente
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // Error 429
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Demasiadas peticiones\", \"message\": \"Has excedido el l\\u00EDmite de intentos. Por favor espera un minuto.\"}");
                return;
            }
        } else {
            // Pasamos de largo las rutas seguras
            filterChain.doFilter(request, response);
        }
    }
}
