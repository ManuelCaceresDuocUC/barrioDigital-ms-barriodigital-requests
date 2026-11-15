package com.barriodigital.ms_requests.streaming;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/** Conexión con Kafka y nombres de los tópicos donde se publican los eventos del trámite. */
@ConfigurationProperties(prefix = "barriodigital.kafka")
public record KafkaStreamProperties(boolean enabled, String bootstrapServers, Duration sendTimeout, Topics topics) {

    public KafkaStreamProperties {
        Assert.notNull(topics, "barriodigital.kafka.topics es obligatorio");
        sendTimeout = sendTimeout == null ? Duration.ofSeconds(5) : sendTimeout;
        if (enabled) {
            Assert.hasText(bootstrapServers, "barriodigital.kafka.bootstrap-servers es obligatorio (KAFKA_BOOTSTRAP_SERVERS)");
        }
    }

    public record Topics(String requestEvents, String auditTimeline) {

        public Topics {
            Assert.hasText(requestEvents, "topics.request-events es obligatorio");
            Assert.hasText(auditTimeline, "topics.audit-timeline es obligatorio");
        }
    }
}
