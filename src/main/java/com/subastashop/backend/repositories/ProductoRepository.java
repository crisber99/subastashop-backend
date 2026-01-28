package com.subastashop.backend.repositories;

import com.subastashop.backend.models.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    // Buscar todos los productos de UN tenant específico
    List<Producto> findByTenantId(String tenantId);

    // Buscar un producto específico, asegurando que pertenezca al tenant
    Optional<Producto> findByIdAndTenantId(Integer id, String tenantId);

    @Query(value = "SELECT * FROM Productos WHERE Estado = 'EN_SUBASTA' AND FechaFinSubasta < :ahora", nativeQuery = true)
    List<Producto> buscarSubastasPorCerrar(@Param("ahora") LocalDateTime ahora);

    long countByEstado(String estado);

    // Buscar productos de UNA tienda específica
    List<Producto> findByTiendaId(Long tiendaId);

    // Buscar productos de una tienda y que estén activos (para el público)
    List<Producto> findByTiendaSlugAndEstado(String slug, String estado);
}