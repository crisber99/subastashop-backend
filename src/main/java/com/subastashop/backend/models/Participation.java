package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ParticipacionesConcurso")
public class Participation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identificador opcional para retrocompatibilidad
    private Integer identificadorInscripcion;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto contest; // Rifa -> Contest

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private AppUsers participant; // Comprador -> Participant

    private LocalDateTime createdAt = LocalDateTime.now(); // fechaCompra -> createdAt

    private Boolean paid = false; // pagado -> paid

    private String paymentSlipUrl; // comprobanteUrl -> paymentSlipUrl

    private Long durationMs; // Nuevo: Tiempo de resolución en milisegundos

    private LocalDateTime completionTimestamp; // Nuevo: Instante de finalización
}