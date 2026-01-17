package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "Ordenes")
public class Orden extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer usuarioId;
    private Integer productoId;
    private LocalDateTime fechaExpiracionReserva;
    private String estado; // PENDIENTE_PAGO, EXPIRADO
    private String empresaEnvio;
    private BigDecimal montoFinal;
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}