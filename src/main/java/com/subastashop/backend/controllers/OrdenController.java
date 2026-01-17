package com.subastashop.backend.controllers;

import com.subastashop.backend.models.Orden;
import com.subastashop.backend.repositories.OrdenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/ordenes")
public class OrdenController {

    @Autowired
    private OrdenRepository ordenRepository;

    @GetMapping("/{id}")
    public ResponseEntity<Orden> obtenerOrden(@PathVariable Integer id) {
        return ordenRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // SIMULACIÓN DE PAGO
    @PostMapping("/{id}/pagar")
    public ResponseEntity<?> pagarOrden(@PathVariable Integer id) {
        Optional<Orden> ordenOpt = ordenRepository.findById(id);
        
        if (ordenOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Orden orden = ordenOpt.get();
        
        if ("PAGADO".equals(orden.getEstado())) {
            return ResponseEntity.badRequest().body("Esta orden ya está pagada.");
        }

        // Aquí iría la lógica real con WebPay / Stripe / PayPal
        // Nosotros simularemos que siempre funciona:
        
        orden.setEstado("PAGADO");
        ordenRepository.save(orden);

        return ResponseEntity.ok("Pago exitoso. ¡Producto en camino!");
    }
}