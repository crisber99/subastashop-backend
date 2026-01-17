package com.subastashop.backend.controllers;

import com.subastashop.backend.models.AppUsers; // <--- Importar tu modelo de usuario
import com.subastashop.backend.models.NuevaPujaRequest;
import com.subastashop.backend.models.Puja;
import com.subastashop.backend.services.SubastaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication; // <--- Importar
import org.springframework.security.core.context.SecurityContextHolder; // <--- Importar
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@RestController
@RequestMapping("/api/subastas")
public class SubastaController {

    @Autowired
    private SubastaService subastaService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostMapping("/pujar")
    public ResponseEntity<?> realizarPuja(@RequestBody NuevaPujaRequest request) {
        try {
            // 1. Obtener el usuario autenticado del Token
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            
            // 2. Extraer el objeto usuario real (Spring lo guardó ahí en el Filtro JWT)
            AppUsers usuarioReal = (AppUsers) auth.getPrincipal();

            // 3. Usar el ID del Token, IGNORANDO lo que venga en el JSON (request.getUsuarioId)
            Puja puja = subastaService.realizarPuja(
                    request.getProductoId(),
                    usuarioReal.getId(), // <--- ¡AQUÍ ESTÁ LA MAGIA!
                    request.getMonto()
            );

            messagingTemplate.convertAndSend("/topic/producto/" + request.getProductoId(), puja);
            
            return ResponseEntity.ok(puja);
            
        } catch (ClassCastException e) {
            return ResponseEntity.status(403).body("Error de autenticación: Token inválido");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}