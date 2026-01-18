package com.subastashop.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration; // <--- Importar
import org.springframework.web.cors.CorsConfigurationSource; // <--- Importar
import org.springframework.web.cors.UrlBasedCorsConfigurationSource; // <--- Importar

import java.util.Arrays; // <--- Importar

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. ACTIVAR CORS AQUÍ (Esto es lo nuevo)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()     // Login y Registro
                .requestMatchers("/ws-subastas/**").permitAll()   // <--- ¡AGREGA ESTA LÍNEA! 🔌
                .requestMatchers(HttpMethod.GET, "/api/productos/**").permitAll() // Ver productos
                .requestMatchers("/api/admin/**").hasAuthority("ADMIN")
                .anyRequest().authenticated()
            )
            .sessionManagement(sess -> sess
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 2. DEFINIR LA REGLA DE CORS PARA SEGURIDAD
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:4200",                                      // Desarrollo Local
            "https://storagesubastasapp.z20.web.core.windows.net"         // Producción (Azure)
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}