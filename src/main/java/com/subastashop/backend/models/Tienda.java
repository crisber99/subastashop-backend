package com.subastashop.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "tiendas")
public class Tienda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String slug;
    private String logoUrl;
    
    // Configuración visual (para que cada tienda tenga sus colores)
    private String colorPrimario; 
    
    private Boolean activa = true;
    private String rutEmpresa; // O RUT personal del dueño
    
    private String documentoAnversoUrl; // Foto Carnet Frente
    private String documentoReversoUrl; // Foto Carnet Dorso
    
    private boolean identidadVerificada = false; // Check del Super Admin
    
    private LocalDateTime fechaCreacion = LocalDateTime.now();
    
    // Datos Bancarios para Transferencia (Ya que el pago es directo)
    @Column(length = 1000) // Texto largo
    private String datosBancarios;
}