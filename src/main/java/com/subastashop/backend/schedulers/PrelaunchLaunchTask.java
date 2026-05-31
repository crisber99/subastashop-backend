package com.subastashop.backend.schedulers;

import com.subastashop.backend.models.PrelaunchSubscriber;
import com.subastashop.backend.repositories.PrelaunchSubscriberRepository;
import com.subastashop.backend.services.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class PrelaunchLaunchTask {

    private final PrelaunchSubscriberRepository subscriberRepository;
    private final EmailService emailService;

    // Fecha objetivo del lanzamiento (31 de Mayo 2026 a las 18:00)
    private static final LocalDateTime LAUNCH_TIME = LocalDateTime.of(2026, 5, 31, 18, 0);

    public PrelaunchLaunchTask(PrelaunchSubscriberRepository subscriberRepository, EmailService emailService) {
        this.subscriberRepository = subscriberRepository;
        this.emailService = emailService;
    }

    // Se ejecuta cada 1 minuto
    @Scheduled(fixedDelay = 60000)
    public void processPrelaunchEmails() {
        if (LocalDateTime.now().isBefore(LAUNCH_TIME)) {
            // Aún no es la hora
            return;
        }

        // Buscar hasta 50 suscriptores que aún no han sido notificados
        List<PrelaunchSubscriber> pendingSubscribers = subscriberRepository.findTop50ByNotifiedFalse();

        if (pendingSubscribers.isEmpty()) {
            return;
        }

        log.info("Iniciando envío de lote de {} correos de bienvenida por prelanzamiento...",
                pendingSubscribers.size());

        for (PrelaunchSubscriber subscriber : pendingSubscribers) {
            enviarCorreoBienvenida(subscriber);
            subscriber.setNotified(true);
            subscriberRepository.save(subscriber);
        }

        log.info("Lote de {} correos de bienvenida enviado correctamente.", pendingSubscribers.size());
    }

    private void enviarCorreoBienvenida(PrelaunchSubscriber subscriber) {
        String asunto = "¡SubastaShop ya está en vivo! 🎉 Te damos la bienvenida";
        String mensaje = buildHtmlMessage();
        emailService.enviarCorreo(subscriber.getEmail(), asunto, mensaje);
    }

    private String buildHtmlMessage() {
        return "<html>" +
                "<body style=\"font-family: Arial, sans-serif; color: #333;\">" +
                "<div style=\"max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #eee; border-radius: 8px;\">"
                +
                "<h2 style=\"color: #0056b3; text-align: center;\">¡Hola!</h2>" +
                "<p style=\"font-size: 16px; line-height: 1.5;\">" +
                "La cuenta regresiva ha terminado y nos emociona anunciar que <strong>SubastaShop</strong> ya está oficialmente abierto para ti."
                +
                "</p>" +
                "<p style=\"font-size: 16px; line-height: 1.5;\">" +
                "Ya puedes ingresar a nuestra plataforma y comenzar a descubrir todo lo que hemos preparado. Recuerda que dentro de la página podrás encontrar toda la información sobre <strong>nuestros planes y sus precios</strong>, para que elijas el que mejor se adapte a lo que necesitas."
                +
                "</p>" +
                "<div style=\"text-align: center; margin-top: 30px; margin-bottom: 30px;\">" +
                "<a href=\"https://www.subastashop.cl\" style=\"background-color: #0056b3; color: #ffffff; padding: 12px 24px; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 16px;\">Entrar a SubastaShop</a>"
                +
                "</div>" +
                "<p style=\"font-size: 16px; line-height: 1.5; text-align: center;\">" +
                "¡Gracias por la espera y bienvenido!<br>" +
                "<strong>El equipo de SubastaShop</strong>" +
                "</p>" +
                "</div>" +
                "</body>" +
                "</html>";
    }
}
