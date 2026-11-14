package com.barriodigital.ms_requests.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
import com.barriodigital.ms_requests.dto.StatusChangeResponse;
import com.barriodigital.ms_requests.messaging.RequestStatusChangedEvent;
import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;
import com.barriodigital.ms_requests.model.RequestStatusHistoryEntity;
import com.barriodigital.ms_requests.repository.RequestRepository;
import com.barriodigital.ms_requests.repository.RequestStatusHistoryRepository;
import com.barriodigital.ms_requests.security.Requester;

/** Historial de cambios de estado y motivo de rechazo. */
class RequestServiceHistoryTest {

    private final RequestRepository repository = mock(RequestRepository.class);
    private final RequestStatusHistoryRepository history = mock(RequestStatusHistoryRepository.class);
    private final CatalogClient catalog = mock(CatalogClient.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RequestService service = new RequestService(repository, history, catalog, events);

    private static final Requester OPERADOR =
            new Requester("oid-olga", "olga@municipio.cl", "Olga Operadora", Set.of("Operador"));
    private static final Requester CLIENTE =
            new Requester("oid-ana", "ana@correo.cl", "Ana", Set.of("Cliente"));
    private static final Requester AUDITOR =
            new Requester("oid-aldo", "aldo@municipio.cl", "Aldo Auditor", Set.of("Auditor"));

    @BeforeEach
    void repositoriosQueDevuelvenLoGuardado() {
        when(repository.save(any(RequestEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    private RequestEntity givenRequestInStatus(RequestStatus status) {
        RequestEntity request = new RequestEntity();
        request.setId(5L);
        request.setProcedureId(7L);
        request.setType("Certificado");
        request.setUserId("oid-ana");
        request.setStatus(status);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        when(repository.findById(5L)).thenReturn(Optional.of(request));
        return request;
    }

    private RequestStatusHistoryEntity lastSavedEntry() {
        ArgumentCaptor<RequestStatusHistoryEntity> captor = ArgumentCaptor.forClass(RequestStatusHistoryEntity.class);
        verify(history).save(captor.capture());
        return captor.getValue();
    }

    private static RequestStatusHistoryEntity entry(RequestStatus from, RequestStatus to, String reason) {
        RequestStatusHistoryEntity entry = new RequestStatusHistoryEntity();
        entry.setRequestId(5L);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setChangedAt(LocalDateTime.of(2026, 11, 13, 10, 30));
        entry.setChangedById("oid-olga");
        entry.setChangedByName("Olga Operadora");
        entry.setChangedByEmail("olga@municipio.cl");
        entry.setChangedByRole("Operador");
        entry.setReason(reason);
        return entry;
    }

    // ------------------------------------------------------------ registro

    @Test
    void crearUnaSolicitudRegistraElIngresoSinEstadoAnterior() {
        CatalogProcedure procedure = mock(CatalogProcedure.class);
        when(procedure.id()).thenReturn(7L);
        when(procedure.name()).thenReturn("Certificado");
        when(procedure.active()).thenReturn(true);
        when(catalog.getProcedure(7L, "token")).thenReturn(procedure);

        service.createRequest(new CreateRequestDto(7L, "Necesito mi certificado", null, null, null), CLIENTE, "token");

        RequestStatusHistoryEntity saved = lastSavedEntry();
        assertThat(saved.getFromStatus()).isNull();
        assertThat(saved.getToStatus()).isEqualTo(RequestStatus.INGRESADO);
        assertThat(saved.getChangedById()).isEqualTo("oid-ana");
        assertThat(saved.getChangedByRole()).isEqualTo("Cliente");
        assertThat(saved.getReason()).isNull();
    }

    @Test
    void cambiarDeEstadoRegistraDeDondeAdondeYQuienLoHizo() {
        givenRequestInStatus(RequestStatus.ADMITIDO);

        service.updateStatus(5L, RequestStatus.EN_GESTION, null, OPERADOR, "token");

        RequestStatusHistoryEntity saved = lastSavedEntry();
        assertThat(saved.getRequestId()).isEqualTo(5L);
        assertThat(saved.getFromStatus()).isEqualTo(RequestStatus.ADMITIDO);
        assertThat(saved.getToStatus()).isEqualTo(RequestStatus.EN_GESTION);
        assertThat(saved.getChangedById()).isEqualTo("oid-olga");
        assertThat(saved.getChangedByName()).isEqualTo("Olga Operadora");
        assertThat(saved.getChangedByEmail()).isEqualTo("olga@municipio.cl");
        assertThat(saved.getChangedByRole()).isEqualTo("Operador");
    }

    @Test
    void unaTransicionInvalidaNoDejaNadaEnElHistorial() {
        givenRequestInStatus(RequestStatus.INGRESADO);

        assertThatThrownBy(() -> service.updateStatus(5L, RequestStatus.EN_TERRENO, null, OPERADOR, "token"))
                .isInstanceOf(ResponseStatusException.class);

        verify(history, never()).save(any(RequestStatusHistoryEntity.class));
    }

    @Test
    void pedirElEstadoActualNoRegistraNada() {
        givenRequestInStatus(RequestStatus.ADMITIDO);

        service.updateStatus(5L, RequestStatus.ADMITIDO, null, OPERADOR, "token");

        verify(history, never()).save(any(RequestStatusHistoryEntity.class));
    }

    // ------------------------------------------------------------- rechazo

    @Test
    void rechazarSinMotivoResponde400YNoCambiaNada() {
        for (String vacio : new String[] { null, "", "   " }) {
            RequestEntity request = givenRequestInStatus(RequestStatus.EN_GESTION);

            assertThatThrownBy(() -> service.updateStatus(5L, RequestStatus.RECHAZADO, vacio, OPERADOR, "token"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getReason()).contains("motivo");
                    });

            assertThat(request.getStatus()).isEqualTo(RequestStatus.EN_GESTION);
        }
        verify(repository, never()).save(any(RequestEntity.class));
        verify(history, never()).save(any(RequestStatusHistoryEntity.class));
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void rechazarGuardaElMotivoSinEspaciosYLoLlevaEnElEvento() {
        givenRequestInStatus(RequestStatus.EN_GESTION);

        service.updateStatus(5L, RequestStatus.RECHAZADO, "  Falta el certificado de domicilio  ", OPERADOR, "token");

        RequestStatusHistoryEntity saved = lastSavedEntry();
        assertThat(saved.getToStatus()).isEqualTo(RequestStatus.RECHAZADO);
        assertThat(saved.getReason()).isEqualTo("Falta el certificado de domicilio");

        ArgumentCaptor<RequestStatusChangedEvent> captor = ArgumentCaptor.forClass(RequestStatusChangedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().reason()).isEqualTo("Falta el certificado de domicilio");
    }

    @Test
    void sePuedeRechazarEstandoEnGestion() {
        RequestEntity request = givenRequestInStatus(RequestStatus.EN_GESTION);

        service.updateStatus(5L, RequestStatus.RECHAZADO, "No corresponde a la comuna", OPERADOR, "token");

        assertThat(request.getStatus()).isEqualTo(RequestStatus.RECHAZADO);
    }

    @Test
    void unMotivoDemasiadoLargoResponde400() {
        givenRequestInStatus(RequestStatus.ADMITIDO);
        String largo = "x".repeat(501);

        assertThatThrownBy(() -> service.updateStatus(5L, RequestStatus.RECHAZADO, largo, OPERADOR, "token"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(history, never()).save(any(RequestStatusHistoryEntity.class));
    }

    @Test
    void enLosDemasEstadosElMotivoSeIgnora() {
        givenRequestInStatus(RequestStatus.ADMITIDO);

        service.updateStatus(5L, RequestStatus.EN_GESTION, "texto que no debe guardarse", OPERADOR, "token");

        assertThat(lastSavedEntry().getReason()).isNull();
        ArgumentCaptor<RequestStatusChangedEvent> captor = ArgumentCaptor.forClass(RequestStatusChangedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().reason()).isNull();
    }

    @Test
    void unaTransicionInvalidaTienePrioridadSobreLaFaltaDeMotivo() {
        givenRequestInStatus(RequestStatus.EN_TERRENO);

        assertThatThrownBy(() -> service.updateStatus(5L, RequestStatus.RECHAZADO, null, OPERADOR, "token"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ------------------------------------------------------------ consulta

    @Test
    void elPersonalVeQuienHizoCadaCambio() {
        givenRequestInStatus(RequestStatus.RECHAZADO);
        when(history.findByRequestIdOrderByChangedAtAscIdAsc(5L)).thenReturn(List.of(
                entry(null, RequestStatus.INGRESADO, null),
                entry(RequestStatus.EN_GESTION, RequestStatus.RECHAZADO, "Falta documentación")));

        List<StatusChangeResponse> result = service.getHistory(5L, AUDITOR);

        assertThat(result).hasSize(2);
        assertThat(result.get(1).changedByName()).isEqualTo("Olga Operadora");
        assertThat(result.get(1).changedByEmail()).isEqualTo("olga@municipio.cl");
        assertThat(result.get(1).changedByRole()).isEqualTo("Operador");
        assertThat(result.get(1).reason()).isEqualTo("Falta documentación");
    }

    @Test
    void elDuenoVeElRolYElMotivoPeroNoLaIdentidadDelFuncionario() {
        givenRequestInStatus(RequestStatus.RECHAZADO);
        when(history.findByRequestIdOrderByChangedAtAscIdAsc(5L)).thenReturn(List.of(
                entry(RequestStatus.EN_GESTION, RequestStatus.RECHAZADO, "Falta documentación")));

        StatusChangeResponse change = service.getHistory(5L, CLIENTE).get(0);

        assertThat(change.changedByName()).isNull();
        assertThat(change.changedByEmail()).isNull();
        assertThat(change.changedByRole()).isEqualTo("Operador");
        assertThat(change.reason()).isEqualTo("Falta documentación");
    }

    @Test
    void unClienteNoPuedeVerElHistorialDeLaSolicitudDeOtroVecino() {
        givenRequestInStatus(RequestStatus.ADMITIDO);
        Requester otro = new Requester("oid-otro", "otro@correo.cl", "Otro", Set.of("Cliente"));

        assertThatThrownBy(() -> service.getHistory(5L, otro))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(history, never()).findByRequestIdOrderByChangedAtAscIdAsc(any());
    }

    @Test
    void unaSolicitudInexistenteResponde404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHistory(99L, OPERADOR))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void unaSolicitudSinRegistrosDevuelveUnaListaVacia() {
        givenRequestInStatus(RequestStatus.INGRESADO);
        when(history.findByRequestIdOrderByChangedAtAscIdAsc(5L)).thenReturn(List.of());

        assertThat(service.getHistory(5L, OPERADOR)).isEmpty();
    }
}
