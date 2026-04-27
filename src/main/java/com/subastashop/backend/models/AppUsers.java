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
@org.hibernate.annotations.SQLDelete(sql = "UPDATE AppUsers SET deleted = 1 WHERE id=?")
@org.hibernate.annotations.Where(clause = "deleted = 0")
public class AppUsers implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private boolean deleted = false;

    private String tenantId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String passwordHash;

    private String nombreCompleto;
    private String alias;
    private String telefono;
    private String direccion;
    private String rut; // 👈 NUEVO: RUT del usuario (Formato xx.xxx.xxx-x)
    private String preferenciaEnvio; // 👈 NUEVO: Opción de envío favorita (Blue Express, Starken, etc.)
    private String profileImageUrl; // 👈 NUEVO: Foto de perfil

    // 🔧 CAMBIO 1: Cambiamos String por Role y agregamos la anotación
    @Enumerated(EnumType.STRING)
    private Role rol; // Ahora es del tipo Enum, no String

    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "tienda_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"rutEmpresa", "datosBancarios", "documentoAnversoUrl", "documentoReversoUrl", "fechaAceptacionTerminos"})
    private Tienda tienda;

    // --- Campos de Suscripción y Trial ---
    @Column(name = "fecha_fin_prueba", nullable = true)
    private LocalDateTime fechaFinPrueba = LocalDateTime.now().plusDays(15);

    @Column(name = "suscripcion_activa", nullable = true)
    private Boolean suscripcionActiva = false;

    @Column(name = "fecha_vencimiento_suscripcion", nullable = true)
    private LocalDateTime fechaVencimientoSuscripcion;

    // --- Campos para Suscripción Automática (Mercado Pago Pre-approvals) ---
    @Column(name = "subscription_id", nullable = true)
    private String subscriptionId;

    @Column(name = "pago_automatico", nullable = true)
    private Boolean pagoAutomatico = false;

    @Column(name = "stripe_customer_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String stripeSubscriptionId;

    public boolean isSuscripcionActiva() {
        return Boolean.TRUE.equals(suscripcionActiva);
    }

    public boolean isPagoAutomatico() {
        return Boolean.TRUE.equals(pagoAutomatico);
    }

    // --- MÉTODOS DE USERDETAILS ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (rol == null) return List.of(new SimpleGrantedAuthority("ROLE_USER"));
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

    public void setPassword(String password) {
        this.passwordHash = password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
    
}