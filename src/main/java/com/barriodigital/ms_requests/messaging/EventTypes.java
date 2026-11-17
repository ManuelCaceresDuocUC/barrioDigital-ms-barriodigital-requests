package com.barriodigital.ms_requests.messaging;

/**
 * QUÉ ES: constantes con los tipos de mensaje que se publican en RabbitMQ (campo "type" del sobre).
 * Clase final con constructor privado: solo agrupa constantes, no se instancia.
 */
public final class EventTypes {

    public static final String EMAIL_NOTIFICATION = "EMAIL_NOTIFICATION";
    public static final String CREW_TICKET = "CREW_TICKET";
    public static final String CERTIFICATE_REQUEST = "CERTIFICATE_REQUEST";

    private EventTypes() {
    }
}
