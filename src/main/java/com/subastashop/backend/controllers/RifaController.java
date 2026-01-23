package com.subastashop.backend.controllers;

import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.TicketRifa;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.TicketRifaRepository; // <--- Debes crear este repo (ver abajo)
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController // <--- Faltaba esto
@RequestMapping("/api/rifas") // <--- Faltaba la ruta base
public class RifaController {

    @Autowired
    private ProductoRepository productoRepository; // <--- Faltaba inyectar esto

    @Autowired
    private TicketRifaRepository ticketRepository; // <--- Faltaba inyectar esto

    @PostMapping("/{productoId}/lanzar")
    public ResponseEntity<?> lanzarRifa(@PathVariable Integer productoId) {
        Producto rifa = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Rifa no encontrada"));

        // 1. Validar que se vendieron todos
        long ticketsVendidos = ticketRepository.countByRifaId(productoId);

        // (Asegúrate que Producto tenga getCantidadNumeros, si no, agrégalo al modelo)
        if (ticketsVendidos < rifa.getCantidadNumeros()) {
            return ResponseEntity.badRequest().body("Aún faltan números por vender.");
        }

        // 2. Obtener todos los participantes
        List<TicketRifa> todosLosTickets = ticketRepository.findByRifaId(productoId);

        // 3. EL SORTEO 🎲
        Collections.shuffle(todosLosTickets);

        // 4. Sacar los ganadores
        List<TicketRifa> ganadores = todosLosTickets.stream()
                .limit(rifa.getCantidadGanadores())
                .collect(Collectors.toList());

        // 5. Guardar resultados
        rifa.setEstado("FINALIZADA");
        productoRepository.save(rifa);

        return ResponseEntity.ok(ganadores);
    }
}