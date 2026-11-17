package com.barriodigital.ms_requests.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.barriodigital.ms_requests.model.RequestStatusHistoryEntity;

/**
 * QUÉ ES: repositorio de la bitácora de cambios de estado.
 */
public interface RequestStatusHistoryRepository extends JpaRepository<RequestStatusHistoryEntity, Long> {

    /** Cambios de una solicitud, del más antiguo al más reciente (el id desempata el mismo instante). */
    List<RequestStatusHistoryEntity> findByRequestIdOrderByChangedAtAscIdAsc(Long requestId);
}
