package com.subastashop.backend.controllers;

import com.subastashop.backend.config.TenantContext;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.services.AzureBlobService;
import com.subastashop.backend.services.StorageService;
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

    @Autowired
    private StorageService storageService; // <--- Inyectamos el servicio de Azure

    @Autowired
    private AzureBlobService azureBlobService;

    @GetMapping
    public ResponseEntity<List<Producto>> listarProductos() {
        String currentTenant = TenantContext.getTenantId();
        List<Producto> productos = productoRepository.findByTenantId(currentTenant);
        return ResponseEntity.ok(productos);
    }

    // NUEVO MÉTODO POST CON IMAGEN
    // Consumes = MULTIPART_FORM_DATA es obligatorio para recibir archivos
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Producto> crearProducto(
            @RequestParam("file") MultipartFile file,
            @RequestParam("nombre") String nombre,
            @RequestParam("descripcion") String descripcion,
            @RequestParam("tipoVenta") String tipoVenta, // DIRECTA o SUBASTA
            @RequestParam("precioBase") BigDecimal precioBase,
            @RequestParam(value = "stock", required = false, defaultValue = "1") Integer stock,
            @RequestParam(value = "fechaFin", required = false) String fechaFinIso // Ejemplo: "2026-12-31T23:59:00"
    ) {
        try {
            // 1. Subir imagen a Azure Blob Storage
            String urlImagen = "https://fakeimg.pl/300/"; // Fallback por si falla la subida
            if (file != null && !file.isEmpty()) {
                urlImagen = storageService.subirImagen(file);
            }

            // 2. Construir el objeto Producto
            Producto p = new Producto();
            p.setNombre(nombre);
            p.setDescripcion(descripcion);
            p.setTipoVenta(tipoVenta);
            p.setPrecioBase(precioBase);
            p.setImagenUrl(urlImagen);

            // Si es subasta, el precio actual inicial es el base
            if ("SUBASTA".equalsIgnoreCase(tipoVenta)) {
                p.setPrecioActual(precioBase);
                if (fechaFinIso != null) {
                    p.setFechaFinSubasta(LocalDateTime.parse(fechaFinIso));
                }
                p.setEstado("EN_SUBASTA");
            } else {
                p.setStock(stock);
                // p.setImagenUrl(urlImagen); // <--- Guardamos la URL de Azure
                p.setEstado("DISPONIBLE");
            }

            // Nota: No seteamos TenantId, BaseEntity lo hace solo.

            // 3. Guardar en BD
            Producto nuevo = productoRepository.save(p);

            return ResponseEntity.ok(nuevo);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Producto> obtenerProducto(@PathVariable Integer id) {
        // 1. Obtenemos el tenant actual
        String currentTenant = TenantContext.getTenantId();
        
        // 2. Buscamos el producto asegurándonos que sea de ESTA tienda
        return productoRepository.findByIdAndTenantId(id, currentTenant)
                .map(ResponseEntity::ok) // Si existe, devolvemos 200 OK con el producto
                .orElse(ResponseEntity.notFound().build()); // Si no existe, 404
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> editarProducto(
            @PathVariable Integer id,
            @RequestParam("nombre") String nombre,
            @RequestParam("descripcion") String descripcion,
            @RequestParam("precioBase") BigDecimal precioBase,
            @RequestParam("fechaFin") String fechaFin,
            @RequestParam(value = "imagen", required = false) MultipartFile imagen) {

        try {
            // 1. Buscar el producto
            Producto producto = productoRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            // 2. 🛡️ VALIDACIÓN DE REGLAS DE NEGOCIO
            String estado = producto.getEstado();
            if ("SUBASTA".equals(estado) || "ADJUDICADO".equals(estado) || "PAGADO".equals(estado)) {
                return ResponseEntity.badRequest()
                    .body("❌ Error: No puedes editar un producto que está en Subasta o ya fue Vendido.");
            }

            // 3. Actualizar datos básicos
            producto.setNombre(nombre);
            producto.setDescripcion(descripcion);
            producto.setPrecioBase(precioBase);
            // Si nadie ha pujado, actualizamos también el precio actual
            if (producto.getPujas() == null || producto.getPujas().isEmpty()) {
                producto.setPrecioActual(precioBase);
            }
            producto.setFechaFinSubasta(LocalDateTime.parse(fechaFin));

            // 4. Actualizar Imagen (Solo si subieron una nueva)
            if (imagen != null && !imagen.isEmpty()) {
                String urlNueva = azureBlobService.subirImagen(imagen);
                producto.setImagenUrl(urlNueva);
            }

            return ResponseEntity.ok(productoRepository.save(producto));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error al actualizar");
        }
    }
}