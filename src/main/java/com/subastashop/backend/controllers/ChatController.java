package com.subastashop.backend.controllers;

import com.subastashop.backend.dto.MensajeChatDTO;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.repositories.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Controller
public class ChatController {

    @Autowired
    private AppUserRepository usuarioRepository;

    @MessageMapping("/chat/{tiendaId}")
    @SendTo("/topic/tienda/{tiendaId}")
    public MensajeChatDTO manejarMensaje(@DestinationVariable Long tiendaId, MensajeChatDTO mensaje) {
        
        // En un entorno real, obtenemos el usuario por email desde el DTO o contexto
        // Para este demo, simplemente formateamos el timestamp
        mensaje.setTiendaId(tiendaId);
        mensaje.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        
        System.out.println("DEBUG: Reenviando mensaje en tienda " + tiendaId + ": " + mensaje.getContenido());
        
        return mensaje;
    }

    // Endpoint adicional para obtener info básica del remitente
    @MessageMapping("/user-info/{id}")
    @SendTo("/topic/user-info/{id}")
    public MensajeChatDTO obtenerUserInfo(@DestinationVariable Integer id) {
        AppUsers user = usuarioRepository.findById(Objects.requireNonNull(id, "ID no puede ser nulo"))
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        MensajeChatDTO info = new MensajeChatDTO();
        info.setRemitenteNombre(user.getNombreCompleto());
        info.setUserEmail(user.getEmail());
        
        return info;
    }
}
