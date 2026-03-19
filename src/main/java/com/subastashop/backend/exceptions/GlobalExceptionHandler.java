package com.subastashop.backend.exceptions;

import com.subastashop.backend.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
        String msg = e.getMessage();
        HttpStatus status = HttpStatus.BAD_REQUEST;

        if (msg.startsWith("ForbiddenStore:")) {
            status = HttpStatus.FORBIDDEN;
            msg = msg.replace("ForbiddenStore: ", "");
        } else if (msg.equals("Orden no encontrada") || msg.equals("Producto no encontrado") || msg.equals("Usuario no encontrado")) {
            status = HttpStatus.NOT_FOUND;
        }

        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), msg));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Ocurrió un error inesperado: " + e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error crítico del servidor: " + e.getMessage()));
    }
}
