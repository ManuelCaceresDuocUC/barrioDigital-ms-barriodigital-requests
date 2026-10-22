package com.barriodigital.ms_requests.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "requests")
public class RequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id del tipo de trámite en ms-barriodigital-catalog. */
    @Column(name = "procedure_id")
    private Long procedureId;

    /** Nombre del tipo de trámite al momento de crear la solicitud (copia del catálogo). */
    private String type;

    private String description;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    /** Dueño de la solicitud: el {@code oid} del JWT (o el del vecino, si la ingresó un Operador/Admin). */
    private String userId;

    /**
     * Correo del solicitante: el del JWT si lo creó el propio vecino, o el que indicó el Operador/Admin
     * si ingresó la solicitud a nombre de un vecino. Se usa para notificar y para mostrar al personal.
     */
    @Column(name = "requester_email")
    private String requesterEmail;

    /** Nombre del solicitante (JWT). Vacío si la solicitud la ingresó el personal a nombre de un vecino. */
    @Column(name = "requester_name")
    private String requesterName;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /** RUT del ciudadano (opcional). Solo para emitir certificados: no se expone por la API. */
    @JsonIgnore
    @Column(name = "citizen_rut")
    private String citizenRut;
}
