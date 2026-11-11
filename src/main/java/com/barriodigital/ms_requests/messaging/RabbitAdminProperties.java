package com.barriodigital.ms_requests.messaging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cliente hacia ms-barriodigital-rabbitadmin, el único que habla con RabbitMQ. En Docker la URL
 * llega por la variable {@code RABBITADMIN_SERVICE_URL}.
 */
@ConfigurationProperties(prefix = "barriodigital.rabbitadmin")
public record RabbitAdminProperties(
        @DefaultValue("http://localhost:8086") String url,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout) {
}
