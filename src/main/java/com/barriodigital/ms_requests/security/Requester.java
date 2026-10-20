package com.barriodigital.ms_requests.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Datos del usuario que hace la petición, tomados del JWT de Azure AD. */
public record Requester(String email, String name) {

    public static Requester from(Jwt jwt) {
        String email = firstNonBlank(jwt.getClaimAsString("email"), jwt.getClaimAsString("preferred_username"));
        return new Requester(email, jwt.getClaimAsString("name"));
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