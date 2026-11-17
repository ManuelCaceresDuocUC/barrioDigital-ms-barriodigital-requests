package com.barriodigital.ms_requests.messaging;

import com.barriodigital.ms_requests.dto.CrewAssignmentDto;
import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;

/**
 * Evento interno: un trámite se creó o cambió de estado.
 *
 * @param reason         motivo del cambio (al rechazar o cancelar en terreno); nulo en los demás casos
 * @param previousStatus estado anterior; nulo al ingresar
 * @param staffAction    quién hizo el cambio y con qué cuadrilla (para la auditoría en Kafka); nulo al ingresar
 *
 * <p>QUÉ ES: un evento de Spring (dentro del mismo proceso, no viaja por la red). RequestService lo publica
 * y DOS oyentes lo reciben: RequestNotificationPublisher (RabbitMQ) y RequestStreamPublisher (Kafka).
 * Es la "fuente única de verdad" de qué pasó.
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
        RequestStatus previousStatus,
        StaffAction staffAction) {

    /** Actuación del personal que cambió el estado; no incluye correo ni RUT de nadie. */
    public record StaffAction(String actorId, String actorName, String actorRole, CrewAssignmentDto crew) {
    }

    // Constructores "de conveniencia": permiten crear el evento con menos datos (los que faltan quedan null).
    // Se conservaron al agregar campos para no romper el código ni los tests existentes.
    public RequestStatusChangedEvent(Long requestId, String type, String description, RequestStatus status,
            String requesterEmail, String requesterName, String citizenRut, String reason,
            RequestStatus previousStatus) {
        this(requestId, type, description, status, requesterEmail, requesterName, citizenRut, reason,
                previousStatus, null);
    }

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

    // Métodos de fábrica encadenados: cada uno completa con null lo que no recibe y delega en el más completo.
    public static RequestStatusChangedEvent from(RequestEntity request) {
        return from(request, null);
    }

    public static RequestStatusChangedEvent from(RequestEntity request, String reason) {
        return from(request, reason, null);
    }

    public static RequestStatusChangedEvent from(RequestEntity request, String reason,
            RequestStatus previousStatus) {
        return from(request, reason, previousStatus, null);
    }

    // Versión completa: copia los datos de la solicitud guardada al evento.
    public static RequestStatusChangedEvent from(RequestEntity request, String reason,
            RequestStatus previousStatus, StaffAction staffAction) {
        return new RequestStatusChangedEvent(
                request.getId(), request.getType(), request.getDescription(), request.getStatus(),
                request.getRequesterEmail(), request.getRequesterName(), request.getCitizenRut(), reason,
                previousStatus, staffAction);
    }
}
