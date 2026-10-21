package com.barriodigital.ms_requests.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.barriodigital.ms_requests.client.CatalogClient;
import com.barriodigital.ms_requests.client.CatalogProcedure;
import com.barriodigital.ms_requests.dto.CreateRequestDto;
import com.barriodigital.ms_requests.messaging.RequestStatusChangedEvent;
import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;
import com.barriodigital.ms_requests.repository.RequestRepository;
import com.barriodigital.ms_requests.security.Requester;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RequestService {

    private static final String NOT_FOUND_MESSAGE = "Trámite no encontrado";

    private final RequestRepository repository;
    private final CatalogClient catalog;
    private final ApplicationEventPublisher events;

    public RequestEntity createRequest(CreateRequestDto dto, Requester requester, String bearerToken) {
        String ownerId = resolveOwner(dto, requester);

        CatalogProcedure procedure = catalog.getProcedure(dto.procedureId(), bearerToken);
        if (!procedure.active()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El tipo de trámite '" + procedure.name() + "' no está disponible");
        }

        RequestEntity request = new RequestEntity();
        request.setProcedureId(procedure.id());
        request.setType(procedure.name());
        request.setDescription(dto.description());
        request.setUserId(ownerId);
        request.setRequesterEmail(requester.email());
        request.setRequesterName(requester.name());
        request.setStatus(RequestStatus.INGRESADO);
        request.setCitizenRut(dto.citizenRut());
        RequestEntity saved = repository.save(request);
        events.publishEvent(RequestStatusChangedEvent.from(saved));
        return saved;
    }

    /** Admin, Operador y Auditor ven todas las solicitudes; el Cliente solo las suyas. */
    public List<RequestEntity> getAllRequests(Requester requester) {
        if (requester.canViewAll()) {
            return repository.findAll();
        }
        return repository.findByUserId(requireUserId(requester));
    }

    /** Para un Cliente, la solicitud de otro vecino se trata como inexistente (404) y no se revela. */
    public RequestEntity getRequestById(Long id, Requester requester) {
        RequestEntity request = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
        boolean isOwner = requester.userId() != null && requester.userId().equals(request.getUserId());
        if (!requester.canViewAll() && !isOwner) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
        }
        return request;
    }

    /**
     * El dueño sale del token. Solo Operador y Admin pueden indicar otro dueño en el cuerpo
     * (ingreso a nombre del vecino); para un Cliente ese campo se ignora.
     */
    private String resolveOwner(CreateRequestDto dto, Requester requester) {
        String onBehalf = dto.userId();
        if (requester.canActOnBehalf() && onBehalf != null && !onBehalf.isBlank()) {
            return onBehalf.trim();
        }
        return requireUserId(requester);
    }

    private String requireUserId(Requester requester) {
        if (requester.userId() == null || requester.userId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El token no incluye el identificador del usuario (oid)");
        }
        return requester.userId();
    }

    /**
     * Cambia el estado. La fila queda bloqueada durante la transacción, así que dos
     * peticiones simultáneas sobre la misma solicitud se ejecutan una tras otra.
     *
     * <p>Pedir el estado que ya tiene la solicitud es idempotente: responde con la solicitud tal
     * cual, sin guardar, sin notificar y sin descontar cupo. Una transición fuera de la tabla de
     * {@link RequestStatus#allowedTransitions()} responde 409 y no cambia nada.
     */
    @Transactional
    public RequestEntity updateStatus(Long id, RequestStatus newStatus, String bearerToken) {
        RequestEntity request = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
        RequestStatus currentStatus = request.getStatus();

        if (newStatus == currentStatus) {
            return request;
        }
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, invalidTransitionMessage(currentStatus, newStatus));
        }

        // CUPO: solo la admisión (INGRESADO -> ADMITIDO) descuenta un cupo diario.
        if (currentStatus == RequestStatus.INGRESADO && newStatus == RequestStatus.ADMITIDO) {
            if (request.getProcedureId() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La solicitud no tiene un tipo de trámite del catálogo, no se puede descontar cupo");
            }
            catalog.consumeQuota(request.getProcedureId(), bearerToken);
        }

        request.setStatus(newStatus);
        RequestEntity saved = repository.save(request);
        events.publishEvent(RequestStatusChangedEvent.from(saved));
        // TODO: Kafka (auditoría/métricas)
        return saved;
    }

    private static String invalidTransitionMessage(RequestStatus current, RequestStatus target) {
        if (target == RequestStatus.EN_TERRENO && current == RequestStatus.INGRESADO) {
            return "No se puede pasar a EN_TERRENO sin haber sido admitida (estado actual: INGRESADO)";
        }
        String base = String.format("No se permite cambiar el estado de %s a %s", current, target);
        if (current.isFinal()) {
            return base + ": " + current + " es un estado final";
        }
        return base + ". Desde " + current + " se puede pasar a: "
                + current.allowedTransitions().stream().map(Enum::name).collect(Collectors.joining(", "));
    }
}
