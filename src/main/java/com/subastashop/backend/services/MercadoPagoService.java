package com.subastashop.backend.services;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.fasterxml.jackson.core.type.TypeReference;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Role;
import com.subastashop.backend.repositories.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Value("${mercadopago.public.key:}")
    private String publicKey;

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
     * Sincroniza el estado de la suscripción de un usuario consultando directamente
     * a Mercado Pago.
     */
    public boolean syncSubscriptionStatus(String userEmail) {
        AppUsers user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String subId = user.getSubscriptionId();
        if (subId == null || subId.isEmpty()) {
            // Si no hay ID, verificamos si debería tenerla o simplemente marcamos como
            // inactiva la auto
            user.setPagoAutomatico(false);
            userRepository.save(user);
            return false;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mercadopago.com/preapproval/" + subId))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> mpResponse = objectMapper.readValue(response.body(), new TypeReference<>() {
                });
                String status = (String) mpResponse.get("status");

                if ("authorized".equals(status) || "active".equals(status)) {
                    user.setSuscripcionActiva(true);
                    user.setPagoAutomatico(true);
                    user.setRol(Role.ROLE_ADMIN);
                } else {
                    user.setPagoAutomatico(false);
                    // No cambiamos suscripcionActiva aquí por si es un plan manual que aún vence
                }
                userRepository.save(user);
                return true;
            }
        } catch (Exception e) {
            log.error("Error sincronizando suscripción para {}: {}", userEmail, e.getMessage());
        }
        return false;
    }

    /**
     * Cancela una suscripción activa en Mercado Pago.
     */
    public boolean cancelSubscription(String userEmail) throws Exception {
        AppUsers user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String subId = user.getSubscriptionId();
        if (subId == null || subId.isEmpty()) {
            log.warn("El usuario {} no tiene un ID de suscripción registrado localmente.", userEmail);
            user.setPagoAutomatico(false);
            userRepository.save(user);
            return true;
        }

        log.info("Cancelando suscripción {} para el usuario {}", subId, userEmail);

        Map<String, String> body = new HashMap<>();
        body.put("status", "cancelled");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mercadopago.com/preapproval/" + subId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 300) {
            log.error("Error al cancelar suscripción en MP: {}", response.body());
            throw new RuntimeException("Mercado Pago no pudo cancelar la suscripción: " + response.body());
        }

        // Actualización local: Bajar categoría y desactivar suscripción
        user.setSubscriptionId(null);
        user.setPagoAutomatico(false);
        user.setSuscripcionActiva(false);
        user.setRol(Role.ROLE_COMPRADOR);
        userRepository.save(user);

        log.info("✅ Suscripción {} cancelada. Usuario {} degradado a ROLE_COMPRADOR", subId, userEmail);
        return true;
    }

    /**
     * Crea una suscripción (Preapproval) usando un token de tarjeta directamente.
     * Esto evita el flujo de login de Mercado Pago.
     */
    public Map<String, Object> subscribeWithCardToken(String userEmail, String cardTokenId) throws Exception {
        AppUsers user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 1. Asegurar Plan V6
        String planName = "SubastaShop PRO";
        BigDecimal amount = new BigDecimal("9990");
        long totalProUsers = userRepository.countByRol(Role.ROLE_ADMIN);
        if (totalProUsers < 100) {
            planName = "SubastaShop PRO Promo";
            amount = new BigDecimal("4990");
        }
        String planId = getOrCreatePlanId(planName, amount);

        // 2. Crear Preapproval con el Token
        Map<String, Object> subBody = new HashMap<>();
        subBody.put("preapproval_plan_id", planId);
        subBody.put("payer_email", userEmail);
        subBody.put("card_token_id", cardTokenId); // CLAVE: Aquí enviamos el token de la tarjeta
        subBody.put("back_url", "https://www.subastashop.cl/admin/configuracion");
        subBody.put("reason", planName);
        subBody.put("status", "authorized"); // Lo enviamos como autorizado si ya hay token
        subBody.put("external_reference", user.getId().toString());

        String jsonSub = objectMapper.writeValueAsString(subBody);
        log.info("Creando suscripción directa con token para {}: {}", userEmail, jsonSub);

        HttpRequest subReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mercadopago.com/preapproval"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken.trim())
                .POST(HttpRequest.BodyPublishers.ofString(jsonSub))
                .build();

        HttpResponse<String> subRes = httpClient.send(subReq, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> response = objectMapper.readValue(subRes.body(), Map.class);

        if (subRes.statusCode() >= 300) {
            log.error("Error en suscripción con token: {}", subRes.body());
            return response;
        }

        // Si se autoriza con éxito, activamos al usuario inmediatamente
        if ("authorized".equalsIgnoreCase(String.valueOf(response.get("status")))) {
            confirmSubscription(user.getId(), 1, true);
            user.setSubscriptionId(String.valueOf(response.get("id")));
            userRepository.save(user);
        }

        return response;
    }

    /**
     * Crea una suscripción recurrente mensual (Pre-approval) usando el enlace
     * directo del PLAN.
     * Descubrimos en la documentación de Chile que el Plan mismo tiene un
     * init_point
     * que evita el error de card_token_id.
     */
    public String createRecurringSubscription(String userEmail) throws Exception {
        AppUsers user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 1. Aseguramos que existe el plan (V6)
        String planName = "SubastaShop PRO";
        BigDecimal amount = new BigDecimal("9990");
        long totalProUsers = userRepository.countByRol(Role.ROLE_ADMIN);
        if (totalProUsers < 100) {
            planName = "SubastaShop PRO Promo";
            amount = new BigDecimal("4990");
        }

        // Obtenemos el ID del plan (debería ser el que te funcionó)
        String planId = getOrCreatePlanId(planName, amount);

        // 2. Creamos una suscripción (preapproval) específica para ESTE usuario y ESTE
        // email
        // Esto es lo que permite que MP intente el flujo de "Invitado" o pre-llene los
        // datos
        Map<String, Object> subBody = new HashMap<>();
        subBody.put("preapproval_plan_id", planId);
        subBody.put("payer_email", userEmail);
        subBody.put("back_url", "https://www.subastashop.cl/admin/configuracion");
        subBody.put("reason", planName);
        subBody.put("status", "pending");
        subBody.put("external_reference", user.getId().toString());

        String jsonSub = objectMapper.writeValueAsString(subBody);
        log.info("Creando Preapproval específica para {} : {}", userEmail, jsonSub);

        HttpRequest subReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mercadopago.com/preapproval"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken.trim())
                .POST(HttpRequest.BodyPublishers.ofString(jsonSub))
                .build();

        HttpResponse<String> subRes = httpClient.send(subReq, HttpResponse.BodyHandlers.ofString());

        if (subRes.statusCode() >= 300) {
            log.error("Error creando Suscripción para {}: {}", userEmail, subRes.body());
            throw new RuntimeException("Error MP al crear Suscripción: " + subRes.body());
        }

        Map<String, Object> subscription = objectMapper.readValue(subRes.body(), Map.class);
        String initPoint = (String) subscription.get("init_point");

        // Guardamos intención
        user.setPagoAutomatico(true);
        userRepository.save(user);

        log.info("Redirigiendo usuario {} al Checkout Personalizado: {}", userEmail, initPoint);
        return initPoint;
    }

    /**
     * Versión auxiliar que devuelve solo el ID del plan.
     */
    private String getOrCreatePlanId(String reason, BigDecimal amount) throws Exception {
        // Reutilizamos la lógica de creación pero devolvemos el ID
        String searchUri = "https://api.mercadopago.com/preapproval_plan/search?reason=" + reason.replace(" ", "%20");
        HttpRequest searchReq = HttpRequest.newBuilder()
                .uri(URI.create(searchUri))
                .header("Authorization", "Bearer " + accessToken.trim())
                .GET()
                .build();
        HttpResponse<String> searchRes = httpClient.send(searchReq, HttpResponse.BodyHandlers.ofString());

        if (searchRes.statusCode() == 200) {
            Map<String, Object> results = objectMapper.readValue(searchRes.body(), Map.class);
            List<Map<String, Object>> resultsList = (List<Map<String, Object>>) results.get("results");
            if (resultsList != null && !resultsList.isEmpty()) {
                return (String) resultsList.get(0).get("id");
            }
        }

        // Si no existe, usamos el método robusto para crear uno nuevo y devolver su ID
        return createNewPlanAndGetId(reason, amount);
    }

    private String createNewPlanAndGetId(String reason, BigDecimal amount) throws Exception {
        Map<String, Object> planBody = new HashMap<>();
        planBody.put("reason", reason);
        planBody.put("back_url", "https://www.subastashop.cl/admin/configuracion");

        Map<String, Object> autoRecurring = new HashMap<>();
        autoRecurring.put("frequency", 1);
        autoRecurring.put("frequency_type", "months");
        autoRecurring.put("repetitions", 12);
        autoRecurring.put("billing_day", 1);
        autoRecurring.put("billing_day_proportional", false);
        autoRecurring.put("transaction_amount", amount.intValue());
        autoRecurring.put("currency_id", "CLP");
        planBody.put("auto_recurring", autoRecurring);

        Map<String, Object> paymentMethods = new HashMap<>();
        List<Map<String, String>> typeList = new ArrayList<>();
        Map<String, String> visaType = new HashMap<>();
        visaType.put("id", "visa");
        typeList.add(visaType);
        paymentMethods.put("payment_types", typeList);

        List<Map<String, String>> methodList = new ArrayList<>();
        Map<String, String> visaMethod = new HashMap<>();
        visaMethod.put("id", "visa");
        methodList.add(visaMethod);
        paymentMethods.put("payment_methods", methodList);
        planBody.put("payment_methods_allowed", paymentMethods);

        HttpRequest createReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mercadopago.com/preapproval_plan"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken.trim())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(planBody)))
                .build();

        HttpResponse<String> createRes = httpClient.send(createReq, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> createdPlan = objectMapper.readValue(createRes.body(), Map.class);
        return (String) createdPlan.get("id");
    }

    public void processPaymentNotification(String paymentId) {
        log.info("🔔 Notificación de Mercado Pago (Pago) recibida: ID {}", paymentId);
        try {
            PaymentClient client = new PaymentClient();
            Payment payment = client.get(Long.parseLong(paymentId));

            if ("approved".equalsIgnoreCase(payment.getStatus())) {
                String externalRef = payment.getExternalReference();

                // Si el pago viene de una suscripción recurrente, el externalRef suele ser solo
                // el ID del usuario
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
        LocalDateTime fechaBase = (user.getFechaVencimientoSuscripcion() != null
                && user.getFechaVencimientoSuscripcion().isAfter(hoy))
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
        String asunto = esAutomatico ? "✅ Pago Automático Exitoso - SubastaShop"
                : "🚀 ¡Bienvenido al Nivel PRO de SubastaShop!";
        String durationText = months + (months == 1 ? " mes" : " meses");

        String html = "<div style='font-family: sans-serif; max-width: 600px; margin: auto; border: 1px solid #eee; padding: 20px; border-radius: 10px; border-top: 5px solid #6366f1;'>"
                +
                "<h1 style='color: #1e293b; text-align: center;'>"
                + (esAutomatico ? "Cobro Recurrente Confirmado" : "¡Suscripción Activada!") + "</h1>" +
                "<p style='font-size: 1.1em; color: #475569;'>Hola <b>" + user.getNombreCompleto() + "</b>,</p>" +
                "<p style='font-size: 1.1em; color: #475569;'>" +
                (esAutomatico ? "Tu suscripción mensual se ha renovado automáticamente con éxito."
                        : "Gracias por confiar en SubastaShop. Tu cuenta ha sido actualizada al nivel PRO.")
                +
                "</p>" +
                "<div style='background: #f8fafc; padding: 15px; border-radius: 5px; margin: 20px 0;'>" +
                "<p style='margin: 5px 0;'>💎 <b>Nivel:</b> PRO Administrator</p>" +
                "<p style='margin: 5px 0;'>⏳ <b>Periodo Añadido:</b> " + durationText + "</p>" +
                "<p style='margin: 5px 0;'>📅 <b>Nueva Fecha de Vencimiento:</b> "
                + user.getFechaVencimientoSuscripcion().toLocalDate() + "</p>" +
                "<p style='margin: 5px 0;'>⚙️ <b>Tipo de Renovación:</b> "
                + (esAutomatico ? "Automática 🔄" : "Manual 👆") + "</p>" +
                "</div>" +
                (esAutomatico ? ""
                        : "<h3>¿Qué puedes hacer ahora?</h3>" +
                                "<ul>" +
                                "<li>Configurar tu tienda y logo.</li>" +
                                "<li>Publicar subastas y ventas directas sin límites.</li>" +
                                "<li>Recibir pagos directos de tus clientes.</li>" +
                                "</ul>")
                +
                "<p style='text-align: center; margin-top: 30px;'>" +
                "<a href='" + frontendUrl
                + "/admin/configuracion' style='background: #6366f1; color: white; padding: 12px 25px; text-decoration: none; border-radius: 5px; font-weight: bold;'>IR A MI DASHBOARD</a>"
                +
                "</p>" +
                "<br><p style='color: #94a3b8; font-size: 0.9em;'>Puedes cambiar tu método de pago o cancelar en cualquier momento desde tu panel.</p>"
                +
                "</div>";

        emailService.enviarCorreo(user.getEmail(), asunto, html);
    }

}
