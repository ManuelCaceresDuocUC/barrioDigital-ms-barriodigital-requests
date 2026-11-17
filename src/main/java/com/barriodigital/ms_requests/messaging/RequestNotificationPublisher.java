package com.barriodigital.ms_requests.messaging;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.barriodigital.ms_requests.model.RequestStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * QUÉ ES: decide qué COMANDOS de RabbitMQ se piden tras un cambio de estado (correo, ticket, certificado).
 * Escucha el evento interno RequestStatusChangedEvent que publica RequestService.
 *
 * REGLAS: correo al vecino si hay correo; ticket de cuadrilla al pasar a EN_TERRENO; certificado al
 * pasar a RESUELTO si hay RUT; nada si es una cancelación interna (EN_TERRENO -> EN_GESTION).
 */
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
        // Cancelación en terreno: asunto interno, no se avisa al vecino ni a la cuadrilla.
        if (event.isInternalOnly()) {
            log.info("Trámite {} vuelve a gestión desde terreno: sin correo al solicitante ni ticket",
                    event.requestId());
            return;
        }

        // Un mismo traceId une todos los mensajes originados por este cambio de estado.
        String traceId = UUID.randomUUID().toString();

        // 1) Correo al solicitante (solo si se conoce su correo).
        if (hasText(event.requesterEmail())) {
            send(messaging.routingKeys().email(), EventTypes.EMAIL_NOTIFICATION, event, traceId,
                    RequestMessages.email(event));
        } else {
            log.warn("Trámite {} sin correo del solicitante: no se envía el email", event.requestId());
        }

        // 2) Ticket de visita para la cuadrilla.
        if (event.status() == RequestStatus.EN_TERRENO) {
            send(messaging.routingKeys().crew(), EventTypes.CREW_TICKET, event, traceId,
                    RequestMessages.crewTicket(event));
        }

        // 3) Certificado al resolver (solo si hay RUT).
        if (event.status() == RequestStatus.RESUELTO) {
            if (hasText(event.citizenRut())) {
                send(messaging.routingKeys().certificate(), EventTypes.CERTIFICATE_REQUEST, event, traceId,
                        RequestMessages.certificate(event));
            } else {
                log.info("Trámite {} resuelto sin RUT: no se emite certificado", event.requestId());
            }
        }
    }

    // Publica un comando; si falla, solo se registra el error (la solicitud ya quedó guardada).
    // Limitación conocida: ese comando se pierde; la mejora sería el patrón outbox.
    private void send(String routingKey, String type, RequestStatusChangedEvent event, String traceId,
                      Object payload) {
        try {
            // correlationId = id de la solicitud: sirve para buscar todos sus mensajes en los logs.
            gateway.publish(routingKey, type, String.valueOf(event.requestId()), traceId, payload);
            log.info("Evento publicado: trámite={}, estado={}, tipo={}, routingKey={}",
                    event.requestId(), event.status(), type, routingKey);
        } catch (MessagePublishException ex) {
            log.error("No se pudo publicar el evento del trámite {} (tipo={}, routingKey={})",
                    event.requestId(), type, routingKey, ex);
        }
    }

    // true si el texto no es null, ni vacío, ni solo espacios.
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
