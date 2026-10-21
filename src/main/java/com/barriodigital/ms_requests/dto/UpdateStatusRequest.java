package com.barriodigital.ms_requests.dto;

import com.barriodigital.ms_requests.model.RequestStatus;

import jakarta.validation.constraints.NotNull;

/** Cuerpo de {@code PUT /api/requests/{id}/status}. Un valor que no sea un estado se rechaza con 400. */
public record UpdateStatusRequest(
        @NotNull(message = "El estado es obligatorio")
        RequestStatus status) {
}
