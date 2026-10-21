package com.barriodigital.ms_requests.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos que el cliente envía para crear una solicitud. El nombre del tipo, el estado
 * y la fecha los define el servidor.
 *
 * @param userId solo lo respeta el servidor cuando quien crea es Operador o Admin (ingreso a nombre
 *               de un vecino, con el {@code oid} del vecino); para un Cliente se ignora y el dueño
 *               es siempre el {@code oid} de su JWT.
 */
public record CreateRequestDto(
        @NotNull(message = "Debes elegir un tipo de trámite")
        Long procedureId,

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 255, message = "La descripción admite hasta {max} caracteres")
        String description,

        String userId,

        @Size(max = 12, message = "El RUT admite hasta {max} caracteres")
        String citizenRut
) {
}
