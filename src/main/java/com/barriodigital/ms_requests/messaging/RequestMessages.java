package com.barriodigital.ms_requests.messaging;

import com.barriodigital.ms_requests.model.RequestStatus;

/**
 * Construye el contenido de los mensajes a partir de un cambio de estado.
 * QUÉ ES: fábrica de mensajes (métodos estáticos). Concentra los textos del correo en un solo lugar.
 */
final class RequestMessages {

    private RequestMessages() {
    }

    // Arma el correo para el vecino: "Hola Ana, tu trámite N° 22 (Poda) cambió al estado RESUELTO. Motivo: ..."
    static EmailNotificationEvent email(RequestStatusChangedEvent event) {
        String greeting = (event.requesterName() == null || event.requesterName().isBlank())
                ? "Hola" : "Hola " + event.requesterName();
        // Se muestran los estados sin guion bajo (EN_TERRENO -> "EN TERRENO").
        String body = "%s, tu trámite N° %d (%s) cambió al estado %s."
                .formatted(greeting, event.requestId(), event.type(), event.status().name().replace('_', ' '));
        // Si hay motivo (rechazo), se agrega al final.
        if (event.reason() != null && !event.reason().isBlank()) {
            body += " Motivo: " + event.reason().trim();
        }
        return new EmailNotificationEvent(
                event.requesterEmail(), subject(event.status()), body, String.valueOf(event.requestId()));
    }

    // Ticket de visita: el id del ticket es el id de la solicitud.
    static CrewTicketEvent crewTicket(RequestStatusChangedEvent event) {
        // requests no guarda ubicación: se usa la descripción del trámite.
        return new CrewTicketEvent(String.valueOf(event.requestId()), event.type(), event.description(), "NORMAL");
    }

    // Pedido de certificado (la URL del documento se completa después, por eso va null).
    static CertificateEvent certificate(RequestStatusChangedEvent event) {
        return new CertificateEvent(event.citizenRut(), event.type(), null);
    }

    // Asunto del correo según el estado. Un switch sobre enum sin "default" obliga a cubrir todos los
    // estados: si se agrega uno nuevo, el compilador marca el error aquí.
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
