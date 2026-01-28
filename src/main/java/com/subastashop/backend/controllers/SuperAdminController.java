package com.subastashop.backend.controllers;

import com.subastashop.backend.dto.CrearTiendaDTO;
import com.subastashop.backend.models.*;
import com.subastashop.backend.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/super-admin")
// ¡IMPORTANTE! En SecurityConfig debes proteger esto:
// .requestMatchers("/api/super-admin/**").hasAuthority("ROLE_SUPER_ADMIN")
public class SuperAdminController {

    @Autowired
    private TiendaRepository tiendaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

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

    @PostMapping("/crear")
    public ResponseEntity<?> crearTienda(@RequestBody CrearTiendaDTO dto) {

        // A. Validar que la URL (slug) no exista
        if (tiendaRepository.existsBySlug(dto.getSlug())) {
            return ResponseEntity.badRequest().body("❌ Error: La URL '" + dto.getSlug() + "' ya está ocupada.");
        }

        // B. Validar que el usuario exista (El dueño debe registrarse primero)
        AppUsers nuevoDueño = usuarioRepository.findByEmail(dto.getEmailAdmin())
                .orElseThrow(() -> new RuntimeException(
                        "❌ El usuario " + dto.getEmailAdmin() + " no existe. Pídele que se registre primero."));

        // C. Validar que el usuario NO tenga ya una tienda
        if (nuevoDueño.getTienda() != null) {
            return ResponseEntity.badRequest()
                    .body("❌ Este usuario ya es dueño de '" + nuevoDueño.getTienda().getNombre() + "'.");
        }

        // D. Crear la Tienda
        Tienda tienda = new Tienda();
        tienda.setNombre(dto.getNombre());
        tienda.setSlug(dto.getSlug());
        tienda.setActiva(true);
        // Puedes agregar logoUrl o colores aquí si los mandas en el DTO

        Tienda tiendaGuardada = tiendaRepository.save(tienda);

        // E. Actualizar al Usuario (Darle Rol y Tienda)
        nuevoDueño.setTienda(tiendaGuardada);
        nuevoDueño.setRol(Role.ROLE_ADMIN); // Le damos poder de Admin
        usuarioRepository.save(nuevoDueño);

        return ResponseEntity.ok("✅ Tienda '" + tienda.getNombre() + "' creada y asignada a " + nuevoDueño.getEmail());
    }
}