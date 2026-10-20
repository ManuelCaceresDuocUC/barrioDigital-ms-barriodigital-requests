package com.barriodigital.ms_requests.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

class EventEnvelopeTest {

    @Test
    void cadaEnvelopeTieneIdUnicoYFechaIso() {
        var first = EventEnvelope.of(EventTypes.CREW_TICKET, "15", "trace-1", "a");
        var second = EventEnvelope.of(EventTypes.CREW_TICKET, "15", "trace-1", "a");

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
        assertThat(Instant.parse(first.timestamp())).isNotNull();
        assertThat(first.type()).isEqualTo("CREW_TICKET");
        assertThat(first.correlationId()).isEqualTo("15");
    }

    @Test
    void elJsonPublicadoTieneLaEstructuraAcordada() {
        var envelope = EventEnvelope.of(EventTypes.EMAIL_NOTIFICATION, "15", "trace-1",
                new EmailNotificationEvent("ana@municipio.cl", "Asunto", "Cuerpo", "15"));

        Message message = new JacksonJsonMessageConverter().toMessage(envelope, new MessageProperties());
        String json = new String(message.getBody(), StandardCharsets.UTF_8);

        assertThat(json)
                .contains("\"type\":\"EMAIL_NOTIFICATION\"")
                .contains("\"correlationId\":\"15\"")
                .contains("\"traceId\":\"trace-1\"")
                .contains("\"eventId\":")
                .contains("\"timestamp\":")
                .contains("\"payload\":{")
                .contains("\"to\":\"ana@municipio.cl\"");
    }
}