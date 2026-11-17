package com.barriodigital.ms_requests.dto;

import com.barriodigital.ms_requests.model.RequestStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PUT /api/requests/{id}/status}. Un valor que no sea un estado se rechaza con 400.
 *
 * @param reason motivo del cambio: obligatorio al pasar a RECHAZADO y al cancelar en terreno
 *               (EN_TERRENO a EN_GESTION), lo valida el servicio; ignorado en los demás cambios
 * @param crew   datos de la cuadrilla: obligatorios al pasar a EN_TERRENO; ignorados en los demás cambios
 */
public record UpdateStatusRequest(
        // El enum RequestStatus rechaza valores desconocidos (ver GlobalExceptionHandler).
        @NotNull(message = "El estado es obligatorio")
        RequestStatus status,

        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String reason,

        // @Valid: valida también los campos internos del objeto cuadrilla.
        @Valid
        CrewAssignmentDto crew) {

    // Constructor corto sin cuadrilla (cómodo en tests y en cambios que no la necesitan).
    public UpdateStatusRequest(RequestStatus status, String reason) {
        this(status, reason, null);
    }
}
