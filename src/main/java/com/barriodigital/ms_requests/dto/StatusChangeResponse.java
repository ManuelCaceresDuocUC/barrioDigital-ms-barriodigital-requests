package com.barriodigital.ms_requests.dto;

import java.time.LocalDateTime;

import com.barriodigital.ms_requests.model.RequestStatus;
import com.barriodigital.ms_requests.model.RequestStatusHistoryEntity;

/**
 * Un cambio de estado tal como lo ve quien consulta el historial.
 *
 * <p>Para el vecino (dueño de la solicitud) {@code changedByName} y {@code changedByEmail} van nulos:
 * ve el rol que actuó (por ejemplo "Operador"), no la identidad del funcionario. Los datos de la cuadrilla
 * y el motivo de una cancelación en terreno son internos: solo los ve el personal.
 */
public record StatusChangeResponse(
        RequestStatus fromStatus,
        RequestStatus toStatus,
        LocalDateTime changedAt,
        String changedByName,
        String changedByEmail,
        String changedByRole,
        String reason,
        String crewName,
        String crewMembers,
        String crewVehiclePlate,
        String crewVehicleType,
        String crewPhone) {

    public StatusChangeResponse(RequestStatus fromStatus, RequestStatus toStatus, LocalDateTime changedAt,
            String changedByName, String changedByEmail, String changedByRole, String reason) {
        this(fromStatus, toStatus, changedAt, changedByName, changedByEmail, changedByRole, reason,
                null, null, null, null, null);
    }

    public static StatusChangeResponse from(RequestStatusHistoryEntity entry, boolean showIdentity) {
        boolean internalCancel = entry.getFromStatus() == RequestStatus.EN_TERRENO
                && entry.getToStatus() == RequestStatus.EN_GESTION;
        boolean hideReason = !showIdentity && internalCancel;
        return new StatusChangeResponse(
                entry.getFromStatus(),
                entry.getToStatus(),
                entry.getChangedAt(),
                showIdentity ? entry.getChangedByName() : null,
                showIdentity ? entry.getChangedByEmail() : null,
                entry.getChangedByRole(),
                hideReason ? null : entry.getReason(),
                showIdentity ? entry.getCrewName() : null,
                showIdentity ? entry.getCrewMembers() : null,
                showIdentity ? entry.getCrewVehiclePlate() : null,
                showIdentity ? entry.getCrewVehicleType() : null,
                showIdentity ? entry.getCrewPhone() : null);
    }
}
