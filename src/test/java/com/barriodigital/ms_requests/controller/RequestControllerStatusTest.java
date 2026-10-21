package com.barriodigital.ms_requests.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.barriodigital.ms_requests.config.SecurityConfig;
import com.barriodigital.ms_requests.exception.GlobalExceptionHandler;
import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;
import com.barriodigital.ms_requests.service.RequestService;

/** PUT /api/requests/{id}/status: estado desconocido, transiciones inválidas y permisos por rol. */
@SpringJUnitWebConfig(classes = RequestControllerStatusTest.TestConfig.class)
class RequestControllerStatusTest {

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

    private ResultActions changeStatus(RequestPostProcessor user, String json) throws Exception {
        return mockMvc.perform(put("/api/requests/5/status").with(user)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void unEstadoDesconocidoResponde400ConLosValoresPermitidos() throws Exception {
        changeStatus(token("Operador"), "{\"status\":\"VOLANDO\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("Estado no válido")))
                .andExpect(jsonPath("$.message").value(containsString("EN_GESTION")));

        verifyNoInteractions(service);
    }

    @Test
    void elNombreConTildeNoEsUnEstadoValido() throws Exception {
        changeStatus(token("Operador"), "{\"status\":\"EN_GESTIÓN\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("EN_GESTION")));

        verifyNoInteractions(service);
    }

    @Test
    void sinEstadoResponde400() throws Exception {
        changeStatus(token("Operador"), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").value("El estado es obligatorio"));
        changeStatus(token("Operador"), "{\"status\":null}")
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void unCuerpoQueNoEsJsonResponde400() throws Exception {
        changeStatus(token("Operador"), "esto no es json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(service);
    }

    @Test
    void unaTransicionNoPermitidaResponde409ConElMensajeDelServicio() throws Exception {
        when(service.updateStatus(eq(5L), eq(RequestStatus.EN_TERRENO), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "No se puede pasar a EN_TERRENO sin haber sido admitida (estado actual: INGRESADO)"));

        changeStatus(token("Operador"), "{\"status\":\"EN_TERRENO\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("sin haber sido admitida")));
    }

    @Test
    void unCambioValidoResponde200() throws Exception {
        RequestEntity updated = new RequestEntity();
        updated.setId(5L);
        updated.setStatus(RequestStatus.ADMITIDO);
        when(service.updateStatus(eq(5L), eq(RequestStatus.ADMITIDO), any())).thenReturn(updated);

        changeStatus(token("Operador"), "{\"status\":\"ADMITIDO\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADMITIDO"));

        verify(service).updateStatus(eq(5L), eq(RequestStatus.ADMITIDO), any());
    }

    @Test
    void elAdminTambienPuedeCambiarEstados() throws Exception {
        when(service.updateStatus(eq(5L), eq(RequestStatus.ADMITIDO), any())).thenReturn(new RequestEntity());

        changeStatus(token("Admin"), "{\"status\":\"ADMITIDO\"}").andExpect(status().isOk());
    }

    @Test
    void elClienteYElAuditorNoPuedenCambiarEstados() throws Exception {
        changeStatus(token("Cliente"), "{\"status\":\"ADMITIDO\"}").andExpect(status().isForbidden());
        changeStatus(token("Auditor"), "{\"status\":\"ADMITIDO\"}").andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(put("/api/requests/5/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ADMITIDO\"}"))
                .andExpect(status().isUnauthorized());
    }
}
