package com.barriodigital.ms_requests.dto;

import java.time.LocalDateTime;

import com.barriodigital.ms_requests.model.RequestStatus;
import com.barriodigital.ms_requests.model.RequestStatusHistoryEntity;

/**
 * Un cambio de estado tal como lo ve quien consulta el historial.
 *
 * <p>Para el vecino (dueño de la solicitud) {@code changedByName} y {@code changedByEmail} van nulos:
 * ve el rol que actuó (por ejemplo "Operador"), no la identidad del funcionario.
 */
public record StatusChangeResponse(
        RequestStatus fromStatus,
        RequestStatus toStatus,
        LocalDateTime changedAt,
        String changedByName,
        String changedByEmail,
        String changedByRole,
        String reason) {

    public static StatusChangeResponse from(RequestStatusHistoryEntity entry, boolean showIdentity) {
        return new StatusChangeResponse(
                entry.getFromStatus(),
                entry.getToStatus(),
                entry.getChangedAt(),
                showIdentity ? entry.getChangedByName() : null,
                showIdentity ? entry.getChangedByEmail() : null,
                entry.getChangedByRole(),
                entry.getReason());
    }
}
