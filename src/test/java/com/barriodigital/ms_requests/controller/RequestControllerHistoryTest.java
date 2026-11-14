package com.barriodigital.ms_requests.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.barriodigital.ms_requests.config.SecurityConfig;
import com.barriodigital.ms_requests.dto.StatusChangeResponse;
import com.barriodigital.ms_requests.exception.GlobalExceptionHandler;
import com.barriodigital.ms_requests.model.RequestStatus;
import com.barriodigital.ms_requests.service.RequestService;

/** GET /api/requests/{id}/history: permisos por rol y forma de la respuesta. */
@SpringJUnitWebConfig(classes = RequestControllerHistoryTest.TestConfig.class)
class RequestControllerHistoryTest {

    @Configuration
    @EnableWebMvc
    @Import({ SecurityConfig.class, GlobalExceptionHandler.class, RequestController.class })
    static class TestConfig {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new BadJwtException("decoder de prueba");
            };
        }

        @Bean
        RequestService requestService() {
            return mock(RequestService.class);
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RequestService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(service);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor token(String role) {
        return jwt()
                .jwt(jwt -> jwt.claim("oid", "oid-" + role).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority(role));
    }

    @Test
    void devuelveLosCambiosDeEstadoComoJson() throws Exception {
        when(service.getHistory(eq(5L), any())).thenReturn(List.of(
                new StatusChangeResponse(null, RequestStatus.INGRESADO, LocalDateTime.of(2026, 11, 13, 10, 0),
                        "Ana", "ana@correo.cl", "Cliente", null),
                new StatusChangeResponse(RequestStatus.EN_GESTION, RequestStatus.RECHAZADO,
                        LocalDateTime.of(2026, 11, 13, 11, 0), "Olga", "olga@municipio.cl", "Operador",
                        "Falta documentación")));

        mockMvc.perform(get("/api/requests/5/history").with(token("Operador")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].toStatus").value("INGRESADO"))
                .andExpect(jsonPath("$[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$[1].fromStatus").value("EN_GESTION"))
                .andExpect(jsonPath("$[1].toStatus").value("RECHAZADO"))
                .andExpect(jsonPath("$[1].changedByRole").value("Operador"))
                .andExpect(jsonPath("$[1].reason").value("Falta documentación"));
    }

    @Test
    void todosLosRolesPuedenConsultarElHistorial() throws Exception {
        when(service.getHistory(eq(5L), any())).thenReturn(List.of());

        for (String role : new String[] { "Cliente", "Operador", "Admin", "Auditor" }) {
            mockMvc.perform(get("/api/requests/5/history").with(token(role))).andExpect(status().isOk());
        }
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/requests/5/history")).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void unaSolicitudAjenaOInexistenteResponde404() throws Exception {
        when(service.getHistory(eq(5L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Trámite no encontrado"));

        mockMvc.perform(get("/api/requests/5/history").with(token("Cliente")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Trámite no encontrado"));
    }
}
