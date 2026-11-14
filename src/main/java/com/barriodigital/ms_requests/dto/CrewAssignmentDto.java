package com.barriodigital.ms_requests.dto;

import jakarta.validation.constraints.Size;

/**
 * Datos de la cuadrilla a la que se asigna la solicitud al pasar a EN_TERRENO. Texto libre: el nombre,
 * los integrantes y la patente son obligatorios (lo valida el servicio, porque solo se exigen en ese
 * cambio de estado); el tipo de vehículo y el teléfono son opcionales.
 */
public record CrewAssignmentDto(
        @Size(max = 100, message = "El nombre de la cuadrilla no puede superar los 100 caracteres")
        String name,

        @Size(max = 300, message = "Los integrantes no pueden superar los 300 caracteres")
        String members,

        @Size(max = 20, message = "La patente no puede superar los 20 caracteres")
        String vehiclePlate,

        @Size(max = 60, message = "El tipo de vehículo no puede superar los 60 caracteres")
        String vehicleType,

        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
        String contactPhone) {
}
