package com.subastashop.backend.services;

import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.Puja;
import com.subastashop.backend.models.SubastaSniper;
import com.subastashop.backend.repositories.SubastaSniperRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SubastaSniperService {

    @Autowired
    private SubastaSniperRepository sniperRepository;

    @Autowired
    @Lazy // Evitar circular dependency con SubastaService
    private SubastaService subastaService;

    @Autowired
    private com.subastashop.backend.repositories.ProductoRepository productoRepository;

    @Transactional
    public SubastaSniper configurarSniper(Integer productoId, Integer usuarioId, BigDecimal montoMaximo) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        if (!"SUBASTA".equalsIgnoreCase(producto.getTipoVenta())) {
            throw new RuntimeException("Solo se puede activar el Sniper en subastas.");
        }

        if (producto.getFechaFinSubasta() != null && LocalDateTime.now().isAfter(producto.getFechaFinSubasta())) {
            throw new RuntimeException("La subasta ya ha finalizado.");
        }

        // Desactivar cualquier sniper previo para este producto/usuario
        Optional<SubastaSniper> existente = sniperRepository.findByProductoIdAndUsuarioIdAndActivoTrue(productoId, usuarioId);
        existente.ifPresent(s -> {
            s.setActivo(false);
            sniperRepository.save(s);
        });

        SubastaSniper nuevo = new SubastaSniper();
        nuevo.setProducto(producto);
        nuevo.setUsuarioId(usuarioId);
        nuevo.setMontoMaximo(montoMaximo);
        nuevo.setActivo(true);
        nuevo.setTenantId(producto.getTenantId());
        
        return sniperRepository.save(nuevo);
    }

    @Transactional
    public void procesarSnipers(Integer productoId, Integer ultimoPujadorId) {
        List<SubastaSniper> snipers = sniperRepository.findByProductoIdAndActivoTrue(productoId);
        if (snipers.isEmpty()) return;

        boolean huboPuja = true;
        while (huboPuja) {
            huboPuja = false;
            // Recargar snipers en cada iteración por si alguno se desactivó
            snipers = sniperRepository.findByProductoIdAndActivoTrue(productoId);
            
            for (SubastaSniper sniper : snipers) {
                // Si el sniper ya es el ganador actual, no pujar
                if (sniper.getUsuarioId().equals(ultimoPujadorId)) continue;

                Producto p = sniper.getProducto();
                BigDecimal precioActual = p.getPrecioActual() != null ? p.getPrecioActual() : p.getPrecioBase();
                
                // Opción B: Incremento del 1%
                BigDecimal incremento = precioActual.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
                if (incremento.compareTo(BigDecimal.ONE) < 0) incremento = BigDecimal.ONE; // Incremento mínimo de $1
                
                BigDecimal nuevaOferta = precioActual.add(incremento);

                // Solo pujar si no supera el máximo
                if (nuevaOferta.compareTo(sniper.getMontoMaximo()) <= 0) {
                    try {
                        subastaService.realizarPuja(p.getId(), sniper.getUsuarioId(), nuevaOferta);
                        ultimoPujadorId = sniper.getUsuarioId();
                        huboPuja = true;
                        System.out.println("🤖 Sniper Bot ejecutado: Usuario " + sniper.getUsuarioId() + " pujó $" + nuevaOferta);
                    } catch (Exception e) {
                        // Si falla (ej: subasta cerrada en ese milisegundo), desactivar
                        System.err.println("Error ejecutando sniper: " + e.getMessage());
                        sniper.setActivo(false);
                        sniperRepository.save(sniper);
                    }
                } else {
                    // Si alcanzó el máximo, se desactiva el bot
                    sniper.setActivo(false);
                    sniperRepository.save(sniper);
                    System.out.println("🤖 Sniper Bot desactivado: Usuario " + sniper.getUsuarioId() + " alcanzó su límite de $" + sniper.getMontoMaximo());
                }
            }
        }
    }
}
