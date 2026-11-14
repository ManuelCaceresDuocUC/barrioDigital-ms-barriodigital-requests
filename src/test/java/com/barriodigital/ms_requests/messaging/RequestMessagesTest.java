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

    @Test
    void elCorreoDeRechazoIncluyeElMotivo() {
        RequestStatusChangedEvent rejected = new RequestStatusChangedEvent(15L, "Certificado de residencia",
                "Calle Larga 123", RequestStatus.RECHAZADO, "ana@municipio.cl", "Ana", null, "Falta el comprobante");

        assertThat(RequestMessages.email(rejected).body()).contains("RECHAZADO").contains("Motivo: Falta el comprobante");
    }

    @Test
    void sinMotivoElCorreoNoMencionaUnMotivo() {
        assertThat(RequestMessages.email(event(RequestStatus.ADMITIDO)).body()).doesNotContain("Motivo");
        RequestStatusChangedEvent blank = new RequestStatusChangedEvent(15L, "Certificado", "Calle", RequestStatus.RECHAZADO,
                "ana@municipio.cl", "Ana", null, "   ");
        assertThat(RequestMessages.email(blank).body()).doesNotContain("Motivo");
    }
}
