package com.barriodigital.ms_requests.client;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Cliente HTTP hacia ms-barriodigital-catalog. Reenvía el token del usuario, de modo que
 * el catálogo aplica sus propios permisos por rol.
 */
@Component
public class CatalogClient {

    private static final String PROCEDURES = "/api/catalog/procedures";

    private final RestClient restClient;

    public CatalogClient(RestClient catalogRestClient) {
        this.restClient = catalogRestClient;
    }

    /** Devuelve el tipo de trámite; si no existe (o el catálogo no lo muestra) responde 400. */
    public CatalogProcedure getProcedure(Long id, String bearerToken) {
        try {
            return restClient.get()
                    .uri(PROCEDURES + "/{id}", id)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(CatalogProcedure.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El tipo de trámite no existe o no está disponible");
        } catch (RestClientResponseException ex) {
            throw upstreamError(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    /** Descuenta un cupo diario del tipo. Responde 409 con el motivo si no se puede. */
    public void consumeQuota(Long procedureId, String bearerToken) {
        try {
            restClient.post()
                    .uri(PROCEDURES + "/{id}/quota/consume", procedureId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict ex) {
            CatalogError error = ex.getResponseBodyAs(CatalogError.class);
            String message = error != null && error.message() != null
                    ? error.message()
                    : "No hay cupo disponible para este tipo de trámite";
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El tipo de trámite de la solicitud ya no existe en el catálogo");
        } catch (RestClientResponseException ex) {
            throw upstreamError(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    private ResponseStatusException upstreamError(RestClientResponseException ex) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "El catálogo respondió con un error (HTTP " + ex.getStatusCode().value() + ")");
    }

    private ResponseStatusException unavailable(ResourceAccessException ex) {
        boolean timeout = ex.getCause() instanceof HttpTimeoutException
                || ex.getCause() instanceof SocketTimeoutException;
        return timeout
                ? new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "El catálogo tardó demasiado en responder")
                : new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo contactar al catálogo");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CatalogError(String message) {
    }
}
