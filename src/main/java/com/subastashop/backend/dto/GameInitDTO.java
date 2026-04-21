package com.subastashop.backend.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameInitDTO {
    private String token; // Token firmado que autoriza el inicio y la marca de tiempo temporal
    private Long serverTimeMs; // Tiempo en el momento en el que el server autorizó
}
