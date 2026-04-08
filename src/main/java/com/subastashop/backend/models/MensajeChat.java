package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "mensajes_chat")
public class MensajeChat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //@Column(name = "ProductoId", nullable = false)
    private Long productoId;

    //@Column(name = "TiendaId", nullable = true)
    private Long tiendaId;

    @Column(nullable = false)
    private String remitenteNombre;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    private String userEmail;

    private String timestampStr; // HH:mm para el frontend

    private LocalDateTime fechaEnvio = LocalDateTime.now();

    private boolean esVendedor = false;
    private boolean admin = false;
}
