package com.subastashop.backend.controllers;

import com.subastashop.backend.dto.GameInitDTO;
import com.subastashop.backend.dto.GameResultDTO;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.services.ContestService;
import com.subastashop.backend.services.GameTokenService;
import com.subastashop.backend.services.GameValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/games")
public class GameController {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private GameTokenService gameTokenService;

    @Autowired
    private GameValidationService gameValidationService;

    @Autowired
    private ContestService contestService;

    /**
     * Endpoint invocado antes de que la partida empiece o cuenta regresiva.
     * Retorna el token criptográfico y el timestamp.
     */
    @PostMapping("/start/{contestId}")
    public ResponseEntity<GameInitDTO> startGame(@PathVariable Integer contestId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Producto contest = productoRepository.findById(contestId)
                .orElseThrow(() -> new RuntimeException("Concurso no encontrado"));

        if (!"DISPONIBLE".equals(contest.getEstado())) {
            return ResponseEntity.badRequest().build();
        }

        long serverTimestamp = System.currentTimeMillis();
        String token = gameTokenService.generarStartToken(user.getId(), contestId, serverTimestamp);

        return ResponseEntity.ok(GameInitDTO.builder()
                .token(token)
                .serverTimeMs(serverTimestamp)
                .build());
    }

    /**
     * Endpoint final para enviar los resultados validados.
     */
    @PostMapping("/submit/{contestId}")
    public ResponseEntity<?> submitGameResult(@PathVariable Integer contestId, @RequestBody GameResultDTO request) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            AppUsers user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            Producto contest = productoRepository.findById(contestId)
                    .orElseThrow(() -> new RuntimeException("Concurso no encontrado"));

            // 1. Extraer timestamp del servidor desde el token criptográfico
            long serverStartMs = gameTokenService.extraerTimestampYValidar(request.getToken(), user.getId(), contestId);
            
            if (serverStartMs == -1) {
                return ResponseEntity.status(403).body(Map.of("error", "Token de juego inválido o corrupto. Posible trampa."));
            }

            // 2. Comprobar tiempo físico transcurrido vs reportado
            long currentServerMs = System.currentTimeMillis();
            long actualElapsedTimeMs = currentServerMs - serverStartMs;

            // 3. Validar con físicas matemáticas
            boolean isHonestResult = gameValidationService.isResultValid(
                    contest, 
                    request.getTimeMs(), 
                    actualElapsedTimeMs, 
                    request.getMoves()
            );

            if (!isHonestResult) {
                return ResponseEntity.status(403).body(Map.of("error", "Resultado rechazado. La validación matemática física detectó anomalías (Speedhack/Script)."));
            }

            // 4. Registrar de manera oficial el puntaje (aprovechamos la lógica existente del ContestService)
            contestService.submitPuntaje(contestId, user.getId(), request.getTimeMs());

            return ResponseEntity.ok(Map.of(
                    "message", "Puntaje registrado exitosamente bajo alta seguridad",
                    "time", request.getTimeMs()
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
