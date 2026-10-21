package com.barriodigital.ms_requests.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.barriodigital.ms_requests.dto.CreateRequestDto;
import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;
import com.barriodigital.ms_requests.security.Requester;
import com.barriodigital.ms_requests.service.RequestService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService service;

    // Cliente, Operador y Admin pueden crear solicitudes (el Operador, también a nombre de un vecino)
    @PostMapping
    @PreAuthorize("hasAnyAuthority('Cliente', 'Operador', 'Admin')")
    public ResponseEntity<RequestEntity> create(
            @Valid @RequestBody CreateRequestDto request,
            @AuthenticationPrincipal Jwt jwt) {
        return new ResponseEntity<>(
                service.createRequest(request, Requester.from(jwt), jwt.getTokenValue()), HttpStatus.CREATED);
    }

    // El Cliente ve solo las suyas; Admin, Operador y Auditor (solo lectura) ven todas
    @GetMapping
    @PreAuthorize("hasAnyAuthority('Cliente', 'Operador', 'Admin', 'Auditor')")
    public ResponseEntity<List<RequestEntity>> getAll(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.getAllRequests(Requester.from(jwt)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Cliente', 'Operador', 'Admin', 'Auditor')")
    public ResponseEntity<RequestEntity> getById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.getRequestById(id, Requester.from(jwt)));
    }

    // Solo el Operador municipal y el Admin pueden cambiar estados
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('Operador', 'Admin')")
    public ResponseEntity<RequestEntity> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {

        RequestStatus newStatus = RequestStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(service.updateStatus(id, newStatus, jwt.getTokenValue()));
    }
}
