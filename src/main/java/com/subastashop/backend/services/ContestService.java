package com.subastashop.backend.services;

import com.subastashop.backend.models.ContestWinner;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.Participation;
import com.subastashop.backend.repositories.ContestWinnerRepository;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.ParticipationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.context.ApplicationContext;

@Slf4j
@Service
public class ContestService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private ContestWinnerRepository ganadorRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Registra el puntaje de un participante tras completar el Memorice.
     */
    @Transactional
    public void submitPuntaje(Integer contestId, Integer userId, Long durationMs) {
        if (durationMs < 3000) {
            throw new RuntimeException("Tiempo de resolución no válido (posible bot).");
        }

        Participation participacion = participationRepository.findByContestIdAndParticipantId(contestId, userId)
                .stream().findFirst()
                .orElseThrow(() -> new RuntimeException("No estás inscrito en este concurso."));

        if (participacion.getDurationMs() != null) {
            throw new RuntimeException("Ya has registrado un puntaje para este concurso.");
        }

        participacion.setDurationMs(durationMs);
        participacion.setCompletionTimestamp(LocalDateTime.now());
        participationRepository.save(participacion);

        // 🏆 Broadcast Top 5 en tiempo real
        notificarPodioActualizado(contestId);
    }

    private void notificarPodioActualizado(Integer contestId) {
        List<Participation> participaciones = participationRepository
                .findByContestIdAndPaidTrueOrderByDurationMsAscCreatedAtAsc(contestId);
        
        List<Map<String, Object>> top5 = new ArrayList<>();
        int rank = 1;
        for (Participation p : participaciones) {
            if (p.getDurationMs() != null) {
                Map<String, Object> dto = new HashMap<>();
                dto.put("rank", rank++);
                dto.put("durationMs", p.getDurationMs());
                dto.put("participant", enmascararEmail(p.getParticipant().getEmail()));
                top5.add(dto);
                if (top5.size() >= 5) break;
            }
        }

        messagingTemplate.convertAndSend("/topic/concurso/" + contestId + "/podio", top5);
    }

    /**
     * Inicia la determinación de ganadores basada en habilidad.
     */
    public void lanzarConcurso(Integer productoId) {
        Producto contest = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Concurso no encontrado"));

        if ("FINALIZADA".equals(contest.getEstado())) {
            throw new RuntimeException("El concurso ya finalizó.");
        }

        // Delegar al proceso asíncrono para el "show" UI
        ContestService proxy = applicationContext.getBean(ContestService.class);
        proxy.procesarGanadoresAsync(productoId);
    }

    @Async
    @Transactional
    public void procesarGanadoresAsync(Integer productoId) {
        try {
            log.info("Calculando ganadores para concurso ID: {}", productoId);

            // Notify UI
            messagingTemplate.convertAndSend("/topic/rifa/" + productoId, 
                Map.of("status", "PREPARANDO", "productoId", productoId, "mensaje", "Calculando resultados finales..."));

            Thread.sleep(3000);

            Producto contest = productoRepository.findById(productoId).get();
            
            // Lógica Central: Ordenar por menor tiempo (Habilidad) y luego antigüedad
            List<Participation> participaciones = participationRepository
                .findByContestIdAndPaidTrueOrderByDurationMsAscCreatedAtAsc(productoId);

            int cantidadGanadores = contest.getCantidadGanadores();
            List<Map<String, Object>> listaGanadoresDTO = new ArrayList<>();

            for (int i = 0; i < cantidadGanadores && i < participaciones.size(); i++) {
                Participation p = participaciones.get(i);

                ContestWinner ganador = new ContestWinner();
                ganador.setContest(contest);
                ganador.setWinningParticipation(p);
                ganador.setRank(i + 1);
                ganador.setWinningDate(LocalDateTime.now());
                ganadorRepository.save(ganador);

                Map<String, Object> dto = new HashMap<>();
                dto.put("puesto", i + 1);
                dto.put("ms", p.getDurationMs());
                dto.put("comprador", enmascararEmail(p.getParticipant().getEmail()));
                listaGanadoresDTO.add(dto);

                notificarGanadorPorEmail(p, contest, i + 1);
            }

            contest.setEstado("FINALIZADA");
            productoRepository.save(contest);

            messagingTemplate.convertAndSend("/topic/rifa/" + productoId, 
                Map.of("status", "FINALIZADO", "productoId", productoId, "ganadores", listaGanadoresDTO));

        } catch (Exception e) {
            log.error("Error procesando ganadores: {}", e.getMessage());
        }
    }

    private void notificarGanadorPorEmail(Participation p, Producto contest, int puesto) {
        String asunto = "¡Felicidades! Eres ganador por tu habilidad en SubastaShop 🎉";
        String mensaje = String.format(
            "Hola %s,<br><br>¡Tu participación ha resultado ganadora (Puesto %d) con un tiempo de %d ms en '%s'!<br><br>" +
            "Revisa tus órdenes para coordinar el premio.<br><br>Saludos, el equipo de SubastaShop.",
            p.getParticipant().getNombreCompleto(),
            puesto,
            p.getDurationMs(),
            contest.getNombre()
        );
        emailService.enviarCorreo(p.getParticipant().getEmail(), asunto, mensaje);
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
