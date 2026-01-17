package com.subastashop.backend.models;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class NuevaPujaRequest {
    private Integer productoId;
    private Integer usuarioId; // Simulado por ahora
    private BigDecimal monto;
}