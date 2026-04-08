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

    // Use default naming for now to avoid crashes on startup
    private Long productoId;
    private Long tiendaId;

    @Column(nullable = false)
    private String remitenteNombre;

    @Column(nullable = false)
    private String userEmail;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String contenido;

    @Column(nullable = false)
    private LocalDateTime fechaEnvio = LocalDateTime.now();

    private boolean esVendedor = false;
    private boolean admin = false;
    
    private String timestampStr;
}
