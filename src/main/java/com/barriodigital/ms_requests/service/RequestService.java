package com.barriodigital.ms_requests.service;


import java.util.List;

import org.springframework.stereotype.Service;

import com.barriodigital.ms_requests.model.RequestEntity;
import com.barriodigital.ms_requests.model.RequestStatus;
import com.barriodigital.ms_requests.repository.RequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository repository;

    public RequestEntity createRequest(RequestEntity request) {
        request.setStatus(RequestStatus.INGRESADO); // Estado inicial por defecto
        return repository.save(request);
    }

    public List<RequestEntity> getAllRequests() {
        return repository.findAll();
    }

    public RequestEntity getRequestById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trámite no encontrado"));
    }

    public RequestEntity updateStatus(Long id, RequestStatus newStatus) {
        RequestEntity request = getRequestById(id);
        
        // Aquí puedes agregar validaciones estrictas de la máquina de estados.
        // Por ejemplo, no pasar de RECHAZADO a ADMITIDO.
        
        request.setStatus(newStatus);
        
        // TODO: Emitir evento Kafka (Ej: "RequestStatusUpdated") para auditoría/notificaciones
        
        return repository.save(request);
    }
}