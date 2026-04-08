package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "participaciones")
public class Participation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer identificadorInscripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    private Producto contest;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUsers participant;

    private LocalDateTime createdAt = LocalDateTime.now();
    private boolean paid = false;
    private String paymentSlipUrl;
    private Long durationMs;
    private LocalDateTime completionTimestamp;
}