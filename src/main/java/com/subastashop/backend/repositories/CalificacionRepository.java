package com.subastashop.backend.repositories;

import com.subastashop.backend.models.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CalificacionRepository extends JpaRepository<Calificacion, Long> {
    List<Calificacion> findByProductoId(Long productoId);
}
