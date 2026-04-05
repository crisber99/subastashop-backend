package com.subastashop.backend.controllers;

import com.subastashop.backend.dtos.TiendaPublicDTO;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.Tienda;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.TiendaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.Cacheable;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final TiendaRepository tiendaRepository;
    private final ProductoRepository productoRepository;

    public PublicController(TiendaRepository tiendaRepository, ProductoRepository productoRepository) {
        this.tiendaRepository = tiendaRepository;
        this.productoRepository = productoRepository;
    }

    private TiendaPublicDTO mapToDTO(Tienda t) {
        return new TiendaPublicDTO(
                t.getId(),
                t.getNombre(),
                t.getSlug(),
                t.getLogoUrl(),
                t.getColorPrimario(),
                t.getActiva(),
                t.isIdentidadVerificada(),
                t.getFechaCreacion(),
                t.getWhatsapp());
    }

    // Endpoint para la Landing Page (Listar Tiendas)
    @GetMapping("/tiendas")
    @Cacheable("tiendasActivas")
    public List<TiendaPublicDTO> obtenerTiendasActivas() {
        return tiendaRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/productos/tienda/{slug}")
    @Cacheable(value = "productosPublicos", key = "#slug + '_' + #pageable.pageNumber")
    public ResponseEntity<Page<Producto>> obtenerProductosPorTienda(
            @PathVariable("slug") String slug,
            Pageable pageable) {
        Tienda tienda = tiendaRepository.findBySlug(slug.toLowerCase())
                .orElseThrow(() -> new RuntimeException("Tienda no encontrada: " + slug));
        Page<Producto> productos = productoRepository.findByTiendaId(tienda.getId(), pageable);
        return ResponseEntity.ok(productos);
    }

    @GetMapping("/tiendas/{slug}")
    public ResponseEntity<TiendaPublicDTO> obtenerTiendaPorSlug(@PathVariable("slug") String slug) {
        return tiendaRepository.findBySlug(slug.toLowerCase())
                .map(t -> ResponseEntity.ok(mapToDTO(t)))
                .orElse(ResponseEntity.notFound().build());
    }
}