package com.subastashop.backend.controllers;

import com.subastashop.backend.models.*;
import com.subastashop.backend.repositories.*;
import com.subastashop.backend.services.ContestService;
import com.subastashop.backend.services.AzureBlobService;
import com.subastashop.backend.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/contests")
@CrossOrigin(origins = "*")
public class ContestController {

    @Autowired
    private ContestService contestService;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private ContestWinnerRepository ganadorRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private AzureBlobService azureBlobService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private UserLegalAcceptanceRepository legalRepository;

    /**
     * Obtener concursos activos
     */
    @GetMapping
    public ResponseEntity<List<Producto>> getContests() {
        return ResponseEntity.ok(productoRepository.findByTipoVenta("RIFA"));
    }

    /**
     * Inscribirse en un concurso (Antes "Comprar Ticket")
     */
    @PostMapping("/{contestId}/join")
    public ResponseEntity<?> joinContest(@PathVariable Integer contestId) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            AppUsers user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            // ⚖️ VALIDACIÓN LEGAL: ¿Ha aceptado los términos vigentes?
            if (legalRepository.findFirstByUserIdOrderByAcceptanceTimestampDesc(user.getId()).isEmpty()) {
                return ResponseEntity.status(403).body("Debes aceptar los Términos y Condiciones legales antes de participar.");
            }

            Producto contest = productoRepository.findById(contestId)
                    .orElseThrow(() -> new RuntimeException("Concurso no encontrado"));

            if (!"DISPONIBLE".equals(contest.getEstado())) {
                return ResponseEntity.badRequest().body("Este concurso no está disponible.");
            }

            Participation p = new Participation();
            p.setContest(contest);
            p.setParticipant(user);
            p.setCreatedAt(LocalDateTime.now());
            participationRepository.save(p);

            return ResponseEntity.ok(p);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Enviar puntaje de Memorice (Habilidad)
     */
    @PostMapping("/{id}/submit-score")
    public ResponseEntity<?> submitScore(@PathVariable Integer id, @RequestBody Map<String, Object> payload) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            AppUsers user = userRepository.findByEmail(email).orElseThrow();
            
            Long totalTimeMs = Long.valueOf(payload.get("totalTimeMs").toString());
            
            contestService.submitPuntaje(id, user.getId(), totalTimeMs);
            return ResponseEntity.ok(Map.of("message", "Puntaje registrado exitosamente", "time", totalTimeMs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Subir comprobante de pago
     */
    @PostMapping("/{participationId}/upload-payment")
    public ResponseEntity<?> uploadPayment(@PathVariable Long participationId, @RequestParam("file") MultipartFile file) {
        try {
            Participation p = participationRepository.findById(participationId).orElseThrow();
            String url = azureBlobService.subirImagen(file);
            p.setPaymentSlipUrl(url);
            participationRepository.save(p);
            return ResponseEntity.ok(p);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Obtener ganadores del concurso
     */
    @GetMapping("/{contestId}/winners")
    public ResponseEntity<List<Map<String, Object>>> getWinners(@PathVariable Integer contestId) {
        List<ContestWinner> ganadores = ganadorRepository.findByContestId(contestId);
        List<Map<String, Object>> respuesta = ganadores.stream().map(g -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("rank", g.getRank());
            dto.put("durationMs", g.getWinningParticipation().getDurationMs());
            dto.put("participant", enmascararEmail(g.getWinningParticipation().getParticipant().getEmail()));
            return dto;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Obtener mis participaciones
     */
    @GetMapping("/my-participations")
    public ResponseEntity<List<Participation>> getMyParticipations() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            AppUsers user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            List<Participation> participaciones = participationRepository.findByParticipantId(user.getId());
            return ResponseEntity.ok(participaciones);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Obtener todos los participantes (Solo para el dueño/admin)
     */
    @GetMapping("/{contestId}/participants")
    public ResponseEntity<List<Participation>> getContestParticipants(@PathVariable Integer contestId) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            AppUsers user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            Producto contest = productoRepository.findById(contestId).orElseThrow(() -> new RuntimeException("Concurso no encontrado"));
            
            if (user.getRol() != Role.ROLE_SUPER_ADMIN && 
               (user.getTienda() == null || contest.getTienda() == null || !user.getTienda().getId().equals(contest.getTienda().getId()))) {
                return ResponseEntity.status(403).body(null); // No es dueño ni admin
            }
            
            List<Participation> participaciones = participationRepository.findByContestId(contestId);
            return ResponseEntity.ok(participaciones);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Lanzar determinación de ganadores (Admin)
     */
    @PostMapping("/{contestId}/lanzar")
    public ResponseEntity<?> lanzarConcurso(@PathVariable Integer contestId) {
        try {
            contestService.lanzarConcurso(contestId);
            return ResponseEntity.ok(Map.of("mensaje", "Proceso de determinación de ganadores iniciado."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
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