package com.barriodigital.ms_requests.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class KafkaStreamPropertiesTest {

    private static final KafkaStreamProperties.Topics TOPICS =
            new KafkaStreamProperties.Topics("requests.events", "audit.timeline");

    @Test
    void habilitadoExigeLosBrokers() {
        assertThatThrownBy(() -> new KafkaStreamProperties(true, " ", Duration.ofSeconds(1), TOPICS))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("KAFKA_BOOTSTRAP_SERVERS");
    }

    @Test
    void deshabilitadoNoNecesitaBrokers() {
        assertThat(new KafkaStreamProperties(false, null, null, TOPICS).sendTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void losTopicosSonObligatorios() {
        assertThatThrownBy(() -> new KafkaStreamProperties.Topics("requests.events", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
