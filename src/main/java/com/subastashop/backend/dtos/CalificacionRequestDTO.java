package com.subastashop.backend.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CalificacionRequestDTO(
    @NotNull(message = "El ID del producto es obligatorio")
    Integer productoId,
    
    @NotNull(message = "La puntuación es obligatoria")
    @Min(value = 1, message = "La puntuación mínima es 1")
    @Max(value = 5, message = "La puntuación máxima es 5")
    Integer puntuacion,
    
    @Size(max = 1000, message = "El comentario excede el tamaño máximo permitido (1000 caracteres)")
    String comentario
) {}
