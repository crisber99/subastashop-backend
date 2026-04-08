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

    @Column(name = "identificadorInscripcion")
    private Integer identificadorInscripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ProductoId")
    private Producto contest; 

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UsuarioId")
    private AppUsers participant; 

    @Column(name = "createdAt")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "paid")
    private boolean paid = false;
    
    @Column(name = "paymentSlipUrl")
    private String paymentSlipUrl;
    
    @Column(name = "durationMs")
    private Long durationMs;
    
    @Column(name = "completionTimestamp")
    private LocalDateTime completionTimestamp;
}