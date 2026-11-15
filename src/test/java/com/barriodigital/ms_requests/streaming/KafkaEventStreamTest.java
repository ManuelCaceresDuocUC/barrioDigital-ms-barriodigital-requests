package com.barriodigital.ms_requests.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class KafkaEventStreamTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void publicaElEventoComoJsonConLaClaveDelTramite() {
        MockProducer<String, String> producer = new MockProducer<>(true, new StringSerializer(), new StringSerializer());
        KafkaEventStream stream = new KafkaEventStream(producer, json);

        stream.publish("requests.events", "7", new RequestEventMessage("e1", RequestEventMessage.CREATED,
                "2026-11-15T00:00:00Z", 7L, "Poda", null, "INGRESADO"));

        assertThat(producer.history()).hasSize(1);
        assertThat(producer.history().get(0).topic()).isEqualTo("requests.events");
        assertThat(producer.history().get(0).key()).isEqualTo("7");
        assertThat(producer.history().get(0).value())
                .contains("\"requestId\":7").contains("\"status\":\"INGRESADO\"").contains("\"eventType\":\"REQUEST_CREATED\"");
    }

    @Test
    void siKafkaRechazaElEnvioNoLanzaExcepcion() {
        MockProducer<String, String> producer = new MockProducer<>(false, new StringSerializer(), new StringSerializer());
        KafkaEventStream stream = new KafkaEventStream(producer, json);

        assertThatCode(() -> {
            stream.publish("requests.events", "7", new RequestEventMessage("e1", "X", "t", 7L, "Poda", null, "INGRESADO"));
            producer.errorNext(new RuntimeException("broker caído"));
        }).doesNotThrowAnyException();
    }

    @Test
    void siSendFallaDeFormaSincronaNoLanzaExcepcion() {
        MockProducer<String, String> producer = new MockProducer<>(true, new StringSerializer(), new StringSerializer());
        producer.close();
        KafkaEventStream stream = new KafkaEventStream(producer, json);

        assertThatCode(() -> stream.publish("requests.events", "7", "evento")).doesNotThrowAnyException();
    }

    @Test
    void conKafkaDeshabilitadoElEventoSeDescarta() {
        assertThatCode(() -> new NoOpEventStream().publish("t", "k", "x")).doesNotThrowAnyException();
    }
}
