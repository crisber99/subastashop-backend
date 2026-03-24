package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.SupportTicket;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.SupportTicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support")
public class SupportTicketController {

    @Autowired
    private SupportTicketRepository ticketRepository;

    @Autowired
    private AppUserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> createTicket(@RequestBody SupportTicket ticket) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers user = userRepository.findByEmail(email).orElseThrow();
        
        ticket.setUsuario(user);
        ticket.setEstado(SupportTicket.TicketEstado.ABIERTO);
        ticket.setFechaCreacion(LocalDateTime.now());
        
        return ResponseEntity.ok(ticketRepository.save(ticket));
    }

    @GetMapping("/me")
    public List<SupportTicket> getMyTickets() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers user = userRepository.findByEmail(email).orElseThrow();
        return ticketRepository.findByUsuarioOrderByFechaCreacionDesc(user);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public List<SupportTicket> getAllTickets() {
        return ticketRepository.findAllByOrderByFechaCreacionDesc();
    }

    @PostMapping("/{id}/reply")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> replyTicket(@PathVariable Long id, @RequestBody Map<String, String> request) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket no encontrado"));
        
        String respuesta = request.get("respuesta");
        ticket.setRespuestaAdmin(respuesta);
        ticket.setFechaRespuesta(LocalDateTime.now());
        ticket.setEstado(SupportTicket.TicketEstado.CERRADO); // Se cierra al responder
        
        return ResponseEntity.ok(ticketRepository.save(ticket));
    }
}
