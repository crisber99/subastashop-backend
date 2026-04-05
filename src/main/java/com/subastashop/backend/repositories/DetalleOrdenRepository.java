package com.subastashop.backend.repositories;

import com.subastashop.backend.models.DetalleOrden;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DetalleOrdenRepository extends JpaRepository<DetalleOrden, Long> {
    java.util.Optional<DetalleOrden> findFirstByProductoIdOrderByOrdenFechaCreacionDesc(Integer productoId);

    @org.springframework.data.jpa.repository.Query("SELECT d.producto.nombre, SUM(d.precioUnitario * d.cantidad) as total " +
            "FROM DetalleOrden d WHERE d.orden.tienda.id = :tiendaId AND (d.orden.estado = 'PAGADO' OR d.orden.estado = 'COMPLETADA') " +
            "GROUP BY d.producto.id, d.producto.nombre ORDER BY total DESC")
    List<Object[]> getTopSellingProducts(@org.springframework.data.repository.query.Param("tiendaId") Long tiendaId, org.springframework.data.domain.Pageable pageable);
}