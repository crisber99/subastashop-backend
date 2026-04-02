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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.HashMap;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

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
        return preference.getInitPoint(); 
    }

    /**
     * Crea una suscripción recurrente mensual (Pre-approval) vía REST usando PLANES.
     */
    public String createRecurringSubscription(String userEmail) throws Exception {
        AppUsers user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 1. Determinar precio y nombre del plan
        String planName = "SubastaShop PRO Estándar";
        BigDecimal amount = new BigDecimal("9990");
        long totalProUsers = userRepository.countByRol(Role.ROLE_ADMIN);
        if (totalProUsers < 100) {
            planName = "SubastaShop PRO Promo";
            amount = new BigDecimal("4990");
        }

        // 2. Obtener o crear el Plan ID en Mercado Pago
        String planId = getOrCreatePlanId(planName, amount);

        // 3. Crear la suscripción vinculada al Plan
        Map<String, Object> body = new HashMap<>();
        body.put("preapproval_plan_id", planId);
        body.put("payer_email", user.getEmail());
        body.put("external_reference", user.getId().toString());
        body.put("back_url", "https://www.subastashop.cl/admin/configuracion");
        body.put("status", "authorized");

        String jsonBody = objectMapper.writeValueAsString(body);
        log.info("Creando suscripción vinculada al plan {}: {}", planId, jsonBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mercadopago.com/preapproval"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken.trim())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 300) {
            log.error("Error al suscribir usuario al plan: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("Error MP al suscribir: " + response.body());
        }

        Map<String, Object> responseMap = objectMapper.readValue(response.body(), Map.class);
        
        // Marcamos que el usuario tiene intención de pago automático
        user.setPagoAutomatico(true);
        userRepository.save(user);

        return String.valueOf(responseMap.get("init_point"));
    }

    public void processPaymentNotification(String paymentId) {
        log.info("🔔 Notificación de Mercado Pago (Pago) recibida: ID {}", paymentId);
        try {
            PaymentClient client = new PaymentClient();
            Payment payment = client.get(Long.parseLong(paymentId));
            
            if ("approved".equalsIgnoreCase(payment.getStatus())) {
                String externalRef = payment.getExternalReference();
                
                // Si el pago viene de una suscripción recurrente, el externalRef suele ser solo el ID del usuario
                // Si viene de una preferencia manual, es "userId:months"
                if (externalRef != null) {
                    if (externalRef.contains(":")) {
                        // Flujo MANUAL
                        String[] parts = externalRef.split(":");
                        Integer userId = Integer.parseInt(parts[0]);
                        Integer months = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                        confirmSubscription(userId, months, false);
                    } else {
                        // Flujo AUTOMÁTICO (Cobro Recurrente)
                        Integer userId = Integer.parseInt(externalRef);
                        confirmSubscription(userId, 1, true);
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Error procesando pago de Mercado Pago", e);
        }
    }

    /**
     * Procesa notificaciones de tipo 'preapproval' (Suscripciones) vía REST.
     */
    public void processSubscriptionNotification(String preapprovalId) {
        log.info("🔔 Notificación de Suscripción recibida: ID {}", preapprovalId);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mercadopago.com/preapproval/" + preapprovalId))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> preApproval = objectMapper.readValue(response.body(), Map.class);
                
                if ("authorized".equalsIgnoreCase(String.valueOf(preApproval.get("status")))) {
                    Integer userId = Integer.parseInt(String.valueOf(preApproval.get("external_reference")));
                    AppUsers user = userRepository.findById(userId).orElse(null);
                    if (user != null) {
                        user.setSubscriptionId(preapprovalId);
                        user.setPagoAutomatico(true);
                        userRepository.save(user);
                        log.info("✅ suscripción {} vinculada al usuario {}", preapprovalId, user.getEmail());
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Error procesando suscripción vía REST", e);
        }
    }

    /**
     * Activa o renueva la suscripción de un usuario.
     */
    public void confirmSubscription(Integer userId, Integer months, boolean esAutomatico) {
        AppUsers user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado (ID: " + userId + ")"));
        
        // Actualizar Fechas
        LocalDateTime hoy = LocalDateTime.now();
        LocalDateTime fechaBase = (user.getFechaVencimientoSuscripcion() != null && user.getFechaVencimientoSuscripcion().isAfter(hoy)) 
                ? user.getFechaVencimientoSuscripcion() 
                : hoy;
        
        user.setFechaVencimientoSuscripcion(fechaBase.plusMonths(months));
        user.setSuscripcionActiva(true);
        user.setRol(Role.ROLE_ADMIN); 
        user.setPagoAutomatico(esAutomatico); // Guardamos la preferencia actual del usuario
        userRepository.save(user);

        log.info("✅ [{} PLAN] Suscripción activada por {} meses para: {}", 
                 esAutomatico ? "AUTO" : "MANUAL", months, user.getEmail());

        // --- ENVIAR EMAIL DE BIENVENIDA O RENOVACIÓN ---
        enviarEmailConfirmacion(user, months, esAutomatico);
    }

    private void enviarEmailConfirmacion(AppUsers user, Integer months, boolean esAutomatico) {
        String asunto = esAutomatico ? "✅ Pago Automático Exitoso - SubastaShop" : "🚀 ¡Bienvenido al Nivel PRO de SubastaShop!";
        String durationText = months + (months == 1 ? " mes" : " meses");
        
        String html = "<div style='font-family: sans-serif; max-width: 600px; margin: auto; border: 1px solid #eee; padding: 20px; border-radius: 10px; border-top: 5px solid #6366f1;'>" +
                "<h1 style='color: #1e293b; text-align: center;'>" + (esAutomatico ? "Cobro Recurrente Confirmado" : "¡Suscripción Activada!") + "</h1>" +
                "<p style='font-size: 1.1em; color: #475569;'>Hola <b>" + user.getNombreCompleto() + "</b>,</p>" +
                "<p style='font-size: 1.1em; color: #475569;'>" + 
                (esAutomatico ? "Tu suscripción mensual se ha renovado automáticamente con éxito." : "Gracias por confiar en SubastaShop. Tu cuenta ha sido actualizada al nivel PRO.") + 
                "</p>" +
                "<div style='background: #f8fafc; padding: 15px; border-radius: 5px; margin: 20px 0;'>" +
                "<p style='margin: 5px 0;'>💎 <b>Nivel:</b> PRO Administrator</p>" +
                "<p style='margin: 5px 0;'>⏳ <b>Periodo Añadido:</b> " + durationText + "</p>" +
                "<p style='margin: 5px 0;'>📅 <b>Nueva Fecha de Vencimiento:</b> " + user.getFechaVencimientoSuscripcion().toLocalDate() + "</p>" +
                "<p style='margin: 5px 0;'>⚙️ <b>Tipo de Renovación:</b> " + (esAutomatico ? "Automática 🔄" : "Manual 👆") + "</p>" +
                "</div>" +
                (esAutomatico ? "" : 
                "<h3>¿Qué puedes hacer ahora?</h3>" +
                "<ul>" +
                "<li>Configurar tu tienda y logo.</li>" +
                "<li>Publicar subastas y ventas directas sin límites.</li>" +
                "<li>Recibir pagos directos de tus clientes.</li>" +
                "</ul>") +
                "<p style='text-align: center; margin-top: 30px;'>" +
                "<a href='" + frontendUrl + "/admin/configuracion' style='background: #6366f1; color: white; padding: 12px 25px; text-decoration: none; border-radius: 5px; font-weight: bold;'>IR A MI DASHBOARD</a>" +
                "</p>" +
                "<br><p style='color: #94a3b8; font-size: 0.9em;'>Puedes cambiar tu método de pago o cancelar en cualquier momento desde tu panel.</p>" +
                "</div>";

        emailService.enviarCorreo(user.getEmail(), asunto, html);
    }

    /**
     * Busca un plan existente o crea uno nuevo si no existe.
     */
    private String getOrCreatePlanId(String reason, BigDecimal amount) throws Exception {
        // Primero intentamos buscar si ya existe un plan con esa razón
        String searchUri = "https://api.mercadopago.com/preapproval_plan/search?reason=" + reason.replace(" ", "%20");
        
        HttpRequest searchReq = HttpRequest.newBuilder()
                .uri(URI.create(searchUri))
                .header("Authorization", "Bearer " + accessToken.trim())
                .GET()
                .build();

        HttpResponse<String> searchRes = httpClient.send(searchReq, HttpResponse.BodyHandlers.ofString());
        
        if (searchRes.statusCode() == 200) {
            Map<String, Object> searchResult = objectMapper.readValue(searchRes.body(), Map.class);
            List<Map<String, Object>> results = (List<Map<String, Object>>) searchResult.get("results");
            if (results != null && !results.isEmpty()) {
                // Retornamos el primer plan activo encontrado
                return String.valueOf(results.get(0).get("id"));
            }
        }

        // Si no existe, lo creamos
        Map<String, Object> planBody = new HashMap<>();
        planBody.put("reason", reason);
        planBody.put("back_url", "https://www.subastashop.cl/admin/configuracion");
        
        Map<String, Object> autoRecurring = new HashMap<>();
        autoRecurring.put("frequency", 1);
        autoRecurring.put("frequency_type", "months");
        autoRecurring.put("transaction_amount", amount.intValue());
        autoRecurring.put("currency_id", "CLP");
        
        planBody.put("auto_recurring", autoRecurring);

        String jsonPlan = objectMapper.writeValueAsString(planBody);
        log.info("Creando nuevo Plan de Suscripción: {}", jsonPlan);

        HttpRequest createReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mercadopago.com/preapproval_plan"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken.trim())
                .POST(HttpRequest.BodyPublishers.ofString(jsonPlan))
                .build();

        HttpResponse<String> createRes = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());

        if (createRes.statusCode() >= 300) {
            log.error("Error creando Plan en MP: {} - {}", createRes.statusCode(), createRes.body());
            throw new RuntimeException("Error MP al crear Plan: " + createRes.body());
        }

        Map<String, Object> createdPlan = objectMapper.readValue(createRes.body(), Map.class);
        return String.valueOf(createdPlan.get("id"));
    }
}
