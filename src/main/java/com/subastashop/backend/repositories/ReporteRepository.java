package com.subastashop.backend.repositories;
import org.springframework.data.jpa.repository.JpaRepository;

import com.subastashop.backend.models.Reporte;

import java.util.List;


public interface ReporteRepository extends JpaRepository<Reporte, Long> {
    List<Reporte> findByEstado(String estado);
}
