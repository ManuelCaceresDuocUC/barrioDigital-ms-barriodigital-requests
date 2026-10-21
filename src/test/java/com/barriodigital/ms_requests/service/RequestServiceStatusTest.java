package com.barriodigital.ms_requests.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.barriodigital.ms_requests.client.CatalogClient;
import com.barriodigital.ms_requests.messaging.RequestStatusChangedEvent;
import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;
import com.barriodigital.ms_requests.repository.RequestRepository;

class RequestServiceStatusTest {

    private final RequestRepository repository = mock(RequestRepository.class);
    private final CatalogClient catalog = mock(CatalogClient.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RequestService service = new RequestService(repository, catalog, events);

    private RequestEntity givenRequestInStatus(RequestStatus status) {
        RequestEntity request = new RequestEntity();
        request.setId(5L);
        request.setProcedureId(7L);
        request.setType("Certificado");
        request.setStatus(status);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        when(repository.save(any(RequestEntity.class))).thenAnswer(call -> call.getArgument(0));
        return request;
    }

    private void assertConflict(RequestStatus from, RequestStatus to) {
        RequestEntity request = givenRequestInStatus(from);

        assertThatThrownBy(() -> service.updateStatus(5L, to, "token"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(request.getStatus()).isEqualTo(from);
        verify(repository, never()).save(any(RequestEntity.class));
        verify(events, never()).publishEvent(any(Object.class));
        verify(catalog, never()).consumeQuota(anyLong(), anyString());
    }

    // ------------------------------------------------------- cambios válidos

    @Test
    void cambiarDeEstadoPublicaUnEventoConElNuevoEstado() {
        givenRequestInStatus(RequestStatus.ADMITIDO);

        service.updateStatus(5L, RequestStatus.EN_GESTION, "token");

        ArgumentCaptor<RequestStatusChangedEvent> captor = ArgumentCaptor.forClass(RequestStatusChangedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().requestId()).isEqualTo(5L);
        assertThat(captor.getValue().status()).isEqualTo(RequestStatus.EN_GESTION);
    }

    @Test
    void admitirDescuentaUnCupoYGuardaElNuevoEstado() {
        givenRequestInStatus(RequestStatus.INGRESADO);

        RequestEntity saved = service.updateStatus(5L, RequestStatus.ADMITIDO, "token");

        assertThat(saved.getStatus()).isEqualTo(RequestStatus.ADMITIDO);
        verify(catalog).consumeQuota(7L, "token");
    }

    @Test
    void losDemasCambiosNoDescuentanCupo() {
        givenRequestInStatus(RequestStatus.EN_GESTION);

        service.updateStatus(5L, RequestStatus.EN_TERRENO, "token");

        verify(catalog, never()).consumeQuota(anyLong(), anyString());
    }

    @Test
    void sePuedeResolverDirectamenteDesdeGestion() {
        givenRequestInStatus(RequestStatus.EN_GESTION);

        assertThat(service.updateStatus(5L, RequestStatus.RESUELTO, "token").getStatus())
                .isEqualTo(RequestStatus.RESUELTO);
    }

    // ------------------------------------------------------- transiciones inválidas

    @Test
    void noSePuedePasarAEnTerrenoSinHaberSidoAdmitida() {
        RequestEntity request = givenRequestInStatus(RequestStatus.INGRESADO);

        assertThatThrownBy(() -> service.updateStatus(5L, RequestStatus.EN_TERRENO, "token"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getReason()).contains("sin haber sido admitida");
                });
        assertThat(request.getStatus()).isEqualTo(RequestStatus.INGRESADO);
        verify(repository, never()).save(any(RequestEntity.class));
    }

    @Test
    void desdeAdmitidaNoSePuedeSaltarAEnTerreno() {
        assertConflict(RequestStatus.ADMITIDO, RequestStatus.EN_TERRENO);
    }

    @Test
    void noSePuedeRetrocederNiReabrirUnaSolicitud() {
        assertConflict(RequestStatus.ADMITIDO, RequestStatus.INGRESADO);
        assertConflict(RequestStatus.EN_TERRENO, RequestStatus.EN_GESTION);
    }

    @Test
    void desdeTerrenoSoloSePuedeResolver() {
        assertConflict(RequestStatus.EN_TERRENO, RequestStatus.RECHAZADO);
    }

    @Test
    void losEstadosFinalesNoCambian() {
        assertConflict(RequestStatus.RESUELTO, RequestStatus.ADMITIDO);
        assertConflict(RequestStatus.RECHAZADO, RequestStatus.INGRESADO);
    }

    @Test
    void elMensajeDeUnaTransicionInvalidaIndicaLasOpciones() {
        givenRequestInStatus(RequestStatus.ADMITIDO);

        assertThatThrownBy(() -> service.updateStatus(5L, RequestStatus.RESUELTO, "token"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason())
                                .contains("de ADMITIDO a RESUELTO")
                                .contains("EN_GESTION")
                                .contains("RECHAZADO"));
    }

    @Test
    void elMensajeDeUnEstadoFinalLoIndica() {
        givenRequestInStatus(RequestStatus.RESUELTO);

        assertThatThrownBy(() -> service.updateStatus(5L, RequestStatus.ADMITIDO, "token"))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                        assertThat(ex.getReason()).contains("RESUELTO es un estado final"));
    }

    // ------------------------------------------------------- idempotencia

    @Test
    void repetirElMismoEstadoNoVuelveANotificar() {
        givenRequestInStatus(RequestStatus.ADMITIDO);

        service.updateStatus(5L, RequestStatus.ADMITIDO, "token");

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void repetirElMismoEstadoNoTieneEfectosSecundarios() {
        givenRequestInStatus(RequestStatus.ADMITIDO);

        RequestEntity result = service.updateStatus(5L, RequestStatus.ADMITIDO, "token");

        assertThat(result.getStatus()).isEqualTo(RequestStatus.ADMITIDO);
        verify(repository, never()).save(any(RequestEntity.class));
        verify(catalog, never()).consumeQuota(anyLong(), anyString());
    }

    @Test
    void repetirAdmitirNoGastaOtroCupo() {
        givenRequestInStatus(RequestStatus.ADMITIDO);

        service.updateStatus(5L, RequestStatus.ADMITIDO, "token");
        service.updateStatus(5L, RequestStatus.ADMITIDO, "token");

        verify(catalog, never()).consumeQuota(anyLong(), anyString());
    }

    @Test
    void repetirUnEstadoFinalTambienEsIdempotente() {
        givenRequestInStatus(RequestStatus.RESUELTO);

        assertThat(service.updateStatus(5L, RequestStatus.RESUELTO, "token").getStatus())
                .isEqualTo(RequestStatus.RESUELTO);
        verify(events, never()).publishEvent(any(Object.class));
    }

    // ------------------------------------------------------- inexistente

    @Test
    void unaSolicitudInexistenteEsUn404() {
        when(repository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(99L, RequestStatus.ADMITIDO, "token"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
