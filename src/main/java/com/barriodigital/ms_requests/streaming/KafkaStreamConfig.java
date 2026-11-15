package com.barriodigital.ms_requests.streaming;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(KafkaStreamProperties.class)
public class KafkaStreamConfig {

    @Bean(destroyMethod = "close")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "barriodigital.kafka.enabled", havingValue = "true", matchIfMissing = true)
    Producer<String, String> kafkaProducer(KafkaStreamProperties properties) {
        int timeoutMs = (int) properties.sendTimeout().toMillis();
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.bootstrapServers());
        config.put(ProducerConfig.CLIENT_ID_CONFIG, "ms-requests");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Sin pérdidas ni duplicados por reintento: espera a las réplicas en sincronía (min ISR 2 del clúster).
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, timeoutMs);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, Math.min(timeoutMs, 3_000));
        // Con Kafka caído, send() espera como máximo esto por los metadatos antes de fallar.
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, Math.min(timeoutMs, 2_000));
        return new KafkaProducer<>(config);
    }

    @Bean
    EventStream eventStream(org.springframework.beans.factory.ObjectProvider<Producer<String, String>> producer) {
        Producer<String, String> available = producer.getIfAvailable();
        return available == null ? new NoOpEventStream() : new KafkaEventStream(available, JsonMapper.builder().build());
    }
}
