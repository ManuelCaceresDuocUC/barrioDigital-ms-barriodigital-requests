package com.barriodigital.ms_requests.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class RolesAuthoritiesConverterTest {

    private final RolesAuthoritiesConverter converter = new RolesAuthoritiesConverter();

    private static Jwt jwt(List<String> roles) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none").subject("usuario");
        if (roles != null) {
            builder.claim("roles", roles);
        }
        return builder.build();
    }

    private List<String> authoritiesOf(List<String> roles) {
        return converter.convert(jwt(roles)).stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    void sinClaimRolesSeTrataComoCliente() {
        assertThat(authoritiesOf(null)).containsExactly("Cliente");
    }

    @Test
    void conRolesVaciosSeTrataComoCliente() {
        assertThat(authoritiesOf(List.of())).containsExactly("Cliente");
    }

    @Test
    void unRolDelPersonalNoRecibeClienteAdemas() {
        assertThat(authoritiesOf(List.of("Admin"))).containsExactly("Admin");
        assertThat(authoritiesOf(List.of("Operador"))).containsExactly("Operador");
        assertThat(authoritiesOf(List.of("Auditor"))).containsExactly("Auditor");
    }

    @Test
    void unClienteExplicitoNoSeDuplica() {
        assertThat(authoritiesOf(List.of("Cliente"))).containsExactly("Cliente");
    }

    @Test
    void unRolDesconocidoNoImpideSerCliente() {
        assertThat(authoritiesOf(List.of("OtroRol"))).containsExactlyInAnyOrder("OtroRol", "Cliente");
    }

    @Test
    void variosRolesConocidosSeConservan() {
        assertThat(authoritiesOf(List.of("Operador", "Auditor"))).containsExactlyInAnyOrder("Operador", "Auditor");
    }
}
