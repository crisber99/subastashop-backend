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
        // 1. Obtener usuario actual (del Token JWT)
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers usuario = usuarioRepository.findByEmail(email).orElseThrow();

        // 2. Validar si el número ya está ocupado
        if (ticketRepository.existsByRifaIdAndNumeroTicket(productoId, numeroTicket)) {
            return ResponseEntity.badRequest().body("⛔ Este número ya fue vendido.");
        }

        // 3. Crear el ticket
        Producto rifa = productoRepository.findById(productoId).orElseThrow();

        // Validación extra: El número no puede ser mayor al total
        if (numeroTicket > rifa.getCantidadNumeros() || numeroTicket < 1) {
            return ResponseEntity.badRequest().body("Número inválido.");
        }

        TicketRifa ticket = new TicketRifa();
        ticket.setRifa(rifa);
        ticket.setNumeroTicket(numeroTicket);
        ticket.setComprador(usuario);
        ticket.setFechaCompra(LocalDateTime.now());

        ticketRepository.save(ticket);

        Map<String, Object> notificacion = new HashMap<>();
        notificacion.put("tipo", "TICKET_VENDIDO"); // Para distinguir de una Puja
        notificacion.put("numero", numeroTicket);
        notificacion.put("productoId", productoId);

        // Enviamos al mismo canal que usas para las subastas
        messagingTemplate.convertAndSend("/topic/producto/" + productoId, notificacion);

        return ResponseEntity.ok("Ticket #" + numeroTicket + " comprado con éxito 🍀");
    }

    @GetMapping("/{productoId}/tickets")
    public ResponseEntity<List<Integer>> obtenerTicketsVendidos(@PathVariable Integer productoId) {
        // Devuelve solo la lista de números ocupados (ej: [1, 5, 20]) para pintar la
        // grilla en rojo
        List<TicketRifa> tickets = ticketRepository.findByRifaId(productoId);
        List<Integer> ocupados = tickets.stream().map(TicketRifa::getNumeroTicket).collect(Collectors.toList());
        return ResponseEntity.ok(ocupados);
    }
}