package com.barriodigital.ms_requests.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.barriodigital.ms_requests.security.Requester;
import com.barriodigital.ms_requests.client.CatalogClient;
import com.barriodigital.ms_requests.client.CatalogProcedure;
import com.barriodigital.ms_requests.dto.CreateRequestDto;
import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;
import com.barriodigital.ms_requests.repository.RequestRepository;
import org.springframework.context.ApplicationEventPublisher;

import com.barriodigital.ms_requests.messaging.RequestStatusChangedEvent;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository repository;
    private final CatalogClient catalog;
    private final ApplicationEventPublisher events;

    public RequestEntity createRequest(CreateRequestDto dto, Requester requester, String bearerToken) {
        CatalogProcedure procedure = catalog.getProcedure(dto.procedureId(), bearerToken);
        if (!procedure.active()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El tipo de trámite '" + procedure.name() + "' no está disponible");
        }

        RequestEntity request = new RequestEntity();
        request.setProcedureId(procedure.id());
        request.setType(procedure.name());
        request.setDescription(dto.description());
        request.setUserId(dto.userId());
        request.setRequesterEmail(requester.email());
        request.setRequesterName(requester.name());
        request.setStatus(RequestStatus.INGRESADO);
        request.setCitizenRut(dto.citizenRut());
        RequestEntity saved = repository.save(request);
        events.publishEvent(RequestStatusChangedEvent.from(saved));
        return saved;
    }

    public List<RequestEntity> getAllRequests() {
        return repository.findAll();
    }

    public RequestEntity getRequestById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trámite no encontrado"));
    }

    /**
     * Cambia el estado. La fila queda bloqueada durante la transacción, así que dos
     * peticiones simultáneas sobre la misma solicitud se ejecutan una tras otra.
     */
    @Transactional
    public RequestEntity updateStatus(Long id, RequestStatus newStatus, String bearerToken) {
        RequestEntity request = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trámite no encontrado"));
        RequestStatus currentStatus = request.getStatus();

        // 1. REGLA CLAVE DE EVALUACIÓN: Bloquear paso a EN_TERRENO si no fue ADMITIDO o puesto EN_GESTION
        if (newStatus == RequestStatus.EN_TERRENO &&
           (currentStatus != RequestStatus.ADMITIDO && currentStatus != RequestStatus.EN_GESTION)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Transición inválida: No se puede pasar a EN_TERRENO sin admisión o gestión previa."
            );
        }

        // 2. MÁQUINA DE ESTADOS: Validar transiciones permitidas
        validateStatusTransition(currentStatus, newStatus);

        // 3. CUPO: solo la admisión real (INGRESADO -> ADMITIDO) descuenta un cupo diario.
        //    Repetir el mismo estado no entra aquí, así que un reintento no gasta otro cupo.
        if (currentStatus == RequestStatus.INGRESADO && newStatus == RequestStatus.ADMITIDO) {
            if (request.getProcedureId() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La solicitud no tiene un tipo de trámite del catálogo, no se puede descontar cupo");
            }
            catalog.consumeQuota(request.getProcedureId(), bearerToken);
        }
        request.setStatus(newStatus);
        RequestEntity saved = repository.save(request);

        // Repetir el mismo estado no es un cambio: no se vuelve a notificar.
        if (newStatus != currentStatus) {
            events.publishEvent(RequestStatusChangedEvent.from(saved));
        }
        // TODO: Kafka (auditoría/métricas)

        return saved;
    }

    private void validateStatusTransition(RequestStatus current, RequestStatus target) {
        if (current == target) return; // Mismo estado

        boolean isValid = switch (current) {
            case INGRESADO -> target == RequestStatus.ADMITIDO || target == RequestStatus.RECHAZADO;
            case ADMITIDO -> target == RequestStatus.EN_GESTION || target == RequestStatus.RECHAZADO;
            case EN_GESTION -> target == RequestStatus.EN_TERRENO || target == RequestStatus.RECHAZADO;
            case EN_TERRENO -> target == RequestStatus.RESUELTO || target == RequestStatus.RECHAZADO;
            case RESUELTO, RECHAZADO -> false; // Estados finales, no se modifican
        };

        if (!isValid) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                String.format("No se permite cambiar el estado de %s a %s", current, target)
            );
        }
    }
}
