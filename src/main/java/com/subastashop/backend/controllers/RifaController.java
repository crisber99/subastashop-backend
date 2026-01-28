package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.TicketRifa;
import com.subastashop.backend.models.GanadorRifa; // 👈 Importante
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.TicketRifaRepository;
import com.subastashop.backend.repositories.UsuarioRepository;
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
    private UsuarioRepository usuarioRepository;


    // 👇 MÉTODO ACTUALIZADO: AHORA GUARDA EN BD Y AVISA POR SOCKET
    @PostMapping("/{productoId}/lanzar")
    public ResponseEntity<?> lanzarRifa(@PathVariable Integer productoId) {
        
        // 1. Validaciones
        Producto rifa = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Rifa no encontrada"));

        // Evitar lanzar dos veces
        // (Asume que crearás este método en el repo, o puedes chequear si rifa.getEstado() == "FINALIZADA")
        if ("FINALIZADA".equals(rifa.getEstado())) { 
             return ResponseEntity.badRequest().body("El sorteo ya fue realizado.");
        }

        List<TicketRifa> todosLosTickets = ticketRepository.findByRifaId(productoId);

        if (todosLosTickets.size() < rifa.getCantidadNumeros()) {
            return ResponseEntity.badRequest().body("Aún faltan números por vender.");
        }

        // 2. EL SORTEO 🎲 (Mezclar tickets)
        Collections.shuffle(todosLosTickets);

        // 3. Seleccionar, Guardar y Preparar Notificación
        int cantidadGanadores = rifa.getCantidadGanadores();
        List<Map<String, Object>> listaGanadoresDTO = new ArrayList<>();

        for (int i = 0; i < cantidadGanadores && i < todosLosTickets.size(); i++) {
            TicketRifa ticketGanador = todosLosTickets.get(i);

            // A) Guardar en Base de Datos (Persistencia)
            GanadorRifa ganador = new GanadorRifa();
            ganador.setRifa(rifa);
            ganador.setTicketGanador(ticketGanador);
            ganador.setPuesto(i + 1); // 1, 2, 3...
            ganador.setFechaGanador(LocalDateTime.now());
            ganadorRepository.save(ganador);

            // B) Preparar objeto para el Frontend (DTO)
            Map<String, Object> dto = new HashMap<>();
            dto.put("puesto", i + 1);
            dto.put("numeroTicket", ticketGanador.getNumeroTicket());
            dto.put("comprador", ticketGanador.getComprador().getEmail());
            // Puedes agregar más datos si quieres
            listaGanadoresDTO.add(dto);
        }

        // 4. Actualizar estado de la Rifa
        rifa.setEstado("FINALIZADA");
        productoRepository.save(rifa);

        // 5. 📢 GRITO AL SOCKET: "¡YA HAY GANADORES!"
        Map<String, Object> notificacion = new HashMap<>();
        notificacion.put("tipo", "SORTEO_FINALIZADO");
        notificacion.put("ganadores", listaGanadoresDTO); // Enviamos la lista
        notificacion.put("productoId", productoId);

        messagingTemplate.convertAndSend("/topic/producto/" + productoId, notificacion);

        return ResponseEntity.ok(listaGanadoresDTO);
    }

    // --- EL RESTO DE TUS MÉTODOS SE MANTIENEN IGUAL ---

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
}