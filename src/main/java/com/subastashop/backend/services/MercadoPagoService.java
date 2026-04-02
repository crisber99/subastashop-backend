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
import java.time.LocalDateTime;
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
    private final EmailService emailService;

    public MercadoPagoService(AppUserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    @PostConstruct
    public void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    public String createSubscriptionPreference(String userEmail, Integer months) throws Exception {
        AppUsers user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        PreferenceClient client = new PreferenceClient();

        // --- LÓGICA DE PRECIOS DINÁMICOS ---
        BigDecimal unitPrice;
        String title;

        if (months == 1) {
            // Promoción: $4.990 para los primeros 100 usuarios PRO
            long totalProUsers = userRepository.countByRol(Role.ROLE_ADMIN);
            if (totalProUsers < 100) {
                unitPrice = new BigDecimal("4990");
                title = "Oferta Lanzamiento: 1 Mes Pro";
            } else {
                unitPrice = new BigDecimal("9990");
                title = "SuscripciónMensual Pro";
            }
        } else if (months == 3) {
            unitPrice = new BigDecimal("26970"); // 10% dcto aprox
            title = "Plan Trimestral Pro (3 Meses)";
        } else if (months == 6) {
            unitPrice = new BigDecimal("50940"); // 15% dcto aprox
            title = "Plan Semestral Pro (6 Meses)";
        } else if (months == 12) {
            unitPrice = new BigDecimal("99900"); // 2 meses gratis
            title = "Plan Anual Pro (12 Meses)";
        } else {
            // Default x mes estándar
            unitPrice = new BigDecimal("9990").multiply(new BigDecimal(months));
            title = "Suscripción Pro - " + months + " meses";
        }

        List<PreferenceItemRequest> items = new ArrayList<>();
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .id("subs-premium-" + months)
                .title(title)
                .description("Acceso Premium a SubastaShop por " + months + (months == 1 ? " mes" : " meses"))
                .quantity(1)
                .unitPrice(unitPrice) 
                .currencyId("CLP")
                .build();
        items.add(item);

        // Determinar URL de retorno según el rol
        // ROLE_COMPRADOR -> Home ("/")
        // Otros -> Admin ("admin/configuracion")
        String redirectPath = (user.getRol() == Role.ROLE_COMPRADOR) ? "/" : "/admin/configuracion";

        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(frontendUrl + redirectPath + "?status=success")
                .pending(frontendUrl + redirectPath + "?status=pending")
                .failure(frontendUrl + redirectPath + "?status=failure")
                .build();

        PreferenceRequest request = PreferenceRequest.builder()
                .items(items)
                .backUrls(backUrls)
                .autoReturn("approved")
                .statementDescriptor("SUBASTASHOP")
                .notificationUrl(notificationUrl)
                .externalReference(user.getId().toString() + ":" + months) // Guardamos ID y MESES
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
                String externalRef = payment.getExternalReference();
                if (externalRef != null) {
                    // El formato es "userId:months"
                    String[] parts = externalRef.split(":");
                    Integer userId = Integer.parseInt(parts[0]);
                    Integer months = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                    
                    confirmSubscription(userId, months);
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
    public void confirmSubscription(Integer userId, Integer months) {
        AppUsers user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado (ID: " + userId + ")"));
        
        // Actualizar Fechas
        LocalDateTime hoy = LocalDateTime.now();
        LocalDateTime fechaBase = (user.getFechaVencimientoSuscripcion() != null && user.getFechaVencimientoSuscripcion().isAfter(hoy)) 
                ? user.getFechaVencimientoSuscripcion() 
                : hoy;
        
        user.setFechaVencimientoSuscripcion(fechaBase.plusMonths(months));
        user.setSuscripcionActiva(true);
        user.setRol(Role.ROLE_ADMIN); // 🛠️ OTORGAMOS PERMISOS DE ADMINISTRADOR (PRO)
        userRepository.save(user);

        log.info("✅ Suscripción y rol ADMIN activados por {} meses para el usuario: {}", months, user.getEmail());

        // --- ENVIAR EMAIL DE BIENVENIDA ---
        enviarEmailBienvenidaPro(user, months);
    }

    private void enviarEmailBienvenidaPro(AppUsers user, Integer months) {
        String asunto = "🚀 ¡Bienvenido al Nivel PRO de SubastaShop!";
        String durationText = months + (months == 1 ? " mes" : " meses");
        
        String html = "<div style='font-family: sans-serif; max-width: 600px; margin: auto; border: 1px solid #eee; padding: 20px; border-radius: 10px;'>" +
                "<h1 style='color: #6366f1; text-align: center;'>¡Felicidades, " + user.getNombreCompleto() + "!</h1>" +
                "<p style='font-size: 1.1em;'>Estamos muy felices de confirmarte que tu cuenta ha sido actualizada al nivel <b>PRO</b> exitosamente.</p>" +
                "<div style='background: #f8fafc; padding: 15px; border-radius: 5px; margin: 20px 0;'>" +
                "<p style='margin: 5px 0;'>💎 <b>Nivel:</b> PRO Administrator</p>" +
                "<p style='margin: 5px 0;'>⏳ <b>Duración añadida:</b> " + durationText + "</p>" +
                "<p style='margin: 5px 0;'>📅 <b>Vencimiento:</b> " + user.getFechaVencimientoSuscripcion().toLocalDate() + "</p>" +
                "</div>" +
                "<h3>¿Qué sigue ahora?</h3>" +
                "<ul>" +
                "<li><b>Crea tu tienda:</b> Ya tienes acceso al panel de configuración para subir tu logo y elegir tus colores.</li>" +
                "<li><b>Publica sin límites:</b> Tus subastas y rifas ya pueden ser publicadas globalmente.</li>" +
                "<li><b>Ventas directas:</b> Configura tus datos bancarios para recibir transferencias de tus ganadores.</li>" +
                "</ul>" +
                "<p style='text-align: center; margin-top: 30px;'>" +
                "<a href='" + frontendUrl + "/admin/configuracion' style='background: #6366f1; color: white; padding: 12px 25px; text-decoration: none; border-radius: 5px; font-weight: bold;'>IR A MI PANEL DE TIENDA</a>" +
                "</p>" +
                "<br><p style='color: #64748b; font-size: 0.9em;'>Si tienes alguna duda, responde a este correo. ¡Mucho éxito con tus ventas!</p>" +
                "<p style='font-size: 0.8em; color: #cbd5e1;'>Atentamente,<br>El equipo de SubastaShop</p>" +
                "</div>";

        emailService.enviarCorreo(user.getEmail(), asunto, html);
    }
}
