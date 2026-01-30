package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Tienda;
import com.subastashop.backend.repositories.AppUserRepository;
import com.subastashop.backend.repositories.TiendaRepository;
import com.subastashop.backend.services.AzureBlobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/tiendas")
public class TiendaController {

    @Autowired
    private TiendaRepository tiendaRepository;

    @Autowired
    private AppUserRepository usuarioRepository;

    @Autowired
    private AzureBlobService azureBlobService;

    // ========================================================================
    // 1. OBTENER MI TIENDA (Para mostrar el formulario lleno)
    // ========================================================================
    @GetMapping("/mi-tienda")
    public ResponseEntity<?> obtenerMiTienda() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            AppUsers admin = usuarioRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

            Tienda tienda = admin.getTienda();
            if (tienda == null) {
                return ResponseEntity.badRequest().body("No tienes una tienda asignada.");
            }

            return ResponseEntity.ok(tienda);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    // ========================================================================
    // 2. ACTUALIZAR CONFIGURACIÓN (RUT, Banco, Fotos, Colores)
    // ========================================================================
    @PutMapping(value = "/mi-tienda/configuracion", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> actualizarConfiguracionTienda(
            @RequestParam(value = "rutEmpresa", required = false) String rutEmpresa,
            @RequestParam(value = "datosBancarios", required = false) String datosBancarios,
            @RequestParam(value = "colorPrimario", required = false) String colorPrimario,
            @RequestParam(value = "fotoAnverso", required = false) MultipartFile fotoAnverso,
            @RequestParam(value = "fotoReverso", required = false) MultipartFile fotoReverso,
            @RequestParam(value = "aceptaTerminos", required = false) Boolean aceptaTerminos
    ) {
        try {
            // A. Identificar al usuario logueado
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            AppUsers admin = usuarioRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            
            Tienda tienda = admin.getTienda();
            if (tienda == null) {
                return ResponseEntity.badRequest().body("❌ Error: No tienes tienda asignada.");
            }

            // B. Actualizar datos de texto (si vienen en la petición)
            if (rutEmpresa != null && !rutEmpresa.isEmpty()) tienda.setRutEmpresa(rutEmpresa);
            if (datosBancarios != null && !datosBancarios.isEmpty()) tienda.setDatosBancarios(datosBancarios);
            if (colorPrimario != null && !colorPrimario.isEmpty()) tienda.setColorPrimario(colorPrimario);

            // C. Subir Fotos a Azure (si el usuario seleccionó archivos)
            if (fotoAnverso != null && !fotoAnverso.isEmpty()) {
                String urlAnverso = azureBlobService.subirImagen(fotoAnverso);
                tienda.setDocumentoAnversoUrl(urlAnverso);
            }

            if (fotoReverso != null && !fotoReverso.isEmpty()) {
                String urlReverso = azureBlobService.subirImagen(fotoReverso);
                tienda.setDocumentoReversoUrl(urlReverso);
            }

            if (Boolean.TRUE.equals(aceptaTerminos)) {
                // Si marca el checkbox, guardamos la fecha y hora exacta (Firma Digital simple)
                tienda.setFechaAceptacionTerminos(LocalDateTime.now());
            }

            // D. Guardar cambios
            tiendaRepository.save(tienda);

            // Respuesta JSON
            Map<String, String> response = new HashMap<>();
            response.put("mensaje", "✅ Configuración de tienda actualizada correctamente.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error al actualizar: " + e.getMessage());
        }
    }
}