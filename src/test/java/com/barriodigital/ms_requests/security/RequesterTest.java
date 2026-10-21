package com.barriodigital.ms_requests.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class RequesterTest {

    private Jwt.Builder token() {
        return Jwt.withTokenValue("token").header("alg", "none");
    }

    @Test
    void usaPreferredUsernameCuandoNoHayClaimEmail() {
        Jwt jwt = token().claim("preferred_username", "ana@municipio.cl").claim("name", "Ana").build();
        Requester requester = Requester.from(jwt);
        assertThat(requester.email()).isEqualTo("ana@municipio.cl");
        assertThat(requester.name()).isEqualTo("Ana");
    }

    @Test
    void prefiereElClaimEmailSiExiste() {
        Jwt jwt = token().claim("email", "real@correo.cl").claim("preferred_username", "upn@municipio.cl").build();
        assertThat(Requester.from(jwt).email()).isEqualTo("real@correo.cl");
    }

    @Test
    void sinClaimsDeCorreoDevuelveNull() {
        Jwt jwt = token().claim("sub", "abc").build();
        assertThat(Requester.from(jwt).email()).isNull();
        assertThat(Requester.from(jwt).name()).isNull();
    }

    @Test
    void tomaElUserIdDelClaimOid() {
        Jwt jwt = token().claim("oid", "oid-123").claim("sub", "otro-valor").build();
        assertThat(Requester.from(jwt).userId()).isEqualTo("oid-123");
    }

    @Test
    void sinClaimOidElUserIdEsNull() {
        assertThat(Requester.from(token().claim("sub", "abc").build()).userId()).isNull();
    }

    @Test
    void leeLosRolesDelClaimRoles() {
        Jwt jwt = token().claim("roles", List.of("Operador")).build();
        assertThat(Requester.from(jwt).roles()).containsExactly("Operador");
    }

    @Test
    void sinClaimRolesNoTieneRoles() {
        Requester requester = Requester.from(token().claim("sub", "abc").build());
        assertThat(requester.roles()).isEmpty();
        assertThat(requester.canViewAll()).isFalse();
        assertThat(requester.canActOnBehalf()).isFalse();
    }

    @Test
    void soloAdminOperadorYAuditorVenTodo() {
        assertThat(new Requester("u", null, null, Set.of("Admin")).canViewAll()).isTrue();
        assertThat(new Requester("u", null, null, Set.of("Operador")).canViewAll()).isTrue();
        assertThat(new Requester("u", null, null, Set.of("Auditor")).canViewAll()).isTrue();
        assertThat(new Requester("u", null, null, Set.of("Cliente")).canViewAll()).isFalse();
    }

    @Test
    void soloAdminYOperadorActuanANombreDeUnVecino() {
        assertThat(new Requester("u", null, null, Set.of("Admin")).canActOnBehalf()).isTrue();
        assertThat(new Requester("u", null, null, Set.of("Operador")).canActOnBehalf()).isTrue();
        assertThat(new Requester("u", null, null, Set.of("Auditor")).canActOnBehalf()).isFalse();
        assertThat(new Requester("u", null, null, Set.of("Cliente")).canActOnBehalf()).isFalse();
    }
}
