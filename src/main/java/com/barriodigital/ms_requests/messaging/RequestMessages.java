package com.barriodigital.ms_requests.messaging;

import com.barriodigital.ms_requests.model.RequestStatus;

/** Construye el contenido de los mensajes a partir de un cambio de estado. */
final class RequestMessages {

    private RequestMessages() {
    }

    static EmailNotificationEvent email(RequestStatusChangedEvent event) {
        String greeting = (event.requesterName() == null || event.requesterName().isBlank())
                ? "Hola" : "Hola " + event.requesterName();
        String body = "%s, tu trámite N° %d (%s) cambió al estado %s."
                .formatted(greeting, event.requestId(), event.type(), event.status().name().replace('_', ' '));
        return new EmailNotificationEvent(
                event.requesterEmail(), subject(event.status()), body, String.valueOf(event.requestId()));
    }

    static CrewTicketEvent crewTicket(RequestStatusChangedEvent event) {
        // requests no guarda ubicación: se usa la descripción del trámite.
        return new CrewTicketEvent(String.valueOf(event.requestId()), event.type(), event.description(), "NORMAL");
    }

    static CertificateEvent certificate(RequestStatusChangedEvent event) {
        return new CertificateEvent(event.citizenRut(), event.type(), null);
    }

    private static String subject(RequestStatus status) {
        return switch (status) {
            case INGRESADO -> "Tu trámite fue ingresado";
            case ADMITIDO -> "Tu trámite fue admitido";
            case EN_GESTION -> "Tu trámite está en gestión";
            case EN_TERRENO -> "Tu trámite está en terreno";
            case RESUELTO -> "Tu trámite fue resuelto";
            case RECHAZADO -> "Tu trámite fue rechazado";
        };
    }
}