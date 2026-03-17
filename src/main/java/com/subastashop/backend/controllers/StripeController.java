package com.subastashop.backend.controllers;

import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.subastashop.backend.services.StripeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/stripe")
public class StripeController {

    private final StripeService stripeService;

    @Value("${stripe.webhook.secret:whsec_placeholder}")
    private String webhookSecret;

    public StripeController(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    /**
     * Endpoint para que el ADMIN inicie su proceso de suscripción.
     */
    @PostMapping("/create-checkout-session")
    public ResponseEntity<Map<String, String>> createCheckoutSession(Authentication authentication) {
        try {
            String url = stripeService.createSubscriptionSession(authentication.getName());
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            log.error("Error creando sesión de Stripe", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Endpoint de Webhook para Stripe. 
     * stripe-cli forward --target-url localhost:8080/api/stripe/webhook
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            switch (event.getType()) {
                case "checkout.session.completed":
                    Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
                    if (session != null) {
                        stripeService.fulfillSubscription(session.getId());
                    }
                    break;
                case "invoice.payment_failed":
                    log.warn("Pago fallido detectado vía webhook.");
                    // Aquí podrías implementar el envío de un correo al usuario
                    break;
                default:
                    log.info("Evento de Stripe no procesado: {}", event.getType());
            }

            return ResponseEntity.ok("Success");
        } catch (Exception e) {
            log.error("Error en Webhook de Stripe", e);
            return ResponseEntity.badRequest().body("Webhook Error");
        }
    }
}
