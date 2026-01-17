package com.subastashop.backend.services;

import com.subastashop.backend.config.TenantContext;
import com.subastashop.backend.models.Producto;
import com.subastashop.backend.models.Puja;
import com.subastashop.backend.repositories.ProductoRepository;
import com.subastashop.backend.repositories.PujaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class SubastaService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PujaRepository pujaRepository;

    @Transactional // <--- ¡Vital! O todo o nada.
    public Puja realizarPuja(Integer productoId, Integer usuarioId, BigDecimal montoOferta) {
        String tenantId = TenantContext.getTenantId();

        // 1. Buscar el producto (Asegurando que sea del tenant actual)
        Producto producto = productoRepository.findByIdAndTenantId(productoId, tenantId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado o no pertenece a esta tienda"));

        // 2. Validaciones de Negocio
        if (!"SUBASTA".equalsIgnoreCase(producto.getTipoVenta())) {
             throw new RuntimeException("Este producto no es de tipo subasta");
        }

        // --- NUEVA VALIDACIÓN: Verificar si la fecha es nula antes de comparar ---
        if (producto.getFechaFinSubasta() == null) {
            throw new RuntimeException("Error crítico: Este producto no tiene fecha de término configurada.");
        }
        if (LocalDateTime.now().isAfter(producto.getFechaFinSubasta())) {
            throw new RuntimeException("La subasta ya finalizó");
        }

        // La oferta debe ser mayor al precio actual (o al base si es la primera)
        BigDecimal precioGanadorActual = producto.getPrecioActual() != null ? 
                                         producto.getPrecioActual() : producto.getPrecioBase();

        if (montoOferta.compareTo(precioGanadorActual) <= 0) {
            throw new RuntimeException("Tu oferta debe ser mayor a $" + precioGanadorActual);
        }

        // 3. Registrar la Puja
        Puja nuevaPuja = new Puja();
        nuevaPuja.setProducto(producto);
        nuevaPuja.setUsuarioId(usuarioId);
        nuevaPuja.setMonto(montoOferta);
        // TenantId se setea solo gracias a BaseEntity

        pujaRepository.save(nuevaPuja);

        // 4. Actualizar el precio del producto en tiempo real
        producto.setPrecioActual(montoOferta);
        productoRepository.save(producto);

        return nuevaPuja;
    }
}