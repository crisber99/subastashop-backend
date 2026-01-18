package com.subastashop.backend.controllers;

import com.subastashop.backend.config.TenantContext;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.services.JwtService;
import lombok.Data;

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
        
        Map<String, String> usuarioMap = new HashMap<>();
        usuarioMap.put("nombre", userCompleto.getNombreCompleto());
        usuarioMap.put("email", userCompleto.getEmail());
        usuarioMap.put("role", userCompleto.getRol()); 
        
        response.put("usuario", usuarioMap);

        return ResponseEntity.ok(response);
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