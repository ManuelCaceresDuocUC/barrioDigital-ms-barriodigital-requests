package com.barriodigital.ms_requests.security;

import java.util.List;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Datos del usuario que hace la petición, tomados del JWT de Azure AD.
 *
 * @param userId identificador del usuario en Azure AD (claim {@code oid})
 * @param roles  roles del claim {@code roles} (Admin, Operador, Cliente, Auditor)
 */
public record Requester(String userId, String email, String name, Set<String> roles) {

    /** Roles que ven las solicitudes de todos los vecinos (el Auditor solo lee). */
    private static final Set<String> VIEW_ALL = Set.of("Admin", "Operador", "Auditor");

    /** Roles que pueden ingresar una solicitud a nombre de un vecino. */
    private static final Set<String> ON_BEHALF = Set.of("Admin", "Operador");

    public Requester {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public static Requester from(Jwt jwt) {
        String email = firstNonBlank(jwt.getClaimAsString("email"), jwt.getClaimAsString("preferred_username"));
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new Requester(
                jwt.getClaimAsString("oid"),
                email,
                jwt.getClaimAsString("name"),
                roles == null ? Set.of() : Set.copyOf(roles));
    }

    /** Admin, Operador y Auditor ven todas las solicitudes; el Cliente solo las suyas. */
    public boolean canViewAll() {
        return roles.stream().anyMatch(VIEW_ALL::contains);
    }

    /** Admin y Operador pueden elegir el dueño de la solicitud que ingresan. */
    public boolean canActOnBehalf() {
        return roles.stream().anyMatch(ON_BEHALF::contains);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
