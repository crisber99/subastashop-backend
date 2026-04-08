package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ganadores_concurso")
public class ContestWinner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Producto contest;

    @ManyToOne(fetch = FetchType.LAZY)
    private Participation winningParticipation;

    private Integer rank;
    
    private LocalDateTime winningDate = LocalDateTime.now();
}