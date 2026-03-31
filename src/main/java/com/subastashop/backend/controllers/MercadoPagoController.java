package com.subastashop.backend.controllers;

import com.mercadopago.exceptions.MPApiException;
import com.subastashop.backend.services.MercadoPagoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mercadopago")
public class MercadoPagoController {

    private final MercadoPagoService mpService;

    public MercadoPagoController(MercadoPagoService mpService) {
        this.mpService = mpService;
    }

    @PostMapping("/create-preference")
    public ResponseEntity<Map<String, String>> createPreference(Authentication authentication) {
        try {
            String initPoint = mpService.createSubscriptionPreference(authentication.getName());
            return ResponseEntity.ok(Map.of("id", initPoint)); 
        } catch (MPApiException e) {
            log.error("Error de API de Mercado Pago: {}", e.getApiResponse().getContent());
            return ResponseEntity.badRequest().body(Map.of("error", "Error en la pasarela de pago"));
        } catch (Exception e) {
            log.error("Error creando preferencia de Mercado Pago", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Webhook recibido de Mercado Pago: {}", payload);
        // Aquí se procesaría la notificación IPN/Webhook para confirmar el pago
        return ResponseEntity.ok("Received");
    }

    /**
     * Endpoint de prueba para simular éxito de pago.
     */
    @PostMapping("/test/simulate-success/{userId}")
    public ResponseEntity<Map<String, String>> simulateSuccess(@PathVariable("userId") Integer userId) {
        try {
            mpService.confirmSubscription(userId);
            return ResponseEntity.ok(Map.of("message", "Suscripción activada con éxito (Simulación)"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
