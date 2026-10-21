package com.barriodigital.ms_requests.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.barriodigital.ms_requests.config.SecurityConfig;
import com.barriodigital.ms_requests.dto.CreateRequestDto;
import com.barriodigital.ms_requests.exception.GlobalExceptionHandler;
import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.security.Requester;
import com.barriodigital.ms_requests.service.RequestService;

/** Acceso por rol a las solicitudes: Cliente, Operador, Admin y Auditor, con tokens simulados. */
@SpringJUnitWebConfig(classes = RequestControllerAccessTest.TestConfig.class)
class RequestControllerAccessTest {

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

    /** Token con el claim oid y el rol, y la autoridad que usa @PreAuthorize. */
    private static RequestPostProcessor token(String oid, String role) {
        return jwt()
                .jwt(jwt -> jwt.claim("oid", oid).claim("roles", List.of(role)).claim("name", role))
                .authorities(new SimpleGrantedAuthority(role));
    }

    private static final String CUERPO = "{\"procedureId\":7,\"description\":\"Certificado\",\"userId\":\"oid-vecino\"}";

    // ---------------------------------------------------------------- crear

    @Test
    void elClientePuedeCrearYElServicioRecibeSuOid() throws Exception {
        when(service.createRequest(any(), any(), any())).thenReturn(new RequestEntity());

        mockMvc.perform(post("/api/requests")
                        .with(token("oid-ana", "Cliente"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated());

        ArgumentCaptor<Requester> captor = ArgumentCaptor.forClass(Requester.class);
        verify(service).createRequest(any(CreateRequestDto.class), captor.capture(), any());
        assertThat(captor.getValue().userId()).isEqualTo("oid-ana");
        assertThat(captor.getValue().roles()).containsExactly("Cliente");
    }

    @Test
    void elOperadorYElAdminPuedenCrear() throws Exception {
        when(service.createRequest(any(), any(), any())).thenReturn(new RequestEntity());

        mockMvc.perform(post("/api/requests").with(token("oid-o", "Operador"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/requests").with(token("oid-a", "Admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated());
    }

    @Test
    void elAuditorNoPuedeCrear() throws Exception {
        mockMvc.perform(post("/api/requests").with(token("oid-u", "Auditor"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    // ---------------------------------------------------------------- listar

    @Test
    void elServicioRecibeQuienListaParaFiltrarEnElBackend() throws Exception {
        when(service.getAllRequests(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/requests").with(token("oid-ana", "Cliente")))
                .andExpect(status().isOk());

        ArgumentCaptor<Requester> captor = ArgumentCaptor.forClass(Requester.class);
        verify(service).getAllRequests(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo("oid-ana");
        assertThat(captor.getValue().canViewAll()).isFalse();
    }

    @Test
    void operadorAdminYAuditorPuedenListar() throws Exception {
        when(service.getAllRequests(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/requests").with(token("oid-o", "Operador"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/requests").with(token("oid-a", "Admin"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/requests").with(token("oid-u", "Auditor"))).andExpect(status().isOk());
    }

    @Test
    void sinTokenSeResponde401() throws Exception {
        mockMvc.perform(get("/api/requests")).andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    // ------------------------------------------------------------ ver una

    @Test
    void unaSolicitudAjenaSeResponde404ConElMensajeDelServicio() throws Exception {
        when(service.getRequestById(eq(2L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Trámite no encontrado"));

        mockMvc.perform(get("/api/requests/2").with(token("oid-ana", "Cliente")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Trámite no encontrado"));
    }

    @Test
    void unaSolicitudPropiaSeEntrega() throws Exception {
        RequestEntity propia = new RequestEntity();
        propia.setId(1L);
        propia.setUserId("oid-ana");
        when(service.getRequestById(eq(1L), any())).thenReturn(propia);

        mockMvc.perform(get("/api/requests/1").with(token("oid-ana", "Cliente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value("oid-ana"));
    }
}
