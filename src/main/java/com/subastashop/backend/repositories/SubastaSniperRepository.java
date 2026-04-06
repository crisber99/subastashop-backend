package com.subastashop.backend.repositories;

import com.subastashop.backend.models.SubastaSniper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubastaSniperRepository extends JpaRepository<SubastaSniper, Integer> {
    
    // Buscar sniper activo de un usuario para un producto específico
    Optional<SubastaSniper> findByProductoIdAndUsuarioIdAndActivoTrue(Integer productoId, Integer usuarioId);
    
    // Buscar todos los snipers activos para un producto (cuando alguien puja manualmente)
    List<SubastaSniper> findByProductoIdAndActivoTrue(Integer productoId);
    
    // Buscar snipers activos para productos que están por terminar
    List<SubastaSniper> findByActivoTrue();
}
