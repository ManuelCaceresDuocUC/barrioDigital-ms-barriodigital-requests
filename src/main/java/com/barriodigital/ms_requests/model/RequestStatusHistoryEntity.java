package com.barriodigital.ms_requests.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un cambio de estado de una solicitud: de qué estado a cuál, cuándo, quién lo hizo y, si se rechazó,
 * por qué. El primer registro de cada solicitud es el ingreso ({@code fromStatus} nulo). Los registros
 * solo se agregan; nunca se modifican ni se borran.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "request_status_history",
        indexes = @Index(name = "idx_status_history_request", columnList = "request_id"))
public class RequestStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    /** Estado anterior; nulo en el registro de ingreso. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private RequestStatus toStatus;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    /** {@code oid} del usuario que hizo el cambio. */
    @Column(name = "changed_by_id")
    private String changedById;

    @Column(name = "changed_by_name")
    private String changedByName;

    @Column(name = "changed_by_email")
    private String changedByEmail;

    /** Rol con el que actuó: Admin, Operador, Auditor o Cliente. */
    @Column(name = "changed_by_role", length = 30)
    private String changedByRole;

    /** Motivo del cambio; hoy solo se pide (y se guarda) al rechazar. */
    @Column(length = 500)
    private String reason;
}
