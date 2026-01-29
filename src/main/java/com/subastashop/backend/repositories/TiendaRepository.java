package com.subastashop.backend.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.subastashop.backend.models.Tienda;

@Repository
public interface TiendaRepository extends JpaRepository<Tienda, Long> {
    Optional<Tienda> findBySlug(String slug);

    // Buscar por RUT (útil para validaciones futuras)
    Optional<Tienda> findByRutEmpresa(String rutEmpresa);

    // 2. Para verificar si una URL ya existe (antes de crearla)
    boolean existsBySlug(String slug);
}
