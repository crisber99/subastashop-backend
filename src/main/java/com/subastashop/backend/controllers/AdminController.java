package com.subastashop.backend.controllers;

import com.subastashop.backend.exceptions.ApiException;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Producto;
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
            throw new ApiException("No tienes una tienda asignada para ver estadísticas.");
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

    // 🚀 ACCIÓN: DETENER TODAS LAS SUBASTAS ACTIVAS
    @PostMapping("/detener-subastas")
    public ResponseEntity<?> detenerSubastas() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers admin = usuarioRepository.findByEmail(email).orElseThrow();
        Long tiendaId = admin.getTienda().getId();

        // Buscamos todas las subastas de esta tienda y las marcamos como FINALIZADA
        var subastas = productoRepository.findByTiendaIdAndEstado(tiendaId, "SUBASTA");
        for (var p : subastas) {
            p.setEstado("FINALIZADA");
            productoRepository.save(p);
        }

        return ResponseEntity.ok("Se han detenido " + subastas.size() + " subastas.");
    }

    // 🚀 ACCIÓN: NOTIFICAR A GANADORES (Dummy logic for now, but endpoints ready)
    @PostMapping("/notificar-ganadores")
    public ResponseEntity<?> notificarGanadores() {
        // Aquí podrías buscar subastas finalizadas sin orden creada y enviar correos
        return ResponseEntity.ok("Notificaciones enviadas correctamente.");
    }

    // 🚀 ACCIÓN: EXPORTAR VENTAS A EXCEL (CSV)
    @GetMapping("/exportar-ventas")
    public ResponseEntity<byte[]> exportarVentas() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers admin = usuarioRepository.findByEmail(email).orElseThrow();
        
        // Simulación de generación de CSV
        StringBuilder csv = new StringBuilder();
        csv.append("ID;Producto;Precio;Estado;Fecha\n");
        csv.append("101;Producto Pro;500.0;PAGADO;2026-03-19\n");
        csv.append("102;Subasta Test;1200.0;PENDIENTE;2026-03-18\n");

        byte[] content = csv.toString().getBytes();
        
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=\"ventas_tienda.csv\"")
                .body(content);
    }

    @PutMapping("/usuarios/{id}/rol")
    public ResponseEntity<?> cambiarRol(@PathVariable Integer id, @RequestBody String nuevoRol) {
        AppUsers usuario = usuarioRepository.findById(id).orElseThrow();
        usuario.setRol(Role.ROLE_VENDEDOR); // "ROLE_VENDEDOR"
        usuarioRepository.save(usuario);
        return ResponseEntity.ok("Rol actualizado");
    }
}