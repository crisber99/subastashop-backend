package com.subastashop.backend.controllers;

import com.subastashop.backend.dto.OrdenRequest;
import com.subastashop.backend.models.Orden;
import com.subastashop.backend.services.OrdenService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ordenes")
public class OrdenController {

    @Autowired
    private OrdenService ordenService;

    // SIMULACIÓN DE PAGO
    @PostMapping("/{id}/pagar")
    public ResponseEntity<?> pagarOrden(@PathVariable Integer id) {
        try {
            String mensaje = ordenService.pagarOrden(id);
            return ResponseEntity.ok(mensaje);
        } catch (RuntimeException e) {
            String msg = e.getMessage();
            if (msg.equals("Orden no encontrada")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(msg);
        }
    }

    @PostMapping("/crear")
    public ResponseEntity<?> crearOrden(@RequestBody OrdenRequest request) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Orden orden = ordenService.crearOrden(request, email);
            return ResponseEntity.ok(orden);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    
    @GetMapping("/mis-ordenes")
    public ResponseEntity<List<Orden>> obtenerMisOrdenes() {
        System.out.println("🚀 ¡Llegó la petición al controlador!");
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        List<Orden> ordenes = ordenService.obtenerMisOrdenes(email);
        return ResponseEntity.ok(ordenes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerOrdenPorId(@PathVariable Integer id) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            Orden orden = ordenService.obtenerOrdenPorId(id, email);
            return ResponseEntity.ok(orden);
        } catch (RuntimeException e) {
            if(e.getMessage().equals("No tienes permiso para ver esta orden")) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/informar-pago")
    public ResponseEntity<?> informarPago(@PathVariable Integer id, @RequestParam("archivo") org.springframework.web.multipart.MultipartFile archivo) {
        try {
            ordenService.informarPago(id, archivo);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/detalle/{detalleId}/abrir-caja")
    public ResponseEntity<?> abrirCajaMisteriosa(@PathVariable Long detalleId) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            String premioObtenido = ordenService.abrirCajaMisteriosa(detalleId, email);
            return ResponseEntity.ok(java.util.Collections.singletonMap("premio", premioObtenido));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}