package com.subastashop.backend.services;

import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.repositories.AppUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Slf4j
@Service
public class StripeService {

    @Value("${stripe.secret.key:sk_test_placeholder}")
    private String secretKey;

    @Value("${stripe.price.id:price_placeholder}")
    private String subscriptionPriceId;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    private final AppUserRepository userRepository;

    public StripeService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }

    /**
     * Crea una sesión de Checkout para suscribirse a un plan mensual.
     */
    public String createSubscriptionSession(String userEmail) throws Exception {
        AppUsers user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(frontendUrl + "/admin/configuracion?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendUrl + "/admin/configuracion")
                .setCustomerEmail(userEmail)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPrice(subscriptionPriceId)
                                .setQuantity(1L)
                                .build()
                )
                .putMetadata("userId", user.getId().toString())
                .build();

        Session session = Session.create(params);
        return session.getUrl();
    }

    /**
     * Procesa el éxito de un pago (normalmente llamado desde un Webhook).
     */
    public void fulfillSubscription(String sessionId) throws Exception {
        Session session = Session.retrieve(sessionId);
        String userId = session.getMetadata().get("userId");
        String subscriptionId = session.getSubscription();
        String customerId = session.getCustomer();

        AppUsers user = userRepository.findById(Integer.parseInt(userId))
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado tras pago"));

        user.setSuscripcionActiva(true);
        user.setStripeSubscriptionId(subscriptionId);
        user.setStripeCustomerId(customerId);
        
        // Al pagar, si era COMPRADOR por mora, lo devolvemos a ADMIN
        if (com.subastashop.backend.models.Role.ROLE_COMPRADOR.equals(user.getRol())) {
            user.setRol(com.subastashop.backend.models.Role.ROLE_ADMIN);
        }

        userRepository.save(user);
        log.info("Suscripción activada exitosamente para el usuario: {}", user.getEmail());
    }
}
