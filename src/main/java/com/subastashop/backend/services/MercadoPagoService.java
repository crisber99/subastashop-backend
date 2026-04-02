package com.subastashop.backend.services;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Role;
import com.subastashop.backend.repositories.AppUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MercadoPagoService {

    @Value("${mercadopago.access.token}")
    private String accessToken;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${mercadopago.notification.url:http://localhost:8080/api/mercadopago/webhook}")
    private String notificationUrl;

    private final AppUserRepository userRepository;

    public MercadoPagoService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    public String createSubscriptionPreference(String userEmail) throws Exception {
        AppUsers user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        PreferenceClient client = new PreferenceClient();

        List<PreferenceItemRequest> items = new ArrayList<>();
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .id("subs-premium-4990")
                .title("Suscripción Pro SubastaShop")
                .description("Acceso Premium mensual a SubastaShop")
                .quantity(1)
                .unitPrice(new BigDecimal("4990")) 
                .currencyId("CLP")
                .build();
        items.add(item);

        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(frontendUrl + "/admin/configuracion?status=success")
                .pending(frontendUrl + "/admin/configuracion?status=pending")
                .failure(frontendUrl + "/admin/configuracion?status=failure")
                .build();

        PreferenceRequest request = PreferenceRequest.builder()
                .items(items)
                .backUrls(backUrls)
                .autoReturn("approved")
                .statementDescriptor("SUBASTASHOP")
                .notificationUrl(notificationUrl)
                .externalReference(user.getId().toString())
                .build();

        Preference preference = client.create(request);
        return preference.getInitPoint(); // Devolvemos el link universal (detecta sandbox/prod automáticamente)
    }

    public void processPaymentNotification(String paymentId) {
        log.info("🔔 Notificación de Mercado Pago recibida: ID de Pago {}", paymentId);
        try {
            PaymentClient client = new PaymentClient();
            Payment payment = client.get(Long.parseLong(paymentId));
            
            if ("approved".equalsIgnoreCase(payment.getStatus())) {
                String userIdStr = payment.getExternalReference();
                if (userIdStr != null) {
                    Integer userId = Integer.parseInt(userIdStr);
                    confirmSubscription(userId);
                }
            } else {
                log.info("⚠️ El pago {} no está aprobado (Status: {})", paymentId, payment.getStatus());
            }
        } catch (Exception e) {
            log.error("❌ Error procesando notificación de Mercado Pago", e);
        }
    }

    /**
     * SIMULACIÓN: Activa manualmente la suscripción de un usuario.
     */
    public void confirmSubscription(Integer userId) {
        AppUsers user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado (ID: " + userId + ")"));
        
        user.setSuscripcionActiva(true);
        user.setRol(Role.ROLE_ADMIN); // 🛠️ OTORGAMOS PERMISOS DE ADMINISTRADOR (PRO)
        userRepository.save(user);
        log.info("✅ Suscripción y rol ADMIN activados para el usuario: {}", user.getEmail());
    }
}
