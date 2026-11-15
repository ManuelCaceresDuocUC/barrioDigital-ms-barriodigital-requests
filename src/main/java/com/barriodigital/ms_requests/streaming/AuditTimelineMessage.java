package com.barriodigital.ms_requests.streaming;

/** Acción del personal sobre un trámite (tópico audit.timeline): quién, qué cambió y por qué. */
public record AuditTimelineMessage(
        String eventId,
        String occurredAt,
        Long requestId,
        String action,
        String fromStatus,
        String toStatus,
        String reason,
        String actorId,
        String actorName,
        String actorRole,
        Crew crew) {

    public static final String STATUS_CHANGE = "STATUS_CHANGE";

    /** Cuadrilla asignada al pasar a EN_TERRENO. */
    public record Crew(String name, String members, String vehiclePlate, String vehicleType, String contactPhone) {
    }
}
