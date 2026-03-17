package com.subastashop.backend.config;

import com.subastashop.backend.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        String mensaje = ex.getMessage();
        HttpStatus status = HttpStatus.BAD_REQUEST; // Default 400

        // Reglas de negocio que hemos visto en el código y devuelven diferentes status
        if (mensaje.equals("Orden no encontrada") || mensaje.equals("Producto no encontrado") || mensaje.equals("Usuario no encontrado")) {
            status = HttpStatus.NOT_FOUND;
        } else if (mensaje.startsWith("ForbiddenStore:") || mensaje.equals("No tienes permiso para ver esta orden") || mensaje.equals("No tienes una tienda asignada para crear productos.")) {
             status = HttpStatus.FORBIDDEN;
             // Limpiar el tag técnico
             if (mensaje.startsWith("ForbiddenStore:")) {
                 mensaje = mensaje.replace("ForbiddenStore: ", "");
             }
        } else if (mensaje.startsWith("CensoredContent:")) {
             status = HttpStatus.BAD_REQUEST;
             mensaje = mensaje.replace("CensoredContent: ", "");
        }

        ErrorResponse error = new ErrorResponse(status.value(), mensaje);
        return new ResponseEntity<>(error, status);
    }

    // Un último escudo de protección para que la API nunca devuelva HTML ante un fallo severo
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "Error interno del servidor: " + ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
