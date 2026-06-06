package com.subastashop.backend.services;

import com.subastashop.backend.dto.LoginRequest;
import com.subastashop.backend.dto.RegisterRequest;
import com.subastashop.backend.dto.ResetPasswordRequest;
import com.subastashop.backend.exceptions.ApiException;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.PasswordResetToken;
import com.subastashop.backend.models.Role;
import com.subastashop.backend.models.UserLegalAcceptance;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.PasswordResetTokenRepository;
import com.subastashop.backend.repositories.UserLegalAcceptanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private EmailService emailService;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;
    @Autowired
    private UserLegalAcceptanceRepository legalRepository;
    @Autowired
    private AzureBlobService azureBlobService;

    public Map<String, Object> authenticateUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        String jwt = jwtService.generateToken(userDetails);

        AppUsers userCompleto = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("Error: Usuario no encontrado."));

        return buildAuthResponse(jwt, userCompleto);
    }

    public Map<String, Object> updateProfile(String email, Map<String, String> updates) {
        AppUsers user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (updates.containsKey("nombre")) user.setNombreCompleto(updates.get("nombre"));
        if (updates.containsKey("alias")) user.setAlias(updates.get("alias"));
        if (updates.containsKey("telefono")) user.setTelefono(updates.get("telefono"));
        if (updates.containsKey("direccion")) user.setDireccion(updates.get("direccion"));
        if (updates.containsKey("rut")) user.setRut(updates.get("rut"));
        if (updates.containsKey("preferenciaEnvio")) user.setPreferenciaEnvio(updates.get("preferenciaEnvio"));
        if (updates.containsKey("profileImageUrl")) user.setProfileImageUrl(updates.get("profileImageUrl"));

        userRepository.save(user);
        return Map.of("message", "Perfil actualizado con éxito", "user", user);
    }

    public String uploadAvatar(String email, MultipartFile file) throws Exception {
        AppUsers user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String url = azureBlobService.subirImagen(file);
        user.setProfileImageUrl(url);
        userRepository.save(user);
        return url;
    }

    public void changePassword(String email, String currentPass, String newPass) {
        AppUsers user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!passwordEncoder.matches(currentPass, user.getPasswordHash())) {
            throw new ApiException("La contraseña actual es incorrecta");
        }

        user.setPasswordHash(passwordEncoder.encode(newPass));
        userRepository.save(user);
    }

    public Map<String, Object> registerUser(RegisterRequest request, String tenantId, String ipAddress, String userAgent) {
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
        user.setRut(request.getRut());
        user.setPreferenciaEnvio(request.getOpcionEnvio());
        user.setRol(Role.ROLE_COMPRADOR);
        user.setTenantId(tenantId);

        userRepository.save(user);

        if (request.isAceptaTerminos()) {
            try {
                UserLegalAcceptance acceptance = new UserLegalAcceptance();
                acceptance.setUserId(user.getId());
                acceptance.setTermsVersion("v1.0");
                acceptance.setAcceptanceTimestamp(LocalDateTime.now());
                acceptance.setIpAddress(ipAddress);
                acceptance.setUserAgent(userAgent);
                acceptance.setType("USER_REGISTRATION");
                legalRepository.save(acceptance);
            } catch (Exception e) {
                System.err.println("Error al grabar aceptación legal en registro: " + e.getMessage());
            }
        }

        String asunto = "¡Bienvenido a SubastaShop!";
        String mensaje = "Hola " + user.getNombreCompleto() + ",<br><br>" +
                "¡Bienvenido a <b>SubastaShop</b>!<br><br>" +
                "Tu cuenta ha sido creada exitosamente.<br><br>" +
                "Saludos,<br>El equipo de SubastaShop";
        emailService.enviarCorreo(user.getEmail(), asunto, mensaje);

        String token = jwtService.generateToken(user);
        return buildAuthResponse(token, user);
    }

    public void forgotPassword(String email) {
        AppUsers user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("No encontramos una cuenta con ese correo."));

        tokenRepository.deleteByEmail(email);

        String code = String.format("%06d", new java.util.Random().nextInt(999999));

        PasswordResetToken token = new PasswordResetToken();
        token.setEmail(email);
        token.setToken(code);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(15));
        tokenRepository.save(token);

        String asunto = "Código de Recuperación - SubastaShop";
        String mensaje = "Hola " + user.getNombreCompleto() + ",<br><br>" +
                "Has solicitado restablecer tu contraseña. Tu código de seguridad es:<br><br>" +
                "<h2 style='color: #6366f1;'>" + code + "</h2><br>" +
                "Este código expirará en 15 minutos.<br><br>" +
                "Si no solicitaste esto, puedes ignorar este correo.<br><br>" +
                "Saludos,<br>El equipo de SubastaShop";

        emailService.enviarCorreo(email, asunto, mensaje);
    }

    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = tokenRepository.findByToken(request.getCode())
                .filter(t -> t.getEmail().equals(request.getEmail()))
                .orElseThrow(() -> new ApiException("Código inválido o correo incorrecto."));

        if (token.isExpired()) {
            tokenRepository.delete(token);
            throw new ApiException("El código ha expirado. Por favor solicita uno nuevo.");
        }

        AppUsers user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException("Usuario no encontrado."));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        tokenRepository.delete(token);
    }

    public Map<String, Object> socialLogin(String provider, String socialToken, String tenantId) {
        String email = null;
        String nombre = null;

        try {
            RestTemplate restTemplate = new RestTemplate();

            if ("GOOGLE".equalsIgnoreCase(provider)) {
                String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + socialToken;
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                if (response != null && response.containsKey("email")) {
                    email = (String) response.get("email");
                    nombre = (String) response.get("name");
                } else {
                    throw new ApiException("Token de Google inválido.");
                }
            } else if ("FACEBOOK".equalsIgnoreCase(provider)) {
                String url = "https://graph.facebook.com/me?fields=id,name,email&access_token=" + socialToken;
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                if (response != null && response.containsKey("email")) {
                    email = (String) response.get("email");
                    nombre = (String) response.get("name");
                } else {
                    throw new ApiException("Token de Facebook inválido o sin permisos de email.");
                }
            } else if ("APPLE".equalsIgnoreCase(provider)) {
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

        if (user == null) {
            user = new AppUsers();
            user.setEmail(email);
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString() + "aA1!social"));
            user.setNombreCompleto(nombre != null ? nombre : "Usuario " + provider);

            String baseAlias = (nombre != null ? nombre.split(" ")[0] : "user").replaceAll("[^a-zA-Z0-9]", "");
            String randomSuffix = String.format("%04d", new java.util.Random().nextInt(10000));
            user.setAlias(baseAlias + "_" + randomSuffix);

            user.setRol(Role.ROLE_COMPRADOR);
            user.setTenantId(tenantId);
            userRepository.save(user);

            emailService.enviarCorreo(email, "¡Bienvenido a SubastaShop!", "Te has registrado exitosamente autorizando a " + provider + ".");
        }

        String jwt = jwtService.generateToken(user);
        return buildAuthResponse(jwt, user);
    }

    public Map<String, Object> getMe(String email) {
        AppUsers user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return buildUserMap(user);
    }

    private Map<String, Object> buildAuthResponse(String token, AppUsers user) {
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("usuario", buildUserMap(user));
        return response;
    }

    private Map<String, Object> buildUserMap(AppUsers user) {
        Map<String, Object> usuarioMap = new HashMap<>();
        usuarioMap.put("id", user.getId());
        usuarioMap.put("nombre", user.getNombreCompleto());
        usuarioMap.put("email", user.getEmail());
        usuarioMap.put("alias", user.getAlias());
        usuarioMap.put("role", user.getRol() != null ? user.getRol().name() : "ROLE_COMPRADOR");
        usuarioMap.put("fechaFinPrueba", user.getFechaFinPrueba());
        usuarioMap.put("suscripcionActiva", user.isSuscripcionActiva());
        usuarioMap.put("fechaVencimientoSuscripcion", user.getFechaVencimientoSuscripcion());
        usuarioMap.put("telefono", user.getTelefono());
        usuarioMap.put("direccion", user.getDireccion());
        usuarioMap.put("rut", user.getRut());
        usuarioMap.put("preferenciaEnvio", user.getPreferenciaEnvio());
        usuarioMap.put("profileImageUrl", user.getProfileImageUrl());
        return usuarioMap;
    }
}
