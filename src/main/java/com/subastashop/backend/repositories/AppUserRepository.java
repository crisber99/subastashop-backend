package com.subastashop.backend.repositories;

import com.subastashop.backend.models.AppUsers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUsers, Integer> {
    // Buscar por email (login)
    Optional<AppUsers> findByEmail(String email);

    boolean existsByEmail(String email);
    
    // Validar que no se repita al registrarse
    boolean existsByEmailAndTenantId(String email, String tenantId);

    // Contar usuarios registrados en esta tienda
    long countByTiendaId(Long tiendaId);

    // Encontrar el primer admin de una tienda específica
    @Query("SELECT u.id FROM AppUsers u WHERE u.tienda.id = :tiendaId AND u.rol = 'ROLE_ADMIN'")
    Integer findOwnerIdByTiendaId(@Param("tiendaId") Long tiendaId);

    // --- Consultas para Trial y Suscripciones ---
    java.util.List<AppUsers> findByRolAndFechaFinPruebaBeforeAndSuscripcionActivaFalse(
            com.subastashop.backend.models.Role rol, java.time.LocalDateTime fecha);
}