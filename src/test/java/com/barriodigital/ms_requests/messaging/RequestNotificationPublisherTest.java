package com.barriodigital.ms_requests.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.barriodigital.ms_requests.model.RequestStatus;

class RequestNotificationPublisherTest {

    private final MessageGateway gateway = mock(MessageGateway.class);
    private final MessagingProperties messaging = new MessagingProperties(
            new MessagingProperties.RoutingKeys("email.send", "crew.ticket", "certificate.gen"));
    private final RequestNotificationPublisher publisher = new RequestNotificationPublisher(gateway, messaging);

    private RequestStatusChangedEvent event(RequestStatus status, String email, String rut) {
        return new RequestStatusChangedEvent(15L, "Certificado", "Calle Larga 123", status, email, "Ana", rut);
    }

    /** Verifica un envío y devuelve el traceId usado. */
    private String verifySent(String routingKey, String type) {
        ArgumentCaptor<String> traceId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(gateway).publish(eq(routingKey), eq(type), eq("15"), traceId.capture(), payload.capture());
        assertThat(traceId.getValue()).isNotBlank();
        assertThat(payload.getValue()).isNotNull();
        return traceId.getValue();
    }

    @Test
    void admitidoSoloEnviaCorreo() {
        publisher.onStatusChanged(event(RequestStatus.ADMITIDO, "ana@municipio.cl", "11111111-1"));
        verifySent("email.send", EventTypes.EMAIL_NOTIFICATION);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void enTerrenoEnviaCorreoYTicketConElMismoTraceId() {
        publisher.onStatusChanged(event(RequestStatus.EN_TERRENO, "ana@municipio.cl", null));
        String emailTrace = verifySent("email.send", EventTypes.EMAIL_NOTIFICATION);
        String ticketTrace = verifySent("crew.ticket", EventTypes.CREW_TICKET);
        assertThat(ticketTrace).isEqualTo(emailTrace);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void resueltoConRutEnviaCorreoYCertificado() {
        publisher.onStatusChanged(event(RequestStatus.RESUELTO, "ana@municipio.cl", "11111111-1"));
        verifySent("email.send", EventTypes.EMAIL_NOTIFICATION);
        verifySent("certificate.gen", EventTypes.CERTIFICATE_REQUEST);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void resueltoSinRutNoEmiteCertificado() {
        publisher.onStatusChanged(event(RequestStatus.RESUELTO, "ana@municipio.cl", " "));
        verifySent("email.send", EventTypes.EMAIL_NOTIFICATION);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void sinCorreoNoEnviaEmailPeroSiElTicket() {
        publisher.onStatusChanged(event(RequestStatus.EN_TERRENO, null, null));
        verifySent("crew.ticket", EventTypes.CREW_TICKET);
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void elContenidoDelCorreoLlevaElEstadoDelTramite() {
        publisher.onStatusChanged(event(RequestStatus.EN_GESTION, "ana@municipio.cl", null));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(gateway).publish(eq("email.send"), eq(EventTypes.EMAIL_NOTIFICATION), eq("15"), anyString(),
                payload.capture());
        EmailNotificationEvent email = (EmailNotificationEvent) payload.getValue();
        assertThat(email.to()).isEqualTo("ana@municipio.cl");
        assertThat(email.body()).contains("EN GESTION");
    }

    @Test
    void unaFallaDelServicioDeMensajeriaNoPropagaLaExcepcion() {
        doThrow(new MessagePublishException("rabbitadmin no responde", new RuntimeException()))
                .when(gateway).publish(anyString(), anyString(), anyString(), anyString(), any());

        assertThatCode(() -> publisher.onStatusChanged(
                event(RequestStatus.ADMITIDO, "ana@municipio.cl", null))).doesNotThrowAnyException();
    }
}
