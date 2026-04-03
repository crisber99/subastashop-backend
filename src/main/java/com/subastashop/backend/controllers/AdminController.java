package com.subastashop.backend.controllers;

import com.subastashop.backend.exceptions.ApiException;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.subastashop.backend.models.Role;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private ProductoRepository productoRepository;
    @Autowired
    private AppUserRepository usuarioRepository;
    @Autowired
    private OrdenRepository ordenRepository;
    @Autowired
    private com.subastashop.backend.repositories.DetalleOrdenRepository detalleOrdenRepository;

    @GetMapping("/stats")
    public ResponseEntity<?> obtenerEstadisticas() {
        
        // 1. OBTENER EL ADMIN LOGUEADO 🕵️‍♂️
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers admin = usuarioRepository.findByEmail(email).orElseThrow();

        // Only Super Admin is Global now 👑
        String roleName = admin.getRol() != null ? admin.getRol().name() : "";
        boolean isGlobalAdmin = "ROLE_SUPER_ADMIN".equals(roleName);

        System.out.println("DEBUG: Admin stats check for " + email + " role: " + roleName + " isGlobal: " + isGlobalAdmin);

        // 2. VERIFICAR QUE TENGA TIENDA (A menos que sea Super Admin)
        if (!isGlobalAdmin && admin.getTienda() == null) {
            throw new ApiException("No tienes una tienda asignada para ver estadísticas.");
        }

        Map<String, Object> stats = new HashMap<>();

        if (isGlobalAdmin) {
            // Lógica Super Admin 👑
            stats.put("totalUsuarios", usuarioRepository.count());
            stats.put("totalProductos", productoRepository.count());
            stats.put("totalSubastas", productoRepository.countByTipoVenta("SUBASTA"));
            stats.put("totalVentaDirecta", productoRepository.countByTipoVenta("DIRECTA"));
            stats.put("totalRifas", productoRepository.countByTipoVenta("RIFA"));
            
            Double total = ordenRepository.sumTotalPagado();
            stats.put("gananciasTotales", total != null ? total : 0.0);
            stats.put("nombreTienda", "Panel Global");
        } else {
            // Lógica Admin de Tienda 🏪
            Long tiendaId = admin.getTienda().getId();
            stats.put("totalUsuarios", 0); 
            
            stats.put("subastasActivas", productoRepository.countByTiendaIdAndTipoVentaAndEstadoIn(tiendaId, "SUBASTA", java.util.List.of("SUBASTA", "EN_SUBASTA")));
            stats.put("ventaDirectaDisponibles", productoRepository.countByTiendaIdAndTipoVentaAndEstadoIn(tiendaId, "DIRECTA", java.util.List.of("DISPONIBLE")));
            stats.put("rifasDisponibles", productoRepository.countByTiendaIdAndTipoVentaAndEstadoIn(tiendaId, "RIFA", java.util.List.of("DISPONIBLE", "SUBASTA"))); // Las rifas a veces usan SUBASTA como estado activo internamente
            
            stats.put("totalSubastas", productoRepository.countByTiendaIdAndTipoVenta(tiendaId, "SUBASTA"));
            stats.put("totalVentaDirecta", productoRepository.countByTiendaIdAndTipoVenta(tiendaId, "DIRECTA"));
            stats.put("totalRifas", productoRepository.countByTiendaIdAndTipoVenta(tiendaId, "RIFA"));

            Double totalTienda = ordenRepository.sumTotalPagadoByTiendaId(tiendaId);
            stats.put("gananciasTotales", totalTienda != null ? totalTienda : 0.0);
            stats.put("nombreTienda", admin.getTienda().getNombre());
        }

        return ResponseEntity.ok(stats);
    }

    // 🚀 ACCIÓN: DETENER TODAS LAS SUBASTAS ACTIVAS
    @Transactional
    @PostMapping("/detener-subastas")
    public ResponseEntity<?> detenerSubastas() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers admin = usuarioRepository.findByEmail(email).orElseThrow();
        
        String roleName = admin.getRol() != null ? admin.getRol().name() : "";
        boolean isGlobalAdmin = "ROLE_SUPER_ADMIN".equals(roleName);

        if (isGlobalAdmin) {
            // Buscamos tanto SUBASTA como EN_SUBASTA por si acaso 🕵️‍♂️
            var subastas = productoRepository.findAll().stream()
                .filter(p -> "SUBASTA".equals(p.getEstado()) || "EN_SUBASTA".equals(p.getEstado()))
                .toList();
            subastas.forEach(p -> p.setEstado("FINALIZADA"));
            productoRepository.saveAll(subastas);
            return ResponseEntity.ok(java.util.Map.of("message", "Se han detenido " + subastas.size() + " subastas globales."));
        }

        if (admin.getTienda() == null) {
            throw new ApiException("No tienes una tienda asignada.");
        }

        Long tiendaId = admin.getTienda().getId();
        var subastas = productoRepository.findByTiendaIdAndEstadoIn(tiendaId, java.util.List.of("SUBASTA", "EN_SUBASTA"));
        for (Producto p : subastas) {
            p.setEstado("FINALIZADA");
        }
        productoRepository.saveAll(subastas);

        return ResponseEntity.ok(java.util.Map.of("message", "Se han detenido " + subastas.size() + " subastas."));
    }

    // 🚀 ACCIÓN: EXPORTAR VENTAS A EXCEL (.XLSX) 📊 - REAL!
    @GetMapping("/exportar-ventas")
    public ResponseEntity<byte[]> exportarVentas() throws IOException {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers admin = usuarioRepository.findByEmail(email).orElseThrow();
        
        if (admin.getTienda() == null && !"ROLE_SUPER_ADMIN".equals(admin.getRol().name())) {
            throw new ApiException("No tienes una tienda asignada.");
        }

        String nombreTienda = admin.getTienda() != null ? admin.getTienda().getNombre() : "Global";
        
        // Obtener todos los productos para reporte de inventario completo 📦
        java.util.List<Producto> productos;
        if (admin.getTienda() != null) {
            productos = productoRepository.findByTiendaId(admin.getTienda().getId());
        } else {
            productos = productoRepository.findAll();
        }

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Inventario y Ventas");

            // Cabecera extendida 📊
            Row headerRow = sheet.createRow(0);
            String[] columns = {"ID", "Producto", "Tipo", "Categoría", "Estado", "Cliente (Email)", "Nombre Completo", "Dirección", "Valor Inicial", "Valor Final", "Estado Pago", "Fecha"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                CellStyle headerStyle = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                headerStyle.setFont(font);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Producto p : productos) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(p.getNombre());
                row.createCell(2).setCellValue(p.getTipoVenta() != null ? p.getTipoVenta() : "N/A");
                row.createCell(3).setCellValue(p.getCategoria() != null ? p.getCategoria().getNombre() : "Sin Categoría");
                row.createCell(4).setCellValue(p.getEstado());

                // Buscar información de venta 💰
                var detalle = detalleOrdenRepository.findFirstByProductoIdOrderByOrdenFechaCreacionDesc(p.getId()).orElse(null);
                
                if (detalle != null && detalle.getOrden() != null) {
                    AppUsers cliente = detalle.getOrden().getUsuario();
                    row.createCell(5).setCellValue(cliente.getEmail());
                    row.createCell(6).setCellValue(cliente.getNombreCompleto() != null ? cliente.getNombreCompleto() : "-");
                    row.createCell(7).setCellValue(cliente.getDireccion() != null ? cliente.getDireccion() : "-");
                    row.createCell(8).setCellValue(p.getPrecioBase() != null ? p.getPrecioBase().doubleValue() : 0.0);
                    row.createCell(9).setCellValue(detalle.getPrecioUnitario() != null ? detalle.getPrecioUnitario().doubleValue() : 0.0);
                    row.createCell(10).setCellValue(detalle.getOrden().getEstado() != null ? detalle.getOrden().getEstado() : "PENDIENTE");
                    row.createCell(11).setCellValue(detalle.getOrden().getFechaCreacion() != null ? detalle.getOrden().getFechaCreacion().toString() : "-");
                } else {
                    row.createCell(5).setCellValue("-");
                    row.createCell(6).setCellValue("-");
                    row.createCell(7).setCellValue("-");
                    row.createCell(8).setCellValue(p.getPrecioBase() != null ? p.getPrecioBase().doubleValue() : 0.0);
                    row.createCell(9).setCellValue(0.0);
                    row.createCell(10).setCellValue("SIN VENTA");
                    row.createCell(11).setCellValue("-");
                }
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            
            String fecha = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String safeNombre = nombreTienda.replaceAll("[^a-zA-Z0-9]", "_");
            String fileName = "tienda_" + safeNombre + "_" + fecha + ".xlsx";

            return ResponseEntity.ok()
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .body(out.toByteArray());
        }
    }

    // 🗑️ ACCIÓN: ELIMINAR PRODUCTO (BORRADO LÓGICO)
    @DeleteMapping("/productos/{id}")
    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "productosPublicos", allEntries = true)
    public ResponseEntity<?> eliminarProducto(@PathVariable Integer id) {
        Producto p = productoRepository.findById(id)
            .orElseThrow(() -> new ApiException("Producto no encontrado"));
        
        // Verificación de propiedad (Opcional, pero segura)
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers admin = usuarioRepository.findByEmail(email).orElseThrow();
        
        if (!"ROLE_SUPER_ADMIN".equals(admin.getRol().name())) {
            if (p.getTienda() == null || !p.getTienda().getId().equals(admin.getTienda().getId())) {
                return ResponseEntity.status(403).body("No tienes permiso para eliminar este producto.");
            }
        }

        productoRepository.delete(p); // El SQLDelete en el modelo hará el trabajo lógico
        return ResponseEntity.ok(java.util.Map.of("message", "Producto '" + p.getNombre() + "' eliminado correctamente"));
    }

    // 📦 LISTAR PRODUCTOS PARA ADMIN
    @GetMapping("/productos")
    public ResponseEntity<?> listarProductosAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers admin = usuarioRepository.findByEmail(email).orElseThrow();

        String roleName = admin.getRol() != null ? admin.getRol().name() : "";
        boolean isGlobalAdmin = "ROLE_SUPER_ADMIN".equals(roleName);

        System.out.println("DEBUG: Listing products for " + email + " role: " + roleName + " isGlobal: " + isGlobalAdmin);

        if (isGlobalAdmin) {
            // Super Admin ve TODO 👑
            return ResponseEntity.ok(productoRepository.findAll());
        }

        // Admin de Tienda ve solo lo SUYO 🏪
        if (admin.getTienda() == null) {
            throw new ApiException("No tienes una tienda asignada.");
        }
        return ResponseEntity.ok(productoRepository.findByTiendaId(admin.getTienda().getId()));
    }

    @PutMapping("/usuarios/{id}/rol")
    public ResponseEntity<?> cambiarRol(@PathVariable Integer id, @RequestBody String nuevoRol) {
        AppUsers usuario = usuarioRepository.findById(id).orElseThrow();
        usuario.setRol(Role.valueOf(nuevoRol)); // Usar el valor enviado
        usuarioRepository.save(usuario);
        return ResponseEntity.ok("Rol actualizado");
    }
}