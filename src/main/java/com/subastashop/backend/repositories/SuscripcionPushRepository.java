package com.subastashop.backend.repositories;

import com.subastashop.backend.models.SuscripcionPush;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SuscripcionPushRepository extends JpaRepository<SuscripcionPush, Long> {
    Optional<SuscripcionPush> findByEndpoint(String endpoint);
    List<SuscripcionPush> findByUsuarioId(Integer usuarioId);
}
