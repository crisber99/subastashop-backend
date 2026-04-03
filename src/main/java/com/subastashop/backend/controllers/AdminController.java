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
            stats.put("subastasActivas", productoRepository.countByEstadoIn(java.util.List.of("SUBASTA", "EN_SUBASTA")));
            stats.put("ventasCerradas", productoRepository.countByEstado("VENDIDO"));
            
            Double total = ordenRepository.sumTotalPagado();
            stats.put("gananciasTotales", total != null ? total : 0.0);
            stats.put("nombreTienda", "Panel Global");
        } else {
            // Lógica Admin de Tienda (Incluye ROLE_ADMIN ahora) 🏪
            Long tiendaId = admin.getTienda().getId();
            stats.put("totalUsuarios", 0); 
            stats.put("subastasActivas", productoRepository.countByTiendaIdAndEstadoIn(tiendaId, java.util.List.of("SUBASTA", "EN_SUBASTA")));
            stats.put("ventasCerradas", productoRepository.countByTiendaIdAndEstado(tiendaId, "VENDIDO"));
            
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
            return ResponseEntity.ok("Se han detenido " + subastas.size() + " subastas globales.");
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

        return ResponseEntity.ok("Se han detenido " + subastas.size() + " subastas.");
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
        
        // Obtener órdenes reales con tipado explícito 💰
        java.util.List<com.subastashop.backend.models.Orden> ventas;
        if (admin.getTienda() != null) {
            ventas = ordenRepository.findByTiendaId(admin.getTienda().getId());
        } else {
            ventas = ordenRepository.findAll();
        }

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Ventas");

            // Cabecera
            Row headerRow = sheet.createRow(0);
            String[] columns = {"ID Orden", "Cliente", "Total", "Estado", "Fecha", "Productos"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                CellStyle headerStyle = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                headerStyle.setFont(font);
                cell.setCellStyle(headerStyle);
            }

            // Datos Reales 📝
            int rowIdx = 1;
            for (com.subastashop.backend.models.Orden orden : ventas) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(orden.getId());
                row.createCell(1).setCellValue(orden.getUsuario().getEmail());
                row.createCell(2).setCellValue(orden.getTotal() != null ? orden.getTotal().doubleValue() : 0.0);
                row.createCell(3).setCellValue(orden.getEstado());
                row.createCell(4).setCellValue(orden.getFechaCreacion() != null ? orden.getFechaCreacion().toString() : "");
                
                // Concatenar nombres de productos del detalle
                String nombresProductosArr = orden.getDetalles().stream()
                    .map(d -> d.getProducto().getNombre())
                    .reduce((a, b) -> a + ", " + b).orElse("");
                row.createCell(5).setCellValue(nombresProductosArr);
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
        return ResponseEntity.ok("Producto eliminado correctamente");
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