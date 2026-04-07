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
    @Column(name = "identificador_inscripcion")
    private Integer identificadorInscripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto contest; // Rifa -> Contest

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private AppUsers participant; // Comprador -> Participant

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now(); // fechaCompra -> createdAt

    private Boolean paid = false; // pagado -> paid

    @Column(name = "payment_slip_url")
    private String paymentSlipUrl; // comprobanteUrl -> paymentSlipUrl

    @Column(name = "duration_ms")
    private Long durationMs; // Nuevo: Tiempo de resolución en milisegundos

    @Column(name = "completion_timestamp")
    private LocalDateTime completionTimestamp; // Nuevo: Instante de finalización
}