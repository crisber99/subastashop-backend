package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "support_tickets")
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private AppUsers usuario;

    @Column(nullable = false)
    private String asunto;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String mensaje;

    @Column(columnDefinition = "TEXT")
    private String respuestaAdmin;

    @Enumerated(EnumType.STRING)
    private TicketEstado estado = TicketEstado.ABIERTO;

    private LocalDateTime fechaCreacion = LocalDateTime.now();
    private LocalDateTime fechaRespuesta;

    public enum TicketEstado {
        ABIERTO, CERRADO
    }
}
