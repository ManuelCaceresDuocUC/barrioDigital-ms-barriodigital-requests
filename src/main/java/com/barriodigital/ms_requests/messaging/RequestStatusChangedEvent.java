package com.barriodigital.ms_requests.messaging;

import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;

/** Evento interno: un trámite se creó o cambió de estado. */
public record RequestStatusChangedEvent(
        Long requestId,
        String type,
        String description,
        RequestStatus status,
        String requesterEmail,
        String requesterName,
        String citizenRut) {

    public static RequestStatusChangedEvent from(RequestEntity request) {
        return new RequestStatusChangedEvent(
                request.getId(), request.getType(), request.getDescription(), request.getStatus(),
                request.getRequesterEmail(), request.getRequesterName(), request.getCitizenRut());
    }
}