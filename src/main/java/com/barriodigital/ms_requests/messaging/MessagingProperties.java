package com.barriodigital.ms_requests.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/** Exchange y routing keys de los eventos que publica requests, centralizados en application.yaml. */
@ConfigurationProperties(prefix = "barriodigital.messaging")
public record MessagingProperties(String exchange, RoutingKeys routingKeys) {

    public MessagingProperties {
        Assert.hasText(exchange, "barriodigital.messaging.exchange es obligatorio");
        Assert.notNull(routingKeys, "barriodigital.messaging.routing-keys es obligatorio");
    }

    public record RoutingKeys(String email, String crew, String certificate) {
        public RoutingKeys {
            Assert.hasText(email, "routing-keys.email es obligatorio");
            Assert.hasText(crew, "routing-keys.crew es obligatorio");
            Assert.hasText(certificate, "routing-keys.certificate es obligatorio");
        }
    }
}