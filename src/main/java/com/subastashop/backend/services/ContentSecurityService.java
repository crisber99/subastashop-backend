package com.subastashop.backend.services;

import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.List;

@Service
public class ContentSecurityService {

    // Lista negra básica (puedes ampliarla o cargarla de BD en el futuro)
    private static final List<String> PALABRAS_PROHIBIDAS = Arrays.asList(
        "droga", "cocaína", "marihuana", "fentanilo", "lsd", "éxtasis",
        "arma", "pistola", "fusil", "munición", "bala", "explosivo",
        "sicario", "asesinato", "violación", "pedofilia", "cp",
        "base de datos", "tarjeta clonada", "hack", "crack", 
        "lavado de dinero", "estafa", "bitcoin falso"
    );

    public boolean tieneContenidoIlegal(String texto) {
        if (texto == null || texto.isEmpty()) return false;
        
        String textoNormalizado = texto.toLowerCase();

        for (String palabra : PALABRAS_PROHIBIDAS) {
            // Buscamos la palabra exacta o contenida, con espacios alrededor para evitar falsos positivos
            // (aunque para máxima seguridad, un 'contains' simple es mejor al inicio)
            if (textoNormalizado.contains(palabra)) {
                return true; 
            }
        }
        return false;
    }
}