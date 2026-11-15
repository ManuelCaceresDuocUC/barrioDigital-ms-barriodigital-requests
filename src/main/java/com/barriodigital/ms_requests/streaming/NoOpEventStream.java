package com.barriodigital.ms_requests.streaming;

import lombok.extern.slf4j.Slf4j;

/** Se usa con barriodigital.kafka.enabled=false: descarta los eventos. */
@Slf4j
public class NoOpEventStream implements EventStream {

    @Override
    public void publish(String topic, String key, Object payload) {
        log.debug("Kafka deshabilitado: evento descartado (tópico={}, clave={})", topic, key);
    }
}
