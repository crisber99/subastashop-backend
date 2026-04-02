package com.subastashop.backend.controllers;

import com.subastashop.backend.models.Role;
import com.subastashop.backend.repositories.*;
import com.subastashop.backend.services.AzureBlobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class MaintenanceController {

    private final AzureBlobService azureBlobService;
    private final ProductoRepository productoRepository;
    private final TiendaRepository tiendaRepository;
    private final AppUserRepository appUserRepository;
    private final PujaRepository pujaRepository;
    private final TicketRifaRepository ticketRifaRepository;
    private final GanadorRifaRepository ganadorRifaRepository;
    private final CalificacionRepository calificacionRepository;
    private final FavoritoRepository favoritoRepository;
    private final DetalleOrdenRepository detalleOrdenRepository;
    private final OrdenRepository ordenRepository;
    private final ReporteRepository reporteRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final SuscripcionPushRepository suscripcionPushRepository;

    @DeleteMapping("/cleanup-all")
    @Transactional
    public ResponseEntity<?> cleanupAll() {
        log.warn("🚨 Iniciando limpieza total de base de datos y almacenamiento...");

        try {
            // 1. Limpiar Almacenamiento
            azureBlobService.eliminarTodo();
            log.info("✅ Almacenamiento Azure limpiado.");

            // 2. Limpiar Base de Datos (Orden de dependencias)
            pujaRepository.deleteAll();
            ticketRifaRepository.deleteAll();
            ganadorRifaRepository.deleteAll();
            calificacionRepository.deleteAll();
            favoritoRepository.deleteAll();
            detalleOrdenRepository.deleteAll();
            reporteRepository.deleteAll();
            supportTicketRepository.deleteAll();
            suscripcionPushRepository.deleteAll();
            
            ordenRepository.deleteAll();
            productoRepository.deleteAll();
            
            // Desenlazar tiendas de usuarios antes de borrar tiendas
            appUserRepository.findAll().forEach(user -> {
                user.setTienda(null);
                appUserRepository.save(user);
            });
            
            tiendaRepository.deleteAll();
            
            // Borrar usuarios excepto SUPER_ADMIN
            appUserRepository.findAll().forEach(user -> {
                if (user.getRol() != Role.ROLE_SUPER_ADMIN) {
                    appUserRepository.delete(user);
                }
            });

            log.info("✅ Base de datos limpiada correctamente.");
            return ResponseEntity.ok(Map.of("message", "Limpieza completada con éxito. Quedó solo el SUPER_ADMIN."));

        } catch (Exception e) {
            log.error("❌ Error durante la limpieza: ", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
