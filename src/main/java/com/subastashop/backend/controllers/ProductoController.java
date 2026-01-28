package com.subastashop.backend.controllers;

import com.subastashop.backend.config.TenantContext;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.Tienda;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.TiendaRepository;
import com.subastashop.backend.services.AzureBlobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    @Autowired
    private ProductoRepository productoRepository;

    // 🔧 CAMBIO: Usamos solo AzureBlobService para evitar conflictos
    @Autowired
    private AzureBlobService azureBlobService;

    @Autowired
    private TiendaRepository tiendaRepository;

    @GetMapping
    public ResponseEntity<List<Producto>> listarProductos() {
        String currentTenant = TenantContext.getTenantId();
        List<Producto> productos = productoRepository.findByTenantId(currentTenant);
        return ResponseEntity.ok(productos);
    }

    @GetMapping("/tienda/{slug}")
    public ResponseEntity<List<Producto>> obtenerProductosPorTienda(@PathVariable String slug) {
        // Buscamos la tienda por su URL amigable
        Tienda tienda = tiendaRepository.findBySlug(slug) // Crear en repo
                .orElseThrow(() -> new RuntimeException("Tienda no existe"));

        return ResponseEntity.ok(productoRepository.findByTiendaId(tienda.getId()));
    }

    // --- CREAR PRODUCTO ---
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Producto> crearProducto(
            @RequestParam("file") MultipartFile file,
            @RequestParam("nombre") String nombre,
            @RequestParam("descripcion") String descripcion,
            @RequestParam("tipoVenta") String tipoVenta,
            @RequestParam("precioBase") BigDecimal precioBase,
            @RequestParam(value = "stock", required = false, defaultValue = "1") Integer stock,
            @RequestParam(value = "fechaFin", required = false) String fechaFinIso,
            @RequestParam(value = "precioTicket", required = false) BigDecimal precioTicket,
            @RequestParam(value = "cantidadNumeros", required = false) Integer cantidadNumeros,
            @RequestParam(value = "cantidadGanadores", required = false) Integer cantidadGanadores) {
        try {
            // 1. Subir imagen a Azure Blob Storage ☁️
            String urlImagen = "https://via.placeholder.com/300"; // Imagen por defecto

            if (file != null && !file.isEmpty()) {
                // Usamos el servicio de Azure que ya funciona
                urlImagen = azureBlobService.subirImagen(file);
            }

            // 2. Construir el objeto
            Producto p = new Producto();
            p.setNombre(nombre);
            p.setDescripcion(descripcion);
            p.setTipoVenta(tipoVenta);
            p.setPrecioBase(precioBase);
            p.setImagenUrl(urlImagen); // Guardamos la URL aquí para ambos casos

            if ("RIFA".equalsIgnoreCase(tipoVenta)) {
                p.setEstado("DISPONIBLE");
                p.setPrecioTicket(precioTicket);
                p.setCantidadNumeros(cantidadNumeros);
                p.setCantidadGanadores(cantidadGanadores);
            } else if ("SUBASTA".equalsIgnoreCase(tipoVenta)) {
                p.setPrecioActual(precioBase);
                p.setEstado("EN_SUBASTA");
                p.setStock(1); // En subasta el stock suele ser 1

                // 🩹 PARCHE DE FECHA (Evita el error 'undefined')
                if (fechaFinIso != null && !fechaFinIso.equals("undefined") && !fechaFinIso.isEmpty()) {
                    // Si viene corta (ej: 15:30), agregamos segundos (15:30:00)
                    if (fechaFinIso.length() == 16) {
                        fechaFinIso += ":00";
                    }
                    p.setFechaFinSubasta(LocalDateTime.parse(fechaFinIso));
                }
            } else {
                p.setStock(stock);
                p.setEstado("DISPONIBLE");
            }

            // 4. Guardar
            Producto nuevo = productoRepository.save(p);
            return ResponseEntity.ok(nuevo);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build(); // Devuelve 500 pero no rompe la app
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Producto> obtenerProducto(@PathVariable Integer id) {
        String currentTenant = TenantContext.getTenantId();
        return productoRepository.findByIdAndTenantId(id, currentTenant)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // --- EDITAR PRODUCTO ---
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> editarProducto(
            @PathVariable Integer id,
            @RequestParam("nombre") String nombre,
            @RequestParam("descripcion") String descripcion,
            @RequestParam("precioBase") BigDecimal precioBase,
            @RequestParam(value = "fechaFin", required = false) String fechaFin, // Puede ser opcional
            @RequestParam(value = "imagen", required = false) MultipartFile imagen) {

        try {
            // 1. Buscar producto
            Producto producto = productoRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            // 2. Validaciones de negocio
            String estado = producto.getEstado();
            if ("SUBASTA".equals(estado) || "ADJUDICADO".equals(estado) || "PAGADO".equals(estado)) {
                return ResponseEntity.badRequest()
                        .body("❌ Error: No puedes editar un producto activo o vendido.");
            }

            // 3. Actualizar datos
            producto.setNombre(nombre);
            producto.setDescripcion(descripcion);
            producto.setPrecioBase(precioBase);

            if (producto.getPujas() == null || producto.getPujas().isEmpty()) {
                producto.setPrecioActual(precioBase);
            }

            // 🩹 PARCHE DE FECHA EN EDICIÓN
            if (fechaFin != null && !fechaFin.equals("undefined") && !fechaFin.isEmpty()) {
                if (fechaFin.length() == 16) {
                    fechaFin += ":00";
                }
                producto.setFechaFinSubasta(LocalDateTime.parse(fechaFin));
            }

            // 4. Subir nueva imagen (si existe)
            if (imagen != null && !imagen.isEmpty()) {
                String urlNueva = azureBlobService.subirImagen(imagen);
                producto.setImagenUrl(urlNueva);
            }

            return ResponseEntity.ok(productoRepository.save(producto));

        } catch (Exception e) {
            e.printStackTrace(); // Esto te mostrará el error en los logs de Azure si pasa algo
            return ResponseEntity.internalServerError().body("Error al actualizar: " + e.getMessage());
        }
    }
}