package com.subastashop.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Punto de conexión para el Frontend
        registry.addEndpoint("/ws-subastas")
                .setAllowedOrigins(
                        "http://localhost:4200", // Local
                        "https://storagesubastasapp.z20.web.core.windows.net" // Tu Azure Frontend
                ) 
                .withSockJS(); // Habilita fallback por si el navegador es viejo
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefijo para los mensajes que enviamos a los clientes
        registry.enableSimpleBroker("/topic");
        // Prefijo para mensajes que vienen del cliente (no usaremos este por ahora)
        registry.setApplicationDestinationPrefixes("/app");
    }
}