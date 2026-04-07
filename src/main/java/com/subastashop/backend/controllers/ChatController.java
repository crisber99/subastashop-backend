package com.subastashop.backend.controllers;

import com.subastashop.backend.config.TenantContext;
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

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    // --- WEBSOCKET EN TIEMPO REAL ---
    @MessageMapping("/chat/{productoId}")
    public void manejarMensaje(@DestinationVariable Long productoId, MensajeChatDTO dto) {

        // FIX CRÍTICO: Los mensajes WebSocket/STOMP no pasan por el TenantInterceptor HTTP.
        // Sin esto, BaseEntity.onPrePersist() llama TenantContext.getTenantId() que devuelve null,
        // y el INSERT falla silenciosamente (excepción en el thread de STOMP).
        if (TenantContext.getTenantId() == null) {
            TenantContext.setTenantId("chat-global");
        }

        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            dto.setProductoId(productoId);
            dto.setTimestamp(timestamp);

            // PERSISTENCIA: Guardar en la base de datos
            MensajeChat entidad = new MensajeChat();
            entidad.setProductoId(productoId);
            entidad.setTiendaId(dto.getTiendaId());
            entidad.setRemitenteNombre(dto.getRemitenteNombre());
            entidad.setContenido(dto.getContenido());
            entidad.setUserEmail(dto.getUserEmail());
            entidad.setTimestampStr(timestamp);
            entidad.setEsVendedor(dto.isEsVendedor());
            entidad.setAdmin(dto.isAdmin());

            entidad = chatRepository.save(entidad);
            dto.setId(entidad.getId().toString());

            System.out.println("✅ Chat guardado: Msj [" + dto.getId() + "] en producto " + productoId + " de " + dto.getRemitenteNombre());

            // DIFUSIÓN: Enviar por el canal exclusivo de chat (separado del canal de subastas/rifas)
            messagingTemplate.convertAndSend("/topic/chat/" + productoId, dto);

        } catch (Exception e) {
            System.err.println("❌ Error al guardar mensaje del chat: " + e.getMessage());
            e.printStackTrace();
        } finally {
            TenantContext.clear(); // Limpiar el contexto del thread STOMP
        }
    }

    // --- REST API: OBTENER HISTORIAL ---
    @GetMapping("/api/chat/producto/{productoId}")
    public org.springframework.http.ResponseEntity<List<MensajeChatDTO>> obtenerHistorial(@PathVariable Long productoId) {
        // El GlobalExceptionHandler capturará cualquier error aquí
        if (TenantContext.getTenantId() == null) {
            TenantContext.setTenantId("chat-global");
        }

        try {
            List<MensajeChatDTO> historial = chatRepository.findTop50ByProductoIdOrderByFechaEnvioAsc(productoId)
                    .stream()
                    .map(m -> {
                        MensajeChatDTO dto = new MensajeChatDTO();
                        dto.setId(m.getId().toString());
                        dto.setContenido(m.getContenido());
                        dto.setRemitenteNombre(m.getRemitenteNombre());
                        dto.setUserEmail(m.getUserEmail());
                        dto.setTimestamp(m.getTimestampStr());
                        dto.setProductoId(m.getProductoId());
                        dto.setTiendaId(m.getTiendaId());
                        dto.setEsVendedor(m.isEsVendedor());
                        dto.setAdmin(m.isAdmin());
                        return dto;
                    })
                    .collect(Collectors.toList());

            return org.springframework.http.ResponseEntity.ok(historial);
        } finally {
            TenantContext.clear();
        }
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
