package com.barriodigital.ms_requests.dto;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cuerpo de error con los mismos nombres de campo que el error por defecto de Spring Boot.
 * QUÉ ES: el JSON uniforme de errores (lo arma GlobalExceptionHandler). Los campos null no se envían.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        // Solo en errores de validación: campo -> mensaje.
        Map<String, String> fieldErrors) {
}
