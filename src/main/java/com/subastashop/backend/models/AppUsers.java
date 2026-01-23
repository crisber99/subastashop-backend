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
public class AppUsers implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String tenantId;
    
    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private String nombreCompleto;

    // 🔧 CAMBIO 1: Cambiamos String por Role y agregamos la anotación
    @Enumerated(EnumType.STRING) 
    private Role rol; // Ahora es del tipo Enum, no String

    private LocalDateTime fechaRegistro = LocalDateTime.now();

    // --- MÉTODOS DE USERDETAILS ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 🔧 CAMBIO 2: Convertimos el Enum a String usando .name()
        // Spring Security necesita texto, así que extraemos el nombre del rol (ej: "ROLE_ADMIN")
        return List.of(new SimpleGrantedAuthority(rol.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
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