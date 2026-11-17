package com.barriodigital.ms_requests.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración del cliente hacia ms-barriodigital-catalog (prefijo {@code catalog}).
 * QUÉ ES: record enlazado al bloque "catalog:" de application.yaml; @DefaultValue da el valor si falta.
 */
@ConfigurationProperties(prefix = "catalog")
public record CatalogProperties(
        @DefaultValue("http://localhost:8081") String url,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("10s") Duration readTimeout) {
}
