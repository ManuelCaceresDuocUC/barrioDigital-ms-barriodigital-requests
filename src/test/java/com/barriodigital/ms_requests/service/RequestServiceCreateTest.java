package com.barriodigital.ms_requests.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.barriodigital.ms_requests.client.CatalogClient;
import com.barriodigital.ms_requests.client.CatalogProcedure;
import com.barriodigital.ms_requests.dto.CreateRequestDto;
import com.barriodigital.ms_requests.messaging.RequestStatusChangedEvent;
import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;
import com.barriodigital.ms_requests.repository.RequestRepository;
import com.barriodigital.ms_requests.security.Requester;

class RequestServiceCreateTest {

    private final RequestRepository repository = mock(RequestRepository.class);
    private final CatalogClient catalog = mock(CatalogClient.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RequestService service = new RequestService(repository, catalog, events);

    private static Requester cliente() {
        return new Requester("oid-ana", "ana@municipio.cl", "Ana", Set.of("Cliente"));
    }

    private static Requester operador() {
        return new Requester("oid-oscar", "oscar@municipio.cl", "Óscar", Set.of("Operador"));
    }

    @BeforeEach
    void catalogoConUnTramiteActivo() {
        CatalogProcedure procedure = mock(CatalogProcedure.class);
        when(procedure.id()).thenReturn(7L);
        when(procedure.name()).thenReturn("Certificado de residencia");
        when(procedure.active()).thenReturn(true);
        when(catalog.getProcedure(7L, "token")).thenReturn(procedure);
        when(repository.save(any(RequestEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static CreateRequestDto dto(String userId) {
        return new CreateRequestDto(7L, "Necesito mi certificado", userId, "11111111-1");
    }

    @Test
    void guardaSolicitanteYRutYPublicaElEventoDeIngreso() {
        RequestEntity saved = service.createRequest(dto(null), cliente(), "token");

        assertThat(saved.getRequesterEmail()).isEqualTo("ana@municipio.cl");
        assertThat(saved.getRequesterName()).isEqualTo("Ana");
        assertThat(saved.getCitizenRut()).isEqualTo("11111111-1");
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.INGRESADO);

        ArgumentCaptor<RequestStatusChangedEvent> captor = ArgumentCaptor.forClass(RequestStatusChangedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(RequestStatus.INGRESADO);
        assertThat(captor.getValue().requesterEmail()).isEqualTo("ana@municipio.cl");
    }

    @Test
    void elClienteSiempreEsDuenoDeSuSolicitudYSeIgnoraElUserIdDelCuerpo() {
        RequestEntity saved = service.createRequest(dto("oid-de-otro-vecino"), cliente(), "token");

        assertThat(saved.getUserId()).isEqualTo("oid-ana");
    }

    @Test
    void elOperadorPuedeIngresarLaSolicitudANombreDeUnVecino() {
        RequestEntity saved = service.createRequest(dto("oid-vecino"), operador(), "token");

        assertThat(saved.getUserId()).isEqualTo("oid-vecino");
    }

    @Test
    void elOperadorSinUserIdEnElCuerpoQuedaComoDueno() {
        assertThat(service.createRequest(dto(null), operador(), "token").getUserId()).isEqualTo("oid-oscar");
        assertThat(service.createRequest(dto("  "), operador(), "token").getUserId()).isEqualTo("oid-oscar");
    }

    @Test
    void elAdminTambienPuedeIngresarANombreDeUnVecino() {
        Requester admin = new Requester("oid-admin", "admin@municipio.cl", "Admin", Set.of("Admin"));

        assertThat(service.createRequest(dto("oid-vecino"), admin, "token").getUserId()).isEqualTo("oid-vecino");
    }

    @Test
    void sinOidEnElTokenNoSePuedeCrear() {
        Requester sinOid = new Requester(null, "ana@municipio.cl", "Ana", Set.of("Cliente"));

        assertThatThrownBy(() -> service.createRequest(dto("oid-de-otro"), sinOid, "token"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(repository, never()).save(any(RequestEntity.class));
    }
}
