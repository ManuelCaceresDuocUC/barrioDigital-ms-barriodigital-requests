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

/**
 * QUÉ ES: la CAPA DE NEGOCIO principal del sistema. Aquí viven todas las reglas de las solicitudes:
 * quién puede ver qué, cómo se crea una solicitud, la máquina de estados, el descuento de cupo, el
 * historial y la publicación de eventos. Si vas a estudiar un solo archivo del backend, es este.
 *
 * COLABORADORES: repository (Oracle), history (bitácora), catalog (HTTP al catálogo) y events (eventos
 * internos de Spring que luego se envían a RabbitMQ y Kafka).
 */
@Service
@RequiredArgsConstructor
public class RequestService {

    private static final String NOT_FOUND_MESSAGE = "Trámite no encontrado";

    // Largo máximo del motivo (coincide con la columna "reason" del historial).
    private static final int REASON_MAX_LENGTH = 500;

    private final RequestRepository repository;
    private final RequestStatusHistoryRepository history;
    private final CatalogClient catalog;
    // Publicador de eventos internos de Spring (no es RabbitMQ ni Kafka: es dentro del mismo proceso).
    private final ApplicationEventPublisher events;

    // FLUJO "crear solicitud": define dueño y contacto -> valida el trámite en el catálogo -> guarda ->
    // anota el historial -> publica el evento. Todo en una transacción (si algo falla, no se guarda nada).
    @Transactional
    public RequestEntity createRequest(CreateRequestDto dto, Requester requester, String bearerToken) {
        // 1) Dueño de la solicitud y datos de contacto (según el rol de quien crea).
        String ownerId = resolveOwner(dto, requester);
        Contact contact = resolveContact(dto, requester);

        // 2) Se consulta el catálogo (HTTP) con el mismo token; si el trámite no existe -> 400.
        CatalogProcedure procedure = catalog.getProcedure(dto.procedureId(), bearerToken);
        if (!procedure.active()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El tipo de trámite '" + procedure.name() + "' no está disponible");
        }

        // 3) Se arma la entidad. El nombre del trámite se COPIA (type) para conservarlo aunque el catálogo cambie.
        RequestEntity request = new RequestEntity();
        request.setProcedureId(procedure.id());
        request.setType(procedure.name());
        request.setDescription(dto.description());
        request.setUserId(ownerId);
        request.setRequesterEmail(contact.email());
        request.setRequesterName(contact.name());
        // Toda solicitud nace en INGRESADO (estado inicial de la máquina de estados).
        request.setStatus(RequestStatus.INGRESADO);
        request.setCitizenRut(dto.citizenRut());
        RequestEntity saved = repository.save(request);
        // 4) Primer registro del historial (estado anterior = null).
        recordChange(saved, null, RequestStatus.INGRESADO, null, requester);
        // 5) Evento interno: lo recogen RequestNotificationPublisher (RabbitMQ) y RequestStreamPublisher
        //    (Kafka) DESPUÉS del commit.
        events.publishEvent(RequestStatusChangedEvent.from(saved));
        return saved;
    }

    /** Admin, Operador y Auditor ven todas las solicitudes; el Cliente solo las suyas. */
    public List<RequestEntity> getAllRequests(Requester requester) {
        if (requester.canViewAll()) {
            return repository.findAll();
        }
        // Un Cliente: solo las solicitudes cuyo dueño es su oid.
        return repository.findByUserId(requireUserId(requester));
    }

    /** Para un Cliente, la solicitud de otro vecino se trata como inexistente (404) y no se revela. */
    public RequestEntity getRequestById(Long id, Requester requester) {
        RequestEntity request = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
        boolean isOwner = requester.userId() != null && requester.userId().equals(request.getUserId());
        // Si no es personal ni el dueño, se responde 404 (y no 403) para no revelar que la solicitud existe.
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
        // Reutiliza la validación de acceso (lanza 404 si no corresponde).
        getRequestById(id, requester);
        // showIdentity decide si se muestran nombres y cuadrilla (personal) o solo el rol (vecino).
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
        // Cliente: su propio correo y nombre (del JWT).
        if (!requester.canActOnBehalf()) {
            return new Contact(requester.email(), requester.name());
        }
        // Personal ingresando a nombre de un vecino: el correo del vecino es obligatorio.
        String citizenEmail = dto.citizenEmail();
        if (citizenEmail == null || citizenEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debes indicar el correo del vecino para ingresar la solicitud a su nombre");
        }
        // Se normaliza a minúsculas (Locale.ROOT evita problemas con idiomas como el turco).
        return new Contact(citizenEmail.trim().toLowerCase(Locale.ROOT), null);
    }

