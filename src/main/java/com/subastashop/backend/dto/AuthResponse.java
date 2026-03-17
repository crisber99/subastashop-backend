package com.subastashop.backend.dto;

import lombok.Data;

@Data
public class AuthResponse {
    private String token;
    private String nombre;
    private Integer userId;
    
    public AuthResponse(String token, String nombre, Integer userId) {
        this.token = token;
        this.nombre = nombre;
        this.userId = userId;
    }
}
