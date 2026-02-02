package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@EqualsAndHashCode(callSuper = false) // Generalmente los detalles no extienden BaseEntity igual
@Entity
@Table(name = "DetalleOrden") // Debe coincidir con tu tabla SQL
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetalleOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id", nullable = false)
    @ToString.Exclude // Importante para evitar bucles infinitos al imprimir logs
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Orden orden;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Producto producto;

    private Integer cantidad;

    private BigDecimal precioUnitario; // Precio al momento de la compra

    @Column(length = 20)
    private String tipoCompra; // "DIRECTA", "SUBASTA", "RIFA"

    private String datosExtra; // Ej: "Ticket #45"
}