package com.subastashop.backend.controllers;

import com.subastashop.backend.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private ProductoRepository productoRepository;
    @Autowired
    private AppUserRepository usuarioRepository;
    @Autowired
    private OrdenRepository ordenRepository;

    @GetMapping("/stats")
    public ResponseEntity<?> obtenerEstadisticas() {
        Map<String, Object> stats = new HashMap<>();
        
        // 1. Total de Usuarios
        stats.put("totalUsuarios", usuarioRepository.count());
        
        // 2. Productos Activos (En Subasta)
        stats.put("subastasActivas", productoRepository.countByEstado("SUBASTA"));
        
        // 3. Ventas Totales (Productos Pagados) - Esto es un ejemplo simplificado
        // En un caso real harías una SUM(monto) en SQL
        long ventasCerradas = ordenRepository.count(); 
        stats.put("ventasCerradas", ventasCerradas);
        
        // 4. Ganancias Totales (Simulado: suma de todas las ordenes)
        // Lo ideal sería un Query personalizado en el Repository: @Query("SELECT SUM(o.montoFinal) FROM Orden o")
        stats.put("gananciasTotales", 1500000); // Dummy por ahora

        return ResponseEntity.ok(stats);
    }
}