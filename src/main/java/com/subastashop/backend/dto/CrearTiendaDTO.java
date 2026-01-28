package com.subastashop.backend.dto;

import lombok.Data;

@Data
public class CrearTiendaDTO {
    private String nombre;
    private String slug;
    private String emailAdmin;
}