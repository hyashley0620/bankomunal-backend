package com.bankomunal.exception;

import com.bankomunal.dto.response.ApiError;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Acceso denegado",
                        "mensaje", "No tienes permisos para realizar esta acción."));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurity(SecurityException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Acceso denegado.";
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Acceso denegado", "mensaje", msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArg(IllegalArgumentException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Solicitud inválida.";
        return ResponseEntity.badRequest().body(Map.of("mensaje", msg));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "Operación no permitida.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje", msg));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(e -> {
            String field = e instanceof FieldError fe ? fe.getField() : e.getObjectName();
            errors.put(field, e.getDefaultMessage() != null ? e.getDefaultMessage() : "valor inválido");
        });
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("mensaje", "Parámetro requerido faltante: " + ex.getParameterName()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("mensaje", "El archivo supera el tamaño máximo permitido (10 MB)."));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(
            org.springframework.dao.DataIntegrityViolationException ex) {
        String msg = "Ya existe un registro con esos datos.";
        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("email"))
                msg = "El correo ya está registrado.";
            else if (ex.getMessage().contains("identification_number"))
                msg = "El número de identificación ya está registrado.";
            else if (ex.getMessage().contains("account_code"))
                msg = "El código de cuenta ya existe.";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje", msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        // Nunca return null mensaje
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = "Error interno del servidor. Revisa los logs para más detalles.";
        }
        System.err.println("[ERROR] " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(500, msg));
    }
}
