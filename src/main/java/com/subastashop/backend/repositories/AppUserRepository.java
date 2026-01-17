package com.subastashop.backend.repositories;

import com.subastashop.backend.models.AppUsers;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUsers, Integer> {
    // Buscar por email (login)
    Optional<AppUsers> findByEmail(String email);
    
    // Validar que no se repita al registrarse
    boolean existsByEmailAndTenantId(String email, String tenantId);
}