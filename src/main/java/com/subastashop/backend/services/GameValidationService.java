package com.subastashop.backend.services;

import com.subastashop.backend.models.Producto;
import org.springframework.stereotype.Service;

@Service
public class GameValidationService {

    /**
     * Valida de manera estricta que los tiempos reportados concuerden con las físicas y matemáticas teóricas.
     * Retorna true si es válido, falso si se detecta trampa.
     */
    public boolean isResultValid(Producto concurso, long reportedTimeMs, long actualElapsedTimeMs, Integer reportedMoves) {
        // Tolerancia por latencia de red y renderizado (ej. 1500ms)
        long LATENCIA_TOLerada_MS = 1500;

        // Regla 1: El tiempo transcurrido físicamente en el servidor no puede ser mucho menor 
        // que el tiempo que el cliente afirma haber tardado (Hack de inyección/modificación de payload).
        if (actualElapsedTimeMs + LATENCIA_TOLerada_MS < reportedTimeMs) {
            System.err.println("❌ Posible Trampa Detectada: El servidor registró " + actualElapsedTimeMs + "ms, pero el cliente reporta " + reportedTimeMs + "ms.");
            return false; 
        }

        String tipoJuego = concurso.getTipoJuego() != null ? concurso.getTipoJuego() : "MEMORICE";

        switch (tipoJuego.toUpperCase()) {
            case "HANOI":
                return validateHanoi(concurso.getNumeroPares(), reportedTimeMs, reportedMoves);
            case "REACCION":
                return validateReaccion(reportedTimeMs);
            case "MEMORICE":
            default:
                return validateMemorice(concurso.getNumeroPares(), reportedTimeMs);
        }
    }

    private boolean validateHanoi(Integer numDiscos, long reportedTimeMs, Integer reportedMoves) {
        if (numDiscos == null) numDiscos = 5;
        
        // El mínimo teórico de movimientos en Torre de Hanói es (2^N) - 1
        int minimosTeoricos = (int) Math.pow(2, numDiscos) - 1;
        
        if (reportedMoves == null || reportedMoves < minimosTeoricos) {
            System.err.println("❌ Fraude en Hanói: Reporta " + reportedMoves + " movs. El mínimo teórico es " + minimosTeoricos);
            return false;
        }

        // Físicamente, un humano muy experto demora al menos ~300ms por movimiento "perfecto" vía drag&drop.
        long minimosMilisegundosFisicos = minimosTeoricos * 300L;
        if (reportedTimeMs < minimosMilisegundosFisicos) {
            System.err.println("❌ Tiempo irreal en Hanói: Tardó " + reportedTimeMs + "ms. Humanamente imposible bajar de " + minimosMilisegundosFisicos + "ms.");
            return false;
        }

        return true;
    }

    private boolean validateReaccion(long reportedTimeMs) {
        // La prueba de reacción humana más rápida ronda los 150ms. 
        // Cualquier cosa bajo 100ms sostenido es probablemente un script pre-programado o autoclicker.
        // Si bien permitimos promedios, un promedio total reportado bajo 100ms es sospechoso, pero por ahora
        // la regla dura puede ser no permitir tiempos negativos o extremadamente irrisorios (bajo 50ms)
        if (reportedTimeMs < 50) {
            System.err.println("❌ Fraude (Autoclicker / Script) en Reacción: " + reportedTimeMs + "ms es un reflejo super-humano constante.");
            return false; // Autoclicker
        }
        return true;
    }

    private boolean validateMemorice(Integer pares, long reportedTimeMs) {
        if (pares == null) pares = 5;
        // Mínimo de clics teóricos = pares * 2 (si jamás de equivoca).
        // Tiempo por volteo de carta y animación ~ 400ms por acción
        // Esto es muy benevolente pero atrapa tiempos cero o absurdos de 1 segundo.
        long minDistanciaFisicaMs = pares * 2 * 300L; 
        
        if (reportedTimeMs < minDistanciaFisicaMs) {
            System.err.println("❌ Fraude en Memorice: " + reportedTimeMs + "ms. Matemáticamente imposible abrir todas en ese tiempo.");
            return false;
        }
        return true;
    }
}
