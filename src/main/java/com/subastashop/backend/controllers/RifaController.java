package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.TicketRifa;
import com.subastashop.backend.models.GanadorRifa;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.TicketRifaRepository;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.GanadorRifaRepository;
import com.subastashop.backend.services.EmailService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.*;
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

    @Autowired
    private EmailService rifaEmailService;

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
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (ticketRepository.existsByRifaIdAndNumeroTicket(productoId, numeroTicket)) {
            return ResponseEntity.badRequest().body("⛔ Este número ya fue vendido.");
        }

        Producto rifa = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Rifa no encontrada"));

        if (!"DISPONIBLE".equals(rifa.getEstado())) {
            return ResponseEntity.badRequest().body("⛔ Esta rifa no está disponible para compra.");
        }

        TicketRifa ticket = new TicketRifa();
        ticket.setRifa(rifa);
        ticket.setNumeroTicket(numeroTicket);
        ticket.setComprador(usuario);
        ticket.setFechaCompra(LocalDateTime.now());
        ticketRepository.save(ticket);

        // Notificación WebSocket
        Map<String, Object> notificacion = new HashMap<>();
        notificacion.put("tipo", "TICKET_VENDIDO");
        notificacion.put("numero", numeroTicket);
        notificacion.put("productoId", productoId);
        messagingTemplate.convertAndSend("/topic/producto/" + productoId, notificacion);

        // Buscar todos los tickets del usuario en esta rifa para el comprobante agrupado
        List<TicketRifa> todosMisTickets = ticketRepository.findByRifaIdAndCompradorId(productoId, usuario.getId());
        List<Integer> numerosTickets = todosMisTickets.stream()
                .map(TicketRifa::getNumeroTicket)
                .sorted()
                .collect(java.util.stream.Collectors.toList());

        // Enviar comprobante agrupado por email (solo 1 email con todos sus tickets)
        enviarComprobanteEmail(usuario, rifa, numerosTickets);

        // Retornar datos del ticket para el comprobante en frontend
        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("mensaje", "Ticket comprado con éxito");
        respuesta.put("numeroTicket", numeroTicket);
        respuesta.put("nombreRifa", rifa.getNombre());
        respuesta.put("precioTicket", rifa.getPrecioTicket());
        respuesta.put("comprador", usuario.getNombreCompleto());
        respuesta.put("fecha", ticket.getFechaCompra().toString());
        respuesta.put("codigoVerificacion", "SS-" + rifa.getId() + "-" + ticket.getNumeroTicket() + "-" + usuario.getId());
        return ResponseEntity.ok(respuesta);
    }

    @GetMapping("/{productoId}/mis-tickets")
    public ResponseEntity<List<Map<String, Object>>> obtenerMisTickets(@PathVariable Integer productoId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<TicketRifa> tickets = ticketRepository.findByRifaIdAndCompradorId(productoId, usuario.getId());

        List<Map<String, Object>> result = tickets.stream().map(t -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", t.getId());
            dto.put("numeroTicket", t.getNumeroTicket());
            dto.put("fechaCompra", t.getFechaCompra().toString());
            dto.put("pagado", t.getPagado());
            dto.put("codigoVerificacion", "SS-" + productoId + "-" + t.getNumeroTicket() + "-" + usuario.getId());
            return dto;
        }).collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(result);
    }

    private void enviarComprobanteEmail(AppUsers usuario, Producto rifa, List<Integer> numerosTickets) {
        String codigo = "SS-" + rifa.getId() + "-" + usuario.getId();
        int totalTickets = numerosTickets.size();
        double total = totalTickets * (rifa.getPrecioTicket() != null ? rifa.getPrecioTicket().doubleValue() : 0);
        String asunto = "🎟️ Tu Comprobante de Tickets - SubastaShop";

        // Generar tickets HTML
        StringBuilder ticketsHtml = new StringBuilder();
        for (int num : numerosTickets) {
            ticketsHtml.append(
                "<div style='display:inline-block;background:linear-gradient(135deg,#6f42c1,#0d6efd);border-radius:12px;padding:15px 25px;margin:6px;text-align:center;'>" +
                "<p style='color:rgba(255,255,255,0.7);margin:0 0 2px;font-size:10px;text-transform:uppercase;letter-spacing:2px;'>TICKET</p>" +
                "<p style='color:#fff;margin:0;font-size:32px;font-weight:900;line-height:1;'>#" + num + "</p>" +
                "</div>"
            );
        }

        String html = "<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'>" +
            "<meta name='viewport' content='width=device-width, initial-scale=1.0'></head>" +
            "<body style='margin:0;padding:0;background:#0f0f1a;font-family:Arial,sans-serif;'>" +
            "<table width='100%' cellpadding='0' cellspacing='0'><tr><td align='center' style='padding:40px 20px;'>" +
            "<table width='560' cellpadding='0' cellspacing='0' style='background:#1a1a2e;border-radius:20px;overflow:hidden;box-shadow:0 20px 60px rgba(0,0,0,0.5);'>" +
            // Header
            "<tr><td style='background:linear-gradient(135deg,#6f42c1,#0d6efd);padding:30px;text-align:center;'>" +
            "<h1 style='color:#fff;margin:0;font-size:28px;font-weight:900;letter-spacing:2px;'>🎟️ SUBASTA<span style='color:#ffc107'>SHOP</span></h1>" +
            "<p style='color:rgba(255,255,255,0.8);margin:5px 0 0;font-size:14px;text-transform:uppercase;letter-spacing:3px;'>Comprobante Oficial de Tickets</p>" +
            "</td></tr>" +
            // Body
            "<tr><td style='padding:40px 30px;'>" +
            "<p style='color:#a0a0c0;margin:0 0 5px;font-size:13px;text-transform:uppercase;letter-spacing:2px;'>Hola,</p>" +
            "<h2 style='color:#ffffff;margin:0 0 25px;font-size:22px;'>" + usuario.getNombreCompleto() + "</h2>" +
            // Rifa info
            "<p style='color:#a0a0c0;margin:0 0 4px;font-size:11px;text-transform:uppercase;letter-spacing:2px;'>Rifa / Sorteo</p>" +
            "<h3 style='color:#ffffff;margin:0 0 20px;font-size:18px;font-weight:700;'>" + rifa.getNombre() + "</h3>" +
            // Tickets
            "<p style='color:#a0a0c0;margin:0 0 10px;font-size:11px;text-transform:uppercase;letter-spacing:2px;'>Tus Tickets (" + totalTickets + ")</p>" +
            "<div style='text-align:center;margin-bottom:25px;'>" + ticketsHtml + "</div>" +
            // Total
            "<div style='display:flex;justify-content:space-between;align-items:center;background:#16213e;border-radius:10px;padding:15px 20px;margin-bottom:20px;'>" +
            "<div>" +
            "<p style='color:#a0a0c0;margin:0 0 4px;font-size:11px;text-transform:uppercase;letter-spacing:1px;'>Total a Pagar</p>" +
            "<p style='color:#ffc107;margin:0;font-size:24px;font-weight:700;'>$" + String.format("%,.0f", total) + "</p>" +
            "</div>" +
            "<div style='text-align:right;'>" +
            "<p style='color:#a0a0c0;margin:0 0 4px;font-size:11px;text-transform:uppercase;letter-spacing:1px;'>Fecha</p>" +
            "<p style='color:#fff;margin:0;font-size:13px;'>" + java.time.LocalDate.now() + "</p>" +
            "</div></div>" +
            // Codigo
            "<div style='background:#0a0a14;border-radius:10px;padding:15px 20px;border:1px solid rgba(255,193,7,0.2);text-align:center;margin-bottom:20px;'>" +
            "<p style='color:#a0a0c0;margin:0 0 6px;font-size:11px;text-transform:uppercase;letter-spacing:2px;'>Código de Verificación</p>" +
            "<p style='color:#ffc107;margin:0;font-size:16px;font-weight:700;font-family:monospace;letter-spacing:3px;'>" + codigo + "</p>" +
            "</div>" +
            // Instrucciones
            "<div style='background:rgba(13,110,253,0.1);border-radius:10px;padding:15px;border-left:3px solid #0d6efd;'>" +
            "<p style='color:#a0a0c0;margin:0;font-size:13px;line-height:1.6;'>📌 <strong style='color:#fff;'>Instrucciones de Pago:</strong><br>" +
            "Para confirmar tu participación, deberás contactar al organizador de la tienda y realizar la transferencia según sus instrucciones. " +
            "Presenta este código de verificación ante cualquier consulta.</p>" +
            "</div>" +
            "</td></tr>" +
            // Footer
            "<tr><td style='background:#0a0a14;padding:20px 30px;text-align:center;border-top:1px solid rgba(255,255,255,0.05);'>" +
            "<p style='color:#606080;margin:0;font-size:12px;'>Este comprobante fue generado automáticamente por " +
            "<strong style='color:#6f42c1;'>SubastaShop</strong>. No requiere firma.</p>" +
            "</td></tr>" +
            "</table></td></tr></table></body></html>";

        rifaEmailService.enviarCorreo(usuario.getEmail(), asunto, html);
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
            dto.put("numeroTicket", g.getTicketGanador() != null ? g.getTicketGanador().getNumeroTicket() : 0);
            
            String emailOriginal = g.getTicketGanador() != null ? g.getTicketGanador().getComprador().getEmail() : "p***@mail.com";
            dto.put("comprador", enmascararEmail(emailOriginal));
            return dto;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(respuesta);
    }

    private String enmascararEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 1) return email;
        return name.charAt(0) + "***@" + domain;
    }
}