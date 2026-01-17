package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "Pujas")
public class Puja extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Relación con el Producto
    @ManyToOne
    @JoinColumn(name = "ProductoId", nullable = false)
    private Producto producto;

    // Por ahora guardamos solo el ID del usuario (simulado hasta que tengamos Auth)
    @Column(nullable = false)
    private Integer usuarioId;

    @Column(nullable = false)
    private BigDecimal monto;

    private LocalDateTime fechaPuja = LocalDateTime.now();
}