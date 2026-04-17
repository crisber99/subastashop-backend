package com.subastashop.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.disable()) // Se deshabilita aquí porque usamos un filtro global de mayor prioridad (abajo)
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // ================================================================
                // 1. ZONA PÚBLICA (Accesible para todos) 🌍
                // ================================================================
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/ws-subastas/**").permitAll()
                .requestMatchers("/error").permitAll() // <-- AÑADIDO: Para evitar que errores internos o BadCredentialsException mascarados por Spring terminen en 403

                // Autenticación (Login/Registro)
                .requestMatchers("/api/auth/**").permitAll()

                // Mercado Pago Webhooks (Público para recibir notificaciones)
                .requestMatchers("/api/mercadopago/webhook").permitAll()

                // Landing Page y Tiendas Públicas
                .requestMatchers("/api/public/**").permitAll()

                // Imágenes
                .requestMatchers("/api/productos/imagen/**").permitAll()

                // Lectura de Catálogo (GET)
                .requestMatchers(HttpMethod.GET, "/api/productos", "/api/productos/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/rifas", "/api/rifas/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categorias", "/api/categorias/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/categorias").hasAuthority("ROLE_SUPER_ADMIN")

                // Chat en vivo (público para ver historial GET, autenticado para enviar POST/WS)
                .requestMatchers(HttpMethod.GET, "/api/chat/**").permitAll()
                .requestMatchers("/api/chat/**").authenticated()

                // Concursos (lectura pública)
                .requestMatchers(HttpMethod.GET, "/api/contests/**").permitAll()


                // ================================================================
                // 2. ZONA PROTEGIDA (Roles Específicos) 👮‍♂️
                // ================================================================

                // 👑 ZONA SUPER ADMIN
                // Gestión de Tiendas y Moderación de Reportes
                .requestMatchers("/api/super-admin/**").hasAuthority("ROLE_SUPER_ADMIN")
                .requestMatchers("/api/reportes/admin/**").hasAuthority("ROLE_SUPER_ADMIN") // 👈 NUEVO: Solo tú ves los reportes

                .requestMatchers("/api/ordenes/**").authenticated()
                
                // Admin de Tienda / Rifa
                .requestMatchers("/api/rifas/*/admin/**").hasAuthority("ROLE_ADMIN")
                .requestMatchers("/api/admin/**").hasAnyAuthority("ROLE_ADMIN", "ROLE_SUPER_ADMIN")

                // Vendedor
                .requestMatchers("/api/vendedor/**").hasAnyAuthority("ROLE_VENDEDOR", "ROLE_ADMIN")

                .requestMatchers("/api/tiendas/**").hasAnyAuthority("ROLE_VENDEDOR", "ROLE_ADMIN", "ROLE_SUPER_ADMIN")

                // ================================================================
                // 3. CANDADO FINAL (Todo lo demás requiere login) 🔒
                // ================================================================
                // Esto incluye POST /api/reportes/{id} (Cualquier usuario logueado puede reportar)
                // Esto incluye /api/pagos/** (Cualquier usuario logueado puede pagar)
                .anyRequest().authenticated()
            )
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> customCorsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(Arrays.asList(
                "http://localhost:4200",
                "https://www.subastashop.cl",
                "https://subastashop.cl",
                "https://storagesubastasapp.z20.web.core.windows.net",
                "https://storagesubastasapp.z20.web.core.windows.net/"
        ));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setExposedHeaders(Arrays.asList("Authorization", "x-tenant-id"));
        config.setMaxAge(3600L);
        
        source.registerCorsConfiguration("/**", config);
        
        CorsFilter filter = new CorsFilter(source);
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(filter);
        
        // Esto es CLAVE: Ejecutar este filtro ANTES que la seguridad de Spring o interceptores
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}