package com.barriodigital.ms_requests.messaging;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Publica pidiéndoselo a ms-barriodigital-rabbitadmin ({@code POST /internal/messages}): este servicio
 * no se conecta a RabbitMQ, así que no repite su configuración, su formato JSON ni sus nombres.
 *
 * <p>QUÉ ES: la implementación de MessageGateway. Convierte "publica esto" en una llamada HTTP interna.
 */
@Component
public class RabbitAdminMessageGateway implements MessageGateway {

    // Endpoint interno de rabbitadmin: no se expone por el BFF ni por el API Gateway.
    private static final String PUBLISH_PATH = "/internal/messages";

    private final RestClient restClient;

    // @Qualifier elige entre varios RestClient del proyecto (también existe el del catálogo).
    public RabbitAdminMessageGateway(@Qualifier("rabbitAdminRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public void publish(String routingKey, String type, String correlationId, String traceId, Object payload) {
        try {
            // POST con JSON. rabbitadmin completa el sobre (eventId, timestamp) y publica en cmd.direct.
            restClient.post()
                    .uri(PUBLISH_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PublishRequest(routingKey, type, correlationId, traceId, payload))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            // Cualquier fallo HTTP o de red se traduce a una excepción propia.
            throw new MessagePublishException("No se pudo publicar el mensaje (routingKey=" + routingKey + ")", ex);
        }
    }

    /** Cuerpo que espera rabbitadmin; el sobre (eventId, timestamp) lo completa ese servicio. */
    record PublishRequest(String routingKey, String type, String correlationId, String traceId, Object payload) {
    }
}
