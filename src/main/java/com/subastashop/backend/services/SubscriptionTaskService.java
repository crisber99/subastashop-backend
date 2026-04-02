package com.subastashop.backend.services;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Role;
import com.subastashop.backend.repositories.AppUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SubscriptionTaskService {

    private final AppUserRepository userRepository;
    private final EmailService emailService;

    @Value("${app.frontend.url:https://www.subastashop.cl}")
    private String frontendUrl;

    public SubscriptionTaskService(AppUserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    /**
     * Se ejecuta todos los días a las 08:00 AM (Hora del servidor)
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void checkExpirations() {
        log.info("⏰ Iniciando tarea programada de revisión de suscripciones...");
        
        LocalDate hoy = LocalDate.now();
        
        // 1. Aviso 7 días antes
        avisoSuscripcion(hoy.plusDays(7), "🚀 ¡Falta una semana!", 
            "Te recordamos que tu suscripción Pro vencerá en 7 días. Renueva hoy para mantener tu tienda siempre activa.");

        // 2. Aviso el día de vencimiento
        avisoSuscripcion(hoy, "⚠️ ¡Tu suscripción vence HOY!", 
            "Hoy es el último día de tu suscripción actual. No permitas que tus subastas se detengan.");

        // 3. Aviso y Suspensión el día después del vencimiento
        procesarSuspensiones(hoy.minusDays(1));
    }

    private void avisoSuscripcion(LocalDate fechaObjetivo, String asunto, String mensaje) {
        LocalDateTime inicio = fechaObjetivo.atStartOfDay();
        LocalDateTime fin = fechaObjetivo.atTime(LocalTime.MAX);

        // Buscamos usuarios que vencen en esa fecha exacta
        List<AppUsers> usuarios = userRepository.findAll().stream()
                .filter(u -> u.getRol() == Role.ROLE_ADMIN && u.isSuscripcionActiva())
                .filter(u -> u.getFechaVencimientoSuscripcion() != null 
                        && u.getFechaVencimientoSuscripcion().isAfter(inicio) 
                        && u.getFechaVencimientoSuscripcion().isBefore(fin))
                .collect(Collectors.toList());

        for (AppUsers user : usuarios) {
            String html = generarCuerpoEmail(user, asunto, mensaje, "RENOVAR AHORA");
            emailService.enviarCorreo(user.getEmail(), "SubastaShop: " + asunto, html);
            log.info("📧 Email de recordatorio enviado a: {} ({})", user.getEmail(), asunto);
        }
    }

    private void procesarSuspensiones(LocalDate fechaAyer) {
        LocalDateTime inicio = fechaAyer.atStartOfDay();
        LocalDateTime fin = fechaAyer.atTime(LocalTime.MAX);

        List<AppUsers> usuariosParaBaja = userRepository.findAll().stream()
                .filter(u -> u.getRol() == Role.ROLE_ADMIN && u.isSuscripcionActiva())
                .filter(u -> u.getFechaVencimientoSuscripcion() != null 
                        && u.getFechaVencimientoSuscripcion().isAfter(inicio) 
                        && u.getFechaVencimientoSuscripcion().isBefore(fin))
                .collect(Collectors.toList());

        for (AppUsers user : usuariosParaBaja) {
            user.setSuscripcionActiva(false);
            // Mantenemos el rol ADMIN pero la flag de suscripción activa en false 
            // Esto permite que el front detecte que es un "Ex-Admin" y le pida pagar.
            userRepository.save(user);

            String asunto = "🚫 Tu suscripción ha finalizado";
            String msn = "Tu suscripción Pro venció ayer. Tu tienda y productos han sido pausados temporalmente. " +
                         "Tus productos siguen guardados, ¡pero tus clientes no pueden verlos hasta que renueves!";
            
            String html = generarCuerpoEmail(user, asunto, msn, "REACTIVAR TIENDA");
            emailService.enviarCorreo(user.getEmail(), "SubastaShop: " + asunto, html);
            log.info("📉 Suscripción desactivada por vencimiento: {}", user.getEmail());
        }
    }

    private String generarCuerpoEmail(AppUsers user, String titulo, String mensajeBody, String btnText) {
        return "<div style='font-family: sans-serif; max-width: 600px; margin: auto; border: 1px solid #eee; padding: 20px; border-radius: 10px; border-top: 5px solid #6366f1;'>" +
                "<h1 style='color: #1e293b; text-align: center;'>" + titulo + "</h1>" +
                "<p style='font-size: 1.1em; color: #475569;'>Hola <b>" + user.getNombreCompleto() + "</b>,</p>" +
                "<p style='font-size: 1.1em; color: #475569;'>" + mensajeBody + "</p>" +
                "<div style='background: #f8fafc; padding: 15px; border-radius: 5px; margin: 20px 0; text-align: center;'>" +
                "<p style='margin: 5px 0; color: #64748b;'>Vencimiento: <b>" + user.getFechaVencimientoSuscripcion().toLocalDate() + "</b></p>" +
                "</div>" +
                "<p style='text-align: center; margin-top: 30px;'>" +
                "<a href='" + frontendUrl + "/admin/configuracion' style='background: #6366f1; color: white; padding: 12px 25px; text-decoration: none; border-radius: 5px; font-weight: bold;'>" + btnText + "</a>" +
                "</p>" +
                "<br><p style='color: #94a3b8; font-size: 0.9em;'>Si crees que esto es un error, por favor contáctanos.</p>" +
                "<p style='font-size: 0.8em; color: #cbd5e1;'>Atentamente,<br>El equipo de SubastaShop</p>" +
                "</div>";
    }
}
