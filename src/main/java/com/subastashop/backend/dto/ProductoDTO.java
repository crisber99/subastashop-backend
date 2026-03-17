package com.subastashop.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductoDTO {
    private Integer id;
    private String nombre;
    private String descripcion;
    private List<String> imagenes;
    private String tipoVenta;
    private BigDecimal precioBase;
    private Integer stock;
    private BigDecimal precioActual;
    private LocalDateTime fechaFinSubasta;
    private String estado;
    private LocalDateTime fechaCreacion;
    private Integer cantidadNumeros;
    private Integer cantidadGanadores;
    private BigDecimal precioTicket;
    private String nombreTienda;
    private String slugTienda;
}
