package com.subastashop.backend.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "suscripciones_push")
@Data
public class SuscripcionPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = true)
    @JsonIgnore
    private AppUsers usuario;

    @Column(nullable = false, length = 1000)
    private String endpoint;

    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;
}
