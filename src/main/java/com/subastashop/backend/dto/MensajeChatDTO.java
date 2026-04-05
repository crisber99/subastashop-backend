package com.subastashop.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MensajeChatDTO {
    private String id; 
    private String contenido;
    private String remitenteNombre;
    private Long tiendaId;
    private String timestamp; 
    private String userEmail; 
    // Añadimos campos para compatibilidad con el frontend anterior por si acaso
    private boolean esVendedor;
    private boolean admin;
}
