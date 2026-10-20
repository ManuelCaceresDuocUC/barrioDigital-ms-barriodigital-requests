package com.barriodigital.ms_requests.messaging;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties messaging;

    // AFTER_COMMIT evita avisar de cambios que luego se revierten; fallbackExecution cubre
    // el alta de trámites, que no corre dentro de una transacción propia.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStatusChanged(RequestStatusChangedEvent event) {
        if (hasText(event.requesterEmail())) {
            send(messaging.routingKeys().email(), RequestMessages.email(event), event);
        } else {
            log.warn("Trámite {} sin correo del solicitante: no se envía el email", event.requestId());
        }

        if (event.status() == RequestStatus.EN_TERRENO) {
            send(messaging.routingKeys().crew(), RequestMessages.crewTicket(event), event);
        }

        if (event.status() == RequestStatus.RESUELTO) {
            if (hasText(event.citizenRut())) {
                send(messaging.routingKeys().certificate(), RequestMessages.certificate(event), event);
            } else {
                log.info("Trámite {} resuelto sin RUT: no se emite certificado", event.requestId());
            }
        }
    }

    private void send(String routingKey, Object payload, RequestStatusChangedEvent event) {
        try {
            rabbitTemplate.convertAndSend(messaging.exchange(), routingKey, payload);
            log.info("Evento publicado: trámite={}, estado={}, routingKey={}",
                    event.requestId(), event.status(), routingKey);
        } catch (AmqpException ex) {
            log.error("No se pudo publicar el evento del trámite {} (routingKey={})",
                    event.requestId(), routingKey, ex);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}