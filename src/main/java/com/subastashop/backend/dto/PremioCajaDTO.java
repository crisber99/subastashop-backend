package com.subastashop.backend.dto;

import lombok.Data;

@Data
public class PremioCajaDTO {
    private Integer id;
    private String nombre;
    private String imagenUrl;
    private Double probabilidad;
    private Integer stock;
}
