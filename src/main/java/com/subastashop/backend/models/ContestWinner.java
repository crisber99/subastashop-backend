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
    //@JoinColumn(name = "ProductoId")
    private Producto contest;

    @ManyToOne(fetch = FetchType.LAZY)
    //@JoinColumn(name = "ParticipacionId")
    private Participation winningParticipation;

    private Integer rank; // puesto -> rank

    private LocalDateTime winningDate = LocalDateTime.now(); // fechaGanador -> winningDate
}