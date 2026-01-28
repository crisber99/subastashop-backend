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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthFilter;
        private final AuthenticationProvider authenticationProvider;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(csrf -> csrf.disable())
                                .authorizeHttpRequests(auth -> auth
                                                // 1. RUTAS DE SOCKET (Prioridad Alta) ⚡
                                                .requestMatchers("/ws-subastas/**").permitAll()

                                                // 2. RUTAS DE ADMIN ESPECÍFICAS (Candado Rojo) 🔒
                                                // ¡Esta regla debe ir ANTES de las públicas de abajo!
                                                .requestMatchers("/api/rifas/*/admin/**").hasAuthority("ROLE_ADMIN")
                                                .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")

                                                // 3. RUTAS PÚBLICAS (Ver productos, login, imágenes) 🔓
                                                .requestMatchers("/api/auth/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/productos/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/rifas/**").permitAll() // General
                                                                                                              // de
                                                                                                              // rifas
                                                .requestMatchers("/api/productos/imagen/**").permitAll()

                                                // 4. RESTO (Vendedor y otros)
                                                .requestMatchers("/api/vendedor/**")
                                                .hasAnyAuthority("ROLE_VENDEDOR", "ROLE_ADMIN")
                                                .requestMatchers("/api/public/**").permitAll()
                                                .anyRequest().authenticated())
                                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authenticationProvider(authenticationProvider)
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(Arrays.asList("http://localhost:4200",
                                "https://storagesubastasapp.z20.web.core.windows.net"));
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(Arrays.asList("*"));
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}