package com.subastashop.backend.controllers;

import com.subastashop.backend.dtos.CalificacionRequestDTO;
import com.subastashop.backend.models.Calificacion;
import com.subastashop.backend.services.CalificacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calificaciones")
public class CalificacionController {

    private final CalificacionService calificacionService;

    public CalificacionController(CalificacionService calificacionService) {
        this.calificacionService = calificacionService;
    }

    @GetMapping("/producto/{productoId}")
    public ResponseEntity<List<Calificacion>> obtenerPorProducto(@PathVariable Long productoId) {
        return ResponseEntity.ok(calificacionService.obtenerPorProducto(productoId));
    }

    @PostMapping
    public ResponseEntity<Calificacion> crearCalificacion(@Valid @RequestBody CalificacionRequestDTO requestDto, 
                                                          Authentication authentication) {
        Calificacion nuevaCalificacion = calificacionService.crearCalificacion(requestDto, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevaCalificacion);
    }
}
