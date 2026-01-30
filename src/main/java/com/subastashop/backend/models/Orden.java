package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true) // O false, depende de tu BaseEntity
@Entity
@Table(name = "Ordenes")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Orden extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Usamos Long porque en SQL es BIGINT

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private AppUsers usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tienda_id", nullable = false)
    private Tienda tienda;

    @CreationTimestamp // Esto llena la fecha automáticamente al guardar
    @Column(updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(length = 20)
    private String estado; // Sugerencia: Podrías usar un ENUM aquí

    private BigDecimal total; // BigDecimal es mejor para dinero que Double

    private String comprobanteUrl;
    private LocalDateTime fechaExpiracionReserva;

    // Relación Bidireccional
    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default // Necesario si usas @Builder para que no borre la inicialización
    private List<DetalleOrden> detalles = new ArrayList<>();
    
    // Método de ayuda para agregar detalles y mantener la coherencia
    public void addDetalle(DetalleOrden detalle) {
        detalles.add(detalle);
        detalle.setOrden(this);
    }
}