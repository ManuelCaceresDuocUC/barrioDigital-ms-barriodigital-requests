package com.barriodigital.ms_requests.model;


import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

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

    private String type; // Ej: "Poda de árbol", "Reparación luminaria"
    
    private String description;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    private String userId; // El ID del usuario que lo creó (lo extraeremos del token luego)

    @CreationTimestamp
    private LocalDateTime createdAt;
}