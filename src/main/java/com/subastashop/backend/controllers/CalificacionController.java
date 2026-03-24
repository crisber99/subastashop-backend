package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Calificacion;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.CalificacionRepository;
import com.subastashop.backend.repositories.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/calificaciones")
public class CalificacionController {

    @Autowired
    private CalificacionRepository calificacionRepository;
    @Autowired
    private ProductoRepository productoRepository;
    @Autowired
    private AppUserRepository userRepository;

    @GetMapping("/producto/{productoId}")
    public ResponseEntity<List<Calificacion>> obtenerPorProducto(@PathVariable Long productoId) {
        return ResponseEntity.ok(calificacionRepository.findByProductoId(productoId));
    }

    @PostMapping
    public ResponseEntity<?> crearCalificacion(@RequestBody Map<String, Object> body, Authentication authentication) {
        try {
            Integer productoId = Integer.valueOf(body.get("productoId").toString());
            Integer puntuacion = Integer.valueOf(body.get("puntuacion").toString());
            String comentario = (String) body.get("comentario");

            AppUsers usuario = userRepository.findByEmail(authentication.getName()).orElseThrow();
            Producto producto = productoRepository.findById(productoId).orElseThrow();

            Calificacion calificacion = new Calificacion();
            calificacion.setProducto(producto);
            calificacion.setUsuario(usuario);
            calificacion.setPuntuacion(puntuacion);
            calificacion.setComentario(comentario);

            return ResponseEntity.ok(calificacionRepository.save(calificacion));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al guardar calificación: " + e.getMessage());
        }
    }
}
