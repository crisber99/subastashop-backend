package com.subastashop.backend.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleApiException(ApiException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Error de Aplicación");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Error de Validación");
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        org.springframework.validation.FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Campo inválido",
                        (msg1, msg2) -> msg1 + ", " + msg2
                ));
        body.put("details", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Acceso Denegado");
        body.put("message", "No tienes permisos suficientes para realizar esta acción.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAllExceptions(Exception ex) {
        ex.printStackTrace();
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Error interno de servidor (500)");
        body.put("message", ex.getMessage());
        body.put("type", ex.getClass().getSimpleName());
        if (ex.getStackTrace().length > 0) {
            body.put("location", ex.getStackTrace()[0].toString());
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Error de tipo de dato");
        String typeName = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "desconocido";
        body.put("message", String.format("El parámetro '%s' con valor '%s' no pudo ser convertido a %s", 
                ex.getName(), ex.getValue(), typeName));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
