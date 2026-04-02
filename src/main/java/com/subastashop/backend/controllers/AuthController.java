package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Role;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.services.JwtService;
import com.subastashop.backend.dto.LoginRequest;
import com.subastashop.backend.dto.RegisterRequest;
import com.subastashop.backend.exceptions.ApiException;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private com.subastashop.backend.services.EmailService emailService;

    @Autowired
    private com.subastashop.backend.repositories.PasswordResetTokenRepository tokenRepository;

    // LOGIN
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        
        // Autenticación estándar (esto ya lo tienes)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        // Generamos el token pasando el userDetails (que es lo que suele pedir JwtService)
        String jwt = jwtService.generateToken(userDetails);

       // --- BUSCAR DATOS EXTRAS DEL USUARIO PARA EL FRONTEND ---
        AppUsers userCompleto = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("Error: Usuario no encontrado."));

        // --- CONSTRUIR RESPUESTA ---
        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        
        Map<String, Object> usuarioMap = new HashMap<>();
        usuarioMap.put("id", userCompleto.getId()); // ID del usuario para notificaciones y perfiles
        usuarioMap.put("nombre", userCompleto.getNombreCompleto());
        usuarioMap.put("email", userCompleto.getEmail());
        usuarioMap.put("role", userCompleto.getRol() != null ? userCompleto.getRol().name() : "ROLE_USER"); 
        usuarioMap.put("fechaFinPrueba", userCompleto.getFechaFinPrueba());
        usuarioMap.put("suscripcionActiva", userCompleto.isSuscripcionActiva());
        
        response.put("usuario", usuarioMap);

        return ResponseEntity.ok(response);
    }

    // REGISTRO
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request, @RequestHeader("X-Tenant-ID") String tenantId) {
        // ... (existing code stays same)
        if (userRepository.existsByEmailAndTenantId(request.getEmail(), tenantId)) {
            throw new ApiException("El email ya existe en esta tienda");
        }

        // VALIDACIÓN DE CONTRASEÑA 🔐
        String passwordPattern = "^(?=.*[0-9])(?=.*[a-zA-Z]).{8,}$";
        if (request.getPassword() == null || !request.getPassword().matches(passwordPattern)) {
            throw new ApiException("La contraseña debe tener al menos 8 caracteres e incluir letras y números.");
        }

        AppUsers user = new AppUsers();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNombreCompleto(request.getNombre());
        user.setTelefono(request.getTelefono());
        user.setDireccion(request.getDireccion());
        user.setRol(Role.ROLE_COMPRADOR);
        user.setTenantId(tenantId);

        userRepository.save(user);

        // Enviar correo de bienvenida
        String asunto = "¡Bienvenido a SubastaShop!";
        String mensaje = "Hola " + user.getNombreCompleto() + ",<br><br>" +
                             "¡Bienvenido a <b>SubastaShop</b>!<br><br>" +
                             "Tu cuenta ha sido creada exitosamente.<br><br>" +
                             "Saludos,<br>El equipo de SubastaShop";
        emailService.enviarCorreo(user.getEmail(), asunto, mensaje);

        String token = jwtService.generateToken(user);
        
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        Map<String, Object> usuarioMap = new HashMap<>();
        usuarioMap.put("id", user.getId()); // Añadiendo ID para ws
        usuarioMap.put("nombre", user.getNombreCompleto());
        usuarioMap.put("email", user.getEmail());
        usuarioMap.put("role", user.getRol() != null ? user.getRol().name() : "ROLE_COMPRADOR");
        usuarioMap.put("fechaFinPrueba", user.getFechaFinPrueba());
        usuarioMap.put("suscripcionActiva", user.isSuscripcionActiva());
        response.put("usuario", usuarioMap);

        return ResponseEntity.ok(response);
    }

    // --- RECUPERACIÓN DE CONTRASEÑA ---

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        AppUsers user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("No encontramos una cuenta con ese correo."));

        // Limpiar tokens viejos
        tokenRepository.deleteByEmail(email);

        // Generar código de 6 dígitos
        String code = String.format("%06d", new java.util.Random().nextInt(999999));
        
        com.subastashop.backend.models.PasswordResetToken token = new com.subastashop.backend.models.PasswordResetToken();
        token.setEmail(email);
        token.setToken(code);
        token.setExpiryDate(java.time.LocalDateTime.now().plusMinutes(15));
        tokenRepository.save(token);

        // Enviar email
        String asunto = "Código de Recuperación - SubastaShop";
        String mensaje = "Hola " + user.getNombreCompleto() + ",<br><br>" +
                         "Has solicitado restablecer tu contraseña. Tu código de seguridad es:<br><br>" +
                         "<h2 style='color: #6366f1;'>" + code + "</h2><br>" +
                         "Este código expirará en 15 minutos.<br><br>" +
                         "Si no solicitaste esto, puedes ignorar este correo.<br><br>" +
                         "Saludos,<br>El equipo de SubastaShop";
        
        emailService.enviarCorreo(email, asunto, mensaje);

        return ResponseEntity.ok(Map.of("message", "Código enviado exitosamente."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");
        String newPassword = request.get("newPassword");

        com.subastashop.backend.models.PasswordResetToken token = tokenRepository.findByToken(code)
                .filter(t -> t.getEmail().equals(email))
                .orElseThrow(() -> new ApiException("Código inválido o correo incorrecto."));

        if (token.isExpired()) {
            tokenRepository.delete(token);
            throw new ApiException("El código ha expirado. Por favor solicita uno nuevo.");
        }

        // Validar nueva contraseña
        String passwordPattern = "^(?=.*[0-9])(?=.*[a-zA-Z]).{8,}$";
        if (newPassword == null || !newPassword.matches(passwordPattern)) {
            throw new ApiException("La nueva contraseña debe tener al menos 8 caracteres e incluir letras y números.");
        }

        AppUsers user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Usuario no encontrado."));
        
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Borrar token usado
        tokenRepository.delete(token);

        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada con éxito."));
    }

    // --- LOGEO SOCIAL (OAuth2 / OpenID Connect) ---
    @PostMapping("/social-login")
    public ResponseEntity<?> socialLogin(@RequestBody Map<String, String> request, @RequestHeader("X-Tenant-ID") String tenantId) {
        String provider = request.get("provider");
        String token = request.get("token"); // ID Token (Google/Apple) o Access Token (Facebook)
        String email = null;
        String nombre = null;

        try {
            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

            if ("GOOGLE".equalsIgnoreCase(provider)) {
                // Validación Estándar de Google (Sin dependencias enormes)
                String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + token;
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                if (response != null && response.containsKey("email")) {
                    email = (String) response.get("email");
                    nombre = (String) response.get("name");
                    // Aquí deberíamos agregar validación extra para que response.get("aud") == tu_client_id
                } else {
                    throw new ApiException("Token de Google inválido.");
                }
            } else if ("FACEBOOK".equalsIgnoreCase(provider)) {
                // Graph API Validación
                String url = "https://graph.facebook.com/me?fields=id,name,email&access_token=" + token;
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                if (response != null && response.containsKey("email")) {
                    email = (String) response.get("email");
                    nombre = (String) response.get("name");
                } else {
                    throw new ApiException("Token de Facebook inválido o sin permisos de email.");
                }
            } else if ("APPLE".equalsIgnoreCase(provider)) {
                // Para Apple, el token JWT devuelto debe descifrarse a mano contra la llave pública alojada en auth.apple.com
                throw new ApiException("La validación de Apple requiere tus llaves p8 y el TeamID configurados en el backend.");
            } else {
                throw new ApiException("Proveedor OAuth no soportado.");
            }
            
        } catch (Exception e) {
             throw new ApiException("Error validando el token social contra " + provider + ": " + e.getMessage());
        }

        if (email == null) {
            throw new ApiException("No se pudo obtener el correo de la red social. Intenta con un método tradicional.");
        }

        AppUsers user = userRepository.findByEmail(email).orElse(null);

        // Auto-crear cuenta si no existe (Seamless Registration)
        if (user == null) {
            user = new AppUsers();
            user.setEmail(email);
            // Autogeneramos contraseña ultra compleja (el logeo será manejado por OAuth)
            user.setPasswordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString() + "aA1!social"));
            user.setNombreCompleto(nombre != null ? nombre : "Usuario " + provider);
            user.setRol(Role.ROLE_COMPRADOR);
            user.setTenantId(tenantId);
            userRepository.save(user);

            emailService.enviarCorreo(email, "¡Bienvenido a SubastaShop!", "Te has registrado exitosamente autorizando a " + provider + ".");
        }

        String jwt = jwtService.generateToken(user);
        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        
        Map<String, Object> usuarioMap = new HashMap<>();
        usuarioMap.put("id", user.getId());
        usuarioMap.put("nombre", user.getNombreCompleto());
        usuarioMap.put("email", user.getEmail());
        usuarioMap.put("role", user.getRol() != null ? user.getRol().name() : "ROLE_COMPRADOR"); 
        
        response.put("usuario", usuarioMap);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
        }

        AppUsers user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Map<String, Object> usuarioMap = new HashMap<>();
        usuarioMap.put("id", user.getId());
        usuarioMap.put("nombre", user.getNombreCompleto());
        usuarioMap.put("email", user.getEmail());
        usuarioMap.put("role", user.getRol() != null ? user.getRol().name() : "ROLE_USER");
        usuarioMap.put("fechaFinPrueba", user.getFechaFinPrueba());
        usuarioMap.put("suscripcionActiva", user.isSuscripcionActiva());
        usuarioMap.put("fechaVencimientoSuscripcion", user.getFechaVencimientoSuscripcion());

        return ResponseEntity.ok(usuarioMap);
    }
}