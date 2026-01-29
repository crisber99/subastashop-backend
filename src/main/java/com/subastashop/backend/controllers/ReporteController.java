package com.subastashop.backend.controllers;

import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.Reporte;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.ReporteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    @Autowired private ReporteRepository reporteRepository;
    @Autowired private ProductoRepository productoRepository;

    // 1. CUALQUIERA PUEDE REPORTAR (Público o Logueado) 📢
    @PostMapping("/{productoId}")
    public ResponseEntity<?> crearReporte(@PathVariable Integer productoId, @RequestBody Reporte reporte) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no existe"));

        reporte.setProducto(producto);
        reporte.setEstado("PENDIENTE");
        reporteRepository.save(reporte);

        return ResponseEntity.ok("Reporte enviado. Gracias por ayudar a la comunidad.");
    }

    // 2. SOLO SUPER ADMIN: VER REPORTES PENDIENTES 👮‍♂️
    @GetMapping("/admin/pendientes")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public List<Reporte> listarPendientes() {
        return reporteRepository.findByEstado("PENDIENTE"); // Crear método en Repo
    }

    // 3. SOLO SUPER ADMIN: TOMAR ACCIÓN (OCULTAR O ELIMINAR) 🔨
    @PostMapping("/admin/accion/{reporteId}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<?> tomarAccion(
            @PathVariable Long reporteId,
            @RequestParam String accion) { // accion: 'OCULTAR', 'ELIMINAR', 'DESCARTAR'

        Reporte reporte = reporteRepository.findById(reporteId).orElseThrow();
        Producto producto = reporte.getProducto();

        if ("OCULTAR".equals(accion)) {
            // Opción Suave: Lo sacamos del catálogo pero no lo borramos de la BD
            producto.setEstado("SUSPENDIDO"); // Asegúrate de filtrar esto en el catálogo público
            productoRepository.save(producto);
            reporte.setEstado("ACEPTADO_OCULTO");
        
        } else if ("ELIMINAR".equals(accion)) {
            // Opción Dura: Borrado total (Cuidado con las compras asociadas)
            // Si tiene compras, mejor solo ocultar.
            productoRepository.delete(producto); 
            reporte.setEstado("ACEPTADO_ELIMINADO");
        
        } else if ("DESCARTAR".equals(accion)) {
            // Falsa alarma
            reporte.setEstado("DESCARTADO");
        }

        reporteRepository.save(reporte);
        return ResponseEntity.ok("Acción realizada: " + accion);
    }
}