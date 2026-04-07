package com.subastashop.backend.exceptions;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAllExceptions(Exception ex) {
        ex.printStackTrace(); // Log fundamental en el servidor
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Error interno o Bad Request");
        body.put("message", ex.getMessage());
        body.put("type", ex.getClass().getSimpleName());
        return ResponseEntity.status(400).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Error de tipo en PathVariable o Param");
        String typeName = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "desconocido";
        body.put("message", String.format("El parámetro '%s' con valor '%s' no pudo ser convertido a %s", 
                ex.getName(), ex.getValue(), typeName));
        return ResponseEntity.status(400).body(body);
    }
}
