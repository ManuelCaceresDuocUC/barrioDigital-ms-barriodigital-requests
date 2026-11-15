package com.barriodigital.ms_requests.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.json.JsonMapper;

class KafkaEventStreamTest {

    @SuppressWarnings("unchecked")
    private final Producer<String, String> producer = mock(Producer.class);
    private final KafkaEventStream stream = new KafkaEventStream(producer, JsonMapper.builder().build());

    @Test
    @SuppressWarnings("unchecked")
    void publicaElEventoComoJsonConLaClaveDelTramite() {
        stream.publish("requests.events", "7", new RequestEventMessage("e1", RequestEventMessage.CREATED,
                "2026-11-15T00:00:00Z", 7L, "Poda", null, "INGRESADO"));

        ArgumentCaptor<ProducerRecord<String, String>> record = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(producer).send(record.capture(), any(Callback.class));
        assertThat(record.getValue().topic()).isEqualTo("requests.events");
        assertThat(record.getValue().key()).isEqualTo("7");
        assertThat(record.getValue().value())
                .contains("\"requestId\":7").contains("\"status\":\"INGRESADO\"")
                .contains("\"eventType\":\"REQUEST_CREATED\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void elResultadoAsincronoSoloSeRegistra() {
        stream.publish("requests.events", "7", "evento");

        ArgumentCaptor<Callback> callback = ArgumentCaptor.forClass(Callback.class);
        verify(producer).send(any(ProducerRecord.class), callback.capture());
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(metadata.partition()).thenReturn(1);
        when(metadata.offset()).thenReturn(42L);

        assertThatCode(() -> {
            callback.getValue().onCompletion(metadata, null);
            callback.getValue().onCompletion(null, new RuntimeException("broker caído"));
        }).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void siSendFallaDeFormaSincronaNoLanzaExcepcion() {
        when(producer.send(any(ProducerRecord.class), any(Callback.class)))
                .thenThrow(new IllegalStateException("sin metadatos del clúster"));

        assertThatCode(() -> stream.publish("requests.events", "7", "evento")).doesNotThrowAnyException();
    }

    @Test
    void conKafkaDeshabilitadoElEventoSeDescarta() {
        assertThatCode(() -> new NoOpEventStream().publish("t", "k", "x")).doesNotThrowAnyException();
    }
}
