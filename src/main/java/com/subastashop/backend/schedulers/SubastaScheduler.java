package com.subastashop.backend.schedulers;

import com.subastashop.backend.config.TenantContext;
import com.subastashop.backend.models.Orden;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.Puja;
import com.subastashop.backend.repositories.OrdenRepository;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.PujaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class SubastaScheduler {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PujaRepository pujaRepository;

    @Autowired
    private OrdenRepository ordenRepository; // <--- Necesitaremos crear este repo

    // Se ejecuta cada 60.000 ms (1 minuto)
    @Scheduled(fixedRate = 60000)
    public void cerrarSubastasVencidas() {
        System.out.println("⏰ Revisando subastas vencidas..." + LocalDateTime.now());

        // 1. Buscar TODOS los productos que deben cerrarse (Sin filtrar por tenant aún)
        // Nota: Necesitaremos un método especial en el repositorio para esto
        List<Producto> productosVencidos = productoRepository.buscarSubastasPorCerrar(LocalDateTime.now());

        for (Producto p : productosVencidos) {
            procesarCierre(p);
        }
    }

    @Transactional
    public void procesarCierre(Producto p) {
        try {
            // CRÍTICO: Seteamos el contexto del Tenant para que las relaciones funcionen
            TenantContext.setTenantId(p.getTenantId());

            System.out.println("Cerrando subasta ID: " + p.getId() + " Tienda: " + p.getTenantId());

            // 1. Buscar la puja más alta
            List<Puja> pujas = pujaRepository.findByProductoIdOrderByMontoDesc(p.getId());

            if (pujas.isEmpty()) {
                p.setEstado("DESIERTO"); // Nadie ofertó
            } else {
                Puja ganadora = pujas.get(0);
                
                // 2. Adjudicar al ganador
                p.setEstado("ADJUDICADO"); // Ya no se puede ofertar
                
                // 3. Crear la Orden (Carrito) automáticamente
                Orden orden = new Orden();
                orden.setProductoId(p.getId());
                orden.setUsuarioId(ganadora.getUsuarioId());
                orden.setMontoFinal(ganadora.getMonto());
                orden.setEstado("PENDIENTE_PAGO");
                
                // 4. EL TEMPORIZADOR DE 3 HORAS
                orden.setFechaExpiracionReserva(LocalDateTime.now().plusHours(3));
                
                ordenRepository.save(orden);
                System.out.println("--> Orden creada para usuario " + ganadora.getUsuarioId());
            }

            // Guardar cambios del producto
            productoRepository.save(p);

        } catch (Exception e) {
            System.err.println("Error cerrando subasta " + p.getId() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Limpiar contexto al terminar este producto
            TenantContext.clear();
        }
    }
}