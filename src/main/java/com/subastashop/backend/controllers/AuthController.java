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
        if (userRepository.existsByEmailAndTenantId(request.getEmail(), tenantId)) {
            throw new ApiException("El email ya existe en esta tienda");
        }

        // VALIDACIÓN DE CONTRASEÑA 🔐
        // Al menos 8 caracteres, una letra y un número
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
                             "Tu cuenta ha sido creada exitosamente. Explora nuestras tiendas, únete a subastas y participa en nuestras rifas exclusivas.<br><br>" +
                             "Saludos,<br>El equipo de SubastaShop";
        emailService.enviarCorreo(user.getEmail(), asunto, mensaje);

        String token = jwtService.generateToken(user);
        
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        Map<String, Object> usuarioMap = new HashMap<>();
        usuarioMap.put("nombre", user.getNombreCompleto());
        usuarioMap.put("email", user.getEmail());
        usuarioMap.put("role", user.getRol() != null ? user.getRol().name() : "ROLE_COMPRADOR");
        usuarioMap.put("fechaFinPrueba", user.getFechaFinPrueba());
        usuarioMap.put("suscripcionActiva", user.isSuscripcionActiva());
        response.put("usuario", usuarioMap);

        return ResponseEntity.ok(response);
    }
}