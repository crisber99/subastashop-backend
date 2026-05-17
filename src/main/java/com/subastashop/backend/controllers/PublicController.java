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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.subastashop.backend.models.PrelaunchSubscriber;
import com.subastashop.backend.repositories.PrelaunchSubscriberRepository;
import java.util.Map;
import java.util.HashMap;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final TiendaRepository tiendaRepository;
    private final ProductoRepository productoRepository;
    private final PrelaunchSubscriberRepository prelaunchSubscriberRepository;

    public PublicController(TiendaRepository tiendaRepository, ProductoRepository productoRepository, PrelaunchSubscriberRepository prelaunchSubscriberRepository) {
        this.tiendaRepository = tiendaRepository;
        this.productoRepository = productoRepository;
        this.prelaunchSubscriberRepository = prelaunchSubscriberRepository;
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

    @GetMapping("/productos/destacados")
    @Cacheable("productosDestacados")
    public List<Producto> obtenerProductosDestacados() {
        // Listamos los 12 más recientes que estén marcados como destacados 
        // y que no estén terminados o eliminados.
        List<String> estadosValidos = List.of("DISPONIBLE", "EN_SUBASTA");
        return productoRepository.findTop12ByDestacadoTrueAndEstadoInOrderByFechaCreacionDesc(estadosValidos);
    }

    @GetMapping("/tiendas/{slug}")
    public ResponseEntity<TiendaPublicDTO> obtenerTiendaPorSlug(@PathVariable("slug") String slug) {
        return tiendaRepository.findBySlug(slug.toLowerCase())
                .map(t -> ResponseEntity.ok(mapToDTO(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/prelaunch/subscribe")
    public ResponseEntity<?> subscribeToPrelaunch(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email es requerido"));
        }
        if (prelaunchSubscriberRepository.existsByEmail(email)) {
            return ResponseEntity.ok(Map.of("message", "Ya estabas suscrito. ¡Pronto te avisaremos!"));
        }
        
        PrelaunchSubscriber sub = new PrelaunchSubscriber();
        sub.setEmail(email);
        prelaunchSubscriberRepository.save(sub);
        
        return ResponseEntity.ok(Map.of("message", "¡Gracias! Te avisaremos apenas abramos."));
    }

    @GetMapping("/chollos-ganados")
    @Cacheable("chollosGanados")
    public List<Producto> obtenerChollosGanados() {
        return productoRepository.findTop10ByEstadoOrderByFechaFinSubastaDesc("ADJUDICADO");
    }
}