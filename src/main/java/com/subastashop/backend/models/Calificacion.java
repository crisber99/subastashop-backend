package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "calificaciones")
public class Calificacion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private AppUsers usuario;

    @Column(name = "valor_puntuacion", nullable = false)
    private Integer puntuacion; // 1 a 5 estrellas

    @Column(length = 1000)
    private String comentario;

    @Column(name = "fecha_calificacion")
    private LocalDateTime fecha = LocalDateTime.now();
}
