package com.barriodigital.ms_requests.service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.barriodigital.ms_requests.client.CatalogClient;
import com.barriodigital.ms_requests.client.CatalogProcedure;
import com.barriodigital.ms_requests.dto.CreateRequestDto;
import com.barriodigital.ms_requests.dto.CrewAssignmentDto;
import com.barriodigital.ms_requests.dto.StatusChangeResponse;
import com.barriodigital.ms_requests.messaging.RequestStatusChangedEvent;
import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;
import com.barriodigital.ms_requests.model.RequestStatusHistoryEntity;
import com.barriodigital.ms_requests.repository.RequestRepository;
import com.barriodigital.ms_requests.repository.RequestStatusHistoryRepository;
import com.barriodigital.ms_requests.security.Requester;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RequestService {

    private static final String NOT_FOUND_MESSAGE = "Trámite no encontrado";

    private static final int REASON_MAX_LENGTH = 500;

    private final RequestRepository repository;
    private final RequestStatusHistoryRepository history;
    private final CatalogClient catalog;
    private final ApplicationEventPublisher events;

    @Transactional
    public RequestEntity createRequest(CreateRequestDto dto, Requester requester, String bearerToken) {
        String ownerId = resolveOwner(dto, requester);
        Contact contact = resolveContact(dto, requester);

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
        request.setRequesterEmail(contact.email());
        request.setRequesterName(contact.name());
        request.setStatus(RequestStatus.INGRESADO);
        request.setCitizenRut(dto.citizenRut());
        RequestEntity saved = repository.save(request);
        recordChange(saved, null, RequestStatus.INGRESADO, null, requester);
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
     * Historial de cambios de estado, del más antiguo al más reciente. Tiene la misma regla de acceso
     * que {@link #getRequestById}: para un Cliente, la solicitud de otro vecino responde 404. El personal
     * ve quién hizo cada cambio; el vecino solo ve el rol.
     */
    @Transactional(readOnly = true)
    public List<StatusChangeResponse> getHistory(Long id, Requester requester) {
        getRequestById(id, requester);
        boolean showIdentity = requester.canViewAll();
        return history.findByRequestIdOrderByChangedAtAscIdAsc(id).stream()
                .map(entry -> StatusChangeResponse.from(entry, showIdentity))
                .toList();
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

    /**
     * Correo y nombre del solicitante. Un Cliente usa los de su token. Cuando el Operador o el Admin
     * ingresa la solicitud a nombre de un vecino, el correo es el que indicó (obligatorio) y el nombre
     * se desconoce: nunca se guarda el dato del funcionario como si fuera el del vecino.
     */
    private Contact resolveContact(CreateRequestDto dto, Requester requester) {
        if (!requester.canActOnBehalf()) {
            return new Contact(requester.email(), requester.name());
        }
        String citizenEmail = dto.citizenEmail();
        if (citizenEmail == null || citizenEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debes indicar el correo del vecino para ingresar la solicitud a su nombre");
        }
        return new Contact(citizenEmail.trim().toLowerCase(Locale.ROOT), null);
    }

    private record Contact(String email, String name) {
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
     *
     * <p>Rechazar y cancelar en terreno (EN_TERRENO a EN_GESTION) exigen un motivo (400 si falta); pasar a
     * EN_TERRENO exige los datos de la cuadrilla (nombre, integrantes y patente). En los demás cambios
     * ambos se ignoran. Cada cambio queda en el historial junto con quién lo hizo, dentro de la misma
     * transacción.
     */
    @Transactional
    public RequestEntity updateStatus(Long id, RequestStatus newStatus, String reason, CrewAssignmentDto crew,
            Requester actor, String bearerToken) {
        RequestEntity request = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
        RequestStatus currentStatus = request.getStatus();

        if (newStatus == currentStatus) {
            return request;
        }
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, invalidTransitionMessage(currentStatus, newStatus));
        }

        String cleanReason = requireReasonWhenNeeded(currentStatus, newStatus, reason);
        CrewAssignmentDto cleanCrew = requireCrewIfGoingToField(newStatus, crew);

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
        recordChange(saved, currentStatus, newStatus, cleanReason, cleanCrew, actor);
        events.publishEvent(RequestStatusChangedEvent.from(saved, cleanReason, currentStatus));
        // TODO: Kafka (auditoría/métricas)
        return saved;
    }

    /** Rechazar y cancelar en terreno exigen motivo; en los demás cambios no se guarda. */
    private static String requireReasonWhenNeeded(RequestStatus current, RequestStatus newStatus, String reason) {
        boolean rejecting = newStatus == RequestStatus.RECHAZADO;
        boolean cancellingInField = current == RequestStatus.EN_TERRENO && newStatus == RequestStatus.EN_GESTION;
        if (!rejecting && !cancellingInField) {
            return null;
        }
        String clean = reason == null ? "" : reason.trim();
        if (clean.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, rejecting
                    ? "Debes indicar el motivo del rechazo"
                    : "Debes indicar el motivo de la cancelación en terreno");
        }
        if (clean.length() > REASON_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El motivo no puede superar los " + REASON_MAX_LENGTH + " caracteres");
        }
        return clean;
    }

    /** Al pasar a EN_TERRENO se exige la cuadrilla (nombre, integrantes, patente); en los demás cambios se ignora. */
    private static CrewAssignmentDto requireCrewIfGoingToField(RequestStatus newStatus, CrewAssignmentDto crew) {
        if (newStatus != RequestStatus.EN_TERRENO) {
            return null;
        }
        String name = clean(crew == null ? null : crew.name());
        String members = clean(crew == null ? null : crew.members());
        String plate = clean(crew == null ? null : crew.vehiclePlate());
        if (name == null || members == null || plate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Para pasar a EN_TERRENO debes indicar el nombre de la cuadrilla, sus integrantes y la patente del vehículo");
        }
        return new CrewAssignmentDto(name, members, plate.toUpperCase(Locale.ROOT),
                clean(crew.vehicleType()), clean(crew.contactPhone()));
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void recordChange(RequestEntity request, RequestStatus from, RequestStatus to, String reason,
            Requester actor) {
        recordChange(request, from, to, reason, null, actor);
    }

    private void recordChange(RequestEntity request, RequestStatus from, RequestStatus to, String reason,
            CrewAssignmentDto crew, Requester actor) {
        RequestStatusHistoryEntity entry = new RequestStatusHistoryEntity();
        entry.setRequestId(request.getId());
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setChangedById(actor.userId());
        entry.setChangedByName(actor.name());
        entry.setChangedByEmail(actor.email());
        entry.setChangedByRole(actor.primaryRole());
        entry.setReason(reason);
        if (crew != null) {
            entry.setCrewName(crew.name());
            entry.setCrewMembers(crew.members());
            entry.setCrewVehiclePlate(crew.vehiclePlate());
            entry.setCrewVehicleType(crew.vehicleType());
            entry.setCrewPhone(crew.contactPhone());
        }
        history.save(entry);
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
