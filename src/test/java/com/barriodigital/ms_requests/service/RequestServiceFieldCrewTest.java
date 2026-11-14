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
import com.barriodigital.ms_requests.dto.CrewAssignmentDto;
import com.barriodigital.ms_requests.dto.StatusChangeResponse;
import com.barriodigital.ms_requests.messaging.RequestStatusChangedEvent;
import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;
import com.barriodigital.ms_requests.model.RequestStatusHistoryEntity;
import com.barriodigital.ms_requests.repository.RequestRepository;
import com.barriodigital.ms_requests.repository.RequestStatusHistoryRepository;
import com.barriodigital.ms_requests.security.Requester;

/** Datos de la cuadrilla al pasar a EN_TERRENO y cancelación en terreno (EN_TERRENO a EN_GESTION). */
class RequestServiceFieldCrewTest {

    private final RequestRepository repository = mock(RequestRepository.class);
    private final RequestStatusHistoryRepository history = mock(RequestStatusHistoryRepository.class);
    private final CatalogClient catalog = mock(CatalogClient.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RequestService service = new RequestService(repository, history, catalog, events);

    private static final Requester OPERADOR =
            new Requester("oid-olga", "olga@municipio.cl", "Olga Operadora", Set.of("Operador"));
    private static final Requester CLIENTE =
            new Requester("oid-ana", "ana@correo.cl", "Ana", Set.of("Cliente"));

    private static final CrewAssignmentDto CUADRILLA =
            new CrewAssignmentDto("Cuadrilla Norte", "J. Pérez, M. Soto", "ABCD12", "Camioneta", "+56911112222");

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
        request.setRequesterEmail("ana@correo.cl");
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

    private void assertBadRequest(Runnable action, String messagePart) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(ex.getReason()).contains(messagePart);
        });
        verify(history, never()).save(any(RequestStatusHistoryEntity.class));
        verify(repository, never()).save(any(RequestEntity.class));
    }

    // ------------------------------------------------------------ pasar a terreno

    @Test
    void alPasarATerrenoSeGuardanLosDatosDeLaCuadrillaEnElHistorial() {
        givenRequestInStatus(RequestStatus.EN_GESTION);

        service.updateStatus(5L, RequestStatus.EN_TERRENO, null, CUADRILLA, OPERADOR, "token");

        RequestStatusHistoryEntity entry = lastSavedEntry();
        assertThat(entry.getToStatus()).isEqualTo(RequestStatus.EN_TERRENO);
        assertThat(entry.getCrewName()).isEqualTo("Cuadrilla Norte");
        assertThat(entry.getCrewMembers()).isEqualTo("J. Pérez, M. Soto");
        assertThat(entry.getCrewVehiclePlate()).isEqualTo("ABCD12");
        assertThat(entry.getCrewVehicleType()).isEqualTo("Camioneta");
        assertThat(entry.getCrewPhone()).isEqualTo("+56911112222");
    }

    @Test
    void laPatenteSeNormalizaAMayusculasYSeLimpianLosEspacios() {
        givenRequestInStatus(RequestStatus.EN_GESTION);
        CrewAssignmentDto crew = new CrewAssignmentDto("  Cuadrilla Sur ", " A. Rojas ", " ab-cd 12 ", "  ", "");

        service.updateStatus(5L, RequestStatus.EN_TERRENO, null, crew, OPERADOR, "token");

        RequestStatusHistoryEntity entry = lastSavedEntry();
        assertThat(entry.getCrewName()).isEqualTo("Cuadrilla Sur");
        assertThat(entry.getCrewMembers()).isEqualTo("A. Rojas");
        assertThat(entry.getCrewVehiclePlate()).isEqualTo("AB-CD 12");
        assertThat(entry.getCrewVehicleType()).isNull();
        assertThat(entry.getCrewPhone()).isNull();
    }

    @Test
    void elTipoDeVehiculoYElTelefonoSonOpcionales() {
        givenRequestInStatus(RequestStatus.EN_GESTION);
        CrewAssignmentDto crew = new CrewAssignmentDto("Cuadrilla Norte", "J. Pérez", "ABCD12", null, null);

        service.updateStatus(5L, RequestStatus.EN_TERRENO, null, crew, OPERADOR, "token");

        assertThat(lastSavedEntry().getCrewVehiclePlate()).isEqualTo("ABCD12");
    }

    @Test
    void sinCuadrillaNoSePuedePasarATerreno() {
        RequestEntity request = givenRequestInStatus(RequestStatus.EN_GESTION);

        assertBadRequest(() -> service.updateStatus(5L, RequestStatus.EN_TERRENO, null, null, OPERADOR, "token"),
                "nombre de la cuadrilla");
        assertThat(request.getStatus()).isEqualTo(RequestStatus.EN_GESTION);
    }

    @Test
    void nombreIntegrantesYPatenteSonObligatorios() {
        givenRequestInStatus(RequestStatus.EN_GESTION);
        CrewAssignmentDto[] incompletas = {
                new CrewAssignmentDto(null, "J. Pérez", "ABCD12", null, null),
                new CrewAssignmentDto("Cuadrilla", " ", "ABCD12", null, null),
                new CrewAssignmentDto("Cuadrilla", "J. Pérez", "", null, null),
                new CrewAssignmentDto("Cuadrilla", "J. Pérez", null, "Camioneta", "123")
        };
        for (CrewAssignmentDto crew : incompletas) {
            assertThatThrownBy(() -> service.updateStatus(5L, RequestStatus.EN_TERRENO, null, crew, OPERADOR, "token"))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(history, never()).save(any(RequestStatusHistoryEntity.class));
    }

    @Test
    void enLosDemasCambiosLaCuadrillaSeIgnora() {
        givenRequestInStatus(RequestStatus.ADMITIDO);

        service.updateStatus(5L, RequestStatus.EN_GESTION, null, CUADRILLA, OPERADOR, "token");

        assertThat(lastSavedEntry().getCrewName()).isNull();
    }

    // ------------------------------------------------------------ cancelación en terreno

    @Test
    void sePuedeCancelarEnTerrenoYVuelveAGestionConElMotivo() {
        RequestEntity request = givenRequestInStatus(RequestStatus.EN_TERRENO);

        RequestEntity result = service.updateStatus(
                5L, RequestStatus.EN_GESTION, "  Camino cortado, no pudieron llegar ", null, OPERADOR, "token");

        assertThat(result.getStatus()).isEqualTo(RequestStatus.EN_GESTION);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.EN_GESTION);
        RequestStatusHistoryEntity entry = lastSavedEntry();
        assertThat(entry.getFromStatus()).isEqualTo(RequestStatus.EN_TERRENO);
        assertThat(entry.getToStatus()).isEqualTo(RequestStatus.EN_GESTION);
        assertThat(entry.getReason()).isEqualTo("Camino cortado, no pudieron llegar");
        assertThat(entry.getChangedByRole()).isEqualTo("Operador");
    }

    @Test
    void cancelarEnTerrenoExigeMotivo() {
        RequestEntity request = givenRequestInStatus(RequestStatus.EN_TERRENO);

        for (String vacio : new String[] { null, "", "   " }) {
            assertThatThrownBy(() -> service.updateStatus(5L, RequestStatus.EN_GESTION, vacio, null, OPERADOR, "token"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ex.getReason()).contains("motivo de la cancelación en terreno");
                    });
        }
        assertThat(request.getStatus()).isEqualTo(RequestStatus.EN_TERRENO);
        verify(history, never()).save(any(RequestStatusHistoryEntity.class));
    }

    @Test
    void elMotivoDeCancelacionNoPuedeSuperar500Caracteres() {
        givenRequestInStatus(RequestStatus.EN_TERRENO);
        String largo = "x".repeat(501);

        assertThatThrownBy(() -> service.updateStatus(5L, RequestStatus.EN_GESTION, largo, null, OPERADOR, "token"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void elEventoDeCancelacionIndicaQueVieneDeTerreno() {
        givenRequestInStatus(RequestStatus.EN_TERRENO);

        service.updateStatus(5L, RequestStatus.EN_GESTION, "Vehículo con falla", null, OPERADOR, "token");

        ArgumentCaptor<RequestStatusChangedEvent> captor = ArgumentCaptor.forClass(RequestStatusChangedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().previousStatus()).isEqualTo(RequestStatus.EN_TERRENO);
        assertThat(captor.getValue().isInternalOnly()).isTrue();
    }

    @Test
    void unaVezEnGestionSePuedeReasignarAOtraCuadrilla() {
        givenRequestInStatus(RequestStatus.EN_GESTION);
        CrewAssignmentDto otra = new CrewAssignmentDto("Cuadrilla Sur", "A. Rojas", "WXYZ98", null, null);

        service.updateStatus(5L, RequestStatus.EN_TERRENO, null, otra, OPERADOR, "token");

        assertThat(lastSavedEntry().getCrewName()).isEqualTo("Cuadrilla Sur");
    }

    // ------------------------------------------------------------ visibilidad en el historial

    private static RequestStatusHistoryEntity entry(RequestStatus from, RequestStatus to, String reason,
            boolean withCrew) {
        RequestStatusHistoryEntity entry = new RequestStatusHistoryEntity();
        entry.setRequestId(5L);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setChangedAt(LocalDateTime.of(2026, 11, 13, 10, 30));
        entry.setChangedByName("Olga Operadora");
        entry.setChangedByEmail("olga@municipio.cl");
        entry.setChangedByRole("Operador");
        entry.setReason(reason);
        if (withCrew) {
            entry.setCrewName("Cuadrilla Norte");
            entry.setCrewMembers("J. Pérez, M. Soto");
            entry.setCrewVehiclePlate("ABCD12");
            entry.setCrewVehicleType("Camioneta");
            entry.setCrewPhone("+56911112222");
        }
        return entry;
    }

    private void givenHistory() {
        givenRequestInStatus(RequestStatus.EN_GESTION);
        when(history.findByRequestIdOrderByChangedAtAscIdAsc(5L)).thenReturn(List.of(
                entry(RequestStatus.EN_GESTION, RequestStatus.EN_TERRENO, null, true),
                entry(RequestStatus.EN_TERRENO, RequestStatus.EN_GESTION, "Camino cortado", false)));
    }

    @Test
    void elPersonalVeLaCuadrillaYElMotivoDeLaCancelacion() {
        givenHistory();

        List<StatusChangeResponse> result = service.getHistory(5L, OPERADOR);

        assertThat(result.get(0).crewName()).isEqualTo("Cuadrilla Norte");
        assertThat(result.get(0).crewMembers()).isEqualTo("J. Pérez, M. Soto");
        assertThat(result.get(0).crewVehiclePlate()).isEqualTo("ABCD12");
        assertThat(result.get(0).crewVehicleType()).isEqualTo("Camioneta");
        assertThat(result.get(0).crewPhone()).isEqualTo("+56911112222");
        assertThat(result.get(1).reason()).isEqualTo("Camino cortado");
    }

    @Test
    void elVecinoNoVeLaCuadrillaNiElMotivoDeLaCancelacion() {
        givenHistory();

        List<StatusChangeResponse> result = service.getHistory(5L, CLIENTE);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).crewName()).isNull();
        assertThat(result.get(0).crewMembers()).isNull();
        assertThat(result.get(0).crewVehiclePlate()).isNull();
        assertThat(result.get(0).crewVehicleType()).isNull();
        assertThat(result.get(0).crewPhone()).isNull();
        assertThat(result.get(1).reason()).isNull();
        assertThat(result.get(1).toStatus()).isEqualTo(RequestStatus.EN_GESTION);
    }

    @Test
    void elVecinoSiVeElMotivoDeUnRechazo() {
        givenRequestInStatus(RequestStatus.RECHAZADO);
        when(history.findByRequestIdOrderByChangedAtAscIdAsc(5L)).thenReturn(List.of(
                entry(RequestStatus.EN_GESTION, RequestStatus.RECHAZADO, "Falta documentación", false)));

        assertThat(service.getHistory(5L, CLIENTE).get(0).reason()).isEqualTo("Falta documentación");
    }
}
