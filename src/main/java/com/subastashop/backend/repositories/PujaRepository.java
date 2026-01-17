package com.subastashop.backend.repositories;

import com.subastashop.backend.models.Puja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PujaRepository extends JpaRepository<Puja, Integer> {
    // Para mostrar el historial de pujas de un producto
    List<Puja> findByProductoIdOrderByMontoDesc(Integer productoId);

    List<Puja> findByUsuarioIdOrderByFechaPujaDesc(Integer usuarioId);
}