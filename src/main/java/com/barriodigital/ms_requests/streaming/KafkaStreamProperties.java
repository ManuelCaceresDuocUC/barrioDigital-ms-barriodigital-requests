package com.barriodigital.ms_requests.streaming;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * Conexión con Kafka y nombres de los tópicos donde se publican los eventos del trámite.
 * QUÉ ES: record enlazado al bloque barriodigital.kafka del yaml, con validación al arrancar.
 */
@ConfigurationProperties(prefix = "barriodigital.kafka")
public record KafkaStreamProperties(boolean enabled, String bootstrapServers, Duration sendTimeout, Topics topics) {

    public KafkaStreamProperties {
        Assert.notNull(topics, "barriodigital.kafka.topics es obligatorio");
        // Si no se configura, el tiempo máximo de envío es de 5 segundos.
        sendTimeout = sendTimeout == null ? Duration.ofSeconds(5) : sendTimeout;
        // La dirección de Kafka solo es obligatoria si Kafka está habilitado.
        if (enabled) {
            Assert.hasText(bootstrapServers, "barriodigital.kafka.bootstrap-servers es obligatorio (KAFKA_BOOTSTRAP_SERVERS)");
        }
    }

    // Nombres de los dos tópicos del sistema (los crea kafkaadmin al arrancar).
    public record Topics(String requestEvents, String auditTimeline) {

        public Topics {
            Assert.hasText(requestEvents, "topics.request-events es obligatorio");
            Assert.hasText(auditTimeline, "topics.audit-timeline es obligatorio");
        }
    }
}
