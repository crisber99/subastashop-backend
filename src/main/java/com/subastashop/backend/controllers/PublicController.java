package com.subastashop.backend.controllers;

import com.subastashop.backend.models.Producto; // Importar Producto
import com.subastashop.backend.models.Tienda;
import com.subastashop.backend.repositories.ProductoRepository; // Importar Repo Producto
import com.subastashop.backend.repositories.TiendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity; // Importar ResponseEntity
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable; // Importar PathVariable
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    @Autowired
    private TiendaRepository tiendaRepository;

    @Autowired
    private ProductoRepository productoRepository; // 👈 1. NECESITAMOS ESTO

    // Endpoint para la Landing Page (Listar Tiendas)
    @GetMapping("/tiendas")
    public List<Tienda> obtenerTiendasActivas() {
        return tiendaRepository.findAll();
    }

    @GetMapping("/productos/tienda/{slug}")
    public ResponseEntity<List<Producto>> obtenerProductosPorTienda(@PathVariable String slug) {

        // A. Buscamos la tienda por su nombre URL (ej: 'don-bernardo')
        Tienda tienda = tiendaRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Tienda no encontrada: " + slug));

        // B. Buscamos los productos que pertenecen a esa ID de tienda
        List<Producto> productos = productoRepository.findByTiendaId(tienda.getId());

        return ResponseEntity.ok(productos);
    }

    public ResponseEntity<?> obtenerTiendaPorSlug(@PathVariable String slug) {
        // Buscamos la tienda. Si no existe, devolvemos 404.
        // Asegúrate de tener este método findBySlug en tu TiendaRepository
        return tiendaRepository.findBySlug(slug)
                .map(tienda -> ResponseEntity.ok(tienda))
                .orElse(ResponseEntity.notFound().build());
    }
}