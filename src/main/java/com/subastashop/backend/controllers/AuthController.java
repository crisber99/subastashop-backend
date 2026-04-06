package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Role;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.services.JwtService;
import com.subastashop.backend.dto.LoginRequest;
import com.subastashop.backend.dto.RegisterRequest;
import com.subastashop.backend.dto.ResetPasswordRequest;
import com.subastashop.backend.exceptions.ApiException;
import jakarta.validation.Valid;

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
        usuarioMap.put("alias", userCompleto.getAlias());
        usuarioMap.put("role", userCompleto.getRol() != null ? userCompleto.getRol().name() : "ROLE_USER"); 
        usuarioMap.put("fechaFinPrueba", userCompleto.getFechaFinPrueba());
        usuarioMap.put("suscripcionActiva", userCompleto.isSuscripcionActiva());
        usuarioMap.put("telefono", userCompleto.getTelefono());      // 👈 NUEVO
        usuarioMap.put("direccion", userCompleto.getDireccion());    // 👈 NUEVO
        usuarioMap.put("rut", userCompleto.getRut());                // 👈 NUEVO
        usuarioMap.put("preferenciaEnvio", userCompleto.getPreferenciaEnvio()); // 👈 NUEVO
        
        response.put("usuario", usuarioMap);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada"));
    }

    // --- MI CUENTA: ACTUALIZAR PERFIL ---
    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> updates) {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (updates.containsKey("nombre")) user.setNombreCompleto(updates.get("nombre"));
        if (updates.containsKey("alias")) user.setAlias(updates.get("alias"));
        if (updates.containsKey("telefono")) user.setTelefono(updates.get("telefono"));
        if (updates.containsKey("direccion")) user.setDireccion(updates.get("direccion"));
        if (updates.containsKey("rut")) user.setRut(updates.get("rut")); // 👈 NUEVO
        if (updates.containsKey("preferenciaEnvio")) user.setPreferenciaEnvio(updates.get("preferenciaEnvio"));

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Perfil actualizado con éxito", "user", user));
    }

    // --- MI CUENTA: CAMBIAR CONTRASEÑA ---
    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> data) {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String currentPass = data.get("currentPassword");
        String newPass = data.get("newPassword");

        if (!passwordEncoder.matches(currentPass, user.getPasswordHash())) {
            return ResponseEntity.status(400).body(Map.of("error", "La contraseña actual es incorrecta"));
        }

        user.setPasswordHash(passwordEncoder.encode(newPass));
        userRepository.save(user);
        
        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada correctamente"));
    }

    // REGISTRO
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, @RequestHeader("X-Tenant-ID") String tenantId) {
        
        if (userRepository.existsByEmailAndTenantId(request.getEmail(), tenantId)) {
            throw new ApiException("El email ya existe en esta tienda");
        }

        if (request.getAlias() == null || request.getAlias().isEmpty()) {
            throw new ApiException("El alias es obligatorio");
        }

        if (userRepository.existsByAliasAndTenantId(request.getAlias(), tenantId)) {
            throw new ApiException("El alias ya está en uso");
        }

        AppUsers user = new AppUsers();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNombreCompleto(request.getNombre());
        user.setAlias(request.getAlias());
        user.setTelefono(request.getTelefono());
        user.setDireccion(request.getDireccion());
        user.setRut(request.getRut()); // 👈 NUEVO
        user.setPreferenciaEnvio(request.getOpcionEnvio());
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
        usuarioMap.put("alias", user.getAlias());
        usuarioMap.put("role", user.getRol() != null ? user.getRol().name() : "ROLE_COMPRADOR");
        usuarioMap.put("fechaFinPrueba", user.getFechaFinPrueba());
        usuarioMap.put("suscripcionActiva", user.isSuscripcionActiva());
        usuarioMap.put("telefono", user.getTelefono());      // 👈 NUEVO
        usuarioMap.put("direccion", user.getDireccion());    // 👈 NUEVO
        usuarioMap.put("rut", user.getRut());                // 👈 NUEVO
        usuarioMap.put("preferenciaEnvio", user.getPreferenciaEnvio()); // 👈 NUEVO
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
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        String email = request.getEmail();
        String code = request.getCode();
        String newPassword = request.getNewPassword();

        com.subastashop.backend.models.PasswordResetToken token = tokenRepository.findByToken(code)
                .filter(t -> t.getEmail().equals(email))
                .orElseThrow(() -> new ApiException("Código inválido o correo incorrecto."));

        if (token.isExpired()) {
            tokenRepository.delete(token);
            throw new ApiException("El código ha expirado. Por favor solicita uno nuevo.");
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
            
            // Generar Alias por defecto (nombre sin espacios + sufijo aleatorio)
            String baseAlias = (nombre != null ? nombre.split(" ")[0] : "user").replaceAll("[^a-zA-Z0-9]", "");
            String randomSuffix = String.format("%04d", new java.util.Random().nextInt(10000));
            user.setAlias(baseAlias + "_" + randomSuffix);

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
        usuarioMap.put("alias", user.getAlias()); // 👈 IMPORTANTE: Incluir alias en la respuesta
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
        usuarioMap.put("alias", user.getAlias());
        usuarioMap.put("role", user.getRol() != null ? user.getRol().name() : "ROLE_USER");
        usuarioMap.put("fechaFinPrueba", user.getFechaFinPrueba());
        usuarioMap.put("suscripcionActiva", user.isSuscripcionActiva());
        usuarioMap.put("fechaVencimientoSuscripcion", user.getFechaVencimientoSuscripcion());
        usuarioMap.put("telefono", user.getTelefono());      // 👈 NUEVO
        usuarioMap.put("direccion", user.getDireccion());    // 👈 NUEVO
        usuarioMap.put("rut", user.getRut());                // 👈 NUEVO
        usuarioMap.put("preferenciaEnvio", user.getPreferenciaEnvio()); // 👈 NUEVO

        return ResponseEntity.ok(usuarioMap);
    }
}