package com.subastashop.backend.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MensajeChatDTO {
    private String id; 
    private String contenido;
    private String remitenteNombre;
    private Long productoId;
    private Long tiendaId;
    private String timestamp; 
    private String userEmail; 
    private boolean esVendedor;
    private boolean admin;
}
