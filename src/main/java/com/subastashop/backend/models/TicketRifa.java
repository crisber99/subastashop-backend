package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class TicketRifa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer numeroTicket;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto rifa;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private AppUsers comprador;

    private LocalDateTime fechaCompra = LocalDateTime.now();
}