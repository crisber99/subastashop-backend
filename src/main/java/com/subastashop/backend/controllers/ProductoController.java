package com.subastashop.backend.controllers;

import com.subastashop.backend.config.TenantContext;
import com.subastashop.backend.dto.ProductoDTO;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.Tienda;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.TiendaRepository;
import com.subastashop.backend.services.ProductoService;
import com.subastashop.backend.services.WebPushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.cache.annotation.CacheEvict;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private TiendaRepository tiendaRepository;

    @Autowired
    private WebPushService webPushService;

    @Autowired
    private ProductoService productoService;

    @GetMapping
    public ResponseEntity<List<ProductoDTO>> listarProductos() {
        String currentTenant = TenantContext.getTenantId();
        List<ProductoDTO> productos = productoRepository.findByTenantId(currentTenant)
                .stream().map(productoService::toDTO).toList();
        return ResponseEntity.ok(productos);
    }

    @GetMapping("/tienda/{slug}")
    public ResponseEntity<List<ProductoDTO>> obtenerProductosPorTienda(@PathVariable String slug) {
        Tienda tienda = tiendaRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Tienda no existe"));

        List<ProductoDTO> productos = productoRepository.findByTiendaId(tienda.getId())
                .stream().map(productoService::toDTO).toList();
        return ResponseEntity.ok(productos);
    }

    // --- CREAR PRODUCTO ---
    @CacheEvict(value = "productosPublicos", allEntries = true)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> crearProducto(
            @RequestParam(value = "archivos", required = false) List<MultipartFile> archivos,
            @RequestParam("nombre") String nombre,
            @RequestParam("descripcion") String descripcion,
            @RequestParam("tipoVenta") String tipoVenta,
            @RequestParam(value = "precioBase", required = false) java.math.BigDecimal precioBase,
            @RequestParam(value = "stock", required = false, defaultValue = "1") Integer stock,
            @RequestParam(value = "fechaFin", required = false) String fechaFinIso,
            @RequestParam(value = "precioTicket", required = false) BigDecimal precioTicket,
            @RequestParam(value = "cantidadNumeros", required = false) Integer cantidadNumeros,
            @RequestParam(value = "cantidadGanadores", required = false) Integer cantidadGanadores,
            @RequestParam(value = "categoriaId", required = false) Integer categoriaId) throws java.io.IOException {

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean isSuperAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        Producto nuevo = productoService.crearProducto(email, isSuperAdmin, archivos, nombre, descripcion,
                tipoVenta, precioBase, stock, fechaFinIso, precioTicket, cantidadNumeros, cantidadGanadores, categoriaId);

        // Enviar notificación Push Global
        try {
            webPushService.enviarGlobal(
                "¡Nuevo Producto Añadido! 🎉", 
                "Se ha publicado en tienda: " + nuevo.getNombre(), 
                "https://www.subastashop.cl/producto/" + nuevo.getSlug()
            );
        } catch (Exception e) {
            System.err.println("Error al enviar push notification: " + e.getMessage());
        }

        return ResponseEntity.ok(productoService.toDTO(nuevo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoDTO> obtenerProducto(@PathVariable Integer id) {
        String currentTenant = TenantContext.getTenantId();
        return productoRepository.findByIdAndTenantId(id, currentTenant)
                .map(p -> ResponseEntity.ok(productoService.toDTO(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/p/{slug}")
    public ResponseEntity<ProductoDTO> obtenerProductoPorSlug(@PathVariable String slug) {
        String currentTenant = TenantContext.getTenantId();
        
        // 1. Intentar buscar por Slug
        java.util.Optional<Producto> op = productoRepository.findBySlugAndTenantId(slug, currentTenant);
        
        // 2. Si no se encuentra, y el 'slug' parece ser un ID (numérico), intentar buscar por ID
        if (op.isEmpty()) {
            try {
                Integer id = Integer.parseInt(slug);
                op = productoRepository.findByIdAndTenantId(id, currentTenant);
            } catch (NumberFormatException e) {
                // No es un número, ignorar el fallback por ID
            }
        }

        return op.map(p -> ResponseEntity.ok(productoService.toDTO(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    // --- EDITAR PRODUCTO ---
    @CacheEvict(value = "productosPublicos", allEntries = true)
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> editarProducto(
            @PathVariable Integer id,
            @RequestParam("nombre") String nombre,
            @RequestParam("descripcion") String descripcion,
            @RequestParam("precioBase") BigDecimal precioBase,
            @RequestParam(value = "fechaFin", required = false) String fechaFin,
            @RequestParam(value = "archivos", required = false) List<MultipartFile> archivos,
            @RequestParam(value = "categoriaId", required = false) Integer categoriaId) throws java.io.IOException {

        boolean isSuperAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        Producto producto = productoService.editarProducto(id, isSuperAdmin, nombre, descripcion, precioBase, fechaFin, archivos, categoriaId);
        return ResponseEntity.ok(productoService.toDTO(producto));
    }
}