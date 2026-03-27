package com.subastashop.backend.repositories;

import com.subastashop.backend.models.Favorito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoritoRepository extends JpaRepository<Favorito, Long> {
    List<Favorito> findByUsuarioIdOrderByFechaCreacionDesc(Integer usuarioId);
    Optional<Favorito> findByUsuarioIdAndProductoId(Integer usuarioId, Integer productoId);
    boolean existsByUsuarioIdAndProductoId(Integer usuarioId, Integer productoId);
    void deleteByUsuarioIdAndProductoId(Integer usuarioId, Integer productoId);
}
