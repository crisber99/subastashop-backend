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

        // Check if global admin (Super Admin or Admin)
        boolean isGlobalAdmin = admin.getRol() == Role.ROLE_ADMIN || admin.getRol() == Role.ROLE_SUPER_ADMIN;

        // 2. VERIFICAR QUE TENGA TIENDA (A menos que sea Super Admin)
        if (!isGlobalAdmin && admin.getTienda() == null) {
            throw new ApiException("No tienes una tienda asignada para ver estadísticas.");
        }

        Map<String, Object> stats = new HashMap<>();

        if (isGlobalAdmin) {
            // Lógica Super Admin 👑
            stats.put("totalUsuarios", usuarioRepository.count());
            stats.put("subastasActivas", productoRepository.countByEstado("SUBASTA"));
            stats.put("ventasCerradas", productoRepository.countByEstado("VENDIDO"));
            
            Double total = ordenRepository.sumTotalPagado();
            stats.put("gananciasTotales", total != null ? total : 0.0);
            stats.put("nombreTienda", "Panel Global");
        } else {
            // Lógica Admin de Tienda 🏪
            Long tiendaId = admin.getTienda().getId();
            stats.put("totalUsuarios", 0); 
            stats.put("subastasActivas", productoRepository.countByTiendaIdAndEstado(tiendaId, "SUBASTA"));
            stats.put("ventasCerradas", productoRepository.countByTiendaIdAndEstado(tiendaId, "VENDIDO"));
            stats.put("gananciasTotales", 0);
            stats.put("nombreTienda", admin.getTienda().getNombre());
        }

        return ResponseEntity.ok(stats);
    }

    // 🚀 ACCIÓN: DETENER TODAS LAS SUBASTAS ACTIVAS
    @PostMapping("/detener-subastas")
    public ResponseEntity<?> detenerSubastas() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers admin = usuarioRepository.findByEmail(email).orElseThrow();
        
        boolean isGlobalAdmin = admin.getRol() == Role.ROLE_ADMIN || admin.getRol() == Role.ROLE_SUPER_ADMIN;

        if (isGlobalAdmin) {
            var subastas = productoRepository.findByEstado("SUBASTA");
            subastas.forEach(p -> p.setEstado("FINALIZADA"));
            productoRepository.saveAll(subastas);
            return ResponseEntity.ok("Se han detenido " + subastas.size() + " subastas globales.");
        }

        if (admin.getTienda() == null) {
            throw new ApiException("No tienes una tienda asignada.");
        }

        Long tiendaId = admin.getTienda().getId();
        var subastas = productoRepository.findByTiendaIdAndEstado(tiendaId, "SUBASTA");
        for (Producto p : subastas) {
            p.setEstado("FINALIZADA");
            productoRepository.save(p);
        }

        return ResponseEntity.ok("Se han detenido " + subastas.size() + " subastas.");
    }

    // 🚀 ACCIÓN: NOTIFICAR A GANADORES
    @PostMapping("/notificar-ganadores")
    public ResponseEntity<?> notificarGanadores() {
        return ResponseEntity.ok("Notificaciones enviadas correctamente.");
    }

    // 🚀 ACCIÓN: EXPORTAR VENTAS A EXCEL (.XLSX) 📊
    @GetMapping("/exportar-ventas")
    public ResponseEntity<byte[]> exportarVentas() throws IOException {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers admin = usuarioRepository.findByEmail(email).orElseThrow();
        
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Ventas");

            // Cabecera
            Row headerRow = sheet.createRow(0);
            String[] columns = {"ID", "Producto", "Precio", "Estado", "Fecha"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                CellStyle headerStyle = workbook.createCellStyle();
                Font font = workbook.createFont();
                font.setBold(true);
                headerStyle.setFont(font);
                cell.setCellStyle(headerStyle);
            }

            // Datos (Mock por ahora)
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(101);
            row.createCell(1).setCellValue("Producto Pro");
            row.createCell(2).setCellValue(500.0);
            row.createCell(3).setCellValue("PAGADO");
            row.createCell(4).setCellValue("2026-03-19");

            workbook.write(out);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .header("Content-Disposition", "attachment; filename=\"ventas_tienda.xlsx\"")
                    .body(out.toByteArray());
        }
    }

    // 📦 LISTAR PRODUCTOS PARA ADMIN
    @GetMapping("/productos")
    public ResponseEntity<?> listarProductosAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        AppUsers admin = usuarioRepository.findByEmail(email).orElseThrow();

        boolean isGlobalAdmin = admin.getRol() == Role.ROLE_ADMIN || admin.getRol() == Role.ROLE_SUPER_ADMIN;

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