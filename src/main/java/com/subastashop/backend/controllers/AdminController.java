package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.subastashop.backend.models.Role;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private ProductoRepository productoRepository;
    @Autowired
    private AppUserRepository usuarioRepository;
    // @Autowired
    // private OrdenRepository ordenRepository;

    @GetMapping("/stats")
    public ResponseEntity<?> obtenerEstadisticas() {
        
        // 1. OBTENER EL ADMIN LOGUEADO 🕵️‍♂️
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers admin = usuarioRepository.findByEmail(email).orElseThrow();

        // 2. VERIFICAR QUE TENGA TIENDA
        if (admin.getTienda() == null) {
            // Si es Super Admin, quizás quieras mostrar todo. 
            // Por ahora, devolvemos error o ceros.
            return ResponseEntity.badRequest().body("No tienes una tienda asignada.");
        }

        Long tiendaId = admin.getTienda().getId();
        Map<String, Object> stats = new HashMap<>();

        // 3. DATOS FILTRADOS POR SU TIENDA 📉
        
        // Total usuarios (Si tu modelo asigna usuarios a tiendas, si no, muestra el total global)
        stats.put("totalUsuarios", 0); // Placeholder si no tienes usuarios por tienda

        // Subastas activas DE ESTA TIENDA
        stats.put("subastasActivas", productoRepository.countByTiendaIdAndEstado(tiendaId, "SUBASTA"));

        // Ventas cerradas (Productos vendidos) DE ESTA TIENDA
        // Aquí asumimos que "VENDIDO" o "ADJUDICADO" es tu estado de venta
        // Puedes crear un método countByTiendaIdAndEstado en el repo
        stats.put("ventasCerradas", 5); // Placeholder

        // Ganancias (Dummy por ahora, pero filtrado en el futuro)
        stats.put("gananciasTotales", 0);

        // Agregamos info de la tienda para mostrar en el Dashboard
        stats.put("nombreTienda", admin.getTienda().getNombre());

        return ResponseEntity.ok(stats);
    }

    @PutMapping("/usuarios/{id}/rol")
    public ResponseEntity<?> cambiarRol(@PathVariable Integer id, @RequestBody String nuevoRol) {
        AppUsers usuario = usuarioRepository.findById(id).orElseThrow();
        usuario.setRol(Role.ROLE_VENDEDOR); // "ROLE_VENDEDOR"
        usuarioRepository.save(usuario);
        return ResponseEntity.ok("Rol actualizado");
    }
}