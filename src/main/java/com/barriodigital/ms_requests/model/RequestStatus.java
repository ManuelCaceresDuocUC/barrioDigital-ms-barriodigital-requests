package com.barriodigital.ms_requests.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados de una solicitud y sus transiciones permitidas. El nombre {@code EN_GESTION} va sin
 * tilde a propósito: es un identificador técnico (API, base de datos y enum); la tilde solo
 * corresponde al texto que se muestra al usuario.
 */
public enum RequestStatus {
    INGRESADO,
    ADMITIDO,
    EN_GESTION,
    EN_TERRENO,
    RESUELTO,
    RECHAZADO;

    /** Estados a los que se puede pasar desde este. RESUELTO y RECHAZADO son finales. */
    public Set<RequestStatus> allowedTransitions() {
        return switch (this) {
            case INGRESADO -> EnumSet.of(ADMITIDO, RECHAZADO);
            case ADMITIDO -> EnumSet.of(EN_GESTION, RECHAZADO);
            case EN_GESTION -> EnumSet.of(EN_TERRENO, RESUELTO, RECHAZADO);
            case EN_TERRENO -> EnumSet.of(RESUELTO);
            case RESUELTO, RECHAZADO -> EnumSet.noneOf(RequestStatus.class);
        };
    }

    public boolean canTransitionTo(RequestStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isFinal() {
        return allowedTransitions().isEmpty();
    }
}