    // Par (correo, nombre) del solicitante: un record privado que solo usa esta clase.
    private record Contact(String email, String name) {
    }

    // Exige que el token traiga el "oid"; sin él no se puede saber quién es el dueño -> 403.
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
    // FLUJO "cambiar estado": bloquear fila -> validar transición -> exigir motivo/cuadrilla -> (si es admitir)
    // descontar cupo -> guardar -> historial -> publicar evento.
    @Transactional
    public RequestEntity updateStatus(Long id, RequestStatus newStatus, String reason, CrewAssignmentDto crew,
            Requester actor, String bearerToken) {
        // 1) Lectura con bloqueo (SELECT ... FOR UPDATE): serializa cambios simultáneos sobre la misma fila.
        RequestEntity request = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
        RequestStatus currentStatus = request.getStatus();

        // 2) Idempotencia: pedir el estado que ya tiene no hace nada (ni cupo, ni evento).
        if (newStatus == currentStatus) {
            return request;
        }
        // 3) Máquina de estados: si la transición no está permitida -> 409 con un mensaje explicativo.
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, invalidTransitionMessage(currentStatus, newStatus));
        }

        // 4) Datos obligatorios según el cambio: motivo (rechazo / cancelación) y cuadrilla (a terreno).
        String cleanReason = requireReasonWhenNeeded(currentStatus, newStatus, reason);
        CrewAssignmentDto cleanCrew = requireCrewIfGoingToField(newStatus, crew);

        // CUPO: solo la admisión (INGRESADO -> ADMITIDO) descuenta un cupo diario.
        // Se hace ANTES de guardar: si el catálogo responde 409 (sin cupo), la excepción deshace la transacción.
        if (currentStatus == RequestStatus.INGRESADO && newStatus == RequestStatus.ADMITIDO) {
            if (request.getProcedureId() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La solicitud no tiene un tipo de trámite del catálogo, no se puede descontar cupo");
            }
            catalog.consumeQuota(request.getProcedureId(), bearerToken);
        }

        // 5) Se guarda el nuevo estado y se anota quién lo cambió (historial).
        request.setStatus(newStatus);
        RequestEntity saved = repository.save(request);
        recordChange(saved, currentStatus, newStatus, cleanReason, cleanCrew, actor);
        // 6) Evento interno con el estado anterior y la actuación del personal (para auditoría en Kafka).
        events.publishEvent(RequestStatusChangedEvent.from(saved, cleanReason, currentStatus,
                new RequestStatusChangedEvent.StaffAction(actor.userId(), actor.name(), actor.primaryRole(),
                        cleanCrew)));
        return saved;
    }

    /** Rechazar y cancelar en terreno exigen motivo; en los demás cambios no se guarda. */
    private static String requireReasonWhenNeeded(RequestStatus current, RequestStatus newStatus, String reason) {
        boolean rejecting = newStatus == RequestStatus.RECHAZADO;
        // Cancelación en terreno = la cuadrilla no pudo cumplir y la solicitud vuelve a gestión.
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
        // clean() convierte vacío o solo espacios en null; así "falta un dato" se detecta con un solo chequeo.
        String name = clean(crew == null ? null : crew.name());
        String members = clean(crew == null ? null : crew.members());
        String plate = clean(crew == null ? null : crew.vehiclePlate());
        if (name == null || members == null || plate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Para pasar a EN_TERRENO debes indicar el nombre de la cuadrilla, sus integrantes y la patente del vehículo");
        }
        // La patente se guarda en mayúsculas; vehículo y teléfono son opcionales.
        return new CrewAssignmentDto(name, members, plate.toUpperCase(Locale.ROOT),
                clean(crew.vehicleType()), clean(crew.contactPhone()));
    }

    // Quita espacios; devuelve null si queda vacío.
    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // Sobrecarga corta: registro sin cuadrilla (lo usa el ingreso de la solicitud).
    private void recordChange(RequestEntity request, RequestStatus from, RequestStatus to, String reason,
            Requester actor) {
        recordChange(request, from, to, reason, null, actor);
    }

    // Agrega una fila a la bitácora: de qué estado a cuál, quién lo hizo (id, nombre, correo, rol), motivo y cuadrilla.
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
        // La cuadrilla solo existe en el cambio a EN_TERRENO.
        if (crew != null) {
            entry.setCrewName(crew.name());
            entry.setCrewMembers(crew.members());
            entry.setCrewVehiclePlate(crew.vehiclePlate());
            entry.setCrewVehicleType(crew.vehicleType());
            entry.setCrewPhone(crew.contactPhone());
        }
        history.save(entry);
    }

    // Mensaje de error amigable para una transición inválida, con las opciones que sí son válidas.
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
