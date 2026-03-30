package com.subastashop.backend.services;

import com.subastashop.backend.models.GanadorRifa;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.TicketRifa;
import com.subastashop.backend.repositories.GanadorRifaRepository;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.TicketRifaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class RifaService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private TicketRifaRepository ticketRepository;

    @Autowired
    private GanadorRifaRepository ganadorRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Inicia el proceso de sorteo. Valida el estado y delega el "show" a un proceso asíncrono.
     */
    public void lanzarRifa(Integer productoId) {
        Producto rifa = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Rifa no encontrada"));

        if ("FINALIZADA".equals(rifa.getEstado())) {
            throw new RuntimeException("El sorteo ya fue realizado.");
        }

        List<TicketRifa> todosLosTickets = ticketRepository.findByRifaId(productoId);
        if (todosLosTickets.size() < rifa.getCantidadNumeros()) {
            throw new RuntimeException("Aún faltan números por vender.");
        }

        // Iniciamos el proceso asíncrono para no bloquear al Admin
        this.procesarSorteoAsync(productoId);
    }

    @Async
    @Transactional
    public void procesarSorteoAsync(Integer productoId) {
        try {
            log.info("Iniciando show asíncrono para la rifa ID: {}", productoId);

            // 1. Notificar inicio de preparación (Teatro)
            messagingTemplate.convertAndSend("/topic/rifa/" + productoId, 
                Map.of("status", "PREPARANDO", "mensaje", "Iniciando sorteo en vivo..."));

            // 2. Pausa dramática de 5 segundos
            Thread.sleep(5000);

            Producto rifa = productoRepository.findById(productoId).get();
            List<TicketRifa> todosLosTickets = ticketRepository.findByRifaId(productoId);

            // 3. Realizar el sorteo
            Collections.shuffle(todosLosTickets);

            int cantidadGanadores = rifa.getCantidadGanadores();
            List<Map<String, Object>> listaGanadoresDTO = new ArrayList<>();

            for (int i = 0; i < cantidadGanadores && i < todosLosTickets.size(); i++) {
                TicketRifa ticketGanador = todosLosTickets.get(i);

                // Guardar persistencia
                GanadorRifa ganador = new GanadorRifa();
                ganador.setRifa(rifa);
                ganador.setTicketGanador(ticketGanador);
                ganador.setPuesto(i + 1);
                ganador.setFechaGanador(LocalDateTime.now());
                ganadorRepository.save(ganador);

                // DTO para notificación
                Map<String, Object> dto = new HashMap<>();
                dto.put("puesto", i + 1);
                dto.put("numeroTicket", ticketGanador.getNumeroTicket());
                dto.put("comprador", ticketGanador.getComprador().getEmail());
                listaGanadoresDTO.add(dto);

                // Enviar correo (Async por definición de EmailService ahora)
                notificarGanadorPorEmail(ticketGanador, rifa, i + 1);
            }

            // 4. Finalizar Rifa
            rifa.setEstado("FINALIZADA");
            productoRepository.save(rifa);

            // 5. Notificar Resultados Finales
            messagingTemplate.convertAndSend("/topic/rifa/" + productoId, 
                Map.of("status", "FINALIZADO", "ganadores", listaGanadoresDTO));

            log.info("Sorteo finalizado exitosamente para la rifa ID: {}", productoId);

        } catch (InterruptedException e) {
            log.error("Error en la pausa del sorteo: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error procesando sorteo asíncrono: {}", e.getMessage());
        }
    }

    private void notificarGanadorPorEmail(TicketRifa ticket, Producto rifa, int puesto) {
        String asunto = "¡Felicidades! Eres ganador en SubastaShop 🎉";
        String mensaje = String.format(
            "Hola %s,<br><br>¡Tu ticket <b>#%d</b> ha resultado ganador (Puesto %d) en la rifa de '%s'!<br><br>" +
            "Revisa tus órdenes para coordinar el premio.<br><br>Saludos, el equipo de SubastaShop.",
            ticket.getComprador().getNombreCompleto(),
            ticket.getNumeroTicket(),
            puesto,
            rifa.getNombre()
        );
        emailService.enviarCorreo(ticket.getComprador().getEmail(), asunto, mensaje);
    }
}
