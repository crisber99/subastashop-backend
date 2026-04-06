package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.SubastaSniper;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.services.SubastaSniperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/snipers")
public class SubastaSniperController {

    @Autowired
    private SubastaSniperService sniperService;

    @Autowired
    private com.subastashop.backend.repositories.SubastaSniperRepository sniperRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @PostMapping("/configurar")
    public ResponseEntity<?> configurarSniper(@RequestBody Map<String, Object> payload) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            AppUsers usuario = (AppUsers) auth.getPrincipal();

            // Validación PRO
            if (!usuario.isSuscripcionActiva()) {
                return ResponseEntity.status(403).body("Esta funcionalidad es exclusiva para usuarios con suscripción PRO activa.");
            }

            Integer productoId = (Integer) payload.get("productoId");
            BigDecimal montoMaximo = new BigDecimal(payload.get("montoMaximo").toString());

            Producto producto = productoRepository.findById(productoId)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            SubastaSniper sniper = sniperService.configurarSniper(productoId, usuario.getId(), montoMaximo);
            sniper.setProducto(producto); // Asegurar para la respuesta
            
            return ResponseEntity.ok(sniper);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/producto/{id}")
    public ResponseEntity<?> obtenerSniper(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AppUsers usuario = (AppUsers) auth.getPrincipal();
        
        return ResponseEntity.ok(sniperRepository.findByProductoIdAndUsuarioIdAndActivoTrue(id, usuario.getId()));
    }

    @PostMapping("/desactivar/{productoId}")
    public ResponseEntity<?> desactivarSniper(@PathVariable Integer productoId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            AppUsers usuario = (AppUsers) auth.getPrincipal();

            java.util.Optional<SubastaSniper> sniper = sniperRepository.findByProductoIdAndUsuarioIdAndActivoTrue(productoId, usuario.getId());
            sniper.ifPresent(s -> {
                s.setActivo(false);
                sniperRepository.save(s);
            });

            return ResponseEntity.ok(Map.of("message", "Sniper desactivado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
