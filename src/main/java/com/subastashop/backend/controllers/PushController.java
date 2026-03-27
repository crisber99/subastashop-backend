package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.SuscripcionPush;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.SuscripcionPushRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushController {

    @Autowired
    private SuscripcionPushRepository suscripcionRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    private AppUsers obtenerUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email.equals("anonymousUser")) return null;
        return appUserRepository.findByEmail(email).orElse(null);
    }

    @PostMapping("/suscribir")
    public ResponseEntity<?> suscribir(@RequestBody Map<String, Object> req) {
        String endpoint = (String) req.get("endpoint");
        Map<String, String> keys = (Map<String, String>) req.get("keys");
        
        if (keys == null || !keys.containsKey("p256dh") || !keys.containsKey("auth")) {
            return ResponseEntity.badRequest().body("Llaves criptográficas inválidas");
        }

        SuscripcionPush sub = suscripcionRepository.findByEndpoint(endpoint).orElse(new SuscripcionPush());
        
        sub.setEndpoint(endpoint);
        sub.setP256dh(keys.get("p256dh"));
        sub.setAuth(keys.get("auth"));
        sub.setUsuario(obtenerUsuarioAutenticado());

        suscripcionRepository.save(sub);
        
        return ResponseEntity.ok(Map.of("message", "Suscripción guardada exitosamente"));
    }

    @PostMapping("/desuscribir")
    public ResponseEntity<?> desuscribir(@RequestBody Map<String, String> req) {
        String endpoint = req.get("endpoint");
        suscripcionRepository.findByEndpoint(endpoint).ifPresent(sub -> {
            suscripcionRepository.delete(sub);
        });
        return ResponseEntity.ok(Map.of("message", "Desuscrito"));
    }
}
