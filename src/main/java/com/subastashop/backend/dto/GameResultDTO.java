package com.subastashop.backend.dto;

import lombok.Data;

@Data
public class GameResultDTO {
    private String token;     // El token que el servidor entrego al iniciar
    private Long timeMs;      // Milisegundos reportados por el cliente
    private Integer moves;    // Movimientos reportados por el cliente (usado en Hanoi, opcional en otros)
}
