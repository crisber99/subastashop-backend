package com.subastashop.backend.controllers;

import com.subastashop.backend.dto.CrearTiendaDTO;
import com.subastashop.backend.models.*;
import com.subastashop.backend.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @GetMapping
    public ResponseEntity<List<AppUsers>> listarUsuarios() {
        // En un sistema real, aquí usarías paginación (Pageable)
        return ResponseEntity.ok(usuarioRepository.findAll());
    }

    @PutMapping("/{id}/rol")
    public ResponseEntity<?> cambiarRol(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        String nuevoRolTexto = body.get("rol"); // Esperamos json: { "rol": "ROLE_ADMIN" }

        AppUsers usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Evitar que el Super Admin se bloquee a sí mismo o se quite el rol
        if (usuario.getRol().name().equals("ROLE_SUPER_ADMIN")) {
            return ResponseEntity.badRequest().body("No puedes modificar al Super Admin Supremo.");
        }

        try {
            // 2. CORRECCIÓN DEL ROL: Convertir String -> Enum
            // Asumiendo que tu Enum se llama 'Role'
            Role rolEnum = Role.valueOf(nuevoRolTexto);
            usuario.setRol(rolEnum);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("El rol enviado no existe.");
        }

        usuarioRepository.save(usuario);

        return ResponseEntity.ok("Rol actualizado a: " + nuevoRolTexto);
    }

    // 3. ELIMINAR/BLOQUEAR USUARIO
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarUsuario(@PathVariable Integer id) {
        // Lógica para borrar o desactivar (soft delete recomendado)
        usuarioRepository.deleteById(id);
        return ResponseEntity.ok("Usuario eliminado");
    }

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