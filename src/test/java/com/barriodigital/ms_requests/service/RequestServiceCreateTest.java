package com.barriodigital.ms_requests.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

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

    @Test
    void guardaSolicitanteYRutYPublicaElEventoDeIngreso() {
        CatalogProcedure procedure = mock(CatalogProcedure.class);
        when(procedure.id()).thenReturn(7L);
        when(procedure.name()).thenReturn("Certificado de residencia");
        when(procedure.active()).thenReturn(true);
        when(catalog.getProcedure(7L, "token")).thenReturn(procedure);
        when(repository.save(any(RequestEntity.class))).thenAnswer(call -> call.getArgument(0));

        RequestEntity saved = service.createRequest(
                new CreateRequestDto(7L, "Necesito mi certificado", "u-1", "11111111-1"),
                new Requester("ana@municipio.cl", "Ana"),
                "token");

        assertThat(saved.getRequesterEmail()).isEqualTo("ana@municipio.cl");
        assertThat(saved.getRequesterName()).isEqualTo("Ana");
        assertThat(saved.getCitizenRut()).isEqualTo("11111111-1");
        assertThat(saved.getStatus()).isEqualTo(RequestStatus.INGRESADO);

        ArgumentCaptor<RequestStatusChangedEvent> captor = ArgumentCaptor.forClass(RequestStatusChangedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(RequestStatus.INGRESADO);
        assertThat(captor.getValue().requesterEmail()).isEqualTo("ana@municipio.cl");
    }
}