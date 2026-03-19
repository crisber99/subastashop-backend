package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

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
    
    @ElementCollection
    @CollectionTable(name = "producto_imagenes", joinColumns = @JoinColumn(name = "producto_id"))
    @Column(name = "url_imagen")
    private List<String> imagenes = new ArrayList<>();

    // --- Configuración Híbrida ---
    @Column(nullable = false)
    private String tipoVenta; // 'DIRECTA' o 'SUBASTA'

    @Column(nullable = false)
    private BigDecimal precioBase;

    private Integer stock;

    // --- Campos de Subasta ---
    private BigDecimal precioActual;
    private LocalDateTime fechaFinSubasta;

    @Column(length = 20)
    private String estado = "DISPONIBLE";

    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @OneToMany(mappedBy = "producto")
    @JsonIgnore
    private List<Puja> pujas;

    private Integer cantidadNumeros;
    private Integer cantidadGanadores;
    private BigDecimal precioTicket;

    @ManyToOne
    @JoinColumn(name = "tienda_id")
    @JsonIgnoreProperties({"productos", "password", "usuario", "hibernateLazyInitializer", "handler"})
    private Tienda tienda;
}