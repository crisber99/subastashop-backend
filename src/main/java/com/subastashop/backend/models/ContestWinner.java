package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "GanadoresConcurso")
public class ContestWinner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto contest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participacion_id")
    private Participation winningParticipation;

    private Integer rank;
    
    private LocalDateTime winningDate = LocalDateTime.now();
}