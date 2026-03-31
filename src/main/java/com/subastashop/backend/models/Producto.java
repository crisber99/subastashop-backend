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
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "Productos")
@org.hibernate.annotations.SQLDelete(sql = "UPDATE Productos SET deleted = 1 WHERE id=?")
@org.hibernate.annotations.Where(clause = "deleted = 0")
public class Producto extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private String nombre;

    @Column(unique = true)
    private String slug;

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
    @JsonIgnoreProperties({"productos", "usuario", "hibernateLazyInitializer", "handler", "rutEmpresa", "datosBancarios", "documentoAnversoUrl", "documentoReversoUrl", "fechaAceptacionTerminos"})
    private Tienda tienda;

    @ManyToOne
    @JoinColumn(name = "categoria_id")
    @JsonIgnoreProperties({"productos", "hibernateLazyInitializer", "handler"})
    private Categoria categoria;

    @PrePersist
    @PreUpdate
    private void generateSlug() {
        if (this.slug == null || this.slug.isEmpty()) {
            if (this.nombre != null) {
                this.slug = this.nombre.toLowerCase()
                    .replaceAll("[^a-z0-9]", "-")
                    .replaceAll("-+", "-")
                    .replaceAll("^-|-$", "");
                
                // Add a small random suffix to ensure "encryption" feel and uniqueness
                this.slug += "-" + java.util.UUID.randomUUID().toString().substring(0, 5);
            }
        }
    }
}