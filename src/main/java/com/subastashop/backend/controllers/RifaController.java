package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.TicketRifa;
import com.subastashop.backend.models.GanadorRifa; // 👈 Importante
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.TicketRifaRepository;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.GanadorRifaRepository; // 👈 Importante

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.*; // Imports generales para List, Map, Collections, etc.
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rifas")
public class RifaController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private TicketRifaRepository ticketRepository;

    @Autowired
    private GanadorRifaRepository ganadorRepository;

    @Autowired
    private AppUserRepository usuarioRepository;

    @Autowired
    private com.subastashop.backend.services.RifaService rifaService;

    // 👇 MÉTODO ACTUALIZADO: AHORA DELEGA AL SERVICIO ASÍNCRONO
    @PostMapping("/{productoId}/lanzar")
    public ResponseEntity<?> lanzarRifa(@PathVariable Integer productoId) {
        try {
            // El servicio valida el estado inicial y dispara el "show" asíncrono
            rifaService.lanzarRifa(productoId);
            
            Map<String, String> respuesta = new HashMap<>();
            respuesta.put("mensaje", "Sorteo iniciado exitosamente. Los resultados aparecerán en breve.");
            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al iniciar el sorteo: " + e.getMessage());
        }
    }


    @PostMapping("/{productoId}/comprar/{numeroTicket}")
    public ResponseEntity<?> comprarTicket(@PathVariable Integer productoId, @PathVariable Integer numeroTicket) {
        // ... (Tu código existente de compra, NO CAMBIA) ...
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (ticketRepository.existsByRifaIdAndNumeroTicket(productoId, numeroTicket)) {
            return ResponseEntity.badRequest().body("⛔ Este número ya fue vendido.");
        }

        Producto rifa = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Rifa no encontrada"));

        TicketRifa ticket = new TicketRifa();
        ticket.setRifa(rifa);
        ticket.setNumeroTicket(numeroTicket);
        ticket.setComprador(usuario);
        ticket.setFechaCompra(LocalDateTime.now());

        ticketRepository.save(ticket);

        // Notificación Socket Compra
        Map<String, Object> notificacion = new HashMap<>();
        notificacion.put("tipo", "TICKET_VENDIDO");
        notificacion.put("numero", numeroTicket);
        notificacion.put("productoId", productoId);
        messagingTemplate.convertAndSend("/topic/producto/" + productoId, notificacion);

        Map<String, String> respuesta = new HashMap<>();
        respuesta.put("mensaje", "Ticket comprado con éxito");
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/{productoId}/tickets")
    public ResponseEntity<List<Integer>> obtenerTicketsVendidos(@PathVariable Integer productoId) {
        List<TicketRifa> tickets = ticketRepository.findByRifaId(productoId);
        List<Integer> ocupados = tickets.stream()
                .map(TicketRifa::getNumeroTicket)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ocupados);
    }

    @GetMapping("/{productoId}/admin/detalles")
    public ResponseEntity<List<Map<String, Object>>> obtenerDetallesAdmin(@PathVariable Integer productoId) {
        List<TicketRifa> tickets = ticketRepository.findByRifaId(productoId);
        List<Map<String, Object>> respuesta = tickets.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("numero", t.getNumeroTicket());
            map.put("comprador", t.getComprador().getEmail());
            map.put("fecha", t.getFechaCompra());
            map.put("pagado", t.getPagado());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/{productoId}/ganadores")
    public ResponseEntity<List<Map<String, Object>>> obtenerGanadores(@PathVariable Integer productoId) {

        List<GanadorRifa> ganadores = ganadorRepository.findByRifaId(productoId);
        
        List<Map<String, Object>> respuesta = ganadores.stream().map(g -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("puesto", g.getPuesto());
            dto.put("numeroTicket", g.getTicketGanador().getNumeroTicket());
            dto.put("comprador", g.getTicketGanador().getComprador().getEmail());
            return dto;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(respuesta);
    }
}