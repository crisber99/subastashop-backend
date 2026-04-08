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
    @Column(name = "IdentificadorInscripcion")
    private Integer identificadorInscripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    //@JoinColumn(name = "ProductoId")
    private Producto contest; // Rifa -> Contest

    @ManyToOne(fetch = FetchType.LAZY)
    //@JoinColumn(name = "UsuarioId")
    private AppUsers participant; // Comprador -> Participant

    @Column(name = "CreatedAt")
    private LocalDateTime createdAt = LocalDateTime.now(); // fechaCompra -> createdAt

    @Column(name = "Paid")
    private Boolean paid = false; // pagado -> paid

    @Column(name = "PaymentSlipUrl")
    private String paymentSlipUrl; // comprobanteUrl -> paymentSlipUrl

    @Column(name = "DurationMs")
    private Long durationMs; // Nuevo: Tiempo de resolución en milisegundos

    @Column(name = "CompletionTimestamp")
    private LocalDateTime completionTimestamp; // Nuevo: Instante de finalización
}