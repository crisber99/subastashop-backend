package com.subastashop.backend.controllers;

import com.subastashop.backend.dto.MensajeChatDTO;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.MensajeChat;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.MensajeChatRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
public class ChatController {

    @Autowired
    private AppUserRepository usuarioRepository;

    @Autowired
    private MensajeChatRepository chatRepository;

    // --- WEBSOCKET EN TIEMPO REAL ---
    @MessageMapping("/chat/{tiendaId}")
    @SendTo("/topic/tienda/{tiendaId}")
    public MensajeChatDTO manejarMensaje(@DestinationVariable Long tiendaId, MensajeChatDTO dto) {
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        dto.setTiendaId(tiendaId);
        dto.setTimestamp(timestamp);
        
        // PERSISTENCIA: Guardar en la base de datos
        MensajeChat entidad = new MensajeChat();
        entidad.setTiendaId(tiendaId);
        entidad.setRemitenteNombre(dto.getRemitenteNombre());
        entidad.setContenido(dto.getContenido());
        entidad.setUserEmail(dto.getUserEmail());
        entidad.setTimestampStr(timestamp);
        entidad.setEsVendedor(dto.isEsVendedor());
        entidad.setAdmin(dto.isAdmin());
        
        chatRepository.save(entidad);
        
        System.out.println("DEBUG (Persistido): Msj en tienda " + tiendaId + " de " + dto.getRemitenteNombre());
        
        return dto;
    }

    // --- REST API: OBTENER HISTORIAL ---
    @GetMapping("/api/chat/tienda/{tiendaId}")
    public List<MensajeChatDTO> obtenerHistorial(@PathVariable Long tiendaId) {
        // Obtenemos los últimos 50 mensajes de esta tienda
        return chatRepository.findTop50ByTiendaIdOrderByFechaEnvioAsc(tiendaId)
                .stream()
                .map(m -> {
                    MensajeChatDTO dto = new MensajeChatDTO();
                    dto.setContenido(m.getContenido());
                    dto.setRemitenteNombre(m.getRemitenteNombre());
                    dto.setUserEmail(m.getUserEmail());
                    dto.setTimestamp(m.getTimestampStr());
                    dto.setTiendaId(m.getTiendaId());
                    dto.setEsVendedor(m.isEsVendedor());
                    dto.setAdmin(m.isAdmin());
                    return dto;
                })
                .collect(Collectors.toList());
    }

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
