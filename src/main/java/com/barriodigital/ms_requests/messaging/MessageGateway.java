package com.barriodigital.ms_requests.messaging;

/**
 * Puerta de salida de los mensajes de este servicio. La lógica de negocio solo conoce esta interfaz;
 * cómo llega el mensaje a RabbitMQ queda en la implementación.
 */
public interface MessageGateway {

    /**
     * Pide que se publique un mensaje.
     *
     * @throws MessagePublishException si el mensaje no pudo entregarse
     */
    void publish(String routingKey, String type, String correlationId, String traceId, Object payload);
}
