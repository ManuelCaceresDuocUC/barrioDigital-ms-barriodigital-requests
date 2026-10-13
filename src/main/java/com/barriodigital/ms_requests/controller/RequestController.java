package com.barriodigital.ms_requests.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;
import com.barriodigital.ms_requests.service.RequestService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

    private final RequestService service;

    // Solo Clientes y Admins pueden crear solicitudes
    @PostMapping
    @PreAuthorize("hasAnyAuthority('Cliente', 'Admin')")
    public ResponseEntity<RequestEntity> create(@RequestBody RequestEntity request) {
        return new ResponseEntity<>(service.createRequest(request), HttpStatus.CREATED);
    }

    // Todos los usuarios autenticados pueden listar/ver solicitudes
    @GetMapping
    @PreAuthorize("hasAnyAuthority('Cliente', 'Operador', 'Admin', 'Auditor')")
    public ResponseEntity<List<RequestEntity>> getAll() {
        return ResponseEntity.ok(service.getAllRequests());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('Cliente', 'Operador', 'Admin', 'Auditor')")
    public ResponseEntity<RequestEntity> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getRequestById(id));
    }

    // Solo el Operador municipal y el Admin pueden cambiar estados
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('Operador', 'Admin')")
    public ResponseEntity<RequestEntity> updateStatus(
            @PathVariable Long id, 
            @RequestBody Map<String, String> body) {
        
        RequestStatus newStatus = RequestStatus.valueOf(body.get("status"));
        return ResponseEntity.ok(service.updateStatus(id, newStatus));
    }
}