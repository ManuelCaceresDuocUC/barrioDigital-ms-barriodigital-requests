package com.barriodigital.ms_requests.messaging;

import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;

/**
 * Evento interno: un trámite se creó o cambió de estado.
 *
 * @param reason         motivo del cambio (al rechazar o cancelar en terreno); nulo en los demás casos
 * @param previousStatus estado anterior; nulo al ingresar
 */
public record RequestStatusChangedEvent(
        Long requestId,
        String type,
        String description,
        RequestStatus status,
        String requesterEmail,
        String requesterName,
        String citizenRut,
        String reason,
        RequestStatus previousStatus) {

    public RequestStatusChangedEvent(Long requestId, String type, String description, RequestStatus status,
            String requesterEmail, String requesterName, String citizenRut, String reason) {
        this(requestId, type, description, status, requesterEmail, requesterName, citizenRut, reason, null);
    }

    public RequestStatusChangedEvent(Long requestId, String type, String description, RequestStatus status,
            String requesterEmail, String requesterName, String citizenRut) {
        this(requestId, type, description, status, requesterEmail, requesterName, citizenRut, null, null);
    }

    /**
     * La cancelación en terreno (EN_TERRENO a EN_GESTION) es un asunto interno: el vecino no recibe correo
     * ni se crea un ticket para la cuadrilla.
     */
    public boolean isInternalOnly() {
        return previousStatus == RequestStatus.EN_TERRENO && status == RequestStatus.EN_GESTION;
    }

    public static RequestStatusChangedEvent from(RequestEntity request) {
        return from(request, null);
    }

    public static RequestStatusChangedEvent from(RequestEntity request, String reason) {
        return from(request, reason, null);
    }

    public static RequestStatusChangedEvent from(RequestEntity request, String reason,
            RequestStatus previousStatus) {
        return new RequestStatusChangedEvent(
                request.getId(), request.getType(), request.getDescription(), request.getStatus(),
                request.getRequesterEmail(), request.getRequesterName(), request.getCitizenRut(), reason,
                previousStatus);
    }
}