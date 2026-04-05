package com.subastashop.backend.dto;

import lombok.Data;

@Data
public class PremioCajaDTO {
    private Integer id;
    private String nombre;
    private String imagenUrl;
    private Integer probabilidad;
    private Integer stock;
}
