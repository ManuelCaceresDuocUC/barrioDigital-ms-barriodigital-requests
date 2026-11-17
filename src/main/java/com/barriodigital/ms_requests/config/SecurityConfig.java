package com.barriodigital.ms_requests.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * QUÉ ES: seguridad del servicio. Valida el JWT de Azure AD (resource server OAuth 2.0) y extrae los roles.
 * La autorización fina por rol se declara con @PreAuthorize en el controlador.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Habilita las anotaciones @PreAuthorize en los controladores
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // API con token (sin cookies de sesión): CSRF no aplica.
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Toda ruta exige un JWT válido (401 si falta o es inválido).
                .anyRequest().authenticated()
            )
            // Valida firma/emisor/expiración con issuer-uri (application.yaml) y convierte los roles.
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    /** Roles del claim 'roles'; un usuario autenticado sin ningún rol conocido se trata como Cliente. */
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(new RolesAuthoritiesConverter());
        return jwtConverter;
    }
}
