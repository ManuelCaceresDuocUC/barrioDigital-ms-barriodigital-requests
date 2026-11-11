package com.barriodigital.ms_requests.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * Routing keys de los eventos que publica requests, centralizadas en application.yaml. El exchange y la
 * lista de claves permitidas se definen una sola vez, en ms-barriodigital-rabbitadmin.
 */
@ConfigurationProperties(prefix = "barriodigital.messaging")
public record MessagingProperties(RoutingKeys routingKeys) {

    public MessagingProperties {
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
