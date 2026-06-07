package com.subastashop.backend.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CuponDTO {
    private Integer id;
    private String codigo;
    private BigDecimal descuento;
    private String tipo; // FIJO o PORCENTAJE
    private LocalDateTime fechaExpiracion;
    private boolean activo;
    private Integer limiteUso;
    private Integer usosActuales;
    private Integer tiendaId;
    private String tiendaNombre;
}
