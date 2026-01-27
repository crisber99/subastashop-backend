package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.TicketRifa;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.TicketRifaRepository; // <--- Debes crear este repo (ver abajo)
import com.subastashop.backend.repositories.UsuarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.Map;
import java.util.HashMap;

@RestController // <--- Faltaba esto
@RequestMapping("/api/rifas") // <--- Faltaba la ruta base
public class RifaController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ProductoRepository productoRepository; // <--- Faltaba inyectar esto

    @Autowired
    private TicketRifaRepository ticketRepository; // <--- Faltaba inyectar esto

    @PostMapping("/{productoId}/lanzar")
    public ResponseEntity<?> lanzarRifa(@PathVariable Integer productoId) {
        Producto rifa = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Rifa no encontrada"));

        // 1. Validar que se vendieron todos
        long ticketsVendidos = ticketRepository.countByRifaId(productoId);

        // (Asegúrate que Producto tenga getCantidadNumeros, si no, agrégalo al modelo)
        if (ticketsVendidos < rifa.getCantidadNumeros()) {
            return ResponseEntity.badRequest().body("Aún faltan números por vender.");
        }

        // 2. Obtener todos los participantes
        List<TicketRifa> todosLosTickets = ticketRepository.findByRifaId(productoId);

        // 3. EL SORTEO 🎲
        Collections.shuffle(todosLosTickets);

        // 4. Sacar los ganadores
        List<TicketRifa> ganadores = todosLosTickets.stream()
                .limit(rifa.getCantidadGanadores())
                .collect(Collectors.toList());

        // 5. Guardar resultados
        rifa.setEstado("FINALIZADA");
        productoRepository.save(rifa);

        return ResponseEntity.ok(ganadores);
    }

    @Autowired
    private UsuarioRepository usuarioRepository; // Necesitamos saber quién compra

    @PostMapping("/{productoId}/comprar/{numeroTicket}")
    public ResponseEntity<?> comprarTicket(@PathVariable Integer productoId, @PathVariable Integer numeroTicket) {

        // 1. Obtener usuario actual
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 2. Validar si el número ya está ocupado
        if (ticketRepository.existsByRifaIdAndNumeroTicket(productoId, numeroTicket)) {
            return ResponseEntity.badRequest().body("⛔ Este número ya fue vendido.");
        }

        Producto rifa = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Rifa no encontrada"));

        // 3. Crear y Guardar el ticket
        TicketRifa ticket = new TicketRifa();
        ticket.setRifa(rifa);
        ticket.setNumeroTicket(numeroTicket);
        ticket.setComprador(usuario);
        ticket.setFechaCompra(LocalDateTime.now());

        ticketRepository.save(ticket);

        // ================================================================
        // 📢 EL GRITO AL SOCKET (ESTO ACTUALIZA LAS OTRAS PANTALLAS)
        // ================================================================
        Map<String, Object> notificacion = new HashMap<>();
        notificacion.put("tipo", "TICKET_VENDIDO");
        notificacion.put("numero", numeroTicket);
        notificacion.put("productoId", productoId);

        // Enviamos el mensaje al canal público del producto
        messagingTemplate.convertAndSend("/topic/producto/" + productoId, notificacion);
        // ================================================================

        // Devolvemos un JSON simple para que el Frontend no se queje de "Texto vs JSON"
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
    // Aquí podrías validar si el usuario es Admin, pero confiaremos en el Frontend por ahora
    // o Spring Security si tienes la ruta protegida.
    
    List<TicketRifa> tickets = ticketRepository.findByRifaId(productoId);
    
    List<Map<String, Object>> respuesta = tickets.stream().map(t -> {
        Map<String, Object> map = new HashMap<>();
        map.put("numero", t.getNumeroTicket());
        map.put("comprador", t.getComprador().getEmail()); // O t.getComprador().getNombre()
        map.put("fecha", t.getFechaCompra());
        map.put("pagado", t.getPagado());
        return map;
    }).collect(Collectors.toList());
    
    return ResponseEntity.ok(respuesta);
}
}