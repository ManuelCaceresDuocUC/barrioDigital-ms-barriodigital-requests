package com.barriodigital.ms_requests.streaming;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/** Publica en Kafka como JSON. El envío es asíncrono: el resultado solo se registra en el log. */
@Slf4j
public class KafkaEventStream implements EventStream {

    private final Producer<String, String> producer;
    private final JsonMapper json;

    public KafkaEventStream(Producer<String, String> producer, JsonMapper json) {
        this.producer = producer;
        this.json = json;
    }

    @Override
    public void publish(String topic, String key, Object payload) {
        try {
            String body = json.writeValueAsString(payload);
            producer.send(new ProducerRecord<>(topic, key, body), (metadata, error) -> {
                if (error != null) {
                    log.error("Kafka no confirmó el evento (tópico={}, clave={})", topic, key, error);
                } else {
                    log.info("Evento en Kafka: tópico={}, clave={}, partición={}, offset={}",
                            topic, key, metadata.partition(), metadata.offset());
                }
            });
        } catch (RuntimeException ex) {
            // send() también puede fallar de forma síncrona (por ejemplo, sin metadatos del clúster a tiempo).
            log.error("No se pudo publicar el evento en Kafka (tópico={}, clave={})", topic, key, ex);
        }
    }
}
