package com.subastashop.backend.controllers;

import com.subastashop.backend.config.TenantContext;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.services.JwtService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder; // Necesitaremos configurar esto

    @Autowired
    private JwtService jwtService;

    // LOGIN
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // 1. Buscar usuario
        AppUsers user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 2. Verificar password (ojo: en producción usar AuthenticationManager)
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.badRequest().body("Contraseña incorrecta");
        }

        // 3. Generar Token
        String token = jwtService.generateToken(user);
        
        return ResponseEntity.ok(new AuthResponse(token, user.getNombreCompleto(), user.getId()));
    }

    // REGISTRO
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request, @RequestHeader("X-Tenant-ID") String tenantId) {
        if (userRepository.existsByEmailAndTenantId(request.getEmail(), tenantId)) {
            return ResponseEntity.badRequest().body("El email ya existe en esta tienda");
        }

        AppUsers user = new AppUsers();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNombreCompleto(request.getNombre());
        user.setRol("COMPRADOR");
        user.setTenantId(tenantId);

        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(token, user.getNombreCompleto(), user.getId()));
    }
}

// Clases auxiliares (DTOs) para no crear archivos aparte
@Data
class LoginRequest {
    private String email;
    private String password;
}

@Data
class RegisterRequest {
    private String email;
    private String password;
    private String nombre;
}

@Data
class AuthResponse {
    private String token;
    private String nombre;
    private Integer userId;
    
    public AuthResponse(String token, String nombre, Integer userId) {
        this.token = token;
        this.nombre = nombre;
        this.userId = userId;
    }
}