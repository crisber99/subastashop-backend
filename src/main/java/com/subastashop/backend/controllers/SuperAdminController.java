package com.subastashop.backend.controllers;

import com.subastashop.backend.models.*;
import com.subastashop.backend.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/super-admin")
// ¡IMPORTANTE! En SecurityConfig debes proteger esto: .requestMatchers("/api/super-admin/**").hasAuthority("ROLE_SUPER_ADMIN")
public class SuperAdminController {

    @Autowired private TiendaRepository tiendaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // 1. VER TODAS LAS TIENDAS
    @GetMapping("/tiendas")
    public List<Tienda> listarTiendas() {
        return tiendaRepository.findAll();
    }

    // 2. CREAR UNA NUEVA TIENDA (ONBOARDING)
    @PostMapping("/tiendas")
    public ResponseEntity<?> crearTienda(@RequestBody Tienda nuevaTienda) {
        if (tiendaRepository.existsBySlug(nuevaTienda.getSlug())) { // Crear existsBySlug en Repo
             return ResponseEntity.badRequest().body("Ese slug/URL ya está ocupado.");
        }
        return ResponseEntity.ok(tiendaRepository.save(nuevaTienda));
    }

    // 3. CREAR UN ADMINISTRADOR PARA UNA TIENDA
    @PostMapping("/tiendas/{tiendaId}/admin")
    public ResponseEntity<?> crearAdminTienda(@PathVariable Long tiendaId, @RequestBody AppUsers nuevoAdmin) {
        Tienda tienda = tiendaRepository.findById(tiendaId).orElseThrow();

        nuevoAdmin.setTienda(tienda);
        nuevoAdmin.setRol(Role.ROLE_ADMIN); // Admin de ESA tienda
        nuevoAdmin.setPassword(passwordEncoder.encode(nuevoAdmin.getPassword()));
        
        usuarioRepository.save(nuevoAdmin);
        
        return ResponseEntity.ok("Admin creado para la tienda: " + tienda.getNombre());
    }
}