package com.barriodigital.ms_requests.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * Routing keys de los eventos que publica requests, centralizadas en application.yaml. El exchange y la
 * lista de claves permitidas se definen una sola vez, en ms-barriodigital-rabbitadmin.
 *
 * <p>QUÉ ES: record enlazado al bloque barriodigital.messaging.routing-keys del yaml. La routing key es la
 * "etiqueta" que decide a qué cola llega el mensaje (email.send, crew.ticket, certificate.gen).
 */
@ConfigurationProperties(prefix = "barriodigital.messaging")
public record MessagingProperties(RoutingKeys routingKeys) {

    // Validación al arrancar: si falta la configuración, la app no inicia (fail-fast).
    public MessagingProperties {
        Assert.notNull(routingKeys, "barriodigital.messaging.routing-keys es obligatorio");
    }

    // Una clave por cada tipo de comando.
    public record RoutingKeys(String email, String crew, String certificate) {

        public RoutingKeys {
            Assert.hasText(email, "routing-keys.email es obligatorio");
            Assert.hasText(crew, "routing-keys.crew es obligatorio");
            Assert.hasText(certificate, "routing-keys.certificate es obligatorio");
        }
    }
}
