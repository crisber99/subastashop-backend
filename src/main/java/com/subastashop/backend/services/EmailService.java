package com.subastashop.backend.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @org.springframework.scheduling.annotation.Async
    public void enviarCorreo(String destinatario, String asunto, String mensaje) {
        if (mailSender == null) {
            log.warn("Servicio de Email no configurado. Simulando envío a: {}", destinatario);
            log.info("Asunto: {} | Mensaje: {}", asunto, mensaje);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // true indica que soporta multipart (HTML)
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            // El true al final indica que es HTML en vez de texto plano
            helper.setText(mensaje, true);
            
            // Aquí configuramos el NOMBRE DEL REMITENTE ("SubastaShop")
            helper.setFrom("notificaciones@subastashop.com", "SubastaShop");

            mailSender.send(message);
            log.info("Email enviado exitosamente a: {}", destinatario);
        } catch (Exception e) {
            log.error("Error al enviar email a {}: {}", destinatario, e.getMessage());
        }
    }
}
