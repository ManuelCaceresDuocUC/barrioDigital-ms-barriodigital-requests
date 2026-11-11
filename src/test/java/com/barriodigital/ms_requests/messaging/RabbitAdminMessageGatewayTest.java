package com.barriodigital.ms_requests.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RabbitAdminMessageGatewayTest {

    private static final String BASE = "http://rabbitadmin:8086";

    private MockRestServiceServer server;
    private RabbitAdminMessageGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new RabbitAdminMessageGateway(builder.build());
    }

    @Test
    void enviaElMensajeComoJsonARabbitadmin() {
        server.expect(requestTo(BASE + "/internal/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.routingKey").value("email.send"))
                .andExpect(jsonPath("$.type").value("EMAIL_NOTIFICATION"))
                .andExpect(jsonPath("$.correlationId").value("15"))
                .andExpect(jsonPath("$.traceId").value("trace-1"))
                .andExpect(jsonPath("$.payload.to").value("ana@municipio.cl"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        gateway.publish("email.send", "EMAIL_NOTIFICATION", "15", "trace-1",
                new EmailNotificationEvent("ana@municipio.cl", "Asunto", "Cuerpo", "15"));

        server.verify();
    }

    @Test
    void unaRespuesta400SeTraduceAUnaFallaDePublicacion() {
        server.expect(requestTo(BASE + "/internal/messages")).andRespond(withBadRequest());

        assertThatThrownBy(() -> gateway.publish("otra.clave", "X", "1", "t", java.util.Map.of()))
                .isInstanceOf(MessagePublishException.class)
                .hasMessageContaining("otra.clave");
    }

    @Test
    void unaRespuesta502SeTraduceAUnaFallaDePublicacion() {
        server.expect(requestTo(BASE + "/internal/messages")).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.publish("email.send", "X", "1", "t", java.util.Map.of()))
                .isInstanceOf(MessagePublishException.class);
    }
}
