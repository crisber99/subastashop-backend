package com.subastashop.backend.controllers;

import com.subastashop.backend.dto.CuponDTO;
import com.subastashop.backend.services.CuponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cupones")
public class CuponController {

    @Autowired
    private CuponService cuponService;

    // --- Endpoints para el Vendedor (Admin) ---
    
    @PostMapping("/admin")
    public ResponseEntity<Cupon> crearCupon(@RequestBody CuponDTO dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(cuponService.crearCupon(email, dto));
    }

    @GetMapping("/admin")
    public ResponseEntity<List<CuponDTO>> obtenerMisCupones() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(cuponService.obtenerMisCupones(email));
    }

    @PutMapping("/admin/{id}/toggle")
    public ResponseEntity<Void> toggleEstado(@PathVariable Integer id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        cuponService.cambiarEstado(id, email);
        return ResponseEntity.ok().build();
    }

    // --- Endpoints Públicos (Validación en Carrito) ---

    @GetMapping("/validar")
    public ResponseEntity<CuponDTO> validarCupon(@RequestParam String codigo, @RequestParam Integer tiendaId) {
        return ResponseEntity.ok(cuponService.validarCupon(codigo, tiendaId));
    }
}
