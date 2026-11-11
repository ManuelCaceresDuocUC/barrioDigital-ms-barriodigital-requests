package com.barriodigital.ms_requests.messaging;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.barriodigital.ms_requests.model.RequestStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestNotificationPublisher {

    private final MessageGateway gateway;
    private final MessagingProperties messaging;

    // AFTER_COMMIT evita avisar de cambios que luego se revierten; fallbackExecution cubre
    // el alta de trámites, que no corre dentro de una transacción propia.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStatusChanged(RequestStatusChangedEvent event) {
        String traceId = UUID.randomUUID().toString();

        if (hasText(event.requesterEmail())) {
            send(messaging.routingKeys().email(), EventTypes.EMAIL_NOTIFICATION, event, traceId,
                    RequestMessages.email(event));
        } else {
            log.warn("Trámite {} sin correo del solicitante: no se envía el email", event.requestId());
        }

        if (event.status() == RequestStatus.EN_TERRENO) {
            send(messaging.routingKeys().crew(), EventTypes.CREW_TICKET, event, traceId,
                    RequestMessages.crewTicket(event));
        }

        if (event.status() == RequestStatus.RESUELTO) {
            if (hasText(event.citizenRut())) {
                send(messaging.routingKeys().certificate(), EventTypes.CERTIFICATE_REQUEST, event, traceId,
                        RequestMessages.certificate(event));
            } else {
                log.info("Trámite {} resuelto sin RUT: no se emite certificado", event.requestId());
            }
        }
    }

    private void send(String routingKey, String type, RequestStatusChangedEvent event, String traceId,
                      Object payload) {
        try {
            gateway.publish(routingKey, type, String.valueOf(event.requestId()), traceId, payload);
            log.info("Evento publicado: trámite={}, estado={}, tipo={}, routingKey={}",
                    event.requestId(), event.status(), type, routingKey);
        } catch (MessagePublishException ex) {
            log.error("No se pudo publicar el evento del trámite {} (tipo={}, routingKey={})",
                    event.requestId(), type, routingKey, ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
