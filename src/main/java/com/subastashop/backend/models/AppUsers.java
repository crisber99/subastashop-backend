package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Data
@Entity
@Table(name = "AppUsers")
public class AppUsers implements UserDetails { // <--- IMPLEMENTAR ESTO

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String tenantId; // No usamos BaseEntity aquí porque el login es previo a saber el tenant a veces
    
    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private String nombreCompleto;
    private String rol; // ADMIN, COMPRADOR
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    // --- MÉTODOS DE USERDETAILS ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(rol));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email; // Usamos el email como usuario
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}