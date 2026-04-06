package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "SubastaSnipers")
@EqualsAndHashCode(callSuper = true)
public class SubastaSniper extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Integer usuarioId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montoMaximo;

    @Column(nullable = false)
    private boolean activo = true;

    private LocalDateTime fechaConfiguracion = LocalDateTime.now();
}
