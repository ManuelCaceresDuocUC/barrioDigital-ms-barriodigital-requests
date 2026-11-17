package com.barriodigital.ms_requests.streaming;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.barriodigital.ms_requests.dto.CrewAssignmentDto;
import com.barriodigital.ms_requests.messaging.RequestStatusChangedEvent;
import com.barriodigital.ms_requests.messaging.RequestStatusChangedEvent.StaffAction;
import lombok.RequiredArgsConstructor;

/**
 * Lleva cada cambio del trámite a Kafka. Va después del commit (no se publican cambios que se revierten)
 * y es independiente del aviso por RabbitMQ: si Kafka falla, el trámite y los correos siguen igual.
 *
 * <p>QUÉ ES: el PRODUCTOR de hechos. Recibe el evento interno y publica hasta dos mensajes:
 * uno en requests.events (siempre) y otro en audit.timeline (solo si hubo una actuación del personal).
 */
@Component
@RequiredArgsConstructor
public class RequestStreamPublisher {

    private final EventStream stream;
    private final KafkaStreamProperties properties;

    // fallbackExecution cubre el alta de trámites, que no corre dentro de una transacción propia.
    // AFTER_COMMIT: solo se ejecuta si la transacción de Oracle se confirmó.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStatusChanged(RequestStatusChangedEvent event) {
        // Clave = id de la solicitud: todos sus eventos van a la misma partición y se leen en orden.
        String key = String.valueOf(event.requestId());
        String occurredAt = Instant.now().toString();
        // Sin estado anterior = la solicitud acaba de ingresar.
        boolean created = event.previousStatus() == null;

        // Mensaje 1 (requests.events): hecho para métricas. Sin datos personales. Cada mensaje lleva un
        // eventId nuevo (UUID) que los consumidores usan para no contarlo dos veces.
        stream.publish(properties.topics().requestEvents(), key, new RequestEventMessage(
                UUID.randomUUID().toString(),
                created ? RequestEventMessage.CREATED : RequestEventMessage.STATUS_CHANGED,
                occurredAt,
                event.requestId(),
                event.type(),
                created ? null : event.previousStatus().name(),
                event.status().name()));

        // Mensaje 2 (audit.timeline): quién hizo el cambio. Solo existe cuando actuó el personal
        // (el ingreso de una solicitud no trae StaffAction).
        StaffAction staff = event.staffAction();
        if (staff != null) {
            stream.publish(properties.topics().auditTimeline(), key, new AuditTimelineMessage(
                    UUID.randomUUID().toString(),
                    occurredAt,
                    event.requestId(),
                    AuditTimelineMessage.STATUS_CHANGE,
                    created ? null : event.previousStatus().name(),
                    event.status().name(),
                    event.reason(),
                    staff.actorId(),
                    staff.actorName(),
                    staff.actorRole(),
                    crewOf(staff.crew())));
        }
    }

    // Convierte la cuadrilla del DTO al formato del mensaje (null si no hay).
    private static AuditTimelineMessage.Crew crewOf(CrewAssignmentDto crew) {
        return crew == null ? null : new AuditTimelineMessage.Crew(crew.name(), crew.members(),
                crew.vehiclePlate(), crew.vehicleType(), crew.contactPhone());
    }
}
