package com.barriodigital.ms_requests.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Convierte el claim {@code roles} del JWT de Azure AD en authorities, sin prefijo.
 *
 * <p>Un usuario autenticado que no trae ningún rol conocido (por ejemplo, un vecino que se registró
 * solo y aún no tiene rol asignado) se trata como {@value #DEFAULT_ROLE}. Los roles del personal
 * (Admin, Operador, Auditor) solo existen si se asignan en Azure: nunca se otorgan por defecto.
 *
 * <p>QUÉ ES: puente entre el token de Azure y Spring Security (idéntico al de catalog). Azure entrega los
 * roles en el claim "roles"; Spring por defecto lee "scope", por eso se necesita este conversor.
 */
public class RolesAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    // Rol que se asume cuando el token no trae ninguno conocido ("sin rol = Cliente").
    static final String DEFAULT_ROLE = "Cliente";

    // Lista blanca de roles reconocidos.
    private static final Set<String> KNOWN_ROLES = Set.of("Cliente", "Operador", "Admin", "Auditor");

    // Conversor estándar de Spring, configurado abajo para leer el claim "roles".
    private final JwtGrantedAuthoritiesConverter delegate = new JwtGrantedAuthoritiesConverter();

    public RolesAuthoritiesConverter() {
        delegate.setAuthoritiesClaimName("roles");
        // Sin prefijo: así @PreAuthorize("hasAuthority('Admin')") funciona con el nombre tal cual.
        delegate.setAuthorityPrefix(""); // 'Admin', 'Operador', 'Cliente', 'Auditor' tal cual
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        // Roles que trae el token (null si no hay claim).
        Collection<GrantedAuthority> fromClaim = delegate.convert(jwt);
        List<GrantedAuthority> authorities = new ArrayList<>(fromClaim == null ? List.of() : fromClaim);

        // ¿Trae al menos un rol de la lista blanca?
        boolean hasKnownRole = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(KNOWN_ROLES::contains);
        // Si no, se agrega "Cliente": solo permisos básicos, nunca de personal.
        if (!hasKnownRole) {
            authorities.add(new SimpleGrantedAuthority(DEFAULT_ROLE));
        }
        return authorities;
    }
}
