package com.subastashop.backend.services;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.resources.preference.Preference;
import com.subastashop.backend.models.AppUsers;
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
                .externalReference(user.getId().toString())
                .build();

        Preference preference = client.create(request);
        return preference.getInitPoint(); // Devolvemos el link universal (detecta sandbox/prod automáticamente)
    }

    public void processPaymentNotification(String paymentId) {
        // Lógica para consultar el pago en MP y activar la suscripción
        log.info("Procesando notificación de pago MP: {}", paymentId);
        // Aquí iría la llamada a PaymentClient.get(paymentId) para obtener el status y external_reference
    }

    /**
     * SIMULACIÓN: Activa manualmente la suscripción de un usuario.
     */
    public void confirmSubscription(Integer userId) {
        AppUsers user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado (ID: " + userId + ")"));
        
        user.setSuscripcionActiva(true);
        userRepository.save(user);
        log.info("✅ Suscripción activada manualmente para el usuario: {}", user.getEmail());
    }
}
