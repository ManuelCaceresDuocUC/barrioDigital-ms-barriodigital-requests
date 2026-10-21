package com.barriodigital.ms_requests.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import com.barriodigital.ms_requests.model.RequestEntity;

import jakarta.persistence.LockModeType;

public interface RequestRepository extends JpaRepository<RequestEntity, Long> {

    /**
     * Lee la solicitud bloqueando su fila hasta el fin de la transacción. Evita que dos
     * "Admitir" simultáneos sobre la misma solicitud descuenten dos cupos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RequestEntity r where r.id = :id")
    Optional<RequestEntity> findByIdForUpdate(@Param("id") Long id);
    List<RequestEntity> findByUserId(String userId);
}
