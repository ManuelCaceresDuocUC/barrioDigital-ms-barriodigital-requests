package com.barriodigital.ms_requests.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados de una solicitud y sus transiciones permitidas. El nombre {@code EN_GESTION} va sin
 * tilde a propósito: es un identificador técnico (API, base de datos y enum); la tilde solo
 * corresponde al texto que se muestra al usuario.
 *
 * <p>QUÉ ES: la MÁQUINA DE ESTADOS del negocio, definida como un enum. Flujo normal:
 * INGRESADO -> ADMITIDO -> EN_GESTION -> EN_TERRENO -> RESUELTO; RECHAZADO es posible desde casi todos.
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
        // "this" es el estado actual; el switch devuelve el conjunto de destinos válidos.
        return switch (this) {
            case INGRESADO -> EnumSet.of(ADMITIDO, RECHAZADO);
            case ADMITIDO -> EnumSet.of(EN_GESTION, RECHAZADO);
            case EN_GESTION -> EnumSet.of(EN_TERRENO, RESUELTO, RECHAZADO);
            // EN_GESTION: la cuadrilla no pudo cumplir la tarea; vuelve a gestión para reasignarla o rechazarla
            case EN_TERRENO -> EnumSet.of(RESUELTO, EN_GESTION);
            // Estados finales: no hay ningún destino posible.
            case RESUELTO, RECHAZADO -> EnumSet.noneOf(RequestStatus.class);
        };
    }

    // ¿Se puede ir de este estado al destino indicado? (lo usa RequestService para validar)
    public boolean canTransitionTo(RequestStatus target) {
        return allowedTransitions().contains(target);
    }

    // Un estado es final si no tiene transiciones de salida.
    public boolean isFinal() {
        return allowedTransitions().isEmpty();
    }
}
