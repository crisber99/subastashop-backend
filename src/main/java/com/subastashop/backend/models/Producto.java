package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true) // Importante para que Lombok incluya el TenantId en el equals
@Entity
@Table(name = "Productos")
public class Producto extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String nombre;

    private String descripcion;
    private String imagenUrl;

    // --- Configuración Híbrida ---
    @Column(nullable = false)
    private String tipoVenta; // 'DIRECTA' o 'SUBASTA'

    @Column(nullable = false)
    private BigDecimal precioBase;

    private Integer stock;

    // --- Campos de Subasta ---
    private BigDecimal precioActual;
    private LocalDateTime fechaFinSubasta;

    @Column(columnDefinition = "varchar(20) default 'DISPONIBLE'")
    private String estado;

    private LocalDateTime fechaCreacion = LocalDateTime.now();
}