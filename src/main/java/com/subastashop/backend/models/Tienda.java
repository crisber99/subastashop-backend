package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "tiendas")
public class Tienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre; // Ej: "Rifas Don Bernardo"
    private String slug;   // Ej: "don-bernardo" (para la URL: subastashop.com/don-bernardo)
    private String logoUrl;
    
    // Configuración visual (para que cada tienda tenga sus colores)
    private String colorPrimario; 
    
    private Boolean activa = true;
    private LocalDateTime fechaCreacion = LocalDateTime.now();
}