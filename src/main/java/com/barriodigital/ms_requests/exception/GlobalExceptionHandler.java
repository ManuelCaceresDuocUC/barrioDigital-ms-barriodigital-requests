package com.barriodigital.ms_requests.exception;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import com.barriodigital.ms_requests.dto.ApiError;
import com.barriodigital.ms_requests.model.RequestStatus;

/**
 * Traduce excepciones a respuestas JSON con un campo {@code message} legible, sin
 * depender de la configuración de errores de Spring Boot.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        return build(status, ex.getReason(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, String.join("; ", fieldErrors.values()), fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        if (mentions(ex, RequestStatus.class.getSimpleName())) {
            String allowed = Arrays.stream(RequestStatus.values()).map(Enum::name).collect(Collectors.joining(", "));
            return build(HttpStatus.BAD_REQUEST, "Estado no válido. Valores permitidos: " + allowed, null);
        }
        return build(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es un JSON válido", null);
    }

    private static boolean mentions(Throwable error, String text) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(text)) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<ApiError> build(HttpStatusCode status, String message, Map<String, String> fieldErrors) {
        String reason = status instanceof HttpStatus http ? http.getReasonPhrase() : status.toString();
        ApiError body = new ApiError(Instant.now(), status.value(), reason, message, fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
