package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cupones")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(nullable = false)
    private BigDecimal descuento;

    @Column(nullable = false)
    private String tipo; // "FIJO" o "PORCENTAJE"

    private LocalDateTime fechaExpiracion;

    @Column(nullable = false)
    private boolean activo = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    private Integer limiteUso; // Cuántas veces en total se puede usar (opcional)

    private Integer usosActuales = 0; // Cuántas veces se ha usado
}
