package com.barriodigital.ms_requests.messaging;

import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;

/**
 * Evento interno: un trámite se creó o cambió de estado.
 *
 * @param reason motivo del cambio (solo al rechazar); nulo en los demás casos
 */
public record RequestStatusChangedEvent(
        Long requestId,
        String type,
        String description,
        RequestStatus status,
        String requesterEmail,
        String requesterName,
        String citizenRut,
        String reason) {

    public RequestStatusChangedEvent(Long requestId, String type, String description, RequestStatus status,
            String requesterEmail, String requesterName, String citizenRut) {
        this(requestId, type, description, status, requesterEmail, requesterName, citizenRut, null);
    }

    public static RequestStatusChangedEvent from(RequestEntity request) {
        return from(request, null);
    }

    public static RequestStatusChangedEvent from(RequestEntity request, String reason) {
        return new RequestStatusChangedEvent(
                request.getId(), request.getType(), request.getDescription(), request.getStatus(),
                request.getRequesterEmail(), request.getRequesterName(), request.getCitizenRut(), reason);
    }
}