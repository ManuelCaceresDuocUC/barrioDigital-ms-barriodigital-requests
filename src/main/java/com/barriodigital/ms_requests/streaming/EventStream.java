package com.barriodigital.ms_requests.streaming;

/**
 * Salida de eventos hacia el streaming. Publicar nunca lanza excepciones: un fallo se registra y se sigue.
 * PATRÓN: interfaz (puerto). Tiene dos implementaciones: KafkaEventStream (real) y NoOpEventStream
 * (no hace nada, para desarrollo sin Kafka).
 */
public interface EventStream {

    /** @param key clave de partición: los eventos de un mismo trámite quedan ordenados entre sí */
    void publish(String topic, String key, Object payload);
}
