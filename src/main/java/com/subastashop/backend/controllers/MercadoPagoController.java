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
    public ResponseEntity<Map<String, String>> createPreference(Authentication authentication, @RequestBody(required = false) Map<String, Object> body) {
        try {
            Integer months = 1;
            if (body != null && body.containsKey("months")) {
                months = Integer.parseInt(body.get("months").toString());
            }
            
            String initPoint = mpService.createSubscriptionPreference(authentication.getName(), months);
            return ResponseEntity.ok(Map.of("id", initPoint)); 
        } catch (MPApiException e) {
            log.error("API Error: {}", e.getApiResponse().getContent());
            return ResponseEntity.badRequest().body(Map.of("error", "Error en la pasarela"));
        } catch (Exception e) {
            log.error("Error preference", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Crea una suscripción automática (Pre-approval).
     */
    @PostMapping("/create-subscription")
    public ResponseEntity<Map<String, String>> createSubscription(Authentication authentication) {
        try {
            String initPoint = mpService.createRecurringSubscription(authentication.getName());
            return ResponseEntity.ok(Map.of("id", initPoint));
        } catch (Exception e) {
            log.error("Error creando suscripción recurrente", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Webhook recibido de Mercado Pago: {}", payload);
        
        try {
            String type = String.valueOf(payload.get("type"));
            String action = String.valueOf(payload.get("action"));
            
            // Caso 1: Notificación de Pago (Manual o Automática)
            if ("payment".equals(type) || payload.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) payload.get("data");
                if (data != null && data.containsKey("id")) {
                    String paymentId = String.valueOf(data.get("id"));
                    mpService.processPaymentNotification(paymentId);
                }
            } 
            // Caso 2: Notificación de Suscripción (Pre-approval)
            else if ("subscription_preapproval".equals(type) || "preapproval".equals(type)) {
                Map<String, Object> data = (Map<String, Object>) payload.get("data");
                if (data != null && data.containsKey("id")) {
                    String preapprovalId = String.valueOf(data.get("id"));
                    mpService.processSubscriptionNotification(preapprovalId);
                }
            }
        } catch (Exception e) {
            log.error("Error al procesar el webhook: {}", e.getMessage());
        }
        
        return ResponseEntity.ok("Received");
    }

    /**
     * Endpoint de prueba para simular éxito de pago.
     */
    @PostMapping("/test/simulate-success/{userId}")
    public ResponseEntity<Map<String, String>> simulateSuccess(@PathVariable("userId") Integer userId) {
        try {
            // Simulación manual de 1 mes
            mpService.confirmSubscription(userId, 1, false); 
            return ResponseEntity.ok(Map.of("message", "Suscripción activada con éxito (Simulación 1 mes)"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
