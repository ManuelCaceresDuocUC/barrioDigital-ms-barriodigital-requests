package com.barriodigital.ms_requests.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.barriodigital.ms_requests.dto.CrewAssignmentDto;
import com.barriodigital.ms_requests.messaging.RequestStatusChangedEvent;
import com.barriodigital.ms_requests.messaging.RequestStatusChangedEvent.StaffAction;
import com.barriodigital.ms_requests.model.RequestStatus;

class RequestStreamPublisherTest {

    private record Sent(String topic, String key, Object payload) {
    }

    private final List<Sent> sent = new ArrayList<>();
    private final EventStream stream = (topic, key, payload) -> sent.add(new Sent(topic, key, payload));
    private final KafkaStreamProperties properties = new KafkaStreamProperties(true, "localhost:9092",
            Duration.ofSeconds(5), new KafkaStreamProperties.Topics("requests.events", "audit.timeline"));
    private final RequestStreamPublisher publisher = new RequestStreamPublisher(stream, properties);

    @Test
    void unTramiteNuevoSoloVaARequestsEventsYSinDatosPersonales() {
        publisher.onStatusChanged(new RequestStatusChangedEvent(7L, "Poda de árbol", "Calle 1",
                RequestStatus.INGRESADO, "ana@correo.cl", "Ana", "11111111-1"));

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).topic()).isEqualTo("requests.events");
        assertThat(sent.get(0).key()).isEqualTo("7");
        RequestEventMessage message = (RequestEventMessage) sent.get(0).payload();
        assertThat(message.eventType()).isEqualTo(RequestEventMessage.CREATED);
        assertThat(message.procedureType()).isEqualTo("Poda de árbol");
        assertThat(message.previousStatus()).isNull();
        assertThat(message.status()).isEqualTo("INGRESADO");
        assertThat(message.toString()).doesNotContain("ana@correo.cl").doesNotContain("11111111-1");
    }

    @Test
    void unCambioDelPersonalVaALosDosTopicosConLaMismaClave() {
        StaffAction staff = new StaffAction("oid-1", "Pedro Operador", "Operador",
                new CrewAssignmentDto("Cuadrilla Norte", "Juan, Luis", "ABCD12", "Camión", "999"));

        publisher.onStatusChanged(new RequestStatusChangedEvent(7L, "Poda de árbol", "Calle 1",
                RequestStatus.EN_TERRENO, "ana@correo.cl", "Ana", null, null, RequestStatus.EN_GESTION, staff));

        assertThat(sent).extracting(Sent::topic).containsExactly("requests.events", "audit.timeline");
        assertThat(sent).extracting(Sent::key).containsOnly("7");
        RequestEventMessage event = (RequestEventMessage) sent.get(0).payload();
        assertThat(event.eventType()).isEqualTo(RequestEventMessage.STATUS_CHANGED);
        assertThat(event.previousStatus()).isEqualTo("EN_GESTION");
        AuditTimelineMessage audit = (AuditTimelineMessage) sent.get(1).payload();
        assertThat(audit.action()).isEqualTo(AuditTimelineMessage.STATUS_CHANGE);
        assertThat(audit.fromStatus()).isEqualTo("EN_GESTION");
        assertThat(audit.toStatus()).isEqualTo("EN_TERRENO");
        assertThat(audit.actorRole()).isEqualTo("Operador");
        assertThat(audit.crew().vehiclePlate()).isEqualTo("ABCD12");
        assertThat(audit.toString()).doesNotContain("ana@correo.cl");
    }

    @Test
    void laCancelacionEnTerrenoTambienQuedaAuditadaConSuMotivo() {
        StaffAction staff = new StaffAction("oid-1", "Pedro", "Operador", null);

        publisher.onStatusChanged(new RequestStatusChangedEvent(7L, "Poda", "Calle 1", RequestStatus.EN_GESTION,
                "ana@correo.cl", "Ana", null, "Camión averiado", RequestStatus.EN_TERRENO, staff));

        AuditTimelineMessage audit = (AuditTimelineMessage) sent.get(1).payload();
        assertThat(audit.reason()).isEqualTo("Camión averiado");
        assertThat(audit.crew()).isNull();
    }

    @Test
    void siFallaElEnvioNoSePropagaNadaAlTramite() {
        EventStream failing = (topic, key, payload) -> { };
        new RequestStreamPublisher(failing, properties).onStatusChanged(new RequestStatusChangedEvent(7L, "Poda",
                "Calle 1", RequestStatus.INGRESADO, null, null, null));
    }
}
