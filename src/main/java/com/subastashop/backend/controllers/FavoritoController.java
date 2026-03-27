package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Favorito;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.FavoritoRepository;
import com.subastashop.backend.repositories.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/favoritos")
public class FavoritoController {

    @Autowired
    private FavoritoRepository favoritoRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    private AppUsers obtenerUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return appUserRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    @GetMapping
    public ResponseEntity<List<Producto>> listarFavoritos() {
        AppUsers usuario = obtenerUsuarioAutenticado();
        List<Producto> productos = favoritoRepository.findByUsuarioIdOrderByFechaCreacionDesc(usuario.getId())
                .stream()
                .map(Favorito::getProducto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(productos);
    }

    @GetMapping("/ids")
    public ResponseEntity<List<Integer>> listarIdsFavoritos() {
        AppUsers usuario = obtenerUsuarioAutenticado();
        List<Integer> ids = favoritoRepository.findByUsuarioIdOrderByFechaCreacionDesc(usuario.getId())
                .stream()
                .map(f -> f.getProducto().getId())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ids);
    }

    @PostMapping("/{productoId}")
    @Transactional
    public ResponseEntity<?> agregarFavorito(@PathVariable Integer productoId) {
        AppUsers usuario = obtenerUsuarioAutenticado();

        if (favoritoRepository.existsByUsuarioIdAndProductoId(usuario.getId(), productoId)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Este producto ya está en tus favoritos"));
        }

        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        Favorito favorito = new Favorito();
        favorito.setUsuario(usuario);
        favorito.setProducto(producto);
        favorito.setFechaCreacion(LocalDateTime.now());
        
        favoritoRepository.save(favorito);
        return ResponseEntity.ok(Map.of("message", "Producto agregado a favoritos"));
    }

    @DeleteMapping("/{productoId}")
    @Transactional
    public ResponseEntity<?> eliminarFavorito(@PathVariable Integer productoId) {
        AppUsers usuario = obtenerUsuarioAutenticado();
        favoritoRepository.deleteByUsuarioIdAndProductoId(usuario.getId(), productoId);
        return ResponseEntity.ok(Map.of("message", "Producto eliminado de favoritos"));
    }
}
