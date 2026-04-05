package com.subastashop.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MensajeChatDTO {
    private String id; // Opcional, generado por el servidor o db
    private String contenido;
    private String remitenteNombre;
    private Long tiendaId;
    private String timestamp; // Formateado para el frontend
    private String userEmail; 
}
