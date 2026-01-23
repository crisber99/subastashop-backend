package com.subastashop.backend.models;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class TicketRifa {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer numeroTicket;

    @ManyToOne
    private Producto rifa;

    @ManyToOne
    private AppUsers comprador;

    private LocalDateTime fechaCompra = LocalDateTime.now();
}
