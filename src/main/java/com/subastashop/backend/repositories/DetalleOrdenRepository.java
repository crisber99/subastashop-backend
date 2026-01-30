package com.subastashop.backend.repositories;

import com.subastashop.backend.models.DetalleOrden;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DetalleOrdenRepository extends JpaRepository<DetalleOrden, Long> {
    // No necesitamos métodos extra por ahora
}