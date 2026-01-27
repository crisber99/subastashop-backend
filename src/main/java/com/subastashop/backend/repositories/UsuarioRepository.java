package com.subastashop.backend.repositories;

import com.subastashop.backend.models.AppUsers;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// Este es el puente entre Java y tu tabla AppUsers
public interface UsuarioRepository extends JpaRepository<AppUsers, Integer> {
    
    // Método mágico para buscar por correo (esencial para el login y las compras)
    Optional<AppUsers> findByEmail(String email);
    
    // Método para verificar si existe un email (útil para registro)
    boolean existsByEmail(String email);
}