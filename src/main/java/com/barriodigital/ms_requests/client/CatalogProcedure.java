package com.barriodigital.ms_requests.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Vista mínima de un tipo de trámite del catálogo.
 * QUÉ ES: DTO con solo los campos que requests necesita (id, nombre, activo); el resto se ignora.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogProcedure(Long id, String name, boolean active) {
}
