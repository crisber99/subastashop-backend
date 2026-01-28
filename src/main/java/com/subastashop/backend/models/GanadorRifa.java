package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ganadores_rifa")
public class GanadorRifa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto rifa;

    @OneToOne
    @JoinColumn(name = "ticket_id")
    private TicketRifa ticketGanador;

    private Integer puesto; // 1, 2, 3

    private LocalDateTime fechaGanador = LocalDateTime.now();
}