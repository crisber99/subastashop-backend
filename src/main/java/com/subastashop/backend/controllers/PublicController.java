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
    private final com.subastashop.backend.services.EmailService emailService;

    public PublicController(TiendaRepository tiendaRepository, ProductoRepository productoRepository, PrelaunchSubscriberRepository prelaunchSubscriberRepository, com.subastashop.backend.services.EmailService emailService) {
        this.tiendaRepository = tiendaRepository;
        this.productoRepository = productoRepository;
        this.prelaunchSubscriberRepository = prelaunchSubscriberRepository;
        this.emailService = emailService;
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
        String rawEmail = payload.get("email");
        if (rawEmail == null || rawEmail.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email es requerido"));
        }
        
        String email = rawEmail.trim().toLowerCase();

        if (prelaunchSubscriberRepository.existsByEmail(email)) {
            return ResponseEntity.ok(Map.of("message", "Ya estabas suscrito. ¡Pronto te avisaremos!"));
        }
        
        PrelaunchSubscriber sub = new PrelaunchSubscriber();
        sub.setEmail(email);
        
        try {
            prelaunchSubscriberRepository.save(sub);
        } catch (Exception e) {
            // Si por condiciones de carrera o bases de datos choca con otro, 
            // simplemente decimos que ya estaba suscrito en lugar de lanzar 500 Error
            return ResponseEntity.ok(Map.of("message", "Ya estabas suscrito. ¡Pronto te avisaremos!"));
        }

        // Enviar email de bienvenida de forma asíncrona
        new Thread(() -> {
            try {
                String html = "<div style='font-family: sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #eee; border-radius: 10px;'>"
                        + "<h2 style='color: #4f46e5; text-align: center;'>¡Estás en la lista! 🚀</h2>"
                        + "<p>Hola,</p>"
                        + "<p>Te has suscrito exitosamente a la lista de espera exclusiva del <b>Gran Lanzamiento de SubastaShop</b>.</p>"
                        + "<p>Serás de las primeras personas en enterarte apenas abramos nuestras puertas, para que puedas asegurar los cupos Fundador y acceder a los mejores chollos antes que nadie.</p>"
                        + "<br><p>Mantente atento a este correo.</p>"
                        + "<p><b>El equipo de SubastaShop</b></p>"
                        + "</div>";
                emailService.enviarCorreo(email, "🚀 ¡Estás en la lista VIP de SubastaShop!", html);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
        
        return ResponseEntity.ok(Map.of("message", "¡Revisa tu bandeja! Te hemos enviado un correo de confirmación."));
    }

    @GetMapping("/chollos-ganados")
    @Cacheable("chollosGanados")
    public List<Producto> obtenerChollosGanados() {
        return productoRepository.findTop10ByEstadoOrderByFechaFinSubastaDesc("ADJUDICADO");
    }
}