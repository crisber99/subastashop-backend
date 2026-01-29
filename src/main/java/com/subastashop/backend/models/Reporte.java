package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reportes")
public class Reporte {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;

    private String motivo; // Ej: "Estafa", "Producto Ilegal", "Ofensivo"
    private String emailDenunciante; // Opcional, para saber quién fue
    
    private LocalDateTime fechaReporte = LocalDateTime.now();
    
    // Estado del reporte: PENDIENTE, REVISADO, DESCARTADO
    private String estado = "PENDIENTE"; 
}