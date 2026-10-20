package com.barriodigital.ms_requests.security;

import static org.assertj.core.api.Assertions.assertThat;

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
}