package com.subastashop.backend.controllers;

import com.mercadopago.exceptions.MPApiException;
import com.subastashop.backend.services.MercadoPagoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
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
    @PostMapping({"/create-subscription", "/create_subscription"})
    public ResponseEntity<Map<String, String>> createSubscription(Authentication authentication) {
        try {
            String initPoint = mpService.createRecurringSubscription(authentication.getName());
            return ResponseEntity.ok(Map.of("id", initPoint));
        } catch (Exception e) {
            log.error("Error creando suscripción recurrente", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Procesa la suscripción directamente usando un token de tarjeta (Card Token).
     * Evita redirecciones externas complejas.
     */
    @PostMapping("/subscribe-with-token")
    public ResponseEntity<Map<String, Object>> subscribeWithToken(Authentication authentication, @RequestBody Map<String, String> body) {
        try {
            String cardTokenId = body.get("token");
            if (cardTokenId == null || cardTokenId.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Token de tarjeta es obligatorio"));
            }
            
            Map<String, Object> result = mpService.subscribeWithCardToken(authentication.getName(), cardTokenId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error en suscripción con token", e);
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
     * Cancela la suscripción del usuario autenticado.
     */
    @PostMapping("/sync-status")
    public ResponseEntity<?> syncStatus(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        boolean updated = mpService.syncSubscriptionStatus(principal.getName());
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping("/cancel-subscription")
    public ResponseEntity<?> cancelSubscription(Authentication authentication) {
        try {
            String email = authentication.getName();
            boolean success = mpService.cancelSubscription(email);
            return ResponseEntity.ok(Map.of("success", success, "message", "Suscripción cancelada correctamente"));
        } catch (Exception e) {
            log.error("Error al cancelar suscripción: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
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
