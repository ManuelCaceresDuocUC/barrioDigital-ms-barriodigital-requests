package com.barriodigital.ms_requests.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trámite no encontrado"));
    }

    public RequestEntity updateStatus(Long id, RequestStatus newStatus) {
        RequestEntity request = getRequestById(id);
        RequestStatus currentStatus = request.getStatus();

        // 1. REGLA CLAVE DE EVALUACIÓN: Bloquear paso a EN_TERRENO si no fue ADMITIDO o puesto EN_GESTION
        if (newStatus == RequestStatus.EN_TERRENO && 
           (currentStatus != RequestStatus.ADMITIDO && currentStatus != RequestStatus.EN_GESTION)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, 
                "Transición inválida: No se puede pasar a EN_TERRENO sin admisión o gestión previa."
            );
        }

        // 2. MÁQUINA DE ESTADOS: Validar transiciones permitidas
        validateStatusTransition(currentStatus, newStatus);

        request.setStatus(newStatus);
        
        // TODO: Si el nuevo estado es ADMITIDO, descontar cupo en ms-catalog
        // TODO: Emitir eventos a RabbitMQ (Notificaciones) y Kafka (Auditoría/Métricas)
        
        return repository.save(request);
    }

    private void validateStatusTransition(RequestStatus current, RequestStatus target) {
        if (current == target) return; // Mismo estado

        boolean isValid = switch (current) {
            case INGRESADO -> target == RequestStatus.ADMITIDO || target == RequestStatus.RECHAZADO;
            case ADMITIDO -> target == RequestStatus.EN_GESTION || target == RequestStatus.RECHAZADO;
            case EN_GESTION -> target == RequestStatus.EN_TERRENO || target == RequestStatus.RECHAZADO;
            case EN_TERRENO -> target == RequestStatus.RESUELTO || target == RequestStatus.RECHAZADO;
            case RESUELTO, RECHAZADO -> false; // Estados finales, no se modifican
        };

        if (!isValid) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, 
                String.format("No se permite cambiar el estado de %s a %s", current, target)
            );
        }
    }
}