package com.barriodigital.ms_requests.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

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

    private void givenRequestInStatus(RequestStatus status) {
        RequestEntity request = new RequestEntity();
        request.setId(5L);
        request.setProcedureId(7L);
        request.setType("Certificado");
        request.setStatus(status);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        when(repository.save(any(RequestEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

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
    void repetirElMismoEstadoNoVuelveANotificar() {
        givenRequestInStatus(RequestStatus.ADMITIDO);

        service.updateStatus(5L, RequestStatus.ADMITIDO, "token");

        verify(events, never()).publishEvent(any(Object.class));
    }
}