package com.subastashop.backend.repositories;

import com.subastashop.backend.models.Cupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CuponRepository extends JpaRepository<Cupon, Integer> {
    Optional<Cupon> findByCodigoAndTenantIdAndActivoTrue(String codigo, String tenantId);
    List<Cupon> findByTiendaIdAndTenantId(Long tiendaId, String tenantId);
    Optional<Cupon> findByIdAndTenantId(Integer id, String tenantId);
}
