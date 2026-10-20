package com.barriodigital.ms_requests.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.barriodigital.ms_requests.model.RequestStatus;

class RequestMessagesTest {

    private RequestStatusChangedEvent event(RequestStatus status) {
        return new RequestStatusChangedEvent(15L, "Certificado de residencia", "Calle Larga 123",
                status, "ana@municipio.cl", "Ana", "11111111-1");
    }

    @Test
    void elCorreoIncluyeDestinatarioNumeroDeTramiteYEstado() {
        EmailNotificationEvent email = RequestMessages.email(event(RequestStatus.ADMITIDO));
        assertThat(email.to()).isEqualTo("ana@municipio.cl");
        assertThat(email.procedureId()).isEqualTo("15");
        assertThat(email.subject()).isEqualTo("Tu trámite fue admitido");
        assertThat(email.body()).contains("Ana").contains("15").contains("ADMITIDO");
    }

    @Test
    void todosLosEstadosTienenAsunto() {
        for (RequestStatus status : RequestStatus.values()) {
            assertThat(RequestMessages.email(event(status)).subject()).isNotBlank();
        }
    }

    @Test
    void elTicketUsaIdTipoYDescripcionDelTramite() {
        CrewTicketEvent ticket = RequestMessages.crewTicket(event(RequestStatus.EN_TERRENO));
        assertThat(ticket.ticketId()).isEqualTo("15");
        assertThat(ticket.category()).isEqualTo("Certificado de residencia");
        assertThat(ticket.location()).isEqualTo("Calle Larga 123");
        assertThat(ticket.priority()).isEqualTo("NORMAL");
    }

    @Test
    void elCertificadoLlevaRutYTipo() {
        CertificateEvent certificate = RequestMessages.certificate(event(RequestStatus.RESUELTO));
        assertThat(certificate.citizenRut()).isEqualTo("11111111-1");
        assertThat(certificate.certificateType()).isEqualTo("Certificado de residencia");
    }
}