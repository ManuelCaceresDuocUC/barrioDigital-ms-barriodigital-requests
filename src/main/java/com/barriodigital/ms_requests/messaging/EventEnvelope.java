package com.barriodigital.ms_requests.messaging;

import java.time.Instant;
import java.util.UUID;

/** Envoltorio común de todos los eventos que requests publica en RabbitMQ. */
public record EventEnvelope<T>(
        String type,
        String eventId,
        String timestamp,
        String traceId,
        String correlationId,
        T payload) {

    public static <T> EventEnvelope<T> of(String type, String correlationId, String traceId, T payload) {
        return new EventEnvelope<>(type, UUID.randomUUID().toString(), Instant.now().toString(),
                traceId, correlationId, payload);
    }
}