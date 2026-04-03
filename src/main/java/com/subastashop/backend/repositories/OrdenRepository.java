package com.subastashop.backend.repositories;
import com.subastashop.backend.models.AppUsers;
import com.subastashop.backend.models.Orden;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, Integer> {

    @org.springframework.data.jpa.repository.Query("SELECT o FROM Orden o LEFT JOIN FETCH o.detalles d LEFT JOIN FETCH d.producto LEFT JOIN FETCH o.usuario LEFT JOIN FETCH o.tienda WHERE o.id = :id")
    java.util.Optional<Orden> findByIdConDetalles(Integer id);

    @org.springframework.data.jpa.repository.Query("SELECT o FROM Orden o LEFT JOIN FETCH o.detalles d LEFT JOIN FETCH d.producto LEFT JOIN FETCH o.usuario LEFT JOIN FETCH o.tienda WHERE o.usuario = :usuario ORDER BY o.id DESC")
    List<Orden> findByUsuarioOrderByIdDesc(AppUsers usuario);

    List<Orden> findByTiendaId(Long tiendaId);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(o.total), 0.0) FROM Orden o WHERE o.estado = 'PAGADO'")
    Double sumTotalPagado();

    @org.springframework.data.jpa.repository.Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END FROM Orden o JOIN o.detalles d WHERE o.usuario.email = :email AND d.producto.id = :productoId AND (o.estado = 'PAGADO' OR o.estado = 'COMPLETADA' OR o.estado = 'ENTREGADO')")
    boolean hasUserBoughtProduct(@org.springframework.data.repository.query.Param("email") String email, @org.springframework.data.repository.query.Param("productoId") Integer productoId);
}