package com.barriodigital.ms_requests.streaming;

/**
 * Evento de análisis de un trámite (tópico requests.events). No lleva datos personales del vecino
 * (correo, nombre ni RUT): quien lo consume solo necesita saber qué pasó, no a quién.
 *
 * @param eventType      REQUEST_CREATED o REQUEST_STATUS_CHANGED
 * @param previousStatus estado anterior; nulo al ingresar
 *
 * <p>QUÉ ES: el JSON que consumen report (KPIs) y audit (registro). Su contrato debe mantenerse
 * compatible con report/dto/RequestEvent.
 */
public record RequestEventMessage(
        String eventId,
        String eventType,
        String occurredAt,
        Long requestId,
        String procedureType,
        String previousStatus,
        String status) {

    public static final String CREATED = "REQUEST_CREATED";
    public static final String STATUS_CHANGED = "REQUEST_STATUS_CHANGED";
}
