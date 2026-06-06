package com.subastashop.backend.controllers;

import com.subastashop.backend.dto.CrearTiendaDTO;
import com.subastashop.backend.models.*;
import com.subastashop.backend.services.SuperAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/super-admin")
// ¡IMPORTANTE! En SecurityConfig debes proteger esto:
// .requestMatchers("/api/super-admin/**").hasAuthority("ROLE_SUPER_ADMIN")
public class SuperAdminController {

    @Autowired
    private SuperAdminService superAdminService;

    @GetMapping
    public ResponseEntity<List<AppUsers>> listarUsuarios() {
        return ResponseEntity.ok(superAdminService.listarUsuarios());
    }

    @PutMapping("/{id}/rol")
    public ResponseEntity<?> cambiarRol(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        try {
            superAdminService.cambiarRol(id, body.get("rol"));
            return ResponseEntity.ok(Map.of("mensaje", "Rol actualizado a: " + body.get("rol")));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/regalar-suscripcion")
    public ResponseEntity<?> regalarSuscripcion(@PathVariable Integer id) {
        try {
            superAdminService.regalarSuscripcion(id);
            return ResponseEntity.ok(Map.of("mensaje", "Suscripción PRO regalada con éxito"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarUsuario(@PathVariable Integer id) {
        superAdminService.eliminarUsuario(id);
        return ResponseEntity.ok(Map.of("mensaje", "Usuario eliminado"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarUsuario(@PathVariable Integer id, @RequestBody AppUsers datos) {
        superAdminService.actualizarUsuario(id, datos);
        return ResponseEntity.ok(Map.of("mensaje", "Usuario actualizado correctamente"));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(superAdminService.getStats());
    }

    @GetMapping("/tiendas")
    public List<Tienda> listarTiendas() {
        return superAdminService.listarTiendas();
    }

    @PostMapping("/tiendas")
    public ResponseEntity<?> crearTienda(@RequestBody Tienda nuevaTienda) {
        try {
            return ResponseEntity.ok(superAdminService.crearTienda(nuevaTienda));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/tiendas/{tiendaId}/admin")
    public ResponseEntity<?> crearAdminTienda(@PathVariable Long tiendaId, @RequestBody AppUsers nuevoAdmin) {
        try {
            superAdminService.crearAdminTienda(tiendaId, nuevoAdmin);
            return ResponseEntity.ok(Map.of("mensaje", "Admin creado para la tienda"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/crear")
    public ResponseEntity<?> crearTiendaDTO(@RequestBody CrearTiendaDTO dto) {
        try {
            superAdminService.crearTiendaDTO(dto);
            return ResponseEntity.ok(Map.of("mensaje", "✅ Tienda creada y asignada"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/global-productos")
    public ResponseEntity<List<Producto>> listarTodosLosProductos() {
        return ResponseEntity.ok(superAdminService.listarTodosLosProductos());
    }

    @DeleteMapping("/tiendas/{id}")
    public ResponseEntity<?> eliminarTienda(@PathVariable Long id) {
        try {
            superAdminService.eliminarTienda(id);
            return ResponseEntity.ok(Map.of("mensaje", "Tienda eliminada correctamente."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/tiendas/{id}")
    public ResponseEntity<?> actualizarTienda(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            superAdminService.actualizarTienda(id, body);
            return ResponseEntity.ok(Map.of("mensaje", "Tienda actualizada"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/prelaunch/subscribers")
    public ResponseEntity<List<PrelaunchSubscriber>> listarSuscriptoresLanzamiento() {
        return ResponseEntity.ok(superAdminService.listarSuscriptoresLanzamiento());
    }
}