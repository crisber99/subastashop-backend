package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.UserLegalAcceptance;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.UserLegalAcceptanceRepository;
import com.subastashop.backend.services.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/legal")
public class LegalController {

    @Autowired
    private UserLegalAcceptanceRepository legalRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @PostMapping("/accept")
    public ResponseEntity<?> acceptTerms(HttpServletRequest request, @RequestBody Map<String, String> payload) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            AppUsers user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            String type = payload.getOrDefault("type", "GENERAL");
            String version = payload.getOrDefault("version", "v1.0");

            UserLegalAcceptance acceptance = new UserLegalAcceptance();
            acceptance.setUserId(user.getId());
            acceptance.setTermsVersion(version);
            acceptance.setAcceptanceTimestamp(LocalDateTime.now());
            acceptance.setIpAddress(request.getRemoteAddr());
            acceptance.setUserAgent(request.getHeader("User-Agent"));
            acceptance.setType(type);

            legalRepository.save(acceptance);

            // Enviar correo de confirmación legal
            enviarConfirmacionLegal(user, type);

            return ResponseEntity.ok(Map.of(
                "mensaje", "Términos aceptados correctamente",
                "timestamp", acceptance.getAcceptanceTimestamp()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private void enviarConfirmacionLegal(AppUsers user, String type) {
        String asunto = "Confirmación de Aceptación de Bases Legales - SubastaShop";
        String contexto = type.equals("SELLER_REGISTRATION") ? "tu registro como Vendedor" : "tu participación en concursos";
        
        String mensaje = "Hola " + user.getNombreCompleto() + ",<br><br>" +
                "Este es un comprobante automático de que has aceptado los <b>Términos y Condiciones</b> de SubastaShop para " + contexto + ".<br><br>" +
                "<b>Detalles del Registro:</b><br>" +
                "- Usuario: " + user.getEmail() + "<br>" +
                "- Fecha/Hora: " + LocalDateTime.now() + "<br>" +
                "- Versión: v1.0 (Chile)<br><br>" +
                "Recuerda que bajo la ley chilena, los <b>Concursos de Habilidad</b> (Memorice) no constituyen juegos de azar y se rigen por las bases publicadas en nuestra plataforma.<br><br>" +
                "Saludos,<br>Departamento Legal de SubastaShop";

        emailService.enviarCorreo(user.getEmail(), asunto, mensaje);
    }
}
