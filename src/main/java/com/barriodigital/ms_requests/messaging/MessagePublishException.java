package com.barriodigital.ms_requests.messaging;

/** El mensaje no pudo entregarse (el servicio de mensajería no respondió o lo rechazó). */
public class MessagePublishException extends RuntimeException {

    public MessagePublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
