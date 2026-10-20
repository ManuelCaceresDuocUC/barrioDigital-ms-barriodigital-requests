package com.barriodigital.ms_requests.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.barriodigital.ms_requests.model.RequestStatus;

class RequestNotificationPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final MessagingProperties messaging = new MessagingProperties("cmd.direct",
            new MessagingProperties.RoutingKeys("email.send", "crew.ticket", "certificate.gen"));
    private final RequestNotificationPublisher publisher =
            new RequestNotificationPublisher(rabbitTemplate, messaging);

    private RequestStatusChangedEvent event(RequestStatus status, String email, String rut) {
        return new RequestStatusChangedEvent(15L, "Certificado", "Calle Larga 123", status, email, "Ana", rut);
    }

    @Test
    void admitidoSoloEnviaCorreo() {
        publisher.onStatusChanged(event(RequestStatus.ADMITIDO, "ana@municipio.cl", "11111111-1"));
        verify(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq("email.send"), any(EmailNotificationEvent.class));
        verifyNoMoreInteractions(rabbitTemplate);
    }

    @Test
    void enTerrenoEnviaCorreoYTicketDeCuadrilla() {
        publisher.onStatusChanged(event(RequestStatus.EN_TERRENO, "ana@municipio.cl", null));
        verify(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq("email.send"), any(EmailNotificationEvent.class));
        verify(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq("crew.ticket"), any(CrewTicketEvent.class));
        verifyNoMoreInteractions(rabbitTemplate);
    }

    @Test
    void resueltoConRutEnviaCorreoYCertificado() {
        publisher.onStatusChanged(event(RequestStatus.RESUELTO, "ana@municipio.cl", "11111111-1"));
        verify(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq("email.send"), any(EmailNotificationEvent.class));
        verify(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq("certificate.gen"), any(CertificateEvent.class));
        verifyNoMoreInteractions(rabbitTemplate);
    }

    @Test
    void resueltoSinRutNoEmiteCertificado() {
        publisher.onStatusChanged(event(RequestStatus.RESUELTO, "ana@municipio.cl", " "));
        verify(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq("email.send"), any(EmailNotificationEvent.class));
        verifyNoMoreInteractions(rabbitTemplate);
    }

    @Test
    void sinCorreoNoEnviaEmailPeroSiElTicket() {
        publisher.onStatusChanged(event(RequestStatus.EN_TERRENO, null, null));
        verify(rabbitTemplate).convertAndSend(eq("cmd.direct"), eq("crew.ticket"), any(CrewTicketEvent.class));
        verifyNoMoreInteractions(rabbitTemplate);
    }

    @Test
    void unaFallaDelBrokerNoPropagaLaExcepcion() {
        doThrow(new AmqpConnectException(new IOException("broker caído")))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> publisher.onStatusChanged(
                event(RequestStatus.ADMITIDO, "ana@municipio.cl", null))).doesNotThrowAnyException();
    }
}