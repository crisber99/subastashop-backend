package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;

@Data
@Entity
@Table(name = "premios_caja")
public class PremioCaja implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String nombre;
    private String descripcion;
    private String imagenUrl;
    
    // Stock disponible de este premio
    private Integer stock;
    
    // Probabilidad de 0 a 100
    private Integer probabilidad;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Producto producto;
}
