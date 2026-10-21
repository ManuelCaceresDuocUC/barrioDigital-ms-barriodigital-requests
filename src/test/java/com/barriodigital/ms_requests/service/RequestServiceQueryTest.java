package com.barriodigital.ms_requests.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.barriodigital.ms_requests.client.CatalogClient;
import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.repository.RequestRepository;
import com.barriodigital.ms_requests.security.Requester;

class RequestServiceQueryTest {

    private final RequestRepository repository = mock(RequestRepository.class);
    private final RequestService service = new RequestService(
            repository, mock(CatalogClient.class), mock(ApplicationEventPublisher.class));

    private static Requester requester(String oid, String role) {
        return new Requester(oid, role + "@municipio.cl", role, Set.of(role));
    }

    private static RequestEntity request(Long id, String ownerId) {
        RequestEntity request = new RequestEntity();
        request.setId(id);
        request.setUserId(ownerId);
        return request;
    }

    // ---------------------------------------------------------------- listar

    @Test
    void elClienteSoloListaSusSolicitudes() {
        List<RequestEntity> suyas = List.of(request(1L, "oid-ana"));
        when(repository.findByUserId("oid-ana")).thenReturn(suyas);

        assertThat(service.getAllRequests(requester("oid-ana", "Cliente"))).isEqualTo(suyas);
        verify(repository, never()).findAll();
    }

    @Test
    void operadorAdminYAuditorListanTodas() {
        List<RequestEntity> todas = List.of(request(1L, "oid-ana"), request(2L, "oid-beto"));
        when(repository.findAll()).thenReturn(todas);

        assertThat(service.getAllRequests(requester("oid-o", "Operador"))).isEqualTo(todas);
        assertThat(service.getAllRequests(requester("oid-a", "Admin"))).isEqualTo(todas);
        assertThat(service.getAllRequests(requester("oid-u", "Auditor"))).isEqualTo(todas);
        verify(repository, never()).findByUserId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void unClienteSinOidNoPuedeListar() {
        Requester sinOid = new Requester(null, null, null, Set.of("Cliente"));

        assertThatThrownBy(() -> service.getAllRequests(sinOid))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ------------------------------------------------------------ ver una

    @Test
    void elClienteVeSuPropiaSolicitud() {
        when(repository.findById(1L)).thenReturn(Optional.of(request(1L, "oid-ana")));

        assertThat(service.getRequestById(1L, requester("oid-ana", "Cliente")).getId()).isEqualTo(1L);
    }

    @Test
    void laSolicitudDeOtroVecinoEsUn404ParaElCliente() {
        when(repository.findById(2L)).thenReturn(Optional.of(request(2L, "oid-beto")));

        assertThatThrownBy(() -> service.getRequestById(2L, requester("oid-ana", "Cliente")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getReason()).isEqualTo("Trámite no encontrado");
                });
    }

    @Test
    void operadorAdminYAuditorVenCualquierSolicitud() {
        when(repository.findById(2L)).thenReturn(Optional.of(request(2L, "oid-beto")));

        assertThat(service.getRequestById(2L, requester("oid-o", "Operador")).getId()).isEqualTo(2L);
        assertThat(service.getRequestById(2L, requester("oid-a", "Admin")).getId()).isEqualTo(2L);
        assertThat(service.getRequestById(2L, requester("oid-u", "Auditor")).getId()).isEqualTo(2L);
    }

    @Test
    void unaSolicitudInexistenteEsUn404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRequestById(99L, requester("oid-o", "Operador")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void unaSolicitudSinDuenoNoEsVisibleParaUnClienteSinOid() {
        when(repository.findById(3L)).thenReturn(Optional.of(request(3L, null)));
        Requester sinOid = new Requester(null, null, null, Set.of("Cliente"));

        assertThatThrownBy(() -> service.getRequestById(3L, sinOid))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
