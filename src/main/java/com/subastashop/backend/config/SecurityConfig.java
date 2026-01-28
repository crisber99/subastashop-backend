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
                                                // ================================================================
                                                // 1. ZONA PÚBLICA (Accesible para todos) 🌍
                                                // ================================================================
                                                // Websockets
                                                .requestMatchers("/ws-subastas/**").permitAll()

                                                // Autenticación (Login/Registro)
                                                .requestMatchers("/api/auth/**").permitAll()

                                                // 👇 ESTA ES LA CLAVE PARA TU LANDING PAGE MULTI-TIENDA 👇
                                                .requestMatchers("/api/public/**").permitAll()

                                                // Imágenes
                                                .requestMatchers("/api/productos/imagen/**").permitAll()

                                                // Lectura de Productos y Rifas (GET)
                                                .requestMatchers(HttpMethod.GET, "/api/productos/**").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/rifas/**").permitAll()

                                                // ================================================================
                                                // 2. ZONA PROTEGIDA (Roles Específicos) 👮‍♂️
                                                // ================================================================
                                                // Admin de Rifa y Admin General
                                                .requestMatchers("/api/rifas/*/admin/**").hasAuthority("ROLE_ADMIN")
                                                .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")

                                                // Vendedor (o Admin actuando como vendedor)
                                                .requestMatchers("/api/vendedor/**")
                                                .hasAnyAuthority("ROLE_VENDEDOR", "ROLE_ADMIN")

                                                // ================================================================
                                                // 3. CANDADO FINAL (Todo lo demás requiere login) 🔒
                                                // ================================================================
                                                .anyRequest().authenticated())
                                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authenticationProvider(authenticationProvider)
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                // Permitimos tanto localhost (pruebas) como Azure (producción)
                configuration.setAllowedOrigins(Arrays.asList(
                                "http://localhost:4200",
                                "https://storagesubastasapp.z20.web.core.windows.net"));
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(Arrays.asList("*"));
                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}